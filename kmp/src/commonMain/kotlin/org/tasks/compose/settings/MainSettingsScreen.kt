package org.tasks.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import tasks.kmp.generated.resources.sync_interval_title
import tasks.kmp.generated.resources.sync_interval_summary
import tasks.kmp.generated.resources.sync_interval_every_minutes
import tasks.kmp.generated.resources.sync_interval_current
import tasks.kmp.generated.resources.cancel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import tasks.kmp.generated.resources.add_account_summary
import tasks.kmp.generated.resources.advanced_summary
import tasks.kmp.generated.resources.about_summary
import tasks.kmp.generated.resources.backups_summary
import tasks.kmp.generated.resources.date_and_time_summary
import tasks.kmp.generated.resources.edit_screen_summary
import tasks.kmp.generated.resources.local_lists_summary
import tasks.kmp.generated.resources.look_and_feel_summary
import tasks.kmp.generated.resources.navigation_drawer_summary
import tasks.kmp.generated.resources.notifications_summary
import tasks.kmp.generated.resources.settings_tab_accounts
import tasks.kmp.generated.resources.settings_tab_more
import tasks.kmp.generated.resources.settings_tab_look
import tasks.kmp.generated.resources.settings_tab_tasks
import tasks.kmp.generated.resources.task_defaults_summary
import tasks.kmp.generated.resources.task_list_options_summary
import tasks.kmp.generated.resources.widget_settings_summary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tasks.data.composeIcon
import org.tasks.data.composeTitle
import org.tasks.data.entity.CaldavAccount
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.EPr_edit_screen_options
import tasks.kmp.generated.resources.add_account
import tasks.kmp.generated.resources.backup_BPr_header
import tasks.kmp.generated.resources.caldav
import tasks.kmp.generated.resources.etesync
import tasks.kmp.generated.resources.gtasks_GPr_header
import tasks.kmp.generated.resources.microsoft
import tasks.kmp.generated.resources.date_and_time
import tasks.kmp.generated.resources.debug
import tasks.kmp.generated.resources.about
import tasks.kmp.generated.resources.local_lists
import tasks.kmp.generated.resources.mcp_server
import tasks.kmp.generated.resources.tasks_org
import tasks.kmp.generated.resources.navigation_drawer
import tasks.kmp.generated.resources.notifications
import tasks.kmp.generated.resources.preferences_advanced
import tasks.kmp.generated.resources.preferences_look_and_feel
import tasks.kmp.generated.resources.settings
import tasks.kmp.generated.resources.task_defaults
import tasks.kmp.generated.resources.task_list_options
import tasks.kmp.generated.resources.link_desktop
import tasks.kmp.generated.resources.link_desktop_description
import tasks.kmp.generated.resources.widget_settings
import tasks.kmp.generated.resources.fourlink_apps
import tasks.kmp.generated.resources.fourlink_apps_summary
import tasks.kmp.generated.resources.works_with_tasks
import tasks.kmp.generated.resources.works_with_tasks_description

sealed interface SettingsPane {
    val titleRes: StringResource
}

sealed class SettingsDestination(override val titleRes: StringResource) : SettingsPane {
    data object LookAndFeel : SettingsDestination(Res.string.preferences_look_and_feel)
    data object Notifications : SettingsDestination(Res.string.notifications)
    data object TaskDefaults : SettingsDestination(Res.string.task_defaults)
    data object TaskList : SettingsDestination(Res.string.task_list_options)
    data object TaskEdit : SettingsDestination(Res.string.EPr_edit_screen_options)
    data object DateAndTime : SettingsDestination(Res.string.date_and_time)
    data object NavigationDrawer : SettingsDestination(Res.string.navigation_drawer)
    data object Backups : SettingsDestination(Res.string.backup_BPr_header)
    data object Widgets : SettingsDestination(Res.string.widget_settings)
    data object McpServer : SettingsDestination(Res.string.mcp_server)
    data object Advanced : SettingsDestination(Res.string.preferences_advanced)
    data object WorksWith : SettingsDestination(Res.string.works_with_tasks)
    data object HelpAndFeedback : SettingsDestination(Res.string.about)
    data object Debug : SettingsDestination(Res.string.debug)
}

data class LocalAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.local_lists
}

data class TasksAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.tasks_org
}

data class CaldavAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.caldav
}

data class EtebaseAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.etesync
}

data class GoogleTasksAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.gtasks_GPr_header
}

data class MicrosoftAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.microsoft
}

data class OpenTaskAccountSettingsPane(
    val account: CaldavAccount,
) : SettingsPane {
    override val titleRes: StringResource = Res.string.settings
}

