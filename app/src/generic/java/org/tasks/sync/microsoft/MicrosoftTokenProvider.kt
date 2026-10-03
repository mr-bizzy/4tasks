package org.tasks.sync.microsoft

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import org.tasks.R
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.http.UnauthorizedException
import org.tasks.security.KeyStoreEncryption
import java.io.IOException
import javax.inject.Inject

/**
 * Hands out the access token for a Microsoft account, renewing it when it has expired. The sign-in state (with the refresh token)
 * is kept encrypted in the account's password column; nothing here, and nothing logged on the way, contains a token.
 *
 * With the `common` authority the token endpoint is `https://login.microsoftonline.com/common/oauth2/v2.0/token`; a refresh token
 * from any tenant is redeemed there (Microsoft's refresh-token flow takes the same {tenant} values as the sign-in).
 */
class MicrosoftTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: KeyStoreEncryption,
    private val caldavDao: CaldavDao,
) {
    fun hasCredentials(account: CaldavAccount): Boolean = !account.password.isNullOrBlank()

    suspend fun getToken(account: CaldavAccount): String {
        val authState = encryption.decrypt(account.password)?.let { AuthState.jsonDeserialize(it) }
            ?: throw UnauthorizedException(context.getString(R.string.microsoft_error_sign_in_again))
        if (authState.needsTokenRefresh) {
            withContext(NonCancellable) {
                val (token, ex) = context.requestTokenRefresh(authState)
                if (ex != null && (ex.type != AuthorizationException.TYPE_OAUTH_TOKEN_ERROR ||
                        ex.error in MicrosoftSignInErrors.TEMPORARY_ERRORS)) {
                    // No connection (or Microsoft's service is down): the sign-in itself is fine. Say so, and try again at the next
                    // sync, instead of using the expired token and reporting a sign-in problem.
                    throw IOException(context.getString(R.string.microsoft_error_renew_network))
                }
                authState.update(token, ex)
                if (authState.isAuthorized) {
                    encryption.encrypt(authState.jsonSerializeString())?.let { encrypted ->
                        account.password = encrypted
                        account.id.takeIf { it != 0L }?.let { caldavDao.setPassword(it, encrypted) }
                    }
                }
            }
        }
        if (!authState.isAuthorized) {
            // Microsoft refused the renewal (the user revoked 4Tasks, changed their password, an admin withdrew consent ...):
            // the account page shows "Sign in" for a message that starts with "401 Unauthorized".
            throw UnauthorizedException(context.getString(R.string.microsoft_error_sign_in_again))
        }
        return authState.accessToken!!
    }
}
