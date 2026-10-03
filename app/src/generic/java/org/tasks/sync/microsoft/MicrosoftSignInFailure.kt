package org.tasks.sync.microsoft

import android.content.Context
import android.content.Intent
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationException.GeneralErrors
import org.tasks.R
import java.io.IOException

/** What kind of failure a Microsoft sign-in ended in, which decides what 4Tasks says and whether it shows the IT admin screen. */
enum class MicrosoftFailureKind {
    /** No Entra registration in this build yet (SyncClients.microsoftConfigured is false). Microsoft is not called. */
    NOT_SET,

    /** The user left the browser without finishing. */
    CANCELLED,

    /** The user pressed "decline" on Microsoft's consent page. */
    DECLINED,

    /** No connection, or Microsoft's service was temporarily unavailable. */
    NETWORK,

    /** The organisation refused or has not yet allowed 4Tasks: consent, an admin's policy. Shows the IT admin screen. */
    ORGANISATION,

    /** Anything else; Microsoft's own text, no admin screen. */
    OTHER,
}

/** A failed Microsoft sign-in: its [kind], and what Microsoft said (the OAuth `error` and its `error_description`), if anything. */
data class MicrosoftSignInFailure(
    val kind: MicrosoftFailureKind,
    val error: String? = null,
    val description: String? = null,
) {
    /** Microsoft's own text, line breaks normalised, or null. */
    val microsoftText: String?
        get() = description?.replace("\r\n", "\n")?.trim()?.takeIf { it.isNotEmpty() }

    /** The AADSTS number in Microsoft's text, such as "65001", or null. */
    val aadstsCode: String?
        get() = MicrosoftSignInErrors.aadstsCode(description)

    /** The plain sentence that opens the problem screen. */
    fun headline(context: Context): String = context.getString(
        when (kind) {
            MicrosoftFailureKind.NOT_SET -> R.string.microsoft_not_set_up
            MicrosoftFailureKind.CANCELLED -> R.string.microsoft_failure_cancelled
            MicrosoftFailureKind.DECLINED -> R.string.microsoft_failure_declined
            MicrosoftFailureKind.NETWORK -> R.string.microsoft_failure_network
            MicrosoftFailureKind.ORGANISATION -> R.string.microsoft_failure_organisation
            MicrosoftFailureKind.OTHER -> R.string.microsoft_failure_other
        }
    )

    fun toIntent(intent: Intent): Intent = intent
        .putExtra(EXTRA_KIND, kind.name)
        .putExtra(EXTRA_ERROR, error)
        .putExtra(EXTRA_DESCRIPTION, description)

    companion object {
        private const val EXTRA_KIND = "extra_failure_kind"
        private const val EXTRA_ERROR = "extra_failure_error"
        private const val EXTRA_DESCRIPTION = "extra_failure_description"

        fun fromIntent(intent: Intent): MicrosoftSignInFailure = MicrosoftSignInFailure(
            kind = MicrosoftFailureKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
                ?: MicrosoftFailureKind.OTHER,
            error = intent.getStringExtra(EXTRA_ERROR),
            description = intent.getStringExtra(EXTRA_DESCRIPTION),
        )

        fun other(text: String?) = MicrosoftSignInFailure(MicrosoftFailureKind.OTHER, description = text)
    }
}

