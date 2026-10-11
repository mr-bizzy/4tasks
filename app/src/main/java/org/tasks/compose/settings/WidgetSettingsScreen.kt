package org.tasks.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.tasks.R
import kotlin.math.roundToInt

/** Everything the widget settings page shows, read from the widget's own preferences. */
data class WidgetSettingsState(
    val filterName: String = "",
    val themeIndex: Int = 3,
    val color: Int = 0,
    val headerOpacity: Int = 100,
    val rowOpacity: Int = 100,
    val footerOpacity: Int = 100,
    val showHeader: Boolean = true,
    val showTitle: Boolean = true,
    val showMenu: Boolean = true,
    val showSettings: Boolean = true,
    val headerSpacing: Int = 0,
    val fontSize: Int = 16,
    val rowSpacing: Int = 0,
    val dueDatePosition: Int = 0,
    val showDescription: Boolean = true,
    val showFullDescription: Boolean = false,
    val showCheckboxes: Boolean = true,
    val showFullTitle: Boolean = false,
    val showDividers: Boolean = true,
    val showSubtasks: Boolean = true,
    val showStartDates: Boolean = true,
    val showPlaces: Boolean = true,
    val showLists: Boolean = true,
    val showTags: Boolean = true,
) {
    /** One slider sets all three; it shows the highest, as the old preference screen did. */
    val opacity: Int get() = maxOf(headerOpacity, rowOpacity, footerOpacity)
    val opacitiesDiffer: Boolean get() = headerOpacity != rowOpacity || headerOpacity != footerOpacity
}

/** The widget switches and lists, each stored under its preference key plus the widget's id. */
enum class WidgetSwitch {
    SHOW_HEADER, SHOW_TITLE, SHOW_MENU, SHOW_SETTINGS,
    SHOW_DESCRIPTION, SHOW_FULL_DESCRIPTION, SHOW_CHECKBOXES, SHOW_FULL_TITLE, SHOW_DIVIDERS,
    SHOW_SUBTASKS, SHOW_START_DATES, SHOW_PLACES, SHOW_LISTS, SHOW_TAGS,
}

enum class WidgetChoice { HEADER_SPACING, ROW_SPACING, DUE_DATE_POSITION }

enum class WidgetSlider { OPACITY, HEADER_OPACITY, ROW_OPACITY, FOOTER_OPACITY, FONT_SIZE }

/**
 * Widget settings as the family's settings cards (FAMILY-LOOK rules 3 and 5): self-titling cards for the list, theme
 * and sort, then one card per section with its title and explanation inside. No leading row icons; the colour
 * swatch stays because it shows the colour.
 */
