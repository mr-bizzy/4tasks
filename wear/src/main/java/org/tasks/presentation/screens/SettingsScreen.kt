package org.tasks.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import com.todoroo.astrid.core.SortHelper
import org.jetbrains.compose.resources.stringResource
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.group_mode
import tasks.kmp.generated.resources.group_none
import tasks.kmp.generated.resources.show_completed
import tasks.kmp.generated.resources.show_unstarted
import tasks.kmp.generated.resources.sort_alphabetical
import tasks.kmp.generated.resources.sort_created
import tasks.kmp.generated.resources.sort_due_date
import tasks.kmp.generated.resources.sort_mode
import tasks.kmp.generated.resources.sort_modified
import tasks.kmp.generated.resources.sort_priority
import tasks.kmp.generated.resources.sort_start_date

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SettingsScreen(
    showHidden: Boolean,
    showCompleted: Boolean,
    sortMode: Int,
    groupMode: Int,
    toggleShowHidden: (Boolean) -> Unit,
    toggleShowCompleted: (Boolean) -> Unit,
    openSortPicker: () -> Unit,
    openGroupPicker: () -> Unit,
) {
    val columnState = rememberResponsiveColumnState()
    ScreenScaffold(
        scrollState = columnState,
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = openGroupPicker,
                    label = { Text(stringResource(Res.string.group_mode)) },
                    secondaryLabel = { Text(groupModeLabel(groupMode)) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = openSortPicker,
                    label = { Text(stringResource(Res.string.sort_mode)) },
                    secondaryLabel = { Text(sortModeLabel(sortMode)) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
            item {
                ToggleChip(
                    checked = showHidden,
                    onCheckedChanged = { toggleShowHidden(it) },
                    label = stringResource(Res.string.show_unstarted),
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }
            item {
                ToggleChip(
                    checked = showCompleted,
                    onCheckedChanged = { toggleShowCompleted(it) },
                    label = stringResource(Res.string.show_completed),
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }
        }
    }
}

@Composable
private fun sortModeLabel(sortMode: Int): String = when (sortMode) {
    SortHelper.SORT_DUE -> stringResource(Res.string.sort_due_date)
    SortHelper.SORT_IMPORTANCE -> stringResource(Res.string.sort_priority)
    SortHelper.SORT_ALPHA -> stringResource(Res.string.sort_alphabetical)
    SortHelper.SORT_CREATED -> stringResource(Res.string.sort_created)
    SortHelper.SORT_MODIFIED -> stringResource(Res.string.sort_modified)
    SortHelper.SORT_START -> stringResource(Res.string.sort_start_date)
    else -> stringResource(Res.string.sort_due_date)
}

@Composable
private fun groupModeLabel(groupMode: Int): String = when (groupMode) {
    SortHelper.GROUP_NONE -> stringResource(Res.string.group_none)
    SortHelper.SORT_DUE -> stringResource(Res.string.sort_due_date)
    SortHelper.SORT_IMPORTANCE -> stringResource(Res.string.sort_priority)
    SortHelper.SORT_ALPHA -> stringResource(Res.string.sort_alphabetical)
    SortHelper.SORT_CREATED -> stringResource(Res.string.sort_created)
    SortHelper.SORT_MODIFIED -> stringResource(Res.string.sort_modified)
    SortHelper.SORT_START -> stringResource(Res.string.sort_start_date)
    else -> stringResource(Res.string.sort_due_date)
}
