package com.passeport.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Ligne pointillée façon ticket, avec deux encoches rondes sur les bords si demandé. */
class PerforationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val notches: Boolean
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.line)
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
    }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.notch)
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PerforationView)
        notches = a.getBoolean(R.styleable.PerforationView_notches, false)
        a.recycle()
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = height / 2f
        val r = 8f * density
        val start = if (notches) r + 4f * density else 0f
        canvas.drawLine(start, y, width - start, y, dash)
        if (notches) {
            canvas.drawCircle(0f, y, r, hole)
            canvas.drawCircle(width.toFloat(), y, r, hole)
        }
    }
}
