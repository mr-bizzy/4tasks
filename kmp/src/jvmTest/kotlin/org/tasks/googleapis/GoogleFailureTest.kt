package org.tasks.googleapis

import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.auth.isUnauthorized
import org.tasks.data.entity.CaldavAccount

/** SYNC-PLAN 3 and 3b: every way a Google token or call can fail is sorted into the kind the app treats differently. */
class GoogleFailureTest {
    private fun http(status: Int, reason: String?, message: String?): GoogleJsonResponseException {
        val error = GoogleJsonError().apply {
            code = status
            this.message = message
            if (reason != null) errors = listOf(GoogleJsonError.ErrorInfo().apply { this.reason = reason })
        }
        return GoogleJsonResponseException(HttpResponseException.Builder(status, "status", HttpHeaders()), error)
    }

    // --- the account-manager token request ---

    @Test
    fun userCancelledThePickerOrConsent() {
        assertEquals(GoogleFailure.Cancelled, GoogleFailureClassifier.fromToken(TokenFailureKind.CANCELLED, null))
        assertEquals(GoogleFailure.Cancelled, GoogleFailureClassifier.fromToken(TokenFailureKind.CANCELLED, "canceled"))
        assertEquals(GoogleFailure.Cancelled, GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "UserCancel"))
    }

    @Test
    fun noGoogleAccountOnThePhone() {
        assertEquals(GoogleFailure.NoAccount, GoogleFailureClassifier.fromToken(TokenFailureKind.ACCOUNT_MISSING, null))
        assertEquals(GoogleFailure.NoAccount, GoogleAuthFailureException(GoogleFailure.NoAccount).failure)
    }

    @Test
    fun anAdminBlockedTheAccount() {
        assertEquals(
            GoogleFailure.AdminBlocked,
            GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "Error 400: admin_policy_enforced"),
        )
        assertEquals(
            GoogleFailure.AdminBlocked,
            GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "ServiceDisabled"),
        )
        // the admin's word wins over a cancel: Google may cancel after showing the admin's message
        assertEquals(
            GoogleFailure.AdminBlocked,
            GoogleFailureClassifier.fromToken(TokenFailureKind.CANCELLED, "admin_policy_enforced"),
        )
        assertEquals(
            GoogleFailure.AdminBlocked,
            GoogleFailureClassifier.fromHttp(403, "forbidden", "admin_policy_enforced: blocked"),
        )
    }

    @Test
    fun theTestingModeGrantExpiredOrWasRevoked() {
        // Testing mode: the grant dies after 7 days; Google answers a refresh with invalid_grant, or the token is refused
        assertEquals(
            GoogleFailure.NeedsSignIn,
            GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "invalid_grant: Token has been expired or revoked."),
        )
        assertEquals(GoogleFailure.NeedsSignIn, GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "NeedPermission"))
        assertEquals(GoogleFailure.NeedsSignIn, GoogleFailureClassifier.fromToken(TokenFailureKind.NO_TOKEN, null))
        assertEquals(GoogleFailure.NeedsSignIn, GoogleFailureClassifier.fromHttp(401, null, "Invalid Credentials"))
        assertEquals(GoogleFailure.NeedsSignIn, GoogleFailureClassifier.fromHttp(400, "invalid_grant", "Bad Request"))
        assertEquals(
            GoogleFailure.NeedsSignIn,
            GoogleFailureClassifier.fromHttp(403, "insufficientPermissions", "Request had insufficient authentication scopes."),
        )
    }

    @Test
    fun aTesterNotOnTheListIsAccessDenied() {
        assertEquals(
            GoogleFailure.NotATester,
            GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "access_denied"),
        )
        assertEquals(GoogleFailure.NotATester, GoogleFailureClassifier.fromHttp(403, "access_denied", "access_denied"))
    }

    @Test
    fun theNetwork() {
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromToken(TokenFailureKind.IO, null))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "NetworkError"))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromException(java.net.UnknownHostException("x")))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromException(java.net.SocketTimeoutException()))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromException(java.io.IOException("connection reset")))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromHttp(503, null, "Service Unavailable"))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromHttp(429, "rateLimitExceeded", "Rate Limit Exceeded"))
    }

    @Test
    fun theProjectBeingSetUpWrongIsNotTheUsersFault() {
        assertEquals(
            GoogleFailure.AppNotSetUp,
            GoogleFailureClassifier.fromHttp(403, "accessNotConfigured", "Google Tasks API has not been used in project 1 before"),
        )
    }

    @Test
    fun anythingElseKeepsItsMessage() {
        assertEquals(GoogleFailure.Other, GoogleFailureClassifier.fromToken(TokenFailureKind.AUTHENTICATOR, "something odd"))
        assertEquals(GoogleFailure.Other, GoogleFailureClassifier.fromHttp(404, null, "Not Found"))
        assertEquals(GoogleFailure.Other, GoogleFailureClassifier.fromException(IllegalStateException("boom")))
    }

    // --- exceptions as the Google client library raises them ---

    @Test
    fun exceptionsFromTheTasksApiAreSortedByStatusAndReason() {
        assertEquals(GoogleFailure.NeedsSignIn, GoogleFailureClassifier.fromException(http(401, "authError", "Invalid Credentials")))
        assertEquals(GoogleFailure.AdminBlocked, GoogleFailureClassifier.fromException(http(403, null, "admin_policy_enforced")))
        assertEquals(GoogleFailure.AppNotSetUp, GoogleFailureClassifier.fromException(http(403, "accessNotConfigured", "disabled")))
        assertEquals(GoogleFailure.Unavailable, GoogleFailureClassifier.fromException(http(500, "backendError", "Backend Error")))
        assertEquals(
            GoogleFailure.NeedsSignIn,
            GoogleFailureClassifier.fromException(HttpResponseException.Builder(401, "Unauthorized", HttpHeaders()).build()),
        )
    }

    @Test
    fun aFailureThatWasAlreadyClassifiedStaysSo() {
        assertEquals(
            GoogleFailure.AdminBlocked,
            GoogleFailureClassifier.fromException(GoogleAuthFailureException(GoogleFailure.AdminBlocked, "x")),
        )
    }

    // --- what is stored on the account, and the "needs sign-in" transitions ---

    @Test
    fun aStoredFailureReadsBack() {
        GoogleFailure.entries.forEach {
            assertEquals(it, GoogleFailure.fromStored(it.stored()))
        }
        assertNull(GoogleFailure.fromStored(null))
        assertNull(GoogleFailure.fromStored("Unable to resolve host"))
        assertNull(GoogleFailure.fromStored("google:no_such_code"))
    }

    @Test
    fun stateComesFromTheStoredError() {
        assertEquals(GoogleAccountState.Ok, GoogleAccountState.fromError(null))
        assertEquals(GoogleAccountState.Ok, GoogleAccountState.fromError(""))
        assertEquals(GoogleAccountState.NeedsSignIn, GoogleAccountState.fromError(GoogleFailure.NeedsSignIn.stored()))
        assertEquals(GoogleAccountState.AdminBlocked, GoogleAccountState.fromError(GoogleFailure.AdminBlocked.stored()))
        assertEquals(GoogleAccountState.NotATester, GoogleAccountState.fromError(GoogleFailure.NotATester.stored()))
        assertEquals(GoogleAccountState.NoAccount, GoogleAccountState.fromError(GoogleFailure.NoAccount.stored()))
        assertEquals(GoogleAccountState.AppNotSetUp, GoogleAccountState.fromError(GoogleFailure.AppNotSetUp.stored()))
        assertEquals(GoogleAccountState.Unavailable, GoogleAccountState.fromError(GoogleFailure.Unavailable.stored()))
        assertEquals(GoogleAccountState.Error, GoogleAccountState.fromError("Not Found"))
        // an account that stored the old text of a 401 still shows "needs sign-in"
        assertEquals(GoogleAccountState.NeedsSignIn, GoogleAccountState.fromError("401 Unauthorized\nPOST https://tasks.googleapis.com"))
    }

    @Test
    fun anAccountNeedsSignInThenSignsInAgain() {
        val google = { error: String? -> CaldavAccount(accountType = CaldavAccount.TYPE_GOOGLE_TASKS, error = error) }

        // a fresh account is fine
        assertFalse(google("").isUnauthorized())

        // the 7-day Testing grant lapses: the sync stores the failure and the account needs sign-in
        val lapsed = google(GoogleFailure.NeedsSignIn.stored())
        assertTrue(lapsed.isUnauthorized())
        assertTrue(GoogleAccountState.fromError(lapsed.error).needsSignIn)

        // a bad connection on the next try does not pretend the sign-in is fine, nor demand one
        val offline = google(GoogleFailure.Unavailable.stored())
        assertFalse(offline.isUnauthorized())

        // the organisation blocks it: not a sign-in problem, the admin screen is the way out
        val blocked = google(GoogleFailure.AdminBlocked.stored())
        assertFalse(blocked.isUnauthorized())
        assertEquals(GoogleAccountState.AdminBlocked, GoogleAccountState.fromError(blocked.error))

        // signing in again clears the error (GtasksLoginActivity does account.copy(error = "")) and the account is fine
        val signedIn = lapsed.copy(error = "")
        assertFalse(signedIn.isUnauthorized())
        assertEquals(GoogleAccountState.Ok, GoogleAccountState.fromError(signedIn.error))

        // accounts stored by an earlier version with the old 401 text are still offered the sign-in
        assertTrue(google("401 Unauthorized").isUnauthorized())
    }

    @Test
    fun aStoredGoogleCodeOnAnotherKindOfAccountIsNotASignInPrompt() {
        val caldav = CaldavAccount(accountType = CaldavAccount.TYPE_CALDAV, error = GoogleFailure.NeedsSignIn.stored())
        assertFalse(caldav.isUnauthorized())
    }
}
