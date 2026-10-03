package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import org.tasks.R

@Composable
fun BackupsScreen(
    backupDirSummary: String,
    showBackupDirWarning: Boolean,
    lastBackupSummary: String,
    showLocalBackupWarning: Boolean,
    backupsEnabled: Boolean,
    ignoreWarnings: Boolean,
    onBackupDir: () -> Unit,
    onBackupNow: () -> Unit,
    onImportBackup: () -> Unit,
    onBackupsEnabled: (Boolean) -> Unit,
    onIgnoreWarnings: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))

        // Backup directory, backup now, import
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.backup_directory),
                    summary = backupDirSummary,
                    summaryMaxLines = Int.MAX_VALUE,
                    showError = showBackupDirWarning,
                    onClick = onBackupDir,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.backup_BAc_export),
                    summary = lastBackupSummary,
                    showError = showLocalBackupWarning,
                    onClick = onBackupNow,
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.backup_BAc_import),
                    onClick = onImportBackup,
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                SwitchPreferenceRow(
                    title = stringResource(R.string.automatic_backups),
                    checked = backupsEnabled,
                    onCheckedChange = onBackupsEnabled,
                )
            }
        }

        // Advanced section
        SectionHeader(
            R.string.preferences_advanced,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        SettingsItemCard(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            SwitchPreferenceRow(
                title = stringResource(R.string.backups_ignore_warnings),
                summary = stringResource(R.string.backups_ignore_warnings_summary),
                checked = ignoreWarnings,
                onCheckedChange = onIgnoreWarnings,
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
