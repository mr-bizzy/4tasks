package org.tasks.googleapis

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import java.io.IOException

/**
 * Why getting a Google token, or a call to the Tasks API, failed, in the few kinds 4Tasks treats differently
 * (docs/SYNC-PLAN.md sections 3 and 3b). The words shown to the user live in the app's strings_sync_google.xml;
 * this file decides only WHICH kind it is, so it can be tested on the JVM.
 */
enum class GoogleFailure(val code: String) {
    /** The user backed out of Google's account or consent screen. Nothing to tell them. */
    Cancelled("cancelled"),

    /** There is no Google account on the phone (or the account 4Tasks was using has been removed). */
    NoAccount("no_account"),

    /** A Google Workspace admin has not allowed the app: the account must ask its admin. */
    AdminBlocked("admin_blocked"),

    /**
     * Google no longer honours the grant: revoked, the password changed, or in Testing mode the 7-day expiry
     * (SYNC-PLAN section 3). The account shows "needs sign-in" and signing in again fixes it.
     */
    NeedsSignIn("needs_sign_in"),

    /** The consent screen is in Testing mode and this account is not on the test-user list (access_denied). */
    NotATester("not_a_tester"),

    /** The project side is wrong (Tasks API not switched on, client not matching): nothing the user can fix. */
    AppNotSetUp("app_not_set_up"),

    /** No network, or Google is busy or down. Sync tries again by itself. */
    Unavailable("unavailable"),

    /** Anything else: the raw message is kept. */
    Other("other"),
    ;

    /** What is stored in the account's error column for this failure; [GoogleAccountState.fromError] reads it back. */
    fun stored(): String = STORED_PREFIX + code

    companion object {
        const val STORED_PREFIX = "google:"

        fun fromStored(error: String?): GoogleFailure? {
            if (error == null || !error.startsWith(STORED_PREFIX)) return null
            val code = error.removePrefix(STORED_PREFIX)
            return entries.firstOrNull { it.code == code }
        }
    }
}

/** What went wrong when asking the phone's account manager for a token, as the Android layer saw it. */
enum class TokenFailureKind {
    /** android.accounts.OperationCanceledException: the user cancelled, or the request was cancelled. */
    CANCELLED,

    /** android.accounts.AuthenticatorException: Google's authenticator failed or refused. */
    AUTHENTICATOR,

    /** java.io.IOException from the account manager: "usually because of network trouble". */
    IO,

    /** The account name is not (or no longer) among the accounts the user has made visible to 4Tasks. */
    ACCOUNT_MISSING,

    /** The account manager answered without a token. */
    NO_TOKEN,

    OTHER,
}

/** Thrown when a token cannot be had. An [IOException], so existing sync code treats it as a failed sync. */
class GoogleAuthFailureException(
    val failure: GoogleFailure,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message ?: failure.code, cause)

object GoogleFailureClassifier {
    /**
     * Failures of the account-manager token request. The message is looked at first, because Google's authenticator
     * puts the reason in it. NOT VERIFIED against a real Android client yet: the match words below are Google's
     * documented OAuth error names (admin_policy_enforced, access_denied, invalid_grant) plus the authenticator
     * status names remembered from GoogleAuthUtil (ServiceDisabled, NeedPermission, BadAuthentication, NetworkError);
     * the debug token probe (GoogleTokenProbeActivity) logs the real exception so this can be corrected.
     */
    fun fromToken(kind: TokenFailureKind, message: String?): GoogleFailure {
        fromText(message)?.let { return it }
        return when (kind) {
            TokenFailureKind.CANCELLED -> GoogleFailure.Cancelled
            TokenFailureKind.ACCOUNT_MISSING -> GoogleFailure.NoAccount
            TokenFailureKind.IO -> GoogleFailure.Unavailable
            TokenFailureKind.NO_TOKEN -> GoogleFailure.NeedsSignIn
            TokenFailureKind.AUTHENTICATOR, TokenFailureKind.OTHER -> GoogleFailure.Other
        }
    }

