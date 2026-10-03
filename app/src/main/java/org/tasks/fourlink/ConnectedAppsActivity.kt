package org.tasks.fourlink

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.themes.TasksSettingsTheme
import uk.mr_biz.fourlink.Caller
import uk.mr_biz.fourlink.android.FourLinkStores
import java.text.DateFormat
import java.util.Date

/**
 * "Apps allowed to use 4Tasks" (spec §6): what each app may do and when it last did it, a
 * Remove that asks first; and the audit log (§9) underneath.
 */
@AndroidEntryPoint
class ConnectedAppsActivity : ThemedInjectingAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val stores = FourLinkStores.of(this)
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        setContent {
            TasksSettingsTheme(theme = tasksTheme.themeBase.index, primary = themeColor.primaryColor) {
                // Bumped after a removal so the lists are read again.
                var version by remember { mutableIntStateOf(0) }
                var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
                @Suppress("UNUSED_EXPRESSION") version
                val pairings = stores.pairings.all()
                val log = stores.audit.all().asReversed()
                FamilyScreen(title = "Apps allowed to use 4Tasks", onBack = { finish() }) {
                    if (pairings.isEmpty()) {
                        FamilyCard(
                            title = null,
                            body = "No other app is allowed. Only our own apps, such as 4Dictate, can use 4Tasks.",
                        )
                    }
                    for (p in pairings) {
                        val label = runCatching {
                            packageManager.getApplicationLabel(packageManager.getApplicationInfo(p.packageName, 0)).toString()
                        }.getOrDefault(p.packageName)
                        val lines = p.granted.sorted().joinToString("\n") { id ->
                            "  • $id — " + (p.lastUsed[id]?.let { "last used ${fmt.format(Date(it))}" } ?: "never used")
                        }
                        FamilyCard(
                            title = "$label (${p.packageName})",
                            body = "Certificate ${Caller.fingerprintOf(p.certDigest)} · paired ${fmt.format(Date(p.grantedAtMs))}\n$lines",
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { removing = p.packageName to label }) { Text("Remove $label") }
                            }
                        }
                    }
                    FamilyCard(
                        title = "Call log (newest first; never the arguments)",
                        body = if (log.isEmpty()) "No calls yet." else log.take(100).joinToString("\n") {
                            "${fmt.format(Date(it.timeMs))}  ${it.callerPackage}  ${it.what}  → ${it.result}"
                        },
                    )
                }
                removing?.let { (packageName, label) ->
                    AlertDialog(
                        onDismissRequest = { removing = null },
                        title = { Text("Remove $label?") },
                        text = { Text("It will no longer be able to use 4Tasks until you allow it again.") },
                        dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
                        confirmButton = {
                            TextButton(onClick = {
                                stores.pairings.remove(packageName)
                                removing = null
                                version++
                            }) { Text("Remove") }
                        },
                    )
                }
            }
        }
    }
}
