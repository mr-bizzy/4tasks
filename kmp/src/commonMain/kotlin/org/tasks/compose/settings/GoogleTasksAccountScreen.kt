package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.authentication_required
import tasks.kmp.generated.resources.cancel
import tasks.kmp.generated.resources.google_sync_limits_summary
import tasks.kmp.generated.resources.google_sync_limits_title
import tasks.kmp.generated.resources.logout
import tasks.kmp.generated.resources.logout_confirmation
import tasks.kmp.generated.resources.logout_warning
import tasks.kmp.generated.resources.reinitialize_account
import tasks.kmp.generated.resources.sign_in_with_google

@Composable
fun GoogleTasksAccountScreen(
    error: String?,
    isUnauthorized: Boolean,
    accountName: String,
    onSignIn: () -> Unit,
    onDelete: () -> Unit,
    signInTitle: StringResource = Res.string.sign_in_with_google,
    /** Replaces "Authentication required" under the sign-in row, e.g. to say testers must sign in every week. */
    unauthorizedSummary: String? = null,
    /** When set, a row under the error opens "What to tell your Workspace admin" (the organisation blocked the app). */
    adminHelpTitle: String? = null,
    adminHelpSummary: String? = null,
    onAdminHelp: (() -> Unit)? = null,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        if (error != null) {
            if (isUnauthorized) {
                Column(
                    modifier = Modifier.padding(horizontal = SettingsContentPadding),
                ) {
                    SettingsItemCard {
                        PreferenceRow(
                            title = stringResource(signInTitle),
                            summary = unauthorizedSummary ?: stringResource(Res.string.authentication_required),
                            summaryMaxLines = 5,
                            icon = Icons.Outlined.Login,
                            onClick = onSignIn,
                        )
                    }
                }
            } else {
                AccountErrorBanner(
                    error = error,
                    modifier = Modifier.padding(horizontal = SettingsContentPadding),
                )
                if (onAdminHelp != null && adminHelpTitle != null) {
                    Spacer(modifier = Modifier.height(SettingsContentPadding))
                    Column(
                        modifier = Modifier.padding(horizontal = SettingsContentPadding),
                    ) {
                        SettingsItemCard {
                            PreferenceRow(
                                title = adminHelpTitle,
                                summary = adminHelpSummary,
                                summaryMaxLines = 3,
                                icon = Icons.Outlined.Info,
                                onClick = onAdminHelp,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(SettingsContentPadding))
        }

        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        ) {
            SettingsItemCard {
                PreferenceRow(
                    title = stringResource(Res.string.google_sync_limits_title),
                    summary = stringResource(Res.string.google_sync_limits_summary),
                    summaryMaxLines = 8,
                )
            }
            Spacer(modifier = Modifier.height(SettingsContentPadding))
            SettingsItemCard {
                PreferenceRow(
                    title = stringResource(Res.string.reinitialize_account),
                    icon = Icons.Outlined.Refresh,
                    onClick = onSignIn,
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))

        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        ) {
            DangerCard(
                icon = Icons.Outlined.Logout,
                title = stringResource(Res.string.logout),
                tint = MaterialTheme.colorScheme.error,
                onClick = { showDeleteDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(Res.string.logout_confirmation, accountName),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.logout_warning),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(text = stringResource(Res.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
}
