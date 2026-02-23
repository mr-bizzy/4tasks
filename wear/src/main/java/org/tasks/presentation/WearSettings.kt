package org.tasks.presentation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tasks.tasklist.SectionedDataSource.Companion.HEADER_COMPLETED

data class WearSettingsState(
    val filter: String? = null,
    val showHidden: Boolean = false,
    val showCompleted: Boolean = false,
    val collapsed: Set<Long> = setOf(HEADER_COMPLETED),
)

class WearSettings private constructor(
    private val prefs: SharedPreferences,
) {
    private val _stateFlow = MutableStateFlow(readFromPrefs())
    val stateFlow: StateFlow<WearSettingsState> = _stateFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _stateFlow.value = readFromPrefs()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readFromPrefs(): WearSettingsState {
        val filter = prefs.getString(KEY_FILTER, null)
        val showHidden = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        val showCompleted = prefs.getBoolean(KEY_SHOW_COMPLETED, false)
        val collapsed = prefs.getStringSet(KEY_COLLAPSED, null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: setOf(HEADER_COMPLETED)
        return WearSettingsState(
            filter = filter,
            showHidden = showHidden,
            showCompleted = showCompleted,
            collapsed = collapsed,
        )
    }

    fun setFilter(filter: String) {
        prefs.edit()
            .putString(KEY_FILTER, filter)
            .putStringSet(KEY_COLLAPSED, setOf(HEADER_COMPLETED.toString()))
            .apply()
    }

    fun setShowHidden(showHidden: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, showHidden).apply()
    }

    fun setShowCompleted(showCompleted: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_COMPLETED, showCompleted).apply()
    }

    fun toggleGroup(value: Long) {
        val current = _stateFlow.value.collapsed
        val updated = if (current.contains(value)) current - value else current + value
        prefs.edit()
            .putStringSet(KEY_COLLAPSED, updated.map { it.toString() }.toSet())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "wear_settings"
        private const val KEY_FILTER = "filter"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_SHOW_COMPLETED = "show_completed"
        private const val KEY_COLLAPSED = "collapsed"

        @Volatile
        private var instance: WearSettings? = null

        fun getInstance(context: Context): WearSettings =
            instance ?: synchronized(this) {
                instance ?: WearSettings(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