/**
 * Settings, as in 4Dictate and 4Zones: a scrollable row of tabs, each page a column of cards, a card being a
 * title and a one-line explanation, with no leading icon. A card opens the detail screen it names.
 */
@Composable
fun MainSettingsScreen(
    accounts: List<CaldavAccount>,
    proCardState: ProCardState?,
    environmentLabel: String? = null,
    showBackupWarning: Boolean,
    showWidgets: Boolean,
    showNotifications: Boolean = true,
    showMcpServer: Boolean = false,
    isDebug: Boolean = false,
    onAccountClick: (CaldavAccount) -> Unit,
    onAddAccountClick: () -> Unit,
    onSettingsClick: (SettingsDestination) -> Unit,
    onProCardClick: () -> Unit,
    showDesktopLinking: Boolean = false,
    onLinkDesktopClick: () -> Unit = {},
    showAddAccount: Boolean = true,
    showWorksWith: Boolean = true,
    onConnectedAppsClick: (() -> Unit)? = null,
    /** The periodic sync, in minutes; null hides the card (no account that syncs). */
    syncIntervalMinutes: Int? = null,
    syncIntervalChoices: List<Int> = emptyList(),
    onSyncIntervalSelected: (Int) -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val tabNames = listOf(
        stringResource(Res.string.settings_tab_accounts),
        stringResource(Res.string.settings_tab_tasks),
        stringResource(Res.string.settings_tab_look),
        stringResource(Res.string.settings_tab_more),
    )
    // The tab the user was on is kept, so coming back from a detail screen returns to it
    var savedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedTab) { tabNames.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { savedTab = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = SettingsContentPadding,
        ) {
            tabNames.forEachIndexed { index, name ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(name, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            SettingsPage(bottomContent) {
                when (page) {
                    0 -> AccountsPage(
                        accounts = accounts,
                        proCardState = proCardState,
                        showAddAccount = showAddAccount,
                        onAccountClick = onAccountClick,
                        onAddAccountClick = onAddAccountClick,
                        onConnectedAppsClick = onConnectedAppsClick,
                        syncIntervalMinutes = syncIntervalMinutes,
                        syncIntervalChoices = syncIntervalChoices,
                        onSyncIntervalSelected = onSyncIntervalSelected,
                    )
                    1 -> TasksPage(
                        showNotifications = showNotifications,
                        onSettingsClick = onSettingsClick,
                    )
                    2 -> LookPage(
                        showWidgets = showWidgets,
                        onSettingsClick = onSettingsClick,
                    )
                    else -> MorePage(
                        showBackupWarning = showBackupWarning,
                        showMcpServer = showMcpServer,
                        isDebug = isDebug,
                        onSettingsClick = onSettingsClick,
                    )
                }
            }
        }
    }
}

/** One page of the pager: a scrolling column of cards, 8 dp apart. */
@Composable
private fun SettingsPage(
    bottomContent: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SettingsContentPadding, vertical = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        content()
        bottomContent()
    }
}

/** A card that opens a detail screen: its title and a one-line explanation (4Dictate's SettingsLink). */
@Composable
private fun SettingsLinkCard(
    title: String,
    summary: String,
    showWarning: Boolean = false,
    onClick: () -> Unit,
) {
    SettingsItemCard {
        PreferenceRow(
            title = title,
            summary = summary,
            showWarning = showWarning,
            onClick = onClick,
        )
    }
}

@Composable
private fun AccountsPage(
    accounts: List<CaldavAccount>,
    proCardState: ProCardState?,
    showAddAccount: Boolean,
    onAccountClick: (CaldavAccount) -> Unit,
    onAddAccountClick: () -> Unit,
    onConnectedAppsClick: (() -> Unit)?,
    syncIntervalMinutes: Int?,
    syncIntervalChoices: List<Int>,
    onSyncIntervalSelected: (Int) -> Unit,
) {
    accounts.forEach { account ->
        SettingsItemCard { AccountRow(account = account, onClick = { onAccountClick(account) }) }
    }
    if (showAddAccount && proCardState !is ProCardState.TasksOrgAccount) {
        SettingsLinkCard(
            title = stringResource(Res.string.add_account),
            summary = stringResource(Res.string.add_account_summary),
            onClick = onAddAccountClick,
        )
    }
    if (syncIntervalMinutes != null && syncIntervalChoices.isNotEmpty()) {
        SyncIntervalCard(syncIntervalMinutes, syncIntervalChoices, onSyncIntervalSelected)
    }
    if (onConnectedAppsClick != null) {
        SettingsLinkCard(
            title = stringResource(Res.string.fourlink_apps),
            summary = stringResource(Res.string.fourlink_apps_summary),
            onClick = onConnectedAppsClick,
        )
    }
}

