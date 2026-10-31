package org.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.tasks.caldav.LocalListSettingsActivity
import org.tasks.data.composeIcon
import org.tasks.data.composeTitle
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.listSettingsClass
import java.io.File

/** EteSync is gone since 0.1.17; a database from before can still hold its account, lists and tasks. */
class OldEteSyncAccountTest {
    private val account = CaldavAccount(uuid = "old", accountType = CaldavAccount.TYPE_ETEBASE)

    @Test
    fun itsListsOpenTheLocalSettingsNotTheCaldavOnes() {
        // The CalDAV screen would send its password to the collection id as if that were a server.
        assertEquals(LocalListSettingsActivity::class.java, account.listSettingsClass())
    }

    /** TaskListViewModel reads the title with !!, so a row with no title would crash the task list. */
    @Test
    fun itKeepsATitleAndAnIcon() {
        assertNotNull(account.composeTitle)
        assertNotNull(account.composeIcon)
    }

    @Test
    fun theLibraryIsNotInTheBuildFiles() {
        val root = File(".").absoluteFile.normalize().let { if (File(it, "kmp").isDirectory) it else it.parentFile }
        listOf("app/build.gradle.kts", "kmp/build.gradle.kts", "gradle/libs.versions.toml").forEach {
            assertFalse(it, File(root, it).readText().contains("etebase", ignoreCase = true))
        }
        assertFalse(File(root, "libs/client-jvm-2.3.2.jar").exists())
    }
}
