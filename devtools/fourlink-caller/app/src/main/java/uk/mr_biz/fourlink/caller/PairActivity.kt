package uk.mr_biz.fourlink.caller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import uk.mr_biz.fourlink.android.FourLinkClient

/**
 * Asks 4Tasks for a pairing, as a third-party app would:
 * adb shell am start -n <package>/uk.mr_biz.fourlink.caller.PairActivity --es functions tasks.add,tasks.list
 */
class PairActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: "uk.mr_biz.fourtasks"
        val ids = (intent.getStringExtra("functions") ?: "tasks.add").split(',')
        val started = FourLinkClient(this).requestPairing(this, pkg, ids)
        Log.i("FourLinkCaller", "PAIR requested started=$started")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i("FourLinkCaller", "PAIR result code=$resultCode")
        finish()
    }
}
