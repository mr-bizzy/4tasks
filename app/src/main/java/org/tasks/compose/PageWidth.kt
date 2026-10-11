package org.tasks.compose

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.tasks.R

/**
 * FAMILY-LOOK rule 8, width on large screens: single-column content (settings, their sub-pages, the task edit screen,
 * the rows of the task list) stops at a reading width of 720 dp and is centred; below that nothing changes, so phones
 * look as they did. Bars still span the window; only the content is capped.
 *
 * Written ONCE per host, never in each page (4Zones ui/PageWidth.kt: "the fifth page added later would not have it"):
 * the settings host (BasePreferences, all its pages), the Settings pager (MainSettingsScreen, which caps each page and
 * leaves its tab row full width), list settings (ListSettingsScaffold), the task edit screen (TaskEditScreen) and the
 * task list's rows (TaskListFragment). [PageMaxWidth] itself is in kmp, beside the pager.
 */
/** A Compose host's content, capped at [PageMaxWidth] and centred at the top: a fillMaxSize Box, TopCenter, widthIn. */
@Composable
fun PageWidth(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .widthIn(max = PageMaxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            content = content,
        )
    }
}

/**
 * A fragment that caps its own content (and keeps something full width, such as the Settings tab row): the View host
 * leaves its padding off while this fragment is shown.
 */
interface CapsOwnPageWidth

/** The cap on a View host; [enabled] turns it off for a fragment that caps itself ([CapsOwnPageWidth]). */
class PageWidthCap internal constructor(private val view: View) {
    private val maxPx = view.resources.getDimensionPixelSize(R.dimen.page_max_width)
    private var added = 0

    var enabled = true
        set(value) {
            field = value
            apply()
        }

    internal fun apply() {
        val side = if (enabled) ((view.width - maxPx) / 2).coerceAtLeast(0) else 0
        if (side == added) return
        view.setPaddingRelative(
            view.paddingStart - added + side,
            view.paddingTop,
            view.paddingEnd - added + side,
            view.paddingBottom,
        )
        added = side
    }
}

/**
 * The same cap for a View host: its children are kept within [PageMaxWidth], centred, by side padding that follows the
 * view's width (none at or below the cap). The view's own side padding is kept and added to.
 */
fun View.capPageWidth(): PageWidthCap {
    val cap = PageWidthCap(this)
    addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) {
            // Not during this layout pass: padding asks for a new one.
            v.post { cap.apply() }
        }
    }
    if (width > 0) cap.apply()
    return cap
}
