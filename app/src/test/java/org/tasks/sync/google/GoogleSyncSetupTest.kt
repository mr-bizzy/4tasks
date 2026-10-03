package org.tasks.sync.google

import com.google.api.services.tasks.TasksScopes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.R
import org.tasks.googleapis.GoogleFailure
import org.tasks.sync.SyncClients
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** What Google Tasks sync is allowed to ask of the phone and of Google (docs/SYNC-PLAN.md sections 2, 3 and 4). */
class GoogleSyncSetupTest {
    private fun appDir(): File = File(".").absoluteFile.normalize().let { if (File(it, "src/main").isDirectory) it else File(it, "app") }

    private val manifest by lazy {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder()
            .parse(File(appDir(), "src/main/AndroidManifest.xml"))
    }

    private fun children(tag: String) = manifest.getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) } }
    private fun attr(n: org.w3c.dom.Node, name: String) = n.attributes?.getNamedItem(name)?.nodeValue

    @Test
    fun theScopeTheFlowAsksForIsTheOneInTheConfigPlaceAndNothingElse() {
        assertEquals(SyncClients.GOOGLE_TASKS_SCOPE, TasksScopes.TASKS)
        val login = File(appDir(), "src/main/java/com/todoroo/astrid/gtasks/auth/GtasksLoginActivity.kt").readText()
        assertTrue(login.contains("getTasksAuthToken"))
        assertFalse("sign-in must not ask for Drive", login.contains("getDriveAuthToken") || login.contains("DriveScopes"))
    }

    @Test
    fun noNewPermissionIsNeededForGoogleTasks() {
        // INTERNET and ACCESS_NETWORK_STATE came with Phase A. The account manager needs none on Android 13+: the picker
        // makes the chosen account visible to the app (Android 8+) and the token request needs no permission after API 22.
        val declared = children("uses-permission")
            .filter { attr(it, "tools:node") != "remove" }
            .map { attr(it, "android:name")!!.removePrefix("android.permission.") }
            .toSet()
        assertEquals(
            setOf(
                "POST_NOTIFICATIONS", "VIBRATE", "SCHEDULE_EXACT_ALARM", "RECEIVE_BOOT_COMPLETED", "WAKE_LOCK",
                "INTERNET", "ACCESS_NETWORK_STATE",
            ),
            declared,
        )
        val removed = children("uses-permission").filter { attr(it, "tools:node") == "remove" }.map { attr(it, "android:name") }
        assertTrue("GET_ACCOUNTS stays refused", "android.permission.GET_ACCOUNTS" in removed)
    }

    @Test
    fun theGoogleScreensAreDeclaredAndDriveBackupIsNot() {
        val activities = children("activity").associateBy { attr(it, "android:name") }
        assertTrue(activities.containsKey("com.todoroo.astrid.gtasks.auth.GtasksLoginActivity"))
        assertTrue(activities.containsKey(".activities.GoogleTaskListSettingsActivity"))
        val admin = activities.getValue(".sync.google.GoogleAdminHelpActivity")
        assertEquals("not exported", "false", attr(admin, "android:exported"))
        assertFalse("Drive backup stays off", activities.keys.any { it!!.contains("DriveLoginActivity") })
    }

    @Test
    fun theDebugProbeIsInDebugSourcesOnly() {
        assertFalse(File(appDir(), "src/main").walkTopDown().any { it.name.contains("TokenProbe") })
        assertFalse(manifest.documentElement.textContent.contains("TokenProbe"))
        assertTrue(File(appDir(), "src/debug/java/org/tasks/sync/google/GoogleTokenProbeActivity.kt").isFile)
        assertFalse(File(appDir(), "src/release").walkTopDown().any { it.name.contains("TokenProbe") })
    }

    @Test
    fun theFailureTextMapsEveryKindAndTheWeeklyNoteFollowsTestingMode() {
        assertNull("a cancel says nothing", GoogleFailureText.messageRes(GoogleFailure.Cancelled))
        assertNull("other keeps its raw message", GoogleFailureText.messageRes(GoogleFailure.Other))
        assertEquals(R.string.google_failure_needs_sign_in_testing, GoogleFailureText.messageRes(GoogleFailure.NeedsSignIn, testingMode = true))
        assertEquals(R.string.google_failure_needs_sign_in, GoogleFailureText.messageRes(GoogleFailure.NeedsSignIn, testingMode = false))
        assertEquals(R.string.google_failure_admin_blocked, GoogleFailureText.messageRes(GoogleFailure.AdminBlocked))
        assertEquals(R.string.google_failure_not_a_tester, GoogleFailureText.messageRes(GoogleFailure.NotATester))
        assertEquals(R.string.google_failure_no_account, GoogleFailureText.messageRes(GoogleFailure.NoAccount))
        assertEquals(R.string.google_failure_unavailable, GoogleFailureText.messageRes(GoogleFailure.Unavailable))
        assertEquals(R.string.google_failure_app_not_set_up, GoogleFailureText.messageRes(GoogleFailure.AppNotSetUp))
    }

    @Test
    fun theRegisteredFingerprintsAreTheOnesInThePlan() {
        assertEquals("5D:70:F9:67:FC:56:E7:00:F2:61:44:D7:2A:65:5B:72:29:8C:0E:6A", SyncClients.GOOGLE_RELEASE_SHA1)
        assertEquals("16:3C:86:72:29:4C:69:FB:AB:52:1A:6A:03:DA:AD:BB:D3:9E:8C:4E", SyncClients.GOOGLE_DEBUG_SHA1)
    }
}
