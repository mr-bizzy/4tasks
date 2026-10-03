package org.tasks.sync.microsoft

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import org.tasks.http.HttpException
import org.tasks.http.NotFoundException
import org.tasks.http.ServiceUnavailableException
import org.tasks.http.UnauthorizedException
import org.tasks.sync.SyncClients
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class MicrosoftSignInPathsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun jwt(claims: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." + enc.encodeToString(claims.toByteArray()) + ".signature"
    }

    // --- not set up: stop with a plain message, call nothing ---

    @Test
    fun withoutAClientIdSignInStopsWithAPlainMessageAndStartsNothing() {
        assumeFalse("the owner has put the real client ID in", SyncClients.microsoftConfigured)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        MicrosoftSignInViewModel(mock()).signIn(activity)
        assertEquals("Microsoft sign-in is not set up in this build.", ShadowToast.getTextOfLatestToast())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    // --- the ID token: issuer under the common authority, and the account's name ---

    @Test
    fun issuerMustBeThatOfTheTokensOwnTenant() {
        val tid = "aaaabbbb-0000-cccc-1111-dddd2222eeee"
        assertTrue(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://login.microsoftonline.com/$tid/v2.0","tid":"$tid"}""")))
        // personal accounts: the consumer tenant
        val msa = "9188040d-6c67-4c5b-b112-36a304b66dad"
        assertTrue(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://login.microsoftonline.com/$msa/v2.0","tid":"$msa"}""")))
        // another tenant's issuer, a different host, a v1 issuer, a literal template, and no tid are all refused
        assertFalse(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://login.microsoftonline.com/$msa/v2.0","tid":"$tid"}""")))
        assertFalse(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://evil.example/$tid/v2.0","tid":"$tid"}""")))
        assertFalse(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://sts.windows.net/$tid/","tid":"$tid"}""")))
        assertFalse(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://login.microsoftonline.com/{tenantid}/v2.0","tid":"{tenantid}"}""")))
        assertFalse(MicrosoftIdToken.issuerMatchesTenant(JSONObject("""{"iss":"https://login.microsoftonline.com/$tid/v2.0"}""")))
    }

    @Test
    fun readsClaimsFromAnIdToken() {
        val claims = MicrosoftIdToken.claims(jwt("""{"tid":"t","preferred_username":"ann@contoso.example","name":"Ann"}"""))!!
        assertEquals("ann@contoso.example", claims.getString("preferred_username"))
        assertNull(MicrosoftIdToken.claims(null))
        assertNull(MicrosoftIdToken.claims("not-a-token"))
        assertNull(MicrosoftIdToken.claims("a.!!!.c"))
        assertNull(MicrosoftIdToken.claims(jwt("not json")))
    }

    @Test
    fun aWorkAccountWithNoEmailClaimIsNamedByItsUsername() {
        val workClaims = MicrosoftIdToken.claims(jwt("""{"tid":"t","preferred_username":"ann@contoso.example"}"""))
        assertEquals("ann@contoso.example", MicrosoftIdToken.accountName(workClaims, userinfoEmail = null))
        // an address from userinfo wins, then the email claim
        assertEquals("ann@mail.example", MicrosoftIdToken.accountName(workClaims, "ann@mail.example"))
        val both = MicrosoftIdToken.claims(jwt("""{"email":"e@x.example","preferred_username":"p@x.example"}"""))
        assertEquals("e@x.example", MicrosoftIdToken.accountName(both, null))
        assertEquals("e@x.example", MicrosoftIdToken.accountName(both, "  "))
        assertNull(MicrosoftIdToken.accountName(null, null))
        assertNull(MicrosoftIdToken.accountName(MicrosoftIdToken.claims(jwt("""{"tid":"t"}""")), ""))
    }

    // --- a work tenant with no mailbox, or To Do switched off (Graph error) ---

    @Test
    fun explainsAMissingMailboxAndForbiddenAccess() {
        assertEquals(
            MicrosoftGraphErrors.Explanation.Mailbox,
            MicrosoftGraphErrors.explain("MailboxNotEnabledForRESTAPI", 404),
        )
        assertEquals(MicrosoftGraphErrors.Explanation.Forbidden, MicrosoftGraphErrors.explain(null, 403))
        assertEquals(MicrosoftGraphErrors.Explanation.Forbidden, MicrosoftGraphErrors.explain("ErrorAccessDenied", 0))
        assertNull(MicrosoftGraphErrors.explain("itemNotFound", 404))
        assertNull(MicrosoftGraphErrors.explain(null, 500))
    }

    @Test
    fun describesAGraphErrorInPlainWords() {
        val mailbox = MicrosoftGraphErrors.describe(
            context, NotFoundException("HTTP 404 - MailboxNotEnabledForRESTAPI: The mailbox is either inactive, soft-deleted, or is hosted on-premise.", graphCode = "MailboxNotEnabledForRESTAPI"),
        )!!
        assertTrue(mailbox, mailbox.contains("mailbox"))
        assertTrue(mailbox, mailbox.contains("IT admin"))
        assertFalse(mailbox, mailbox.contains("HTTP"))

        val forbidden = MicrosoftGraphErrors.describe(context, HttpException(403, "HTTP 403 - Forbidden"))!!
        assertTrue(forbidden, forbidden.contains("licence"))

        // everything else is left to the exception's own message
        assertNull(MicrosoftGraphErrors.describe(context, ServiceUnavailableException("HTTP 503")))
        assertNull(MicrosoftGraphErrors.describe(context, UnauthorizedException("HTTP 401")))
        assertNull(MicrosoftGraphErrors.describe(context, RuntimeException("boom")))
    }

    @Test
    fun theWordsForNeedingANewSignInAreWhatTheAccountPageLooksFor() {
        val message = context.getString(org.tasks.R.string.microsoft_error_sign_in_again)
        assertTrue(message.startsWith("401 Unauthorized"))
        assertTrue(org.tasks.preferences.fragments.MicrosoftAccount.Companion.run { message.isUnauthorized() })
        assertTrue(org.tasks.preferences.fragments.MicrosoftAccount.Companion.run { "HTTP 401 - InvalidAuthenticationToken".isUnauthorized() })
        assertFalse(org.tasks.preferences.fragments.MicrosoftAccount.Companion.run { "HTTP 404".isUnauthorized() })
    }
}
