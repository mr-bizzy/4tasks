package org.tasks.preferences.fragments

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import org.tasks.PlatformConfiguration
import org.tasks.R
import org.tasks.billing.Inventory
import org.tasks.broadcast.RefreshBroadcaster
import org.tasks.dialogs.ThemePickerDialog
import org.tasks.filters.FilterPreferenceCodec
import org.tasks.injection.ApplicationScope
import org.tasks.preferences.Preferences
import org.tasks.themes.BaseTheme
import org.tasks.viewmodel.LookAndFeelViewModel
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LookAndFeelHiltViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val inventory: Inventory,
    private val locale: Locale,
    platformConfiguration: PlatformConfiguration,
    refreshBroadcaster: RefreshBroadcaster,
    @ApplicationScope persistenceScope: CoroutineScope,
    filterCodec: FilterPreferenceCodec,
) : LookAndFeelViewModel(
    appPreferences = preferences,
    platformConfiguration = platformConfiguration,
    refreshBroadcaster = refreshBroadcaster,
    persistenceScope = persistenceScope,
    filterCodec = filterCodec,
) {
    private var currentThemeBaseIndex by mutableIntStateOf(preferences.themeBase)

    private var currentThemeColor by mutableIntStateOf(preferences.defaultThemeColor)

    override val themeIndex: Int get() = currentThemeBaseIndex

    override val themeColor: Int get() = currentThemeColor

    override val dynamicColorAvailable: Boolean get() = DynamicColors.isDynamicColorAvailable()

    override val dynamicColorEnabled: Boolean
        get() = dynamicColorAvailable && settings.dynamicColor

    override val localeName: String get() = locale.getDisplayName(locale)

    fun refreshState(themeBaseIndex: Int, themeColorPickerColor: Int) {
        currentThemeBaseIndex = themeBaseIndex
        currentThemeColor = themeColorPickerColor
        refreshState()
    }

    override fun setTheme(index: Int) {
        preferences.setInt(R.string.p_theme, index)
        refreshState()
    }

    override fun setThemeColor(color: Int) {
        preferences.setInt(R.string.p_theme_color, color)
        refreshState()
    }

    fun updateDynamicColor(enabled: Boolean) {
        setDynamicColor(enabled)
    }

    fun setBaseTheme(index: Int): Boolean {
        setTheme(index)
        return currentThemeBaseIndex != index
    }

    fun handleThemePickerResult(selectedIndex: Int): ThemePickerResult =
        if (inventory.purchasedThemes() || BaseTheme.isFree(selectedIndex)) {
            ThemePickerResult.ApplyTheme(selectedIndex)
        } else {
            ThemePickerResult.PurchaseRequired
        }

    fun handlePurchaseResult(data: Intent?): Int =
        if (inventory.hasPro) {
            data?.getIntExtra(ThemePickerDialog.EXTRA_SELECTED, BaseTheme.DEFAULT)
                ?: currentThemeBaseIndex
        } else {
            preferences.themeBase
        }

    fun handleColorPickerResult(selectedColor: Int): Boolean {
        if (preferences.defaultThemeColor == selectedColor) {
            return false
        }
        setThemeColor(selectedColor)
        return true
    }

    sealed interface ThemePickerResult {
        data class ApplyTheme(val index: Int) : ThemePickerResult
        data object PurchaseRequired : ThemePickerResult
    }
}