    /** A failed call to the Tasks API: the HTTP status, Google's error reason (first of `errors[].reason`) and message. */
    fun fromHttp(status: Int, reason: String?, message: String?): GoogleFailure {
        val text = (reason.orEmpty() + " " + message.orEmpty()).lowercase()
        return when {
            status == 401 -> GoogleFailure.NeedsSignIn
            status == 400 -> if ("invalid_grant" in text) GoogleFailure.NeedsSignIn else GoogleFailure.Other
            status == 403 -> when {
                "admin_policy_enforced" in text || "administrator" in text || "admin policy" in text ->
                    GoogleFailure.AdminBlocked
                "access_denied" in text || "not_verified" in text || "has not completed the google verification" in text ->
                    GoogleFailure.NotATester
                "accessnotconfigured" in text || "service_disabled" in text ||
                    "has not been used in project" in text || "api has not been enabled" in text ->
                    GoogleFailure.AppNotSetUp
                "insufficientpermissions" in text || "insufficient authentication scopes" in text ||
                    "access_token_scope_insufficient" in text -> GoogleFailure.NeedsSignIn
                "ratelimitexceeded" in text || "quotaexceeded" in text -> GoogleFailure.Unavailable
                else -> GoogleFailure.Other
            }
            status == 408 || status == 429 || status in 500..599 -> GoogleFailure.Unavailable
            else -> GoogleFailure.Other
        }
    }

    fun fromException(e: Throwable): GoogleFailure = when (e) {
        is GoogleAuthFailureException -> e.failure
        is GoogleJsonResponseException ->
            fromHttp(e.statusCode, e.details?.errors?.firstOrNull()?.reason, e.details?.message ?: e.statusMessage)
        is com.google.api.client.http.HttpResponseException -> fromHttp(e.statusCode, null, e.statusMessage)
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            GoogleFailure.Unavailable
        // any other I/O error on the way to Google is the network
        is IOException -> fromText(e.message) ?: GoogleFailure.Unavailable
        else -> fromText(e.message) ?: GoogleFailure.Other
    }

    /** Words that identify a kind on their own, whatever exception carried them. */
    private fun fromText(message: String?): GoogleFailure? {
        val m = message?.lowercase() ?: return null
        return when {
            "admin_policy_enforced" in m || "servicedisabled" in m || "service_disabled" in m ||
                "devicemanagementrequired" in m || "blocked by your administrator" in m -> GoogleFailure.AdminBlocked
            "access_denied" in m || "accessdenied" in m || "notverified" in m || "not_verified" in m -> GoogleFailure.NotATester
            "usercancel" in m || "user_cancel" in m -> GoogleFailure.Cancelled
            "networkerror" in m || "network_error" in m || "unable to resolve host" in m || "timeout" in m ->
                GoogleFailure.Unavailable
            "badauthentication" in m || "needpermission" in m || "invalid_grant" in m || "invalid_token" in m ||
                "expired or revoked" in m -> GoogleFailure.NeedsSignIn
            else -> null
        }
    }
}

/** What a Google Tasks account is doing, read from the error its last sync stored. */
enum class GoogleAccountState {
    Ok,
    NeedsSignIn,
    AdminBlocked,
    NotATester,
    NoAccount,
    AppNotSetUp,
    Unavailable,

    /** A failure with only its raw message to show. */
    Error,
    ;

    /** True when only signing in again (the account's Sign in row) can fix it. */
    val needsSignIn: Boolean get() = this == NeedsSignIn

    companion object {
        /** The older text of a 401, kept so accounts that stored it before this version still show "needs sign-in". */
        private const val OLD_UNAUTHORIZED = "401 Unauthorized"

        fun fromError(error: String?): GoogleAccountState {
            if (error.isNullOrBlank()) return Ok
            if (error.startsWith(OLD_UNAUTHORIZED, ignoreCase = true)) return NeedsSignIn
            return when (GoogleFailure.fromStored(error)) {
                GoogleFailure.NeedsSignIn -> NeedsSignIn
                GoogleFailure.AdminBlocked -> AdminBlocked
                GoogleFailure.NotATester -> NotATester
                GoogleFailure.NoAccount -> NoAccount
                GoogleFailure.AppNotSetUp -> AppNotSetUp
                GoogleFailure.Unavailable -> Unavailable
                GoogleFailure.Cancelled, GoogleFailure.Other, null -> Error
            }
        }
    }
}
