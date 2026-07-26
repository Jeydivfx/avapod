package com.avapod.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()

    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val bars = FloatArray(60) { 0.2f + random.nextFloat() * (1.0f - 0.2f) }

    var onProgressChanged: ((Float) -> Unit)? = null

    init {
        paint.color = context.getColor(R.color.waveform_background)
        progressPaint.color = context.getColor(R.color.waveform_progress)

        paint.style = Paint.Style.FILL
        progressPaint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width / (bars.size.toFloat() * 1.5f)
        val space = barWidth / 2

        for (i in bars.indices) {
            val left = i * (barWidth + space)
            val barHeight = bars[i] * height

            val top = height - barHeight
            val right = left + barWidth
            val bottom = height.toFloat()

            val currentBarPaint = if ((left / width) <= progress) progressPaint else paint

            canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, currentBarPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                progress = (event.x / width).coerceIn(0f, 1f)
                onProgressChanged?.invoke(progress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}