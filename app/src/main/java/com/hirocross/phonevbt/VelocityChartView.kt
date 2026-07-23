package com.hirocross.phonevbt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class VelocityChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val values = ArrayDeque<Float>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    fun addValue(value: Float) {
        values.addLast(value)
        if (values.size > 180) values.removeFirst()
        invalidate()
    }

    fun clear() {
        values.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return

        val maxValue = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = width.toFloat() / (values.size - 1)
        val path = android.graphics.Path()

        values.forEachIndexed { index, value ->
            val x = index * step
            val y = height - (value / maxValue) * (height - 24f) - 12f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
