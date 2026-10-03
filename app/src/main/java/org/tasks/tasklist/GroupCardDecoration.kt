package org.tasks.tasklist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.tasks.R

/** What an adapter position holds, as far as the cards are concerned. */
enum class ItemKind { OTHER, TASK, HEADER }

/**
 * Each group of tasks sits in one rounded card, the way 4Dictate and 4Zones group their rows
 * (surfaceVariant like 4Dictate's Cards, 16 dp corners). With [GroupStyle.LABEL_ABOVE_CARD] the group's label sits above
 * the card; with [GroupStyle.TITLE_IN_CARD] the header is the top row of the card. The rows draw no
 * background of their own; the card is painted here, behind them.
 */
class GroupCardDecoration(
    context: Context,
    private val style: GroupStyle,
    private val kindAt: (Int) -> ItemKind,
) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val sideInset = (16 * density).toInt()
    private val groupGap = (12 * density).toInt()
    private val radius = 16 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val titleInCard = style == GroupStyle.TITLE_IN_CARD

    init {
        paint.color = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceVariant,
            ContextCompat.getColor(context, R.color.surface_variant),
        )
    }

    private fun inCard(holder: RecyclerView.ViewHolder) =
        holder is TaskViewHolder || (titleInCard && holder is HeaderViewHolder)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val holder = parent.getChildViewHolder(view)
        if (!inCard(holder)) return
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        outRect.left = sideInset
        outRect.right = sideInset
        if (kindAt(position + 1) != ItemKind.TASK) outRect.bottom = groupGap
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var first: View? = null
        var last: View? = null
        var firstKind = ItemKind.OTHER
        var firstPosition = RecyclerView.NO_POSITION
        var lastPosition = RecyclerView.NO_POSITION

        fun flush() {
            val top = first ?: return
            val bottom = last ?: return
            // a group starts at its header, or at a task that follows something that is not a task
            val startsGroup = firstKind == ItemKind.HEADER || kindAt(firstPosition - 1) != ItemKind.TASK
            val endsGroup = kindAt(lastPosition + 1) != ItemKind.TASK
            val t = if (startsGroup) radius else 0f
            val b = if (endsGroup) radius else 0f
            rect.set(
                (parent.paddingLeft + sideInset).toFloat(),
                top.top + top.translationY,
                (parent.width - parent.paddingRight - sideInset).toFloat(),
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
            if (inCard(holder) && position != RecyclerView.NO_POSITION) {
                val kind = if (holder is HeaderViewHolder) ItemKind.HEADER else ItemKind.TASK
                // a new header starts a new card; a gap in positions also ends the run
                if (first != null && (kind == ItemKind.HEADER || position != lastPosition + 1)) flush()
                if (first == null) {
                    first = child
                    firstKind = kind
                    firstPosition = position
                }
                last = child
                lastPosition = position
            } else {
                flush()
            }
        }
        flush()
    }
}
