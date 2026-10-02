package org.tasks.fourlink

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
class PairingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalogue = ownCatalogue() ?: return finishSaying("This app's catalogue could not be read.")
        val request = PairingRequest.from(this, intent, catalogue)
            ?: return finishSaying("Not a pairing request from an app that can be identified.")
        if (request.requested.isEmpty()) return finishSaying("${request.label} asked for nothing this app offers.")

        val store = FourLinkStores.of(this).pairings
        val ticks = linkedMapOf<String, CheckBox>()
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16)) }
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            request.icon?.let { addView(ImageView(this@PairingActivity).apply { setImageDrawable(it); layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)) }) }
            addView(TextView(this@PairingActivity).apply {
                text = "${request.label} wants to use ${catalogue.app}"
                textSize = 18f; setPadding(dp(12), dp(8), 0, 0)
            })
        })
        column.addView(text("Package: ${request.caller.packageName}\nCertificate: ${request.caller.fingerprint}", 13f))
        column.addView(text("Tick what it may do. Each line says what the app will receive.", 14f))

        val defaults = request.defaultTicked()
        for ((effect, functions) in request.byEffect) {
            if (functions.isEmpty()) continue
            column.addView(text(
                when (effect) { Effect.READ -> "Read"; Effect.CHANGE -> "Change"; Effect.DELETE -> "Delete" },
                16f,
            ).apply { setPadding(0, dp(12), 0, dp(4)) })
            for (f in functions) {
                val fields = f.input.properties.entries.joinToString { (k, s) -> k + (s.description?.let { " ($it)" } ?: "") }
                val box = CheckBox(this).apply {
                    text = "${f.title}\n${f.description}\nReceives: ${fields.ifBlank { "nothing" }}"
                    isChecked = f.id in defaults
                }
                ticks[f.id] = box
                column.addView(box)
            }
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        buttons.addView(Button(this).apply {
            text = "Refuse"
            setOnClickListener { request.approve(store, emptySet()); setResult(RESULT_CANCELED); finishSaying("Refused.") }
        })
        buttons.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        buttons.addView(Button(this).apply {
            text = "Allow ticked"
            setOnClickListener {
                val granted = ticks.filterValues { it.isChecked }.keys
                request.approve(store, granted)
                setResult(if (granted.isEmpty()) RESULT_CANCELED else RESULT_OK)
                finishSaying(if (granted.isEmpty()) "Nothing allowed." else "Allowed ${granted.size} function(s) for ${request.label}.")
            }
        })
        column.addView(buttons)
        setContentView(ScrollView(this).apply { addView(column) }.padForSystemBars())
    }

    private fun text(s: String, size: Float) = TextView(this).apply { text = s; textSize = size; setPadding(0, 6, 0, 6) }

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
