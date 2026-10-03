package org.tasks.sync.google

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.extensions.Context.openUri
import org.tasks.fourlink.FamilyCard
import org.tasks.fourlink.FamilyScreen
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.sync.SyncClients
import org.tasks.themes.TasksSettingsTheme

/**
 * "What to tell your Workspace admin": shown when Google says the user's organisation has not allowed 4Tasks
 * (docs/SYNC-PLAN.md section 3b). It says what 4Tasks is, who publishes it, the one scope and why, and the admin's route;
 * its buttons share or copy a ready-made message and open the help page for admins.
 */
@AndroidEntryPoint
class GoogleAdminHelpActivity : ThemedInjectingAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val account = intent.getStringExtra(EXTRA_ACCOUNT).orEmpty()
        val facts = GoogleAdminMessage.Facts(account = account, packageName = packageName)
        val texts = GoogleAdminMessage.Texts(
            message = getString(R.string.google_admin_message),
            clientIdLine = getString(R.string.google_admin_client_id_line),
        )
        val message = GoogleAdminMessage.build(texts, facts)
        setContent {
            TasksSettingsTheme(theme = tasksTheme.themeBase.index, primary = themeColor.primaryColor) {
                GoogleAdminScreen(
                    packageName = packageName,
                    clientIdLine = GoogleAdminMessage.clientIdLine(texts, facts),
                    message = message,
                    onBack = { finish() },
                    onShare = { share(message) },
                    onCopy = { copy(message) },
                    onHelpPage = { openUri(SyncClients.GOOGLE_ADMIN_HELP_URL) },
                )
            }
        }
    }

    private fun share(message: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.google_admin_message_subject))
            .putExtra(Intent.EXTRA_TEXT, message)
        startActivity(Intent.createChooser(send, getString(R.string.google_admin_share_chooser)))
    }

    private fun copy(message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.google_admin_message_subject), message))
        Toast.makeText(this, R.string.google_admin_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ACCOUNT = "extra_account"

        fun intent(context: Context, account: String?): Intent =
            Intent(context, GoogleAdminHelpActivity::class.java).putExtra(EXTRA_ACCOUNT, account)
    }
}

/** Cards in the family look: titleSmall titles, bodySmall text, 14 dp padding, 8 dp gaps (see FamilyScreen). */
@Composable
fun GoogleAdminScreen(
    packageName: String,
    clientIdLine: String,
    message: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onHelpPage: () -> Unit,
) {
    FamilyScreen(title = stringResource(R.string.google_admin_title), onBack = onBack) {
        FamilyCard(
            title = stringResource(R.string.google_admin_intro_title),
            body = stringResource(R.string.google_admin_intro_body),
        )
        FamilyCard(
            title = stringResource(R.string.google_admin_about_title),
            body = stringResource(R.string.google_admin_about_body, packageName, clientIdLine),
        )
        FamilyCard(
            title = stringResource(R.string.google_admin_scope_title),
            body = stringResource(
                R.string.google_admin_scope_body,
                SyncClients.GOOGLE_TASKS_SCOPE,
                SyncClients.GOOGLE_TASKS_SCOPE_TITLE,
            ),
        )
        FamilyCard(
            title = stringResource(R.string.google_admin_route_title),
            body = stringResource(R.string.google_admin_route_body, packageName),
        )
        FamilyCard(title = stringResource(R.string.google_admin_message_title)) {
            SelectionContainer {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.google_admin_share))
                }
                OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.google_admin_copy))
                }
                TextButton(onClick = onHelpPage, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.google_admin_help_page))
                }
            }
        }
    }
}
