package org.tasks.preferences.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import com.todoroo.astrid.utility.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.TasksUrls
import org.tasks.BuildConfig
import org.tasks.compose.settings.AboutScreen
import org.tasks.fourlink.LicensesActivity
import org.tasks.extensions.Context.openUri
import org.tasks.logging.FileLogger
import org.tasks.preferences.BasePreferences
import org.tasks.preferences.DiagnosticInfo
import org.tasks.preferences.Preferences
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class HelpAndFeedback : Fragment() {

    @Inject lateinit var theme: Theme
    @Inject lateinit var diagnosticInfo: DiagnosticInfo
    @Inject lateinit var fileLogger: FileLogger
    @Inject lateinit var preferences: Preferences

    private val viewModel: HelpAndFeedbackHiltViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        TasksSettingsTheme(
            theme = theme.themeBase.index,
            primary = theme.themeColor.primaryColor,
        ) {
            AboutScreen(
                versionName = BuildConfig.VERSION_NAME,
                onTasksOrgSource = { context?.openUri("https://github.com/tasks/tasks") },
                onFourTasksSource = { context?.openUri("https://github.com/mr-bizzy/4tasks") },
                onGpl = {
                    startActivity(
                        Intent(requireContext(), LicensesActivity::class.java)
                            .putExtra(LicensesActivity.EXTRA_FILE, LicensesActivity.GPL)
                            .putExtra(LicensesActivity.EXTRA_TITLE, "GNU General Public Licence v3"),
                    )
                },
                onThirdParty = {
                    startActivity(
                        Intent(requireContext(), LicensesActivity::class.java)
                            .putExtra(LicensesActivity.EXTRA_FILE, LicensesActivity.THIRD_PARTY)
                            .putExtra(LicensesActivity.EXTRA_TITLE, "Notices and licences"),
                    )
                },
                onPrivacyPolicy = { context?.openUri("https://mr-biz.uk/4tasks/privacy/") },
                bottomInsets = {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val surfaceColor = theme.themeBase.getSettingsSurfaceColor(requireActivity())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(surfaceColor)
            (toolbar.parent as? View)?.setBackgroundColor(surfaceColor)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val defaultColor = org.tasks.themes.surfaceBackground(requireContext())
        (activity as? BasePreferences)?.toolbar?.let { toolbar ->
            toolbar.setBackgroundColor(defaultColor)
            (toolbar.parent as? View)?.setBackgroundColor(defaultColor)
        }
    }
}
