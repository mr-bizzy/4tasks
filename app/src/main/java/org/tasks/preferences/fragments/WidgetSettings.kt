package org.tasks.preferences.fragments

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.LocalBroadcastManager
import org.tasks.R
import org.tasks.compose.FilterSelectionActivity.Companion.launch
import org.tasks.compose.FilterSelectionActivity.Companion.registerForFilterPickerResult
import org.tasks.compose.settings.WidgetChoice
import org.tasks.compose.settings.WidgetSettingsScreen
import org.tasks.compose.settings.WidgetSettingsState
import org.tasks.compose.settings.WidgetSlider
import org.tasks.compose.settings.WidgetSwitch
import org.tasks.dialogs.ColorPalettePicker.Companion.newColorPalette
import org.tasks.dialogs.ColorPickerAdapter.Palette
import org.tasks.dialogs.ColorWheelPicker
import org.tasks.dialogs.SortSettingsActivity
import org.tasks.dialogs.ThemePickerDialog
import org.tasks.dialogs.ThemePickerDialog.Companion.newThemePickerDialog
import org.tasks.filters.AstridOrderingFilter
import org.tasks.filters.Filter
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import org.tasks.widget.WidgetPreferences
import javax.inject.Inject

/**
 * A widget's settings, as Compose settings cards (it was an androidx Preference screen with leading icons). The
 * values are stored exactly as before: each preference key plus the widget id, sliders as Int, switches as Boolean,
 * the spacing and due date lists as the String index, so existing widgets keep their settings.
 */
@AndroidEntryPoint
class WidgetSettings : Fragment() {

    companion object {
        const val EXTRA_WIDGET_ID = "extra_widget_id"
        private const val FRAG_TAG_COLOR_PICKER = "frag_tag_color_picker"
        private const val REQUEST_KEY_COLOR = "widget_color_picker_result"

        fun newWidgetSettings(appWidgetId: Int): WidgetSettings {
            val widget = WidgetSettings()
            val args = Bundle()
            args.putInt(EXTRA_WIDGET_ID, appWidgetId)
            widget.arguments = args
            return widget
        }
    }

    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var localBroadcastManager: LocalBroadcastManager
    @Inject lateinit var theme: Theme

