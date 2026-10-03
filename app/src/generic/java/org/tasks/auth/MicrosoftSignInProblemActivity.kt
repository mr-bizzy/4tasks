package org.tasks.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.fourlink.FamilyCard
import org.tasks.fourlink.FamilyScreen
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.sync.SyncClients
import org.tasks.sync.microsoft.MicrosoftAdminMessage
import org.tasks.sync.microsoft.MicrosoftFailureKind
import org.tasks.sync.microsoft.MicrosoftSignInFailure
import org.tasks.themes.TasksSettingsTheme

/**
 * What Microsoft said when a sign-in did not work, and, when an organisation is the reason, "What to tell your IT admin": 4Tasks'
 * name and publisher, the one permission and why, how an administrator allows it, the application (client) ID and redirect, a
 * ready-made message to share or copy, and the help page.
 *
 * It opens by itself only for an organisation's refusal ([MicrosoftFailureKind.ORGANISATION]). For a cancelled sign-in, a declined
 * consent or another failure it shows the plain message with one button to the admin screen, because Microsoft's own "approval
 * required" page can send the user back to the app as "cancelled" or "declined".
 */
@AndroidEntryPoint
class MicrosoftSignInProblemActivity : ThemedInjectingAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val failure = MicrosoftSignInFailure.fromIntent(intent)
        setContent {
            TasksSettingsTheme(theme = tasksTheme.themeBase.index, primary = themeColor.primaryColor) {
                var showAdmin by rememberSaveable { mutableStateOf(failure.kind == MicrosoftFailureKind.ORGANISATION) }
                if (showAdmin) {
                    AdminScreen(
                        failure = failure,
                        onBack = { finish() },
                        onShare = { share(failure) },
                        onCopy = { copy(failure) },
                        onHelp = { openHelp() },
                    )
                } else {
                    PlainScreen(
                        failure = failure,
                        onBack = { finish() },
                        onAdmin = { showAdmin = true },
                    )
                }
            }
        }
    }

    private fun share(failure: MicrosoftSignInFailure) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, MicrosoftAdminMessage.subject(this))
            .putExtra(Intent.EXTRA_TEXT, MicrosoftAdminMessage.body(this, failure.microsoftText))
        startActivity(Intent.createChooser(send, getString(R.string.microsoft_admin_share_chooser)))
    }

    private fun copy(failure: MicrosoftSignInFailure) {
        // Android 13 and later (4Tasks needs it) confirms a copy on screen itself.
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            ClipData.newPlainText(
                MicrosoftAdminMessage.subject(this),
                MicrosoftAdminMessage.body(this, failure.microsoftText),
            )
        )
    }

    private fun openHelp() {
        startActivity(Intent(Intent.ACTION_VIEW, SyncClients.ADMIN_HELP_URL_MICROSOFT.toUri()))
    }
}

/** A failure that is not (yet) an organisation's refusal: one plain message, Microsoft's text if there is any, and a way to the admin screen. */
@Composable
private fun PlainScreen(failure: MicrosoftSignInFailure, onBack: () -> Unit, onAdmin: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    FamilyScreen(title = stringResource(R.string.microsoft_sign_in_title), onBack = onBack) {
        FamilyCard(title = failure.headline(context)) {
            failure.microsoftText?.let { text ->
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (failure.kind != MicrosoftFailureKind.NETWORK && failure.kind != MicrosoftFailureKind.NOT_SET) {
            FamilyCard(title = null, body = stringResource(R.string.microsoft_failure_admin_hint)) {
                TextButton(onClick = onAdmin) { Text(stringResource(R.string.microsoft_admin_open)) }
            }
        }
    }
}

/** "What to tell your IT admin", in the family look: cards 8 dp apart, 14 dp inside, titleSmall over bodySmall. */
@Composable
internal fun AdminScreen(
    failure: MicrosoftSignInFailure,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onHelp: () -> Unit,
    clientId: String = SyncClients.MICROSOFT_CLIENT_ID,
    redirectUri: String = SyncClients.microsoftRedirectUri(BuildConfig.APPLICATION_ID),
) {
    FamilyScreen(title = stringResource(R.string.microsoft_admin_title), onBack = onBack) {
        failure.microsoftText?.let { text ->
            FamilyCard(title = stringResource(R.string.microsoft_admin_said_title)) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FamilyCard(
            title = stringResource(R.string.microsoft_admin_why_title),
            body = stringResource(R.string.microsoft_admin_why_body),
        )
        FamilyCard(title = stringResource(R.string.microsoft_admin_app_title)) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val line = MaterialTheme.typography.bodySmall
                    val colour = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(stringResource(R.string.microsoft_admin_app_name, SyncClients.APP_NAME), style = line, color = colour)
                    Text(
                        stringResource(R.string.microsoft_admin_app_publisher, SyncClients.PUBLISHER_NAME, SyncClients.PUBLISHER_DOMAIN),
                        style = line,
                        color = colour,
                    )
                    Text(stringResource(R.string.microsoft_admin_app_client_id, clientId), style = line, color = colour)
                    Text(stringResource(R.string.microsoft_admin_app_redirect, redirectUri), style = line, color = colour)
                }
            }
        }
        FamilyCard(title = stringResource(R.string.microsoft_admin_permission_title, SyncClients.MICROSOFT_PERMISSION)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val colour = MaterialTheme.colorScheme.onSurfaceVariant
                Text(stringResource(R.string.microsoft_admin_permission_what), style = MaterialTheme.typography.bodySmall, color = colour)
                Text(stringResource(R.string.microsoft_admin_permission_why), style = MaterialTheme.typography.bodySmall, color = colour)
            }
        }
        FamilyCard(
            title = stringResource(R.string.microsoft_admin_howto_title),
            body = stringResource(R.string.microsoft_admin_howto_body),
        )
        FamilyCard(
            title = stringResource(R.string.microsoft_admin_send_title),
            body = stringResource(R.string.microsoft_admin_send_body),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.microsoft_admin_share)) }
                OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.microsoft_admin_copy)) }
                OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.microsoft_admin_help)) }
            }
        }
    }
}
