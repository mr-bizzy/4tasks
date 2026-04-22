package org.tasks.caldav

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.compose.settings.CaldavAccountScreen
import org.tasks.preferences.fragments.CaldavAccountSettingsHiltViewModel
import org.tasks.themes.TasksSettingsTheme
import org.tasks.themes.Theme
import javax.inject.Inject

@AndroidEntryPoint
class CaldavSignInActivity : ComponentActivity() {

    @Inject lateinit var theme: Theme

    private val viewModel: CaldavAccountSettingsHiltViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TasksSettingsTheme(
                theme = theme.themeBase.index,
                primary = theme.themeColor.primaryColor,
            ) {
                val state by viewModel.state.collectAsState()
                var showDiscardDialog by remember { mutableStateOf(false) }

                CaldavAccountScreen(
                    state = state,
                    isNewAccount = true,
                    accountName = "",
                    showDiscardDialog = showDiscardDialog,
                    onUrlChange = viewModel::setUrl,
                    onUsernameChange = viewModel::setUsername,
                    onPasswordChange = viewModel::setPassword,
                    onNameChange = {},
                    onServerTypeChange = viewModel::setServerType,
                    onSave = {
                        viewModel.save {
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                    },
                    onDelete = {},
                    onNavigateBack = { finish() },
                    onDiscardDialogChange = { showDiscardDialog = it },
                    onDismissSnackbar = viewModel::dismissSnackbar,
                )
            }
        }
    }
}
