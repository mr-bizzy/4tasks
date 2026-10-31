package org.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** "Migrate to Tasks.org Cloud" led to PurchaseActivity, which no manifest declares, so tapping it stopped the app (0.1.17). */
class NoMigrateToCloudRowTest {
    private val moduleDir = File(".").absoluteFile.normalize().let { if (File(it, "src").isDirectory) it else File(it, "app") }

    @Test
    fun theLocalAccountScreenIsToldNotToShowTheRow() {
        val source = File(moduleDir, "src/main/java/org/tasks/preferences/fragments/LocalAccount.kt").readText()
        assertTrue(source.contains("showMigrate = false"))
        assertFalse(source.contains("showMigrate = !"))
    }

    @Test
    fun purchaseActivityIsStillNotDeclared() {
        listOf("src/main/AndroidManifest.xml", "src/generic/AndroidManifest.xml").forEach {
            assertFalse(it, File(moduleDir, it).readText().contains("PurchaseActivity"))
        }
    }
}
