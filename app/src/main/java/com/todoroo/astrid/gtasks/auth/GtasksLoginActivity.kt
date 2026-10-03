/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.gtasks.auth

import android.accounts.AccountManager
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.todoroo.andlib.utility.DialogUtilities
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tasks.PermissionUtil.verifyPermissions
import org.tasks.R
import org.tasks.analytics.Constants
import org.tasks.analytics.Firebase
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_GOOGLE_TASKS
import org.tasks.dialogs.DialogBuilder
import org.tasks.googleapis.GoogleAuthFailureException
import org.tasks.googleapis.GoogleFailure
import org.tasks.googleapis.GoogleFailureClassifier
import org.tasks.googleapis.InvokerFactory
import org.tasks.gtasks.GoogleAccountManager
import org.tasks.preferences.PermissionRequestor
import org.tasks.sync.google.GoogleAdminHelpActivity
import org.tasks.sync.google.GoogleFailureText
import javax.inject.Inject

/**
 * This activity allows users to sign in or log in to Google Tasks through the Android account
 * manager: the system's account picker, then a token for the Tasks scope, then one call to the Tasks API to prove it
 * works before the account is kept. Every way that can fail is sorted into a [GoogleFailure] (SYNC-PLAN 3, 3b): a
 * cancel says nothing, an organisation's block opens "What to tell your Workspace admin", the rest set the result's
 * [EXTRA_ERROR] to plain words the caller shows.
 *
 * @author Sam Bosley
 */
@AndroidEntryPoint
class GtasksLoginActivity : AppCompatActivity() {
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var googleAccountManager: GoogleAccountManager
    @Inject lateinit var invokerFactory: InvokerFactory
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var firebase: Firebase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chooseAccount()
    }

    private fun chooseAccount() {
        // Choosing the account here is also what makes it visible to 4Tasks (Android 8+), so no contacts permission.
        val chooseAccountIntent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf(GoogleAccountManager.GOOGLE_ACCOUNT_TYPE), null, null, null, null)
        startActivityForResult(chooseAccountIntent, RC_CHOOSE_ACCOUNT)
    }

    private suspend fun signIn(account: String) {
        val pd = dialogBuilder.newProgressDialog(R.string.gtasks_GLA_authenticating)
        pd.show()
        signIn(account, pd)
    }

    private suspend fun signIn(accountName: String, pd: ProgressDialog) {
        try {
            val bundle = googleAccountManager.getTasksAuthToken(this, accountName)
            val intent = bundle?.get(AccountManager.KEY_INTENT)
            org.tasks.sync.google.GoogleDiagnostics.stage(
                "sign-in: answer from the account manager: token=${!bundle?.getString(AccountManager.KEY_AUTHTOKEN).isNullOrEmpty()}, google screen needed=${intent is Intent}"
            )
            if (intent is Intent) {
                // Google wants to show a screen of its own; it is shown, and the user starts again from here.
                startActivity(intent)
                finishWith(pd, Activity.RESULT_CANCELED)
                return
            }
            if (bundle?.getString(AccountManager.KEY_AUTHTOKEN).isNullOrEmpty()) {
                throw GoogleAuthFailureException(GoogleFailure.NeedsSignIn, "no token")
            }
            // One real call, so a block or a missing grant shows now and not at the first background sync.
            invokerFactory.getGtasksInvoker(accountName).allGtaskLists(null)
            org.tasks.sync.google.GoogleDiagnostics.stage("sign-in: the Tasks API answered, the account is kept")
            withContext(NonCancellable) {
                val account = caldavDao.getAccount(TYPE_GOOGLE_TASKS, accountName)
                if (account == null) {
                    caldavDao.insert(
                        CaldavAccount(
                            accountType = TYPE_GOOGLE_TASKS,
                            uuid = accountName,
                            name = accountName,
                            username = accountName,
                        )
                    )
                    firebase.logEvent(
                            R.string.event_sync_add_account,
                            R.string.param_type to Constants.SYNC_TYPE_GOOGLE_TASKS
                    )
                } else {
                    caldavDao.update(
                        account.copy(error = "")
                    )
                    caldavDao.resetLastSync(accountName)
                }
            }
            finishWith(pd, Activity.RESULT_OK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = GoogleFailureClassifier.fromException(e)
            org.tasks.sync.google.GoogleDiagnostics.failure("sign-in", e, failure)
            fail(accountName, failure, e.message, pd)
        }
    }

    private suspend fun fail(accountName: String, failure: GoogleFailure, rawMessage: String?, pd: ProgressDialog) {
        if (failure != GoogleFailure.Cancelled) {
            // an account that is already here shows it on its own screen too
            withContext(NonCancellable) {
                caldavDao.getAccount(TYPE_GOOGLE_TASKS, accountName)?.let { caldavDao.setError(it.id, failure.stored()) }
            }
        }
        if (failure == GoogleFailure.AdminBlocked) {
            startActivity(GoogleAdminHelpActivity.intent(this, accountName))
        }
        val message = if (failure == GoogleFailure.AdminBlocked) {
            null // the admin screen has opened; no toast on top of it
        } else {
            GoogleFailureText.forSignIn(this, failure, rawMessage)
        }
        finishWith(pd, Activity.RESULT_CANCELED, message)
    }

    private fun finishWith(pd: ProgressDialog, resultCode: Int, error: String? = null) {
        setResult(resultCode, error?.let { Intent().putExtra(EXTRA_ERROR, it) })
        DialogUtilities.dismissDialog(this, pd)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_CHOOSE_ACCOUNT) {
            val account = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (resultCode == Activity.RESULT_OK && account != null) {
                lifecycleScope.launch {
                    signIn(account)
                }
            } else {
                // backed out of the account picker
                finish()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PermissionRequestor.REQUEST_GOOGLE_ACCOUNTS) {
            if (verifyPermissions(grantResults)) {
                chooseAccount()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    companion object {
        const val EXTRA_ERROR = "extra_error"
        private const val RC_CHOOSE_ACCOUNT = 10988
    }
}
