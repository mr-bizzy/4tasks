package org.tasks.sync.google

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import org.tasks.googleapis.GoogleAuthFailureException
import org.tasks.googleapis.GoogleFailureClassifier
import org.tasks.googleapis.TokenFailureKind
import org.tasks.gtasks.GoogleAccountManager
import org.tasks.sync.SyncClients
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * DEBUG BUILDS ONLY (src/debug): proves SYNC-PLAN risk 1, that the account-manager token flow still works for 4Tasks with
 * its own Android OAuth client. It does what the real sign-in does (the system account picker, then
 * AccountManager.getAuthToken("oauth2:" + the Tasks scope) with this activity so Google's consent can show), then calls
 * GET https://tasks.googleapis.com/tasks/v1/users/@me/lists and logs, under the tag [TAG], the stages, the HTTP status, the
 * number of lists, and on a failure the exception class and message plus what 4Tasks classifies it as. It never logs the token.
 *
 * Run (emulator or a test phone, debug build installed, a Google account on it):
 *   adb shell am start -n uk.mr_biz.fourtasks/org.tasks.sync.google.GoogleTokenProbeActivity
 *   adb logcat -s 4TasksProbe
 * Optional: --es account name@example.com (skips the picker if that account is already visible to 4Tasks).
 */
class GoogleTokenProbeActivity : Activity() {
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setPadding(32, 32, 32, 32); textSize = 13f }
        setContentView(ScrollView(this).apply { addView(out) })
        say("start: package=$packageName scope=${SyncClients.GOOGLE_TASKS_SCOPE}")
        val wanted = intent.getStringExtra(EXTRA_ACCOUNT)
        val visible = AccountManager.get(this).getAccountsByType(GoogleAccountManager.GOOGLE_ACCOUNT_TYPE)
        say("accounts visible to this app: ${visible.size}")
        val known = visible.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
        if (known != null) {
            probe(known.name)
        } else {
            startActivityForResult(
                AccountManager.newChooseAccountIntent(
                    null, null, arrayOf(GoogleAccountManager.GOOGLE_ACCOUNT_TYPE), null, null, null, null,
                ),
                RC_CHOOSE,
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val name = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (requestCode == RC_CHOOSE && resultCode == RESULT_OK && name != null) {
            probe(name)
        } else {
            say("picker: no account chosen (resultCode=$resultCode)")
        }
    }

    private fun probe(account: String) {
        // The account name is the user's own address; it is only logged on this test device.
        say("account chosen: $account")
        thread(name = "4TasksProbe") {
            val token = try {
                val bundle = AccountManager.get(this)
                    .getAuthToken(
                        AccountManager.get(this).getAccountsByType(GoogleAccountManager.GOOGLE_ACCOUNT_TYPE)
                            .first { it.name == account },
                        "oauth2:${SyncClients.GOOGLE_TASKS_SCOPE}",
                        Bundle(),
                        this,
                        null,
                        null,
                    ).result
                bundle.get(AccountManager.KEY_INTENT)?.let { say("token: Google returned an intent (consent screen)") }
                bundle.getString(AccountManager.KEY_AUTHTOKEN)
            } catch (e: Exception) {
                val failure = GoogleAccountManager.failureOf(e).failure
                say("token: FAILED ${e.javaClass.name}: ${e.message} -> 4Tasks classifies it as ${failure.code}")
                return@thread
            }
            if (token.isNullOrEmpty()) {
                say("token: none returned -> ${GoogleFailureClassifier.fromToken(TokenFailureKind.NO_TOKEN, null).code}")
                return@thread
            }
            say("token: obtained (length ${token.length}, not logged)")
            callTasksApi(token)
        }
    }

    private fun callTasksApi(token: String) {
        try {
            val c = URL(LISTS_URL).openConnection() as HttpURLConnection
            c.setRequestProperty("Authorization", "Bearer $token")
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            val status = c.responseCode
            val body = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status in 200..299) {
                val lists = JSONObject(body).optJSONArray("items")?.length() ?: 0
                say("tasks api: HTTP $status, lists=$lists")
            } else {
                val failure = GoogleFailureClassifier.fromHttp(status, null, body.take(500))
                say("tasks api: HTTP $status -> ${failure.code}; body: ${body.take(300).replace('\n', ' ')}")
                // the real flow retries a 401 once with a fresh token; log that it would
                if (status == 401) say("tasks api: 401 means the token was refused (the real flow would invalidate it and retry once)")
            }
        } catch (e: GoogleAuthFailureException) {
            say("tasks api: FAILED ${e.failure.code}")
        } catch (e: Exception) {
            say("tasks api: FAILED ${e.javaClass.name}: ${e.message} -> ${GoogleFailureClassifier.fromException(e).code}")
        }
    }

    private fun say(line: String) {
        Log.i(TAG, line)
        runOnUiThread { out.append(line + "\n") }
    }

    companion object {
        const val TAG = "4TasksProbe"
        const val EXTRA_ACCOUNT = "account"
        private const val RC_CHOOSE = 1
        private const val LISTS_URL = "https://tasks.googleapis.com/tasks/v1/users/@me/lists"
    }
}