    private lateinit var widgetPreferences: WidgetPreferences
    private var appWidgetId = 0
    private var state by mutableStateOf(WidgetSettingsState())
    private val listPickerLauncher = registerForFilterPickerResult {
        widgetPreferences.setFilter(defaultFilterProvider.getFilterPreferenceValue(it))
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = requireArguments().getInt(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        widgetPreferences = WidgetPreferences(requireContext(), preferences, appWidgetId)

        parentFragmentManager.setFragmentResultListener(
            ThemePickerDialog.REQUEST_KEY, this
        ) { _, bundle ->
            val selected = bundle.getInt(ThemePickerDialog.EXTRA_SELECTED, widgetPreferences.themeIndex)
            widgetPreferences.setTheme(selected)
            refresh()
        }
        parentFragmentManager.setFragmentResultListener(
            REQUEST_KEY_COLOR, this
        ) { _, bundle ->
            val selected = bundle.getInt(ColorWheelPicker.EXTRA_SELECTED, 0)
            widgetPreferences.setColor(selected)
            refresh()
        }
        refresh()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            WidgetSettingsScreen(
                state = state,
                onFilter = {
                    lifecycleScope.launch {
                        listPickerLauncher.launch(requireContext(), selectedFilter = getFilter())
                    }
                },
                onTheme = {
                    newThemePickerDialog(widgetPreferences.themeIndex, widget = true)
                        .show(parentFragmentManager, FRAG_TAG_COLOR_PICKER)
                },
                onSort = {
                    lifecycleScope.launch {
                        val filter = getFilter()
                        requireActivity().startActivity(
                            SortSettingsActivity.getIntent(
                                requireActivity(),
                                filter.supportsManualSort(),
                                filter is AstridOrderingFilter,
                                appWidgetId,
                                completedAndHiddenEnabled = filter.supportsHiddenTasks(),
                            )
                        )
                    }
                },
                onColor = {
                    newColorPalette(REQUEST_KEY_COLOR, widgetPreferences.color, Palette.WIDGET)
                        .show(parentFragmentManager, FRAG_TAG_COLOR_PICKER)
                },
                onSwitch = { which, value ->
                    preferences.setBoolean(key(which.key), value)
                    refresh()
                },
                onChoice = { which, index ->
                    preferences.setString(key(which.key), index.toString())
                    refresh()
                },
                onSlider = { which, value ->
                    when (which) {
                        // The one opacity slider sets the header, rows and footer together.
                        WidgetSlider.OPACITY -> listOf(
                            R.string.p_widget_header_opacity,
                            R.string.p_widget_opacity,
                            R.string.p_widget_footer_opacity,
                        ).forEach { preferences.setInt(key(it), value) }
                        else -> preferences.setInt(key(which.key), value)
                    }
                    refresh()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // The bar takes the settings page's surface, as on the other Compose settings pages.
        val surfaceColor = theme.themeBase.getSettingsSurfaceColor(requireActivity())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(surfaceColor)
            (toolbar.parent as? View)?.setBackgroundColor(surfaceColor)
        }
    }

    override fun onPause() {
        super.onPause()

        localBroadcastManager.reconfigureWidget(appWidgetId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val defaultColor = org.tasks.themes.surfaceBackground(requireContext())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(defaultColor)
            (toolbar.parent as? View)?.setBackgroundColor(defaultColor)
        }
    }

    private fun key(resId: Int) = widgetPreferences.getKey(resId)

    private fun bool(resId: Int, default: Boolean) = preferences.getBoolean(key(resId), default)

    private fun index(resId: Int, default: String = "0") =
        (preferences.getStringValue(key(resId)) ?: default).toIntOrNull() ?: 0

    /** Reads every value with the defaults the old preference XML had. */
    private fun refresh() {
        val row = preferences.getInt(key(R.string.p_widget_opacity), 100)
        state = WidgetSettingsState(
            filterName = state.filterName,
            themeIndex = widgetPreferences.themeIndex,
            color = widgetPreferences.color,
            headerOpacity = preferences.getInt(key(R.string.p_widget_header_opacity), row),
            rowOpacity = row,
            footerOpacity = preferences.getInt(key(R.string.p_widget_footer_opacity), row),
            showHeader = bool(R.string.p_widget_show_header, true),
            showTitle = bool(R.string.p_widget_show_title, true),
            showMenu = bool(R.string.p_widget_show_menu, true),
            showSettings = bool(R.string.p_widget_show_settings, true),
            headerSpacing = index(R.string.p_widget_header_spacing),
            fontSize = preferences.getInt(key(R.string.p_widget_font_size), 16),
            rowSpacing = index(R.string.p_widget_spacing),
            dueDatePosition = widgetPreferences.dueDatePosition,
            showDescription = bool(R.string.p_widget_show_description, true),
            showFullDescription = bool(R.string.p_widget_show_full_description, false),
            showCheckboxes = bool(R.string.p_widget_show_checkboxes, true),
            showFullTitle = bool(R.string.p_widget_show_full_task_title, false),
            showDividers = bool(R.string.p_widget_show_dividers, true),
            showSubtasks = bool(R.string.p_widget_show_subtasks, true),
            showStartDates = bool(R.string.p_widget_show_start_dates, true),
            showPlaces = bool(R.string.p_widget_show_places, true),
            showLists = bool(R.string.p_widget_show_lists, true),
            showTags = bool(R.string.p_widget_show_tags, true),
        )
        lifecycleScope.launch {
            state = state.copy(filterName = getFilter().title)
        }
    }

    private suspend fun getFilter(): Filter =
        defaultFilterProvider.getFilterFromPreference(widgetPreferences.filterId)
}

private val WidgetSwitch.key: Int
    get() = when (this) {
        WidgetSwitch.SHOW_HEADER -> R.string.p_widget_show_header
        WidgetSwitch.SHOW_TITLE -> R.string.p_widget_show_title
        WidgetSwitch.SHOW_MENU -> R.string.p_widget_show_menu
        WidgetSwitch.SHOW_SETTINGS -> R.string.p_widget_show_settings
        WidgetSwitch.SHOW_DESCRIPTION -> R.string.p_widget_show_description
        WidgetSwitch.SHOW_FULL_DESCRIPTION -> R.string.p_widget_show_full_description
        WidgetSwitch.SHOW_CHECKBOXES -> R.string.p_widget_show_checkboxes
        WidgetSwitch.SHOW_FULL_TITLE -> R.string.p_widget_show_full_task_title
        WidgetSwitch.SHOW_DIVIDERS -> R.string.p_widget_show_dividers
        WidgetSwitch.SHOW_SUBTASKS -> R.string.p_widget_show_subtasks
        WidgetSwitch.SHOW_START_DATES -> R.string.p_widget_show_start_dates
        WidgetSwitch.SHOW_PLACES -> R.string.p_widget_show_places
        WidgetSwitch.SHOW_LISTS -> R.string.p_widget_show_lists
        WidgetSwitch.SHOW_TAGS -> R.string.p_widget_show_tags
    }

private val WidgetChoice.key: Int
    get() = when (this) {
        WidgetChoice.HEADER_SPACING -> R.string.p_widget_header_spacing
        WidgetChoice.ROW_SPACING -> R.string.p_widget_spacing
        WidgetChoice.DUE_DATE_POSITION -> R.string.p_widget_due_date_position
    }

private val WidgetSlider.key: Int
    get() = when (this) {
        WidgetSlider.OPACITY -> R.string.p_widget_opacity
        WidgetSlider.HEADER_OPACITY -> R.string.p_widget_header_opacity
        WidgetSlider.ROW_OPACITY -> R.string.p_widget_opacity
        WidgetSlider.FOOTER_OPACITY -> R.string.p_widget_footer_opacity
        WidgetSlider.FONT_SIZE -> R.string.p_widget_font_size
    }
