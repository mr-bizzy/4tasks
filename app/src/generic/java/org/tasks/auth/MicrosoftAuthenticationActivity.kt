package org.tasks.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceDiscovery
import okhttp3.Request
import org.json.JSONObject
import org.tasks.R
import org.tasks.analytics.Constants
import org.tasks.analytics.Firebase
import org.tasks.data.UUIDHelper
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_MICROSOFT
import org.tasks.http.HttpClientFactory
import org.tasks.security.KeyStoreEncryption
import org.tasks.sync.microsoft.MicrosoftFailureKind
import org.tasks.sync.microsoft.MicrosoftIdToken
import org.tasks.sync.microsoft.MicrosoftSignInErrors
import org.tasks.sync.microsoft.MicrosoftSignInFailure
import org.tasks.sync.microsoft.requestTokenExchange
import java.io.IOException
import javax.inject.Inject

/**
 * Where AppAuth sends the user back after Microsoft's sign-in page: with a code (then exchanged for tokens and the account is
 * saved), with an error, or because the user left the browser. A failure goes to [MicrosoftSignInProblemActivity], which says
 * what Microsoft said and, when an organisation is the reason, what to tell its IT admin.
 */
@AndroidEntryPoint
class MicrosoftAuthenticationActivity : ComponentActivity() {

    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var encryption: KeyStoreEncryption
    @Inject lateinit var httpClientFactory: HttpClientFactory
    @Inject lateinit var firebase: Firebase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)
        if (response == null || exception != null) {
            problem(
                exception?.let { MicrosoftSignInErrors.classify(it) }
                    ?: MicrosoftSignInFailure.other(getString(R.string.microsoft_failure_exchange))
            )
            return
        }
        val authState = AuthState(response, null)
        lifecycleScope.launch {
            val (tokens, tokenException) = requestTokenExchange(response)
            authState.update(tokens, tokenException)
            if (!authState.isAuthorized) {
                problem(
                    tokenException?.let { MicrosoftSignInErrors.classify(it) }
                        ?: MicrosoftSignInFailure.other(getString(R.string.microsoft_failure_exchange))
                )
                return@launch
            }
            // The `common` authority cannot use AppAuth's own issuer check (IdentityProvider.multiTenant), so check it here.
            val claims = MicrosoftIdToken.claims(tokens?.idToken)
            if (claims == null || !MicrosoftIdToken.issuerMatchesTenant(claims)) {
                problem(MicrosoftSignInFailure.other(getString(R.string.microsoft_failure_token_check)))
                return@launch
            }
            val name = MicrosoftIdToken.accountName(claims, getEmail(authState.accessToken))
            if (name == null) {
                problem(MicrosoftSignInFailure.other(getString(R.string.microsoft_failure_profile)))
                return@launch
            }
            caldavDao
                .getAccount(TYPE_MICROSOFT, name)
                ?.let {
                    caldavDao.update(
                        it.copy(password = encryption.encrypt(authState.jsonSerializeString()))
                    )
                }
                ?: caldavDao
                    .insert(
                        CaldavAccount(
                            uuid = UUIDHelper.newUUID(),
                            name = name,
                            username = name,
                            password = encryption.encrypt(authState.jsonSerializeString()),
                            accountType = TYPE_MICROSOFT,
                        )
                    )
                    .also {
                        firebase.logEvent(
                            R.string.event_sync_add_account,
                            R.string.param_type to Constants.SYNC_TYPE_MICROSOFT
                        )
                    }
            finish()
        }
        setContent {
            var showDialog by remember { mutableStateOf(true) }
            if (showDialog) {
                Dialog(
                    onDismissRequest = { showDialog = false },
                    DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .background(White, shape = RoundedCornerShape(8.dp))
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    /** The address the userinfo endpoint gives for the account, or null (the token's own claims are the fall-back). */
    private suspend fun getEmail(accessToken: String?): String? = withContext(Dispatchers.IO) {
        if (accessToken == null) {
            return@withContext null
        }
        try {
            val userinfoEndpoint = intent.getStringExtra(EXTRA_SERVICE_DISCOVERY)
                ?.let { AuthorizationServiceDiscovery(JSONObject(it)).userinfoEndpoint }
                ?: return@withContext null
            httpClientFactory
                .newClient(foreground = false)
                .newCall(
                    Request.Builder()
                        .url(userinfoEndpoint.toString())
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                )
                .execute()
                .use { userInfo ->
                    if (!userInfo.isSuccessful) return@use null
                    val body = userInfo.body?.string() ?: return@use null
                    JSONObject(body).optString("email", "").takeIf { it.isNotBlank() }
                }
        } catch (e: IOException) {
            null
        } catch (e: org.json.JSONException) {
            null
        }
    }

    private fun problem(failure: MicrosoftSignInFailure) {
        startActivity(failure.toIntent(Intent(this, MicrosoftSignInProblemActivity::class.java)))
        finish()
    }

    companion object {
        const val EXTRA_SERVICE_DISCOVERY = "extra_service_discovery"
    }
}
