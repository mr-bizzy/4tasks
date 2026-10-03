package org.tasks.fourlink

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.compose.settings.SettingsCardGap
import org.tasks.injection.ThemedInjectingAppCompatActivity
import org.tasks.themes.TasksSettingsTheme
import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.Effect
import uk.mr_biz.fourlink.FourLink
import uk.mr_biz.fourlink.android.FourLinkStores
import uk.mr_biz.fourlink.android.PairingRequest

/**
 * The pairing screen (spec §6). Opens ONLY for a PAIR intent from an
 * identifiable app: who it is (name, icon, certificate fingerprint), what it
 * asked for grouped Read / Change / Delete with the data each function
 * receives, Delete unticked. Approve all, some, or refuse.
 */
@AndroidEntryPoint
class PairingActivity : ThemedInjectingAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val catalogue = ownCatalogue() ?: return finishSaying("This app's catalogue could not be read.")
        val request = PairingRequest.from(this, intent, catalogue)
            ?: return finishSaying("Not a pairing request from an app that can be identified.")
        if (request.requested.isEmpty()) return finishSaying("${request.label} asked for nothing this app offers.")

        val store = FourLinkStores.of(this).pairings
        val defaults = request.defaultTicked()
        val iconBitmap = request.icon?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }

        setContent {
            TasksSettingsTheme(theme = tasksTheme.themeBase.index, primary = themeColor.primaryColor) {
                val ticks = remember { mutableStateMapOf<String, Boolean>().apply { request.requested.forEach { put(it.id, it.id in defaults) } } }
                FamilyScreen(title = "${request.label} wants to use ${catalogue.app}", onBack = { finish() }) {
                    FamilyCard(
                        title = null,
                        body = "Package: ${request.caller.packageName}\nCertificate: ${request.caller.fingerprint}",
                    ) {
                        iconBitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                    }
                    Text(
                        "Tick what it may do. Each line says what the app will send.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for ((effect, functions) in request.byEffect) {
                        if (functions.isEmpty()) continue
                        Text(
                            when (effect) { Effect.READ -> "Read"; Effect.CREATE -> "Create"; Effect.CHANGE -> "Change"; Effect.DELETE -> "Delete" },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        for (f in functions) {
                            val fields = f.input.properties.entries.joinToString { (k, s) -> k + (s.description?.let { " ($it)" } ?: "") }
                            FamilyCard(
                                title = f.title,
                                body = "${f.description}\nSends: ${fields.ifBlank { "nothing" }}",
                                onClick = { ticks[f.id] = ticks[f.id] != true },
                                trailing = { Checkbox(checked = ticks[f.id] == true, onCheckedChange = null) },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = SettingsCardGap),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OutlinedButton(onClick = {
                            request.approve(store, emptySet()); setResult(RESULT_CANCELED); finishSaying("Refused.")
                        }) { Text("Refuse") }
                        Button(onClick = {
                            val granted = ticks.filterValues { it }.keys
                            request.approve(store, granted)
                            setResult(if (granted.isEmpty()) RESULT_CANCELED else RESULT_OK)
                            finishSaying(if (granted.isEmpty()) "Nothing allowed." else "Allowed ${granted.size} function(s) for ${request.label}.")
                        }) { Text("Allow ticked") }
                    }
                }
            }
        }
    }

    /** Our own catalogue, asked through our own door: this app is family to itself. */
    private fun ownCatalogue(): Catalogue? {
        val reply = runCatching { contentResolver.call(FourLink.authorityOf(packageName), FourLink.METHOD_CATALOGUE, null, null) }.getOrNull()
        return reply?.getString(FourLink.KEY_JSON)?.let { Catalogue.parse(it)?.catalogue }
    }

    private fun finishSaying(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