@Composable
private fun TasksPage(
    showNotifications: Boolean,
    onSettingsClick: (SettingsDestination) -> Unit,
) {
    SettingsLinkCard(
        title = stringResource(Res.string.task_defaults),
        summary = stringResource(Res.string.task_defaults_summary),
        onClick = { onSettingsClick(SettingsDestination.TaskDefaults) },
    )
    SettingsLinkCard(
        title = stringResource(Res.string.task_list_options),
        summary = stringResource(Res.string.task_list_options_summary),
        onClick = { onSettingsClick(SettingsDestination.TaskList) },
    )
    SettingsLinkCard(
        title = stringResource(Res.string.EPr_edit_screen_options),
        summary = stringResource(Res.string.edit_screen_summary),
        onClick = { onSettingsClick(SettingsDestination.TaskEdit) },
    )
    SettingsLinkCard(
        title = stringResource(Res.string.date_and_time),
        summary = stringResource(Res.string.date_and_time_summary),
        onClick = { onSettingsClick(SettingsDestination.DateAndTime) },
    )
    if (showNotifications) {
        SettingsLinkCard(
            title = stringResource(Res.string.notifications),
            summary = stringResource(Res.string.notifications_summary),
            onClick = { onSettingsClick(SettingsDestination.Notifications) },
        )
    }
}

@Composable
private fun LookPage(
    showWidgets: Boolean,
    onSettingsClick: (SettingsDestination) -> Unit,
) {
    SettingsLinkCard(
        title = stringResource(Res.string.preferences_look_and_feel),
        summary = stringResource(Res.string.look_and_feel_summary),
        onClick = { onSettingsClick(SettingsDestination.LookAndFeel) },
    )
    SettingsLinkCard(
        title = stringResource(Res.string.navigation_drawer),
        summary = stringResource(Res.string.navigation_drawer_summary),
        onClick = { onSettingsClick(SettingsDestination.NavigationDrawer) },
    )
    if (showWidgets) {
        SettingsLinkCard(
            title = stringResource(Res.string.widget_settings),
            summary = stringResource(Res.string.widget_settings_summary),
            onClick = { onSettingsClick(SettingsDestination.Widgets) },
        )
    }
}

/** Backups, Advanced and About on one page, so the tab row fits four tabs as 4Dictate's does. */
@Composable
private fun MorePage(
    showBackupWarning: Boolean,
    showMcpServer: Boolean,
    isDebug: Boolean,
    onSettingsClick: (SettingsDestination) -> Unit,
) {
    SettingsLinkCard(
        title = stringResource(Res.string.backup_BPr_header),
        summary = stringResource(Res.string.backups_summary),
        showWarning = showBackupWarning,
        onClick = { onSettingsClick(SettingsDestination.Backups) },
    )
    if (showMcpServer) {
        SettingsLinkCard(
            title = stringResource(Res.string.mcp_server),
            summary = "",
            onClick = { onSettingsClick(SettingsDestination.McpServer) },
        )
    }
    SettingsLinkCard(
        title = stringResource(Res.string.preferences_advanced),
        summary = stringResource(Res.string.advanced_summary),
        onClick = { onSettingsClick(SettingsDestination.Advanced) },
    )
    SettingsLinkCard(
        title = stringResource(Res.string.about),
        summary = stringResource(Res.string.about_summary),
        onClick = { onSettingsClick(SettingsDestination.HelpAndFeedback) },
    )
    if (isDebug) {
        SettingsLinkCard(
            title = stringResource(Res.string.debug),
            summary = "",
            onClick = { onSettingsClick(SettingsDestination.Debug) },
        )
    }
}

@Composable
private fun AccountRow(
    account: CaldavAccount,
    onClick: () -> Unit,
) {
    val title = account.composeTitle
    PreferenceRow(
        title = if (title != null) stringResource(title) else account.name.orEmpty(),
        summary = if (account.accountType == CaldavAccount.TYPE_LOCAL) {
            stringResource(Res.string.local_lists_summary)
        } else {
            account.name
        },
        showError = account.hasError,
        onClick = onClick,
    )
}

/** "Sync interval": a card that opens a choice of how often the background sync runs. */
@Composable
private fun SyncIntervalCard(
    minutes: Int,
    choices: List<Int>,
    onSelected: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingsLinkCard(
        title = stringResource(Res.string.sync_interval_title),
        summary = stringResource(Res.string.sync_interval_current, minutes),
        onClick = { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(Res.string.sync_interval_title)) },
            text = {
                Column {
                    Text(
                        stringResource(Res.string.sync_interval_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    choices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(choice); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == minutes, onClick = { onSelected(choice); open = false })
                            Text(stringResource(Res.string.sync_interval_every_minutes, choice))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
