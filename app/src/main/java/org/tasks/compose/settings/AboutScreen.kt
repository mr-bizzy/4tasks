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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PermIdentity
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.tasks.R

/**
 * About 4Tasks, to the family standard (as 4Dictate's and 4Home 0.1.22's, 0.1.15): name, version and
 * version code; what it is and who makes it; then the website, the manual, the privacy policy and
 * support. After those, what 4Tasks already had: where Tasks.org's code came from, the source and the
 * licences, and the apps allowed to use 4Tasks. Nothing here asks for money or links to Tasks.org's services.
 */
@Composable
fun AboutScreen(
    versionName: String,
    versionCode: Int,
    onWebsite: () -> Unit,
    onManual: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onSupport: () -> Unit,
    onTasksOrgSource: () -> Unit,
    onFourTasksSource: () -> Unit,
    onGpl: () -> Unit,
    onThirdParty: () -> Unit,
    onConnectedApps: () -> Unit,
    bottomInsets: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Column(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            SettingsItemCard(position = CardPosition.Only) {
                PreferenceRow(
                    title = stringResource(R.string.about_title, versionName, versionCode),
                    icon = Icons.Outlined.Info,
                    summary = stringResource(R.string.about_maker),
                    summaryMaxLines = 4,
                    onClick = {},
                )
                PreferenceRow(
                    title = stringResource(R.string.about_website),
                    icon = Icons.Outlined.Public,
                    summary = stringResource(R.string.about_website_summary),
                    onClick = onWebsite,
                )
                PreferenceRow(
                    title = stringResource(R.string.about_manual),
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    summary = stringResource(R.string.about_manual_summary),
                    onClick = onManual,
                )
                PreferenceRow(
                    title = stringResource(R.string.about_privacy_policy),
                    icon = Icons.Outlined.PermIdentity,
                    summary = stringResource(R.string.about_privacy_policy_summary),
                    onClick = onPrivacyPolicy,
                )
                PreferenceRow(
                    title = stringResource(R.string.about_support),
                    icon = Icons.Outlined.Email,
                    summary = stringResource(R.string.about_support_summary),
                    onClick = onSupport,
                )
            }
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Column(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            SettingsItemCard(position = CardPosition.Only) {
                PreferenceRow(
                    title = stringResource(R.string.about_based_on),
                    summary = stringResource(R.string.about_based_on_summary),
                    onClick = onTasksOrgSource,
                )
            }
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsSection(
            title = stringResource(R.string.about_open_source),
            explanation = stringResource(R.string.about_source_code_summary),
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        ) {
            PreferenceRow(
                title = stringResource(R.string.about_source_code),
                onClick = onFourTasksSource,
            )
            PreferenceRow(
                title = stringResource(R.string.about_gpl),
                icon = Icons.Outlined.Gavel,
                onClick = onGpl,
            )
            PreferenceRow(
                title = stringResource(R.string.about_third_party),
                icon = Icons.Outlined.Gavel,
                onClick = onThirdParty,
            )
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Column(modifier = Modifier.padding(horizontal = SettingsContentPadding)) {
            SettingsItemCard(position = CardPosition.Only) {
                PreferenceRow(
                    title = stringResource(R.string.about_connected_apps),
                    icon = Icons.Outlined.Apps,
                    summary = stringResource(R.string.about_connected_apps_summary),
                    onClick = onConnectedApps,
                )
            }
        }
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        bottomInsets()
    }
}