@Composable
fun WidgetSettingsScreen(
    state: WidgetSettingsState,
    onFilter: () -> Unit,
    onTheme: () -> Unit,
    onSort: () -> Unit,
    onColor: () -> Unit,
    onSwitch: (WidgetSwitch, Boolean) -> Unit,
    onChoice: (WidgetChoice, Int) -> Unit,
    onSlider: (WidgetSlider, Int) -> Unit,
) {
    val themeNames = stringArrayResource(R.array.widget_themes)
    val spacingNames = stringArrayResource(R.array.widget_spacing_titles)
    val dueDateNames = stringArrayResource(R.array.widget_due_date_position)
    var choice by rememberSaveable { mutableStateOf<WidgetChoice?>(null) }
    var showOpacities by rememberSaveable { mutableStateOf(state.opacitiesDiffer) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SettingsContentPadding),
    ) {
        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsItemCard {
            PreferenceRow(
                title = stringResource(R.string.filter),
                summary = state.filterName,
                onClick = onFilter,
            )
        }
        Spacer(modifier = Modifier.height(SettingsCardGap))
        SettingsItemCard {
            PreferenceRow(
                title = stringResource(R.string.theme),
                summary = themeNames.getOrNull(state.themeIndex),
                onClick = onTheme,
            )
        }
        Spacer(modifier = Modifier.height(SettingsCardGap))
        SettingsItemCard {
            PreferenceRow(
                title = stringResource(R.string.sort_options),
                onClick = onSort,
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsSection(
            title = stringResource(R.string.opacity),
            explanation = stringResource(R.string.widget_opacity_explanation),
        ) {
            SliderRow(
                title = null,
                value = state.opacity,
                range = 0..100,
                step = 5,
                onValueChange = { onSlider(WidgetSlider.OPACITY, it) },
            )
            if (showOpacities) {
                SliderRow(
                    title = stringResource(R.string.opacity_header),
                    value = state.headerOpacity,
                    range = 0..100,
                    step = 5,
                    enabled = state.showHeader,
                    onValueChange = { onSlider(WidgetSlider.HEADER_OPACITY, it) },
                )
                SliderRow(
                    title = stringResource(R.string.opacity_row),
                    value = state.rowOpacity,
                    range = 0..100,
                    step = 5,
                    onValueChange = { onSlider(WidgetSlider.ROW_OPACITY, it) },
                )
                SliderRow(
                    title = stringResource(R.string.opacity_footer),
                    value = state.footerOpacity,
                    range = 0..100,
                    step = 5,
                    onValueChange = { onSlider(WidgetSlider.FOOTER_OPACITY, it) },
                )
            } else {
                PreferenceRow(
                    title = stringResource(R.string.preferences_advanced),
                    showChevron = true,
                    onClick = { showOpacities = true },
                )
            }
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsSection(
            title = stringResource(R.string.widget_header_settings),
            explanation = stringResource(R.string.widget_header_explanation),
        ) {
            SwitchPreferenceRow(
                title = stringResource(R.string.widget_show_header),
                checked = state.showHeader,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_HEADER, it) },
            )
            if (state.themeIndex != 4) {
                PreferenceRow(
                    title = stringResource(R.string.color),
                    enabled = state.showHeader,
                    leading = { ColorSwatch(Color(state.color)) },
                    onClick = onColor,
                )
            }
            PreferenceRow(
                title = stringResource(R.string.header_spacing),
                summary = spacingNames.getOrNull(state.headerSpacing),
                onClick = { choice = WidgetChoice.HEADER_SPACING },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.widget_show_title),
                checked = state.showTitle,
                enabled = state.showHeader,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_TITLE, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.widget_show_menu),
                checked = state.showMenu,
                enabled = state.showHeader,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_MENU, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.widget_show_settings),
                checked = state.showSettings,
                enabled = state.showHeader,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_SETTINGS, it) },
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsSection(
            title = stringResource(R.string.widget_row_settings),
            explanation = stringResource(R.string.widget_rows_explanation),
        ) {
            SliderRow(
                title = stringResource(R.string.font_size),
                value = state.fontSize,
                range = 10..22,
                step = 1,
                onValueChange = { onSlider(WidgetSlider.FONT_SIZE, it) },
            )
            PreferenceRow(
                title = stringResource(R.string.row_spacing),
                summary = spacingNames.getOrNull(state.rowSpacing),
                onClick = { choice = WidgetChoice.ROW_SPACING },
            )
            PreferenceRow(
                title = stringResource(R.string.due_date),
                summary = dueDateNames.getOrNull(state.dueDatePosition),
                onClick = { choice = WidgetChoice.DUE_DATE_POSITION },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.show_description),
                checked = state.showDescription,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_DESCRIPTION, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.show_full_description),
                checked = state.showFullDescription,
                enabled = state.showDescription,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_FULL_DESCRIPTION, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.widget_show_checkboxes),
                checked = state.showCheckboxes,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_CHECKBOXES, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.EPr_fullTask_title),
                checked = state.showFullTitle,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_FULL_TITLE, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.widget_show_dividers),
                checked = state.showDividers,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_DIVIDERS, it) },
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        SettingsSection(
            title = stringResource(R.string.chips),
            explanation = stringResource(R.string.chips_explanation),
        ) {
            SwitchPreferenceRow(
                title = stringResource(R.string.subtasks),
                checked = state.showSubtasks,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_SUBTASKS, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.start_date),
                checked = state.showStartDates,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_START_DATES, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.places),
                checked = state.showPlaces,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_PLACES, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.lists),
                checked = state.showLists,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_LISTS, it) },
            )
            SwitchPreferenceRow(
                title = stringResource(R.string.tags),
                checked = state.showTags,
                onCheckedChange = { onSwitch(WidgetSwitch.SHOW_TAGS, it) },
            )
        }

        Spacer(modifier = Modifier.height(SettingsContentPadding))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    choice?.let { which ->
        val (title, options, selected) = when (which) {
            WidgetChoice.HEADER_SPACING ->
                Triple(stringResource(R.string.header_spacing), spacingNames, state.headerSpacing)
            WidgetChoice.ROW_SPACING ->
                Triple(stringResource(R.string.row_spacing), spacingNames, state.rowSpacing)
            WidgetChoice.DUE_DATE_POSITION ->
                Triple(stringResource(R.string.due_date), dueDateNames, state.dueDatePosition)
        }
        ChoiceDialog(
            title = title,
            options = options.toList(),
            selected = selected,
            onSelect = {
                onChoice(which, it)
                choice = null
            },
            onDismiss = { choice = null },
        )
    }
}

@Composable
private fun SliderRow(
    title: String?,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    // Follow the finger while dragging; store once, when it is let go.
    var dragging by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(value) }
    val shown = if (dragging) current else value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = SettingsCardPadding, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shown.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = {
                dragging = true
                current = snapToStep(it, range, step)
            },
            onValueChangeFinished = {
                dragging = false
                onValueChange(current)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = enabled,
        )
    }
}

/** The slider position rounded to the nearest whole [step] from the start of [range]. */
internal fun snapToStep(value: Float, range: IntRange, step: Int): Int =
    ((value - range.first) / step).roundToInt() * step + range.first

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .padding(start = SettingsCardPadding)
            .size(SettingsIconSize)
            .clip(CircleShape)
            .background(color)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = CircleShape),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
    )
}
