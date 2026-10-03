package uk.mr_biz.fourlink.caller

import android.app.Activity
import android.os.Bundle
import android.util.Log
import uk.mr_biz.fourlink.android.FourLinkClient

/**
 * adb shell am start -n uk.mr_biz.fourlink.caller/.CallActivity --es fn tasks.add --es args '{"title":"x"}'
 * fn is hello, catalogue, pair (args = function ids) or a function id; pkg defaults to 4Tasks. The answer goes to logcat, tag FourLinkCaller.
 */
class CallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: "uk.mr_biz.fourtasks"
        val fn = intent.getStringExtra("fn") ?: "hello"
        val args = intent.getStringExtra("args") ?: "{}"
        val client = FourLinkClient(this)
        if (fn == "pair") {
            // Opens the provider's pairing screen for these function ids (comma separated in args); stays open behind it.
            client.requestPairing(this, pkg, args.split(",").filter { it.isNotBlank() })
            return
        }
        val answer = when (fn) {
            "hello" -> client.hello(pkg).toString()
            "catalogue" -> client.catalogue(pkg).toString()
            else -> client.invoke(pkg, fn, args, "1").toString()
        }
        Log.i("FourLinkCaller", "RESULT $fn $answer")
        finish()
    }
}
