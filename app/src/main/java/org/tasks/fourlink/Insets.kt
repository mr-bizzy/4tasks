package org.tasks.fourlink

import android.view.View
import android.view.WindowInsets

/** targetSdk 36 draws edge to edge: keep the first views out from under the status and navigation bars. */
fun View.padForSystemBars(): View {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    setOnApplyWindowInsetsListener { v, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
        insets
    }
    return this
}
