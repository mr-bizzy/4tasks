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
 * the settings host (BasePreferences, all its pages), list settings (ListSettingsScaffold), the task edit screen
 * (TaskEditScreen) and the task list's rows (TaskListFragment).
 */
val PageMaxWidth = 720.dp

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
 * The same cap for a View host: its children are kept within [PageMaxWidth], centred, by side padding that follows the
 * view's width (none at or below the cap). The view's own side padding is kept and added to.
 */
fun View.capPageWidth() {
    val maxPx = resources.getDimensionPixelSize(R.dimen.page_max_width)
    var added = 0
    fun apply(width: Int) {
        val side = ((width - maxPx) / 2).coerceAtLeast(0)
        if (side == added) return
        setPaddingRelative(paddingStart - added + side, paddingTop, paddingEnd - added + side, paddingBottom)
        added = side
    }
    addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft) {
            // Not during this layout pass: padding asks for a new one.
            v.post { apply(v.width) }
        }
    }
    if (width > 0) apply(width)
}
