package com.omegarouser.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Круглый индикатор прогресса загрузки файла (как значок скачивания в браузерах):
 * серое кольцо-фон + красная дуга прогресса. Если progress < 0 — показывает
 * "неопределённое" вращающееся состояние (просто небольшая дуга-заглушка).
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Int = 0 // 0..100, или -1 для неопределённого состояния
        set(value) {
            field = value
            invalidate()
        }

    private val strokeWidthPx = dp(3f)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.parseColor("#E0E0E0")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#EA4335") // красный, как индикатор загрузки в браузерах
    }

    private val rect = RectF()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx / 2f
        rect.set(inset, inset, width - inset, height - inset)

        canvas.drawOval(rect, backgroundPaint)

        val sweep = if (progress in 0..100) {
            360f * (progress / 100f)
        } else {
            60f // неопределённое состояние — небольшая дуга-индикатор
        }
        canvas.drawArc(rect, -90f, sweep, false, progressPaint)
    }
}