/**
 * Sorts a failed sign-in into a [MicrosoftFailureKind].
 *
 * The AADSTS codes below are from Microsoft's error-code reference,
 * https://learn.microsoft.com/en-us/entra/identity-platform/reference-error-codes (page dated 2026-06-15, read 2026-10-03),
 * which gives each code a name and text. Quoted from it:
 *
 *  - AADSTS65001 "DelegationDoesNotExist - The user or administrator hasn't consented to use the application with ID X. Send an
 *    interactive authorization request for this user and resource."
 *  - AADSTS65002 "Consent between first party application '{applicationId}' and first party resource '{resourceId}' must be
 *    configured via preauthorization - applications owned and operated by Microsoft must get approval from the API owner before
 *    requesting tokens for that API. A developer in your tenant might be attempting to reuse an App ID owned by Microsoft."
 *  - AADSTS65004 "UserDeclinedConsent - User declined to consent to access the app. Have the user retry the sign-in and consent to
 *    the app"
 *  - AADSTS90094 "AdminConsentRequired - Administrator consent is required."
 *  - AADSTS90095 "AdminConsentRequiredRequestAccess - In the Admin Consent Workflow experience, an interrupt that appears when the
 *    user is told they need to ask the admin for consent."
 *  - AADSTS90008 "TokenForItselfRequiresGraphPermission - The user or administrator hasn't consented to use the application. ..."
 *  - AADSTS500011 "InvalidResourceServicePrincipalNotFound - The resource principal named {name} wasn't found in the tenant named
 *    {tenant}. This can happen if the application hasn't been installed by the administrator of the tenant or consented to by any
 *    user in the tenant. ... If you expect the app to be installed, you might need to provide administrator permissions to add it."
 *  - AADSTS700016 "UnauthorizedClient_DoesNotMatchRequest - The application wasn't found in the directory/tenant. This can happen if
 *    the application has not been installed by the administrator of the tenant or consented to by any user in the tenant. ..."
 *  - AADSTS53003 "BlockedByConditionalAccess - Access has been blocked by Conditional Access policies. The access policy does not
 *    allow token issuance. If this is unexpected, see the Conditional Access policy that applied to this request or contact your
 *    administrator. ..."
 *  - AADSTS530032 "BlockedByConditionalAccessOnSecurityPolicy - The tenant admin has configured a security policy that blocks this
 *    request. ..."
 *  - AADSTS53000 "DeviceNotCompliant", AADSTS53001 "DeviceNotDomainJoined", AADSTS53002 "ApplicationUsedIsNotAnApprovedApp" (all
 *    Conditional Access), AADSTS53011 "User blocked due to risk on home tenant."
 *  - AADSTS500021 "Access to '{tenant}' tenant is denied. ... the tenant restriction feature is configured and that the user is
 *    trying to access a tenant that isn't in the list of allowed tenants ..."
 *  - AADSTS50105 "EntitlementGrantsNotFound - The signed in user isn't assigned to a role for the signed in app. Assign the user to
 *    the app. ..."
 *
 * The same page's table of `error` values (invalid_request, invalid_grant, unauthorized_client, invalid_client, invalid_resource,
 * interaction_required, temporarily_unavailable) says: `unauthorized_client` "usually occurs when the client application isn't
 * registered in Microsoft Entra ID or isn't added to the user's Microsoft Entra tenant", `invalid_resource` "indicates the resource,
 * if it exists, hasn't been configured in the tenant", `interaction_required` "The request requires user interaction", and
 * `temporarily_unavailable` "The server is temporarily too busy to handle the request". `consent_required` and `access_denied` are
 * the OAuth names Microsoft sends on a refused or declined consent (the admin consent endpoint's own page, v2-admin-consent, shows
 * `error=consent_required` with AADSTS65004).
 *
 * The same page warns that "Error codes are subject to change at any time" and that `error_description` must never be used to react
 * to an error; so the `error` field decides first where it is enough, the AADSTS number only where Microsoft's own reference names it
 * as an organisation's decision, and anything not known is [MicrosoftFailureKind.OTHER], which shows Microsoft's text and no more.
 */
object MicrosoftSignInErrors {
    private val AADSTS = Regex("AADSTS(\\d+)")

    /** Codes that mean the organisation (its consent setting, an admin's policy or assignment) is the reason. */
    val ORGANISATION_CODES = setOf(
        "65001", "65002", "90094", "90095", "90008", "500011", "700016",
        "53003", "530032", "53000", "53001", "53002", "53011", "500021", "50105",
    )

    /** The user said no on the consent page. */
    val DECLINED_CODES = setOf("65004")

    /** OAuth `error` values that mean an organisation or consent decision, when no code settles it. */
    val ORGANISATION_ERRORS = setOf(
        "consent_required", "interaction_required", "access_denied", "unauthorized_client", "invalid_resource",
    )

    /** OAuth `error` values that mean "try again later". */
    val TEMPORARY_ERRORS = setOf("temporarily_unavailable")

    fun aadstsCode(description: String?): String? = description?.let { AADSTS.find(it)?.groupValues?.get(1) }

    /** Sorts what Microsoft sent back: the OAuth [error] string and its [description], either may be null. */
    fun classify(error: String?, description: String?): MicrosoftFailureKind {
        val code = aadstsCode(description)
        return when {
            code in DECLINED_CODES -> MicrosoftFailureKind.DECLINED
            code in ORGANISATION_CODES -> MicrosoftFailureKind.ORGANISATION
            error in TEMPORARY_ERRORS -> MicrosoftFailureKind.NETWORK
            error in ORGANISATION_ERRORS -> MicrosoftFailureKind.ORGANISATION
            else -> MicrosoftFailureKind.OTHER
        }
    }

    /** Sorts an AppAuth exception: the user leaving, no network, or what Microsoft said. */
    fun classify(ex: AuthorizationException): MicrosoftSignInFailure {
        val kind = when {
            ex == GeneralErrors.USER_CANCELED_AUTH_FLOW || ex == GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW ->
                MicrosoftFailureKind.CANCELLED
            ex == GeneralErrors.NETWORK_ERROR || ex == GeneralErrors.SERVER_ERROR || ex.cause is IOException ->
                MicrosoftFailureKind.NETWORK
            else -> classify(ex.error, ex.errorDescription)
        }
        // A general error's text is AppAuth's own ("Network error"), not Microsoft's, so it is not shown as Microsoft's.
        val fromMicrosoft = ex.type != AuthorizationException.TYPE_GENERAL_ERROR
        return MicrosoftSignInFailure(
            kind = kind,
            error = ex.error ?: ex.errorDescription.takeIf { !fromMicrosoft },
            description = ex.errorDescription.takeIf { fromMicrosoft },
        )
    }
}
