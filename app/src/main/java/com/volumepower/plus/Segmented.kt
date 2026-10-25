package com.volumepower.plus

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** iOS-style segmented control. */
class Segmented(
    context: Context,
    items: List<String>,
    selected: Int,
    private val onSelect: (Int) -> Unit
) : LinearLayout(context) {

    private val labels = ArrayList<TextView>()
    private var current = selected

    init {
        orientation = HORIZONTAL
        val d = resources.displayMetrics.density
        val pad = (2 * d).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.ios_seg_track))
            cornerRadius = 9 * d
        }
        items.forEachIndexed { i, label ->
            val tv = TextView(context).apply {
                text = label
                textSize = 12.5f
                gravity = Gravity.CENTER
                maxLines = 1
                setOnClickListener { select(i) }
            }
            labels.add(tv)
            addView(tv, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        render()
    }

    private fun render() {
        val d = resources.displayMetrics.density
        labels.forEachIndexed { i, tv ->
            tv.setTextColor(ContextCompat.getColor(context, R.color.ios_label))
            if (i == current) {
                tv.background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(context, R.color.ios_seg_selected))
                    cornerRadius = 7 * d
                }
                tv.typeface = Typeface.DEFAULT_BOLD
            } else {
                tv.background = null
                tv.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun select(i: Int) {
        if (i == current) return
        current = i
        render()
        onSelect(i)
    }
}
