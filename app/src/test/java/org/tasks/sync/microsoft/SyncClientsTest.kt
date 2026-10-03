package org.tasks.sync.microsoft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.sync.SyncClients
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/** The Microsoft registration values live in SyncClients and nowhere else; the manifest has to agree with them. */
class SyncClientsTest {
    private val moduleDir = File(".").absoluteFile.normalize().let { if (File(it, "src").isDirectory) it else File(it, "app") }

    @Test
    fun signatureHashIsTheBase64OfTheSha1OfTheReleaseKey() {
        // The SHA-1 in docs/SYNC-PLAN.md section 5 (4Tasks' own release key, not 4Dictate's).
        val sha1 = "5D:70:F9:67:FC:56:E7:00:F2:61:44:D7:2A:65:5B:72:29:8C:0E:6A"
            .split(':').map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(20, sha1.size)
        assertEquals(SyncClients.MICROSOFT_SIGNATURE_HASH, Base64.getEncoder().encodeToString(sha1))
        assertEquals("XXD5Z/xW5wDyYUTXKmVbcimMDmo=", SyncClients.MICROSOFT_SIGNATURE_HASH)
        // and it is a digest of 20 bytes, as MSAL computes it
        assertEquals(20, MessageDigest.getInstance("SHA-1").digestLength)
    }

    @Test
    fun redirectIsTheFormEntraShows() {
        assertEquals(
            "msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D",
            SyncClients.microsoftRedirectUri("uk.mr_biz.fourtasks"),
        )
    }

    @Test
    fun redirectDecodesToThePathTheManifestMatches() {
        val uri = URI(SyncClients.microsoftRedirectUri("uk.mr_biz.fourtasks"))
        assertEquals("msauth", uri.scheme)
        assertEquals("uk.mr_biz.fourtasks", uri.authority) // (java.net.URI's host is null for an underscore)
        // Android compares an intent filter's android:path with the decoded path.
        assertEquals("/XXD5Z/xW5wDyYUTXKmVbcimMDmo=", uri.path)
        assertEquals(SyncClients.microsoftRedirectPath, uri.path)
    }

    @Test
    fun authorityIsCommonAndDiscoveryUrlFollowsIt() {
        assertEquals("common", SyncClients.MICROSOFT_AUTHORITY)
        assertEquals(
            "https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration",
            SyncClients.microsoftDiscoveryUrl,
        )
    }

    @Test
    fun scopeIsTheOnePermissionPlusTheStandardSignInSet() {
        assertEquals("user.read Tasks.ReadWrite openid offline_access email", SyncClients.MICROSOFT_SCOPE)
        assertTrue(SyncClients.MICROSOFT_SCOPE.split(' ').contains(SyncClients.MICROSOFT_PERMISSION))
    }

    @Test
    fun notConfiguredWhileTheClientIdIsAPlaceholder() {
        assertEquals(SyncClients.NOT_SET == SyncClients.MICROSOFT_CLIENT_ID, !SyncClients.microsoftConfigured)
        assertFalse(SyncClients.NOT_SET.isBlank())
    }

    @Test
    fun helpPageIsOnOurDomain() {
        assertEquals("https://mr-biz.uk/4tasks/admin/#microsoft", SyncClients.ADMIN_HELP_URL_MICROSOFT)
        assertTrue(SyncClients.ADMIN_HELP_URL_MICROSOFT.startsWith("https://${SyncClients.PUBLISHER_DOMAIN}/"))
    }

    private fun manifest(path: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(moduleDir, path))

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val toolsNs = "http://schemas.android.com/tools"

    @Test
    fun genericManifestDeclaresExactlyOneRedirectActivityWithOnlyTheMsauthRedirect() {
        val activities = manifest("src/generic/AndroidManifest.xml").getElementsByTagName("activity")
        val receivers = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
            .filter { it.getAttributeNS(androidNs, "name") == "net.openid.appauth.RedirectUriReceiverActivity" }
        assertEquals(1, receivers.size)
        val activity = receivers.single()
        assertEquals("true", activity.getAttributeNS(androidNs, "exported"))
        // replace, so the library's own filter (appAuthRedirectScheme) never merges in
        assertEquals("replace", activity.getAttributeNS(toolsNs, "node"))
        val filters = activity.getElementsByTagName("intent-filter")
        assertEquals(1, filters.length)
        val data = (filters.item(0) as org.w3c.dom.Element).getElementsByTagName("data")
        assertEquals(1, data.length)
        val d = data.item(0) as org.w3c.dom.Element
        assertEquals("msauth", d.getAttributeNS(androidNs, "scheme"))
        assertEquals("\${applicationId}", d.getAttributeNS(androidNs, "host"))
        assertEquals(SyncClients.microsoftRedirectPath, d.getAttributeNS(androidNs, "path"))
    }

    @Test
    fun mainManifestNoLongerRemovesTheRedirectActivity() {
        val activities = manifest("src/main/AndroidManifest.xml").getElementsByTagName("activity")
        val names = (0 until activities.length).map { (activities.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name") }
        assertFalse(names.contains("net.openid.appauth.RedirectUriReceiverActivity"))
    }

    @Test
    fun noAppAuthSchemeOtherThanMsauth() {
        val gradle = File(moduleDir, "build.gradle.kts").readText()
        assertTrue(gradle.contains("manifestPlaceholders[\"appAuthRedirectScheme\"] = \"msauth\""))
    }
}
