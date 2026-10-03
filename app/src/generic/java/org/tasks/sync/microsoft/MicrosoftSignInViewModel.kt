package org.tasks.sync.microsoft

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.auth.DebugConnectionBuilder
import org.tasks.auth.IdentityProvider
import org.tasks.auth.MicrosoftAuthenticationActivity
import org.tasks.auth.MicrosoftAuthenticationActivity.Companion.EXTRA_SERVICE_DISCOVERY
import org.tasks.auth.MicrosoftSignInProblemActivity
import org.tasks.sync.SyncClients
import javax.inject.Inject

@HiltViewModel
class MicrosoftSignInViewModel @Inject constructor(
    private val debugConnectionBuilder: DebugConnectionBuilder,
) : ViewModel() {
    /** [loginHint] names the account to sign in again (its address), so Microsoft offers that one first. */
    fun signIn(activity: Activity, loginHint: String? = null) {
        if (!SyncClients.microsoftConfigured) {
            // No Entra registration in this build yet: say so, and do not call Microsoft with a zero client ID.
            Toast.makeText(activity, R.string.microsoft_not_set_up, Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch {
            val idp = IdentityProvider.MICROSOFT
            val discovered = try {
                idp.retrieveConfig()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No network, or Microsoft's discovery document could not be read: say so instead of crashing.
                val failure = (e as? AuthorizationException)?.let { MicrosoftSignInErrors.classify(it) }
                    ?: MicrosoftSignInFailure(MicrosoftFailureKind.NETWORK)
                activity.startActivity(
                    failure.toIntent(Intent(activity, MicrosoftSignInProblemActivity::class.java))
                )
                return@launch
            }
            val serviceConfig = idp.requestConfig(discovered)
            val authRequest = AuthorizationRequest
                .Builder(
                    serviceConfig,
                    idp.clientId,
                    ResponseTypeValues.CODE,
                    idp.redirectUri
                )
                .setScope(idp.scope)
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .apply { loginHint?.takeIf { it.isNotBlank() }?.let { setLoginHint(it) } }
                .build()
            val intent = Intent(activity, MicrosoftAuthenticationActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // The discovery document still names the userinfo endpoint, which the request configuration no longer carries.
            intent.putExtra(
                EXTRA_SERVICE_DISCOVERY,
                discovered.discoveryDoc!!.docJson.toString()
            )

            val authorizationService = AuthorizationService(
                activity,
                AppAuthConfiguration.Builder()
                    .setBrowserMatcher(AnyBrowserMatcher.INSTANCE)
                    .setConnectionBuilder(
                        if (BuildConfig.DEBUG) {
                            debugConnectionBuilder
                        } else {
                            DefaultConnectionBuilder.INSTANCE
                        }
                    )
                    .build()
            )
            authorizationService.performAuthorizationRequest(
                authRequest,
                PendingIntent.getActivity(
                    activity,
                    authRequest.hashCode(),
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                ),
                // The user left the browser without finishing. Microsoft's own "approval required" page can end this way,
                // so the cancelled screen points to "What to tell your IT admin".
                PendingIntent.getActivity(
                    activity,
                    authRequest.hashCode() + 1,
                    Intent(activity, MicrosoftAuthenticationActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                ),
                authorizationService.createCustomTabsIntentBuilder()
                    .build()
            )
        }
    }
}
