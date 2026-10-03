package org.tasks.auth

import android.net.Uri
import androidx.core.net.toUri
import net.openid.appauth.AuthorizationServiceConfiguration
import org.tasks.BuildConfig
import org.tasks.sync.SyncClients
import kotlin.coroutines.suspendCoroutine

data class IdentityProvider(
    val name: String,
    val discoveryEndpoint: Uri,
    val clientId: String,
    val redirectUri: Uri,
    val scope: String,
    /**
     * True when one authority serves many tenants (Microsoft's `common`). Its discovery document names the issuer as
     * `https://login.microsoftonline.com/{tenantid}/v2.0`, literally, while every ID token carries the real tenant id, and AppAuth
     * refuses a token whose issuer is not equal to the document's ("Issuer mismatch"). So the request is made with a configuration
     * that holds only the endpoints, and the issuer is checked against the token's own `tid` instead (MicrosoftIdToken).
     */
    val multiTenant: Boolean = false,
) {
    suspend fun retrieveConfig(): AuthorizationServiceConfiguration {
        return suspendCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromUrl(discoveryEndpoint) { serviceConfiguration, ex ->
                cont.resumeWith(
                    when {
                        ex != null -> Result.failure(ex)
                        serviceConfiguration != null -> Result.success(serviceConfiguration)
                        else -> Result.failure(IllegalStateException())
                    }
                )
            }
        }
    }

    /** The configuration to authorise with: the discovered one, or for a multi-tenant authority only its endpoints. */
    fun requestConfig(discovered: AuthorizationServiceConfiguration): AuthorizationServiceConfiguration =
        if (multiTenant) {
            AuthorizationServiceConfiguration(
                discovered.authorizationEndpoint,
                discovered.tokenEndpoint,
                discovered.registrationEndpoint,
            )
        } else {
            discovered
        }

    companion object {
        /** Microsoft To Do: our own Entra registration (any organisation's accounts and personal ones), all from [SyncClients]. */
        val MICROSOFT = microsoft(BuildConfig.APPLICATION_ID)

        fun microsoft(applicationId: String) = IdentityProvider(
            name = "Microsoft",
            discoveryEndpoint = SyncClients.microsoftDiscoveryUrl.toUri(),
            clientId = SyncClients.MICROSOFT_CLIENT_ID,
            redirectUri = SyncClients.microsoftRedirectUri(applicationId).toUri(),
            scope = SyncClients.MICROSOFT_SCOPE,
            multiTenant = true,
        )
    }
}
