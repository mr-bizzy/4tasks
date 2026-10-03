package org.tasks.sync.microsoft

import android.net.Uri
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationException.GeneralErrors
import net.openid.appauth.AuthorizationException.TokenRequestErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * The texts below are Microsoft's own, from the AADSTS error-code reference
 * (https://learn.microsoft.com/en-us/entra/identity-platform/reference-error-codes, read 2026-10-03), in the form Microsoft puts
 * in `error_description`: "AADSTS<code>: <text>\r\nTrace ID: ...".
 */
@RunWith(RobolectricTestRunner::class)
class MicrosoftSignInErrorsTest {
    private val trace = "\r\nTrace ID: 0000aaaa-11bb-cccc-dd22-eeeeee333333\r\nCorrelation ID: aaaa0000-bb11-2222-33cc-444444dddddd\r\nTimestamp: 2026-10-03 10:00:00Z"

    private fun classify(error: String?, code: String, text: String) =
        MicrosoftSignInErrors.classify(error, "AADSTS$code: $text$trace")

    // --- an organisation's decision: the IT admin screen ---

    @Test
    fun consentNotGivenByUserOrAdmin_65001() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify(
            "invalid_grant", "65001",
            "The user or administrator hasn't consented to use the application with ID X. Send an interactive authorization request for this user and resource.",
        ),
    )

    @Test
    fun firstPartyPreauthorisation_65002() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify(
            "invalid_client", "65002",
            "Consent between first party application '{applicationId}' and first party resource '{resourceId}' must be configured via preauthorization",
        ),
    )

    @Test
    fun adminConsentRequired_90094() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify("consent_required", "90094", "Administrator consent is required."),
    )

    @Test
    fun adminConsentWorkflow_90095() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify(
            "access_denied", "90095",
            "In the Admin Consent Workflow experience, an interrupt that appears when the user is told they need to ask the admin for consent.",
        ),
    )

    @Test
    fun resourcePrincipalNotInTenant_500011() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify(
            "invalid_resource", "500011",
            "The resource principal named {name} wasn't found in the tenant named {tenant}. This can happen if the application hasn't been installed by the administrator of the tenant or consented to by any user in the tenant.",
        ),
    )

    @Test
    fun applicationNotFoundInTheTenant_700016() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify(
            "unauthorized_client", "700016",
            "The application wasn't found in the directory/tenant. This can happen if the application has not been installed by the administrator of the tenant or consented to by any user in the tenant.",
        ),
    )

    @Test
    fun blockedByConditionalAccess_53003() = assertEquals(
        MicrosoftFailureKind.ORGANISATION,
        classify(
            "interaction_required", "53003",
            "Access has been blocked by Conditional Access policies. The access policy does not allow token issuance.",
        ),
    )

    @Test
    fun otherConditionalAccessAndPolicyCodes() {
        for (code in listOf("530032", "53000", "53001", "53002", "53011", "500021", "50105")) {
            assertEquals(code, MicrosoftFailureKind.ORGANISATION, classify(null, code, "text"))
        }
    }

    @Test
    fun theOauthErrorAloneIsEnoughWhenThereIsNoCode() {
        for (error in listOf("consent_required", "interaction_required", "access_denied", "unauthorized_client", "invalid_resource")) {
            assertEquals(error, MicrosoftFailureKind.ORGANISATION, MicrosoftSignInErrors.classify(error, null))
            assertEquals(error, MicrosoftFailureKind.ORGANISATION, MicrosoftSignInErrors.classify(error, "Some text without a code"))
        }
    }

    // --- not an organisation's decision: no admin screen ---

    @Test
    fun userDeclinedConsent_65004_isNotTheAdminScreen() = assertEquals(
        MicrosoftFailureKind.DECLINED,
        classify("access_denied", "65004", "User declined to consent to access the app. Have the user retry the sign-in and consent to the app"),
    )

    @Test
    fun temporarilyUnavailableIsNetwork() =
        assertEquals(MicrosoftFailureKind.NETWORK, MicrosoftSignInErrors.classify("temporarily_unavailable", "The server is temporarily too busy to handle the request."))

    @Test
    fun unknownThingsShowMicrosoftsTextOnly() {
        assertEquals(MicrosoftFailureKind.OTHER, MicrosoftSignInErrors.classify("invalid_request", "AADSTS900144: The request body must contain the following parameter: 'client_id'."))
        assertEquals(MicrosoftFailureKind.OTHER, MicrosoftSignInErrors.classify(null, null))
        // a code Microsoft documents but that is not an organisation's decision (a wrong or missing account)
        assertEquals(MicrosoftFailureKind.OTHER, classify("invalid_grant", "50034", "To sign into this application, the account must be added to the directory."))
        // the invalid_grant of a plain bad code
        assertEquals(MicrosoftFailureKind.OTHER, MicrosoftSignInErrors.classify("invalid_grant", "AADSTS70000: bad"))
    }

    // --- from AppAuth's own exceptions ---

    @Test
    fun theUserLeavingTheBrowserIsCancelled() {
        assertEquals(MicrosoftFailureKind.CANCELLED, MicrosoftSignInErrors.classify(GeneralErrors.USER_CANCELED_AUTH_FLOW).kind)
        assertEquals(MicrosoftFailureKind.CANCELLED, MicrosoftSignInErrors.classify(GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW).kind)
    }

    @Test
    fun noNetworkIsNetwork() {
        assertEquals(MicrosoftFailureKind.NETWORK, MicrosoftSignInErrors.classify(GeneralErrors.NETWORK_ERROR).kind)
        assertEquals(MicrosoftFailureKind.NETWORK, MicrosoftSignInErrors.classify(GeneralErrors.SERVER_ERROR).kind)
        assertEquals(
            MicrosoftFailureKind.NETWORK,
            MicrosoftSignInErrors.classify(AuthorizationException.fromTemplate(GeneralErrors.NETWORK_ERROR, IOException("no route"))).kind,
        )
        assertEquals(
            MicrosoftFailureKind.NETWORK,
            MicrosoftSignInErrors.classify(
                AuthorizationException(AuthorizationException.TYPE_GENERAL_ERROR, 99, null, "x", null, java.net.UnknownHostException("login.microsoftonline.com")),
            ).kind,
        )
        // AppAuth's own words are not shown as Microsoft's
        assertNull(MicrosoftSignInErrors.classify(GeneralErrors.NETWORK_ERROR).microsoftText)
    }

    @Test
    fun aRefusalMicrosoftSendsBackInTheRedirect() {
        val redirect = Uri.parse(
            "msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D" +
                "?error=access_denied&error_description=" + Uri.encode("AADSTS65004: User declined to consent to access the app.$trace") +
                "&state=abc",
        )
        val failure = MicrosoftSignInErrors.classify(AuthorizationException.fromOAuthRedirect(redirect))
        assertEquals(MicrosoftFailureKind.DECLINED, failure.kind)
        assertEquals("access_denied", failure.error)
        assertEquals("65004", failure.aadstsCode)

        val adminNeeded = Uri.parse(
            "msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D" +
                "?error=consent_required&error_description=" + Uri.encode("AADSTS90094: Administrator consent is required.$trace"),
        )
        val admin = MicrosoftSignInErrors.classify(AuthorizationException.fromOAuthRedirect(adminNeeded))
        assertEquals(MicrosoftFailureKind.ORGANISATION, admin.kind)
        assertEquals("90094", admin.aadstsCode)
    }

    @Test
    fun aTokenEndpointRefusal() {
        val ex = AuthorizationException.fromOAuthTemplate(
            TokenRequestErrors.INVALID_GRANT, null,
            "AADSTS65001: The user or administrator hasn't consented to use the application with ID X. Send an interactive authorization request for this user and resource.$trace",
            null,
        )
        val failure = MicrosoftSignInErrors.classify(ex)
        assertEquals(MicrosoftFailureKind.ORGANISATION, failure.kind)
        assertEquals("65001", failure.aadstsCode)
        assertEquals(
            "AADSTS65001: The user or administrator hasn't consented to use the application with ID X. Send an interactive authorization request for this user and resource.\n" +
                "Trace ID: 0000aaaa-11bb-cccc-dd22-eeeeee333333\nCorrelation ID: aaaa0000-bb11-2222-33cc-444444dddddd\nTimestamp: 2026-10-03 10:00:00Z",
            failure.microsoftText,
        )
    }

    @Test
    fun aCodeIsReadFromTheTextEvenWithoutAnErrorString() {
        assertEquals("53003", MicrosoftSignInErrors.aadstsCode("AADSTS53003: Access has been blocked by Conditional Access policies."))
        assertNull(MicrosoftSignInErrors.aadstsCode("Something else"))
        assertNull(MicrosoftSignInErrors.aadstsCode(null))
    }
}
