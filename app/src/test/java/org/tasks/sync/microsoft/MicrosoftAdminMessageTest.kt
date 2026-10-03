package org.tasks.sync.microsoft

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.tasks.R
import org.tasks.sync.SyncClients

@RunWith(RobolectricTestRunner::class)
class MicrosoftAdminMessageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clientId = "11112222-aaaa-3333-bbbb-444455556666"
    private val redirect = SyncClients.microsoftRedirectUri("uk.mr_biz.fourtasks")

    private fun body(said: String? = null) = MicrosoftAdminMessage.body(context, said, clientId, redirect)

    @Test
    fun saysWhoWhatAndWhy() {
        val text = body()
        assertTrue(text.contains("4Tasks"))
        assertTrue(text.contains("Publisher: Mr-Bizzy (mr-biz.uk)"))
        assertTrue(text.contains("Application (client) ID: $clientId"))
        assertTrue(text.contains("Redirect URI: msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D"))
        assertTrue(text.contains("Tasks.ReadWrite: Create, read, update, and delete the signed-in user's tasks and task lists."))
        assertTrue(text.contains("Enterprise applications"))
        assertTrue(text.contains("grant admin consent for the organisation"))
        assertTrue(text.contains("admin consent workflow"))
        assertTrue(text.contains("https://mr-biz.uk/4tasks/admin/#microsoft"))
    }

    @Test
    fun hasNoUnfilledPlaceholdersOrNulls() {
        val text = body("AADSTS90094: Administrator consent is required.")
        assertFalse(text.contains("%1\$s"))
        assertFalse(text.contains("null"))
        assertFalse(text.contains("\${"))
        assertFalse(text.contains("\\n"))
    }

    @Test
    fun addsWhatMicrosoftSaidOnlyWhenThereIsSomething() {
        assertFalse(body(null).contains("What Microsoft told me"))
        assertFalse(body("   ").contains("What Microsoft told me"))
        val withText = body("AADSTS90094: Administrator consent is required.\nTrace ID: abc")
        assertTrue(withText.endsWith("What Microsoft told me when I tried to sign in:\nAADSTS90094: Administrator consent is required.\nTrace ID: abc"))
    }

    @Test
    fun theDefaultsComeFromSyncClients() {
        val text = MicrosoftAdminMessage.body(context, null)
        assertTrue(text.contains("Application (client) ID: ${SyncClients.MICROSOFT_CLIENT_ID}"))
        assertTrue(text.contains("Redirect URI: ${SyncClients.microsoftRedirectUri(org.tasks.BuildConfig.APPLICATION_ID)}"))
    }

    @Test
    fun subjectIsPlain() {
        assertEquals("Please allow 4Tasks for Microsoft sign-in", MicrosoftAdminMessage.subject(context))
    }

    @Test
    fun theScreenAndTheMessageShareTheirWording() {
        // the permission, the reason and the way to allow it are one string each, used by both
        val text = body()
        for (id in listOf(R.string.microsoft_admin_permission_what, R.string.microsoft_admin_permission_why, R.string.microsoft_admin_howto_body)) {
            assertTrue(text.contains(context.getString(id)))
        }
    }

    @Test
    fun failureHeadlinesAreWrittenForEachKind() {
        for (kind in MicrosoftFailureKind.entries) {
            val headline = MicrosoftSignInFailure(kind).headline(context)
            assertTrue(kind.name, headline.isNotBlank())
        }
        assertEquals("Microsoft sign-in is not set up in this build.", MicrosoftSignInFailure(MicrosoftFailureKind.NOT_SET).headline(context))
    }
}
