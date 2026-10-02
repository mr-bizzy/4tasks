package org.tasks.fourlink

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import uk.mr_biz.fourlink.Caller
import uk.mr_biz.fourlink.android.FourLinkStores
import java.text.DateFormat
import java.util.Date

/**
 * "Apps allowed to use 4Tasks" (spec §6): what each app may do and when it last did it, a
 * Remove that asks first; and the audit log (§9) underneath.
 */
class ConnectedAppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val stores = FourLinkStores.of(this)
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 32, 40, 32) }
        column.addView(TextView(this).apply { text = "Apps allowed to use 4Tasks"; textSize = 20f })
        val pairings = stores.pairings.all()
        if (pairings.isEmpty()) column.addView(TextView(this).apply { text = "No other app is allowed. Only our own apps (4Dictate and 4Zones) can use 4Tasks." })
        for (p in pairings) {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p.packageName, 0)).toString() }.getOrDefault(p.packageName)
            val lines = p.granted.sorted().joinToString("\n") { id ->
                "  • $id — " + (p.lastUsed[id]?.let { "last used ${fmt.format(Date(it))}" } ?: "never used")
            }
            column.addView(TextView(this).apply {
                text = "$label (${p.packageName})\nCertificate ${Caller.fingerprintOf(p.certDigest)} · paired ${fmt.format(Date(p.grantedAtMs))}\n$lines"
                setPadding(0, 16, 0, 4)
            })
            column.addView(Button(this).apply {
                text = "Remove $label"
                setOnClickListener {
                    AlertDialog.Builder(this@ConnectedAppsActivity)
                        .setTitle("Remove $label?")
                        .setMessage("It will no longer be able to use 4Tasks until you allow it again.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ -> stores.pairings.remove(p.packageName); render() }
                        .show()
                }
            })
        }
        column.addView(TextView(this).apply { text = "Call log (newest first; never the arguments)"; textSize = 20f; setPadding(0, 32, 0, 8) })
        val log = stores.audit.all().asReversed()
        if (log.isEmpty()) column.addView(TextView(this).apply { text = "No calls yet." })
        column.addView(TextView(this).apply {
            text = log.take(100).joinToString("\n") { "${fmt.format(Date(it.timeMs))}  ${it.callerPackage}  ${it.what}  → ${it.result}" }
            textSize = 12f
        })
        setContentView(ScrollView(this).apply { addView(column) }.padForSystemBars())
    }
}
