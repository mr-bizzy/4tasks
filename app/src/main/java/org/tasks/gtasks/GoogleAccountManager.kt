package org.tasks.gtasks

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.google.api.services.drive.DriveScopes
import com.google.api.services.tasks.TasksScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tasks.Strings.isNullOrEmpty
import org.tasks.googleapis.GoogleAuthFailureException
import org.tasks.googleapis.GoogleFailure
import org.tasks.googleapis.GoogleFailureClassifier
import org.tasks.googleapis.TokenFailureKind
import org.tasks.preferences.Preferences
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Google accounts through Android's account manager, with no Google SDK: the account is picked with
 * AccountManager.newChooseAccountIntent (which is what makes it visible to 4Tasks on Android 8+, so GET_ACCOUNTS is not
 * needed), and a token for a scope is asked for as "oauth2:<scope>". Google identifies 4Tasks to its authenticator by
 * package name and signing-certificate SHA-1 (the Android OAuth client in the Cloud project). See docs/SYNC-PLAN.md
 * section 1, risk 1.
 */
class GoogleAccountManager @Inject constructor(
        @ApplicationContext context: Context?,
        private val preferences: Preferences
) {
    private val accountManager: AccountManager = AccountManager.get(context)

    val accounts: List<String>
        get() = accountList.map { it.name }

    private val accountList: List<Account>
        get() = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE).toList()

    fun getAccount(name: String?): Account? = if (isNullOrEmpty(name)) {
        null
    } else {
        accountList.find { name.equals(it.name, ignoreCase = true) }
    }

    fun canAccessAccount(name: String): Boolean = getAccount(name) != null

    /**
     * A token for [scope], or a [GoogleAuthFailureException] that says why not (SYNC-PLAN 3, 3b). Never null, so a
     * request is never sent without a token. Asked for in the background, so Google may raise its own "sign in again"
     * notification once.
     */
    @Throws(GoogleAuthFailureException::class)
    suspend fun getAccessToken(name: String?, scope: String): String {
        val account = name?.let { getAccount(it) }
        if (account == null) {
            Timber.e("Cannot find the Google account")
            throw GoogleAuthFailureException(GoogleFailure.NoAccount)
        }
        val alreadyNotified = preferences.alreadyNotified(name, scope)
        val token = try {
            withContext(Dispatchers.IO) {
                accountManager.blockingGetAuthToken(account, "oauth2:$scope", !alreadyNotified)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            throw failureOf(e)
        }
        preferences.setAlreadyNotified(name, scope, isNullOrEmpty(token))
        if (token.isNullOrEmpty()) {
            throw GoogleAuthFailureException(GoogleFailure.NeedsSignIn, "no token")
        }
        return token
    }

    /** Asks with Google's own screens (the account manager shows consent itself through [activity]). */
    suspend fun getTasksAuthToken(activity: Activity, accountName: String): Bundle? =
            getToken(TasksScopes.TASKS, activity, accountName)

    suspend fun getDriveAuthToken(activity: Activity, accountName: String): Bundle? =
            getToken(DriveScopes.DRIVE_FILE, activity, accountName)

    @SuppressLint("CheckResult")
    private suspend fun getToken(scope: String, activity: Activity, accountName: String): Bundle? {
        val account = getAccount(accountName)
                ?: throw GoogleAuthFailureException(GoogleFailure.NoAccount)
        return withContext(Dispatchers.IO) {
            val bundle = try {
                accountManager
                        .getAuthToken(account, "oauth2:$scope", Bundle(), activity, null, null)
                        .result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                throw failureOf(e)
            }
            preferences.setAlreadyNotified(accountName, scope, false)
            bundle
        }
    }

    fun invalidateToken(token: String?) {
        accountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, token)
    }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"

        /** The account manager's exceptions in the terms [GoogleFailureClassifier] works with (it knows no Android types). */
        fun failureOf(e: Exception): GoogleAuthFailureException {
            val kind = when (e) {
                is OperationCanceledException -> TokenFailureKind.CANCELLED
                is AuthenticatorException -> TokenFailureKind.AUTHENTICATOR
                is IOException -> TokenFailureKind.IO
                else -> TokenFailureKind.OTHER
            }
            return GoogleAuthFailureException(GoogleFailureClassifier.fromToken(kind, e.message), e.message, e)
        }
    }
}
