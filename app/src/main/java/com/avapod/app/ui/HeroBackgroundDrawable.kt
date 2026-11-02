package com.avapod.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class HeroBackgroundDrawable(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val r = 19f * density
        val save = canvas.save()
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) }
        canvas.clipPath(shape)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, w, h * .25f,
            intArrayOf(Color.rgb(167, 15, 23), Color.rgb(57, 14, 27), Color.rgb(26, 12, 26)),
            floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = Color.argb(30, 240, 58, 42)
        val glow = Path().apply {
            moveTo(0f, 0f); lineTo(w * .29f, 0f)
            cubicTo(w * .45f, h * .42f, w * .27f, h * .78f, 0f, h * .62f)
            close()
        }
        canvas.drawPath(glow, paint)
        paint.color = Color.argb(58, 165, 39, 40)
        val wave = Path().apply {
            moveTo(0f, h * .67f)
            cubicTo(w * .23f, h * .55f, w * .47f, h * 1.13f, w, h * .73f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        canvas.drawPath(wave, paint)
        paint.color = Color.argb(35, 255, 85, 72)
        val bottom = Path().apply {
            moveTo(0f, h * .87f)
            cubicTo(w * .28f, h * 1.03f, w * .58f, h * .88f, w, h * .94f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        canvas.drawPath(bottom, paint)
        canvas.restoreToCount(save)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * density
        paint.color = Color.argb(195, 133, 22, 30)
        canvas.drawRoundRect(RectF(paint.strokeWidth, paint.strokeWidth,
            w - paint.strokeWidth, h - paint.strokeWidth), r, r, paint)
        paint.style = Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) { invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
