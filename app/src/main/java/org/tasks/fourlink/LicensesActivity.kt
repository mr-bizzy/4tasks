package org.tasks.fourlink

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Shows a licence text bundled in assets/licenses (the GPL, or the notice with third-party components). */
class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_FILE) ?: THIRD_PARTY
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Licences"
        val text = runCatching { assets.open("licenses/$file").bufferedReader().use { it.readText() } }
            .getOrDefault("The licence text could not be read.")
        val view = TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(40, 32, 40, 32)
        }
        setContentView(ScrollView(this).apply { addView(view) }.padForSystemBars())
    }

    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_TITLE = "title"
        const val GPL = "GPL-3.0.txt"
        const val THIRD_PARTY = "THIRD_PARTY.txt"
    }
}
