package org.tasks.widget

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.Strings.isNullOrEmpty
import org.tasks.analytics.Firebase
import org.tasks.compose.FilterSelectionActivity.Companion.launch
import org.tasks.compose.FilterSelectionActivity.Companion.registerForFilterPickerResult
import org.tasks.data.UUIDHelper
import org.tasks.databinding.ActivityWidgetShortcutLayoutBinding
import org.tasks.filters.Filter
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.intents.TaskIntents
import org.tasks.preferences.DefaultFilterProvider
import javax.inject.Inject

@AndroidEntryPoint
class ShortcutConfigActivity : ThemedInjectingAppCompatActivity() {
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var firebase: Firebase

    private lateinit var toolbar: Toolbar
    private lateinit var shortcutList: TextInputEditText
    private lateinit var shortcutName: TextInputEditText

    private var selectedFilter: Filter? = null
    private val listPickerResult = registerForFilterPickerResult {
        if (selectedFilter != null && selectedFilter!!.title == getShortcutName()) {
            shortcutName.text = null
        }
        selectedFilter = it
        updateFilter()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        val binding = ActivityWidgetShortcutLayoutBinding.inflate(layoutInflater)
        binding.let {
            toolbar = it.toolbar.toolbar
            shortcutList = it.body.shortcutList.apply {
                setOnClickListener { showListPicker() }
                setOnFocusChangeListener { _, hasFocus -> onListFocusChange(hasFocus) }
            }
            shortcutName = it.body.shortcutName
            it.body.color.root.visibility = View.GONE
        }
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }
            binding.body.root.updatePadding(bottom = systemBars.bottom)
            insets
        }

        toolbar.setTitle(R.string.FSA_label)
        toolbar.navigationIcon = getDrawable(R.drawable.ic_outline_save_24px)
        toolbar.setNavigationOnClickListener { save() }
        if (savedInstanceState == null) {
            selectedFilter = defaultFilterProvider.startupFilter
        } else {
            selectedFilter = savedInstanceState.getParcelable(EXTRA_FILTER)
        }
        updateFilter()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(EXTRA_FILTER, selectedFilter)
    }

    private fun onListFocusChange(focused: Boolean) {
        if (focused) {
            shortcutList.clearFocus()
            showListPicker()
        }
    }

    private fun showListPicker() {
        listPickerResult.launch(context = this, selectedFilter = selectedFilter)
    }

    private fun updateFilter() {
        if (isNullOrEmpty(getShortcutName()) && selectedFilter != null) {
            shortcutName.setText(selectedFilter!!.title)
        }
        if (selectedFilter != null) {
            shortcutList.setText(selectedFilter!!.title)
        }
    }

    private fun getShortcutName(): String = shortcutName.text.toString().trim { it <= ' ' }

    private fun save() {
        val filterId = defaultFilterProvider.getFilterPreferenceValue(selectedFilter!!)
        ShortcutManagerCompat.requestPinShortcut(
            this,
            ShortcutInfoCompat.Builder(this, UUIDHelper.newUUID())
                .setShortLabel(
                    getShortcutName().takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
                )
                .setIntent(TaskIntents.getTaskListByIdIntent(this, filterId))
                .setIcon(IconCompat.createWithResource(this, org.tasks.kmp.R.mipmap.ic_launcher))
                .build(),
            null,
        )
        firebase.logEvent(R.string.event_create_shortcut, R.string.param_type to "shortcut_config")
        finish()
    }

    companion object {
        private const val EXTRA_FILTER = "extra_filter"
    }
}