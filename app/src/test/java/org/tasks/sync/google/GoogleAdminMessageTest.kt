package org.tasks.sync.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.sync.SyncClients
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The admin message is built from the REAL strings in strings_sync_google.xml (read from the file), so what the user
 * sends, what the admin screen says and what the help page must match are checked against the same words.
 */
class GoogleAdminMessageTest {
    private val strings: Map<String, String> by lazy { loadStrings() }

    private fun loadStrings(): Map<String, String> {
        val file = listOf("src/main/res/values/strings_sync_google.xml", "app/src/main/res/values/strings_sync_google.xml")
            .map { File(it) }.first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val n = nodes.item(it)
            n.attributes.getNamedItem("name").nodeValue to unescape(n.textContent)
        }
    }

    /** What aapt makes of a string: \n, \' and \" become the character. */
    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\@", "@")

    private val texts get() = GoogleAdminMessage.Texts(strings.getValue("google_admin_message"), strings.getValue("google_admin_client_id_line"))
    private val facts = GoogleAdminMessage.Facts(account = "sam@work.example", packageName = "uk.mr_biz.fourtasks", clientId = "")

    @Test
    fun theMessageNamesTheAppThePublisherThePackageTheScopeAndTheHelpPage() {
        val m = GoogleAdminMessage.build(texts, facts)
        assertTrue(m, "4Tasks" in m)
        assertTrue(m, "Mr-Bizzy, mr-biz.uk" in m)
        assertTrue(m, "uk.mr_biz.fourtasks" in m)
        assertTrue(m, "https://www.googleapis.com/auth/tasks" in m)
        assertTrue(m, "Create, edit, organize, and delete all your tasks" in m)
        assertTrue(m, "https://mr-biz.uk/4tasks/admin/#google" in m)
        assertTrue(m, "sam@work.example" in m)
        assertTrue(m, GoogleAdminMessage.ADMIN_GUIDE_URL in m)
    }

    @Test
    fun theMessageGivesTheAdminsRouteThroughTheAdminConsole() {
        val m = GoogleAdminMessage.build(texts, facts)
        listOf("Security", "Access and data control", "API controls", "Manage app access", "Configure new app", "Limited", "Specific Google data")
            .forEach { assertTrue("missing '$it' in: $m", it in m) }
    }

    @Test
    fun theMessageAsksForOnlyTheOneScope() {
        val m = GoogleAdminMessage.build(texts, facts)
        assertEquals("exactly one scope URL", 1, Regex("https://www\\.googleapis\\.com/auth/[a-z.]+").findAll(m).count())
        assertFalse("must not ask for a Drive scope", "auth/drive" in m)
    }

    @Test
    fun aClientIdIsIncludedOnlyOnceThereIsOne() {
        assertFalse("OAuth client ID" in GoogleAdminMessage.build(texts, facts))
        val withId = GoogleAdminMessage.build(texts, facts.copy(clientId = "123-abc.apps.googleusercontent.com"))
        assertTrue(withId, "OAuth client ID: 123-abc.apps.googleusercontent.com" in withId)
    }

    @Test
    fun aMissingAccountDoesNotLeaveAHole() {
        val m = GoogleAdminMessage.build(texts, facts.copy(account = ""))
        assertFalse(m, "()" in m)
        assertFalse(m, "%" in m)
    }

    @Test
    fun noPlaceholderIsLeftUnfilled() {
        val m = GoogleAdminMessage.build(texts, facts.copy(clientId = "x"))
        assertFalse(m, Regex("%\\d?\\$?s").containsMatchIn(m))
    }

    @Test
    fun theFactsDefaultToTheOneConfigPlace() {
        val d = GoogleAdminMessage.Facts(account = "a", packageName = "p")
        assertEquals(SyncClients.GOOGLE_TASKS_SCOPE, d.scope)
        assertEquals(SyncClients.GOOGLE_TASKS_SCOPE_TITLE, d.scopeTitle)
        assertEquals(SyncClients.GOOGLE_ADMIN_HELP_URL, d.helpUrl)
        assertEquals(SyncClients.GOOGLE_ANDROID_CLIENT_ID, d.clientId)
        assertEquals("https://www.googleapis.com/auth/tasks", SyncClients.GOOGLE_TASKS_SCOPE)
        assertEquals("https://mr-biz.uk/4tasks/admin/#google", SyncClients.GOOGLE_ADMIN_HELP_URL)
    }

    @Test
    fun theNewStringsAreBritishEnglishAndOwnTheirNames() {
        assertTrue(strings.isNotEmpty())
        strings.keys.forEach { assertTrue("$it must start with google_ so it cannot clash with Phase B", it.startsWith("google_")) }
        val us = Regex("organization|color|synchroniz|recogniz|center|license ", RegexOption.IGNORE_CASE)
        strings.forEach { (k, v) ->
            // Google's own words for its scope ("organize") are quoted as they appear on its consent screen
            val text = v.replace("Create, edit, organize, and delete all your tasks", "")
            assertFalse("$k uses a US spelling: $v", us.containsMatchIn(text))
        }
    }

    @Test
    fun everyFailureHasWordsOrIsDeliberatelyQuiet() {
        // the strings the failures map to exist in the file
        listOf(
            "google_failure_no_account", "google_failure_needs_sign_in", "google_failure_needs_sign_in_testing",
            "google_failure_admin_blocked", "google_failure_not_a_tester", "google_failure_app_not_set_up",
            "google_failure_unavailable", "google_failure_other", "google_failure_other_plain",
        ).forEach { assertTrue("missing $it", it in strings) }
        // testers are told about the weekly sign-in
        assertTrue("7 days" in strings.getValue("google_failure_needs_sign_in_testing"))
    }
}
