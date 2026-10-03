package org.tasks.tasklist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.tasks.R

/**
 * Each group of tasks sits in one rounded card, the way 4Dictate and 4Zones group their rows: the group's
 * label above, then a surfaceContainer card with 16 dp corners holding the group's rows. The rows draw no
 * background of their own; the card is painted here, behind them. [isTaskAt] says whether the item at an
 * adapter position is a task (not a group header, a banner, or beyond the list).
 */
class GroupCardDecoration(
    context: Context,
    private val isTaskAt: (Int) -> Boolean,
) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val sideInset = (16 * density).toInt()
    private val groupGap = (12 * density).toInt()
    private val radius = 16 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    init {
        paint.color = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceContainer,
            ContextCompat.getColor(context, R.color.surface_container),
        )
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val holder = parent.getChildViewHolder(view)
        if (holder !is TaskViewHolder) return
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        outRect.left = sideInset
        outRect.right = sideInset
        if (!isTaskAt(position + 1)) outRect.bottom = groupGap
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var first: View? = null
        var last: View? = null
        var lastPosition = RecyclerView.NO_POSITION
        var startsGroup = false

        fun flush() {
            val top = first ?: return
            val bottom = last ?: return
            val endsGroup = !isTaskAt(lastPosition + 1)
            val t = radiusFor(startsGroup)
            val b = radiusFor(endsGroup)
            rect.set(
                (parent.paddingLeft + sideInset).toFloat(),
                top.top + top.translationY,
                (parent.width - parent.paddingRight - sideInset).toFloat(),
                // the group's gap sits below the last row; the card stops at the row
                bottom.bottom + bottom.translationY,
            )
            path.reset()
            path.addRoundRect(rect, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
            canvas.drawPath(path, paint)
            first = null
            last = null
        }

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(child)
            val position = holder.bindingAdapterPosition
            if (holder is TaskViewHolder && position != RecyclerView.NO_POSITION) {
                if (first != null && position != lastPosition + 1) flush()
                if (first == null) {
                    first = child
                    startsGroup = !isTaskAt(position - 1)
                }
                last = child
                lastPosition = position
            } else {
                flush()
            }
        }
        flush()
    }

    private fun radiusFor(rounded: Boolean) = if (rounded) radius else 0f
}
