package org.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The merged manifest that ships (0.1.15, privacy audit of 0.1.14): no Pebble door, no Microsoft sign-in
 * activity and no msauth return, and cloud backup and phone-to-phone transfer both off.
 * Reads the merged genericRelease manifest (testGenericDebugUnitTest builds it first, app/build.gradle.kts);
 * if it is missing (a run from the IDE), the merged manifest Robolectric uses, with the same components.
 */
class ShippedManifestTest {
    private val moduleDir = File(".").absoluteFile.normalize().let { if (File(it, "src").isDirectory) it else File(it, "app") }
    private val android = "http://schemas.android.com/apk/res/android"

    private val manifestFile: File by lazy {
        File(moduleDir, "build/intermediates/merged_manifests/genericRelease/processGenericReleaseManifest/AndroidManifest.xml")
            .takeIf { it.isFile }
            ?: run {
                val props = Properties()
                javaClass.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")!!.use { props.load(it) }
                File(moduleDir, props.getProperty("android_merged_manifest"))
            }
    }

    private val application: Element by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(manifestFile).getElementsByTagName("application").item(0) as Element
    }

    private val components: List<Element> by lazy {
        (0 until application.childNodes.length).map { application.childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName in setOf("activity", "activity-alias", "receiver", "service", "provider") }
    }

    private fun Element.name() = getAttributeNS(android, "name")
    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.isExported() =
        when (getAttributeNS(android, "exported")) {
            "true" -> true
            "false" -> false
            else -> children("intent-filter").isNotEmpty()
        }

    @Test
    fun theManifestIsTheMergedOne() {
        assertTrue(manifestFile.path, manifestFile.isFile)
        // merged: library components are present
        assertTrue(components.any { it.name().startsWith("androidx.") })
    }

    @Test
    fun nothingListensForPebble() {
        val text = manifestFile.readText()
        assertFalse(text.contains("getpebble", ignoreCase = true))
        assertFalse(components.any { it.name().contains("pebble", ignoreCase = true) })
    }

    @Test
    fun noMicrosoftSignInOrMsauthReturn() {
        val names = components.map { it.name() }
        assertFalse(names.any { it.contains("MicrosoftAuthenticationActivity") })
        assertFalse(names.any { it.contains("MicrosoftSignInProblemActivity") })
        assertFalse(names.contains("net.openid.appauth.RedirectUriReceiverActivity"))
        val schemes = components.flatMap { it.children("data") }.map { it.getAttributeNS(android, "scheme") }
        assertFalse(schemes.contains("msauth"))
    }

    @Test
    fun noExportedComponentNamesPebbleMicrosoftOrMsauth() {
        val exported = components.filter { it.isExported() }
        val bad = exported.filter { c ->
            val n = c.name()
            n.contains("pebble", true) || n.contains("microsoft", true) || n.contains("appauth", true) ||
                c.children("data").any { it.getAttributeNS(android, "scheme") == "msauth" }
        }
        assertEquals(emptyList<String>(), bad.map { it.name() })
    }

    @Test
    fun backupAndDeviceTransferAreOff() {
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/full_backup_content", application.getAttributeNS(android, "fullBackupContent"))
    }

    @Test
    fun dataExtractionRulesExcludeEveryDomainFromBoth() {
        val rules = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(moduleDir, "src/main/res/xml/data_extraction_rules.xml")).documentElement
        val every = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
        for (section in listOf("cloud-backup", "device-transfer")) {
            val s = rules.getElementsByTagName(section).item(0) as Element
            assertTrue("$section has no include", s.getElementsByTagName("include").length == 0)
            val excludes = s.getElementsByTagName("exclude").let { l -> (0 until l.length).map { l.item(it) as Element } }
            assertEquals(section, every, excludes.filter { it.getAttribute("path") == "." }.map { it.getAttribute("domain") }.toSet())
        }
    }
}
