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
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val bars = FloatArray(60) { 0.2f + random.nextFloat() * (1.0f - 0.2f) }

    var onProgressChanged: ((Float) -> Unit)? = null

    init {
        isFocusable = true
        isClickable = true
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
                parent.requestDisallowInterceptTouchEvent(true)
                progress = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                onProgressChanged?.invoke(progress)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(
            android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 1f, progress)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        val next = when (action) {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> progress + 0.05f
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> progress - 0.05f
            android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id ->
                arguments?.getFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) ?: return false
            else -> return super.performAccessibilityAction(action, arguments)
        }
        progress = next
        onProgressChanged?.invoke(progress)
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED)
        return true
    }
}
