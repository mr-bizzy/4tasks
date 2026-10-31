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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import org.tasks.compose.PageMaxWidth
import org.tasks.themes.WarningColor
import kotlin.math.abs
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
import tasks.kmp.generated.resources.date_and_time_summary
import tasks.kmp.generated.resources.edit_screen_summary
import tasks.kmp.generated.resources.local_lists_summary
import tasks.kmp.generated.resources.look_and_feel_summary
import tasks.kmp.generated.resources.navigation_drawer_summary
import tasks.kmp.generated.resources.notifications_summary
import tasks.kmp.generated.resources.settings_tab_accounts
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
 * title and a one-line explanation, with no leading icon. On Accounts, Tasks and Look a card opens the detail screen
 * it names; Backups, Advanced and About are tabs of their own that show their page directly (owner: "These should
 * be menu items in their own right, not stuck on the more menu").
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
    /** The Backups, Advanced and About pages themselves, each shown directly as its own tab (they live in the app). */
    backupsPage: @Composable () -> Unit,
    advancedPage: @Composable () -> Unit,
    aboutPage: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit = {},
) {
    val tabNames = listOf(
        stringResource(Res.string.settings_tab_accounts),
        stringResource(Res.string.settings_tab_tasks),
        stringResource(Res.string.settings_tab_look),
        stringResource(Res.string.backup_BPr_header),
        stringResource(Res.string.preferences_advanced),
        stringResource(Res.string.about),
    )
    // The tab the user was on is kept, so coming back from a detail screen returns to it
    var savedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedTab.coerceIn(0, tabNames.lastIndex)) { tabNames.size }
    val scope = rememberCoroutineScope()
    val tabScroll = rememberScrollState()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { savedTab = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // The tab row spans the window and scrolls; only the pages below are capped (FAMILY-LOOK rule 8).
        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            scrollState = tabScroll,
            modifier = Modifier.wheelScrollsSideways(tabScroll),
            containerColor = TabRowDefaults.primaryContainerColor,
            contentColor = TabRowDefaults.primaryContentColor,
            edgePadding = SettingsContentPadding,
        ) {
            tabNames.forEachIndexed { index, name ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            // The backup warning (the orange "!") the Backups card used to carry.
                            if (index == TAB_BACKUPS && showBackupWarning) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = WarningColor,
                                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            // FAMILY-LOOK rule 8, written once for every page of the pager: the reading width, centred.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = PageMaxWidth).fillMaxSize()) {
                    when (page) {
                        TAB_ACCOUNTS -> SettingsPage(bottomContent) {
                            AccountsPage(
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
                        }
                        TAB_TASKS -> SettingsPage(bottomContent) {
                            TasksPage(
                                showNotifications = showNotifications,
                                onSettingsClick = onSettingsClick,
                            )
                        }
                        TAB_LOOK -> SettingsPage(bottomContent) {
                            LookPage(
                                showWidgets = showWidgets,
                                onSettingsClick = onSettingsClick,
                            )
                        }
                        // The page's own content, not a card that opens it.
                        TAB_BACKUPS -> backupsPage()
                        TAB_ADVANCED -> Column(modifier = Modifier.fillMaxSize()) {
                            ExtraAdvancedLinks(
                                showMcpServer = showMcpServer,
                                isDebug = isDebug,
                                onSettingsClick = onSettingsClick,
                            )
                            Box(modifier = Modifier.weight(1f)) { advancedPage() }
                        }
                        else -> aboutPage()
                    }
                }
            }
        }
    }
}

private const val TAB_ACCOUNTS = 0
private const val TAB_TASKS = 1
private const val TAB_LOOK = 2
private const val TAB_BACKUPS = 3
private const val TAB_ADVANCED = 4

/**
 * A mouse wheel over the tab row scrolls it sideways (DeX, a tablet with a mouse), as 4Dictate's does. Only a wheel
 * event the row itself did not use is taken; a touch drag is left to the row.
 */
private fun Modifier.wheelScrollsSideways(state: ScrollState): Modifier = composed {
    val scope = rememberCoroutineScope()
    pointerInput(state) {
        val step = 64.dp.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull { !it.isConsumed } ?: continue
                val delta = change.scrollDelta
                val notches = if (abs(delta.y) >= abs(delta.x)) delta.y else delta.x
                if (notches == 0f) continue
                scope.launch { state.scrollBy(notches * step) }
                event.changes.forEach { it.consume() }
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

/** Debug builds' Debug page (and the MCP server, where offered), as cards above the Advanced page. */
@Composable
private fun ExtraAdvancedLinks(
    showMcpServer: Boolean,
    isDebug: Boolean,
    onSettingsClick: (SettingsDestination) -> Unit,
) {
    if (!showMcpServer && !isDebug) return
    Column(
        modifier = Modifier.padding(start = SettingsContentPadding, end = SettingsContentPadding, top = SettingsContentPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
    ) {
        if (showMcpServer) {
            SettingsLinkCard(
                title = stringResource(Res.string.mcp_server),
                summary = "",
                onClick = { onSettingsClick(SettingsDestination.McpServer) },
            )
        }
        if (isDebug) {
            SettingsLinkCard(
                title = stringResource(Res.string.debug),
                summary = "",
                onClick = { onSettingsClick(SettingsDestination.Debug) },
            )
        }
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
