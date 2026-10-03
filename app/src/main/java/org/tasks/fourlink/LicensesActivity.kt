package org.tasks.fourlink

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.themes.TasksSettingsTheme

/** Shows a licence text bundled in assets/licenses (the GPL, or the notice with third-party components). */
@AndroidEntryPoint
class LicensesActivity : ThemedInjectingAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val file = intent.getStringExtra(EXTRA_FILE) ?: THIRD_PARTY
        val heading = intent.getStringExtra(EXTRA_TITLE) ?: "Licences"
        val text = runCatching { assets.open("licenses/$file").bufferedReader().use { it.readText() } }
            .getOrDefault("The licence text could not be read.")
        setContent {
            TasksSettingsTheme(theme = tasksTheme.themeBase.index, primary = themeColor.primaryColor) {
                FamilyScreen(title = heading, onBack = { finish() }) {
                    FamilyCard(title = null) {
                        SelectionContainer {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_TITLE = "title"
        const val GPL = "GPL-3.0.txt"
        const val THIRD_PARTY = "THIRD_PARTY.txt"
    }
}
