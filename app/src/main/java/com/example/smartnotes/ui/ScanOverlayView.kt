package com.example.smartnotes.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A000000")
        style = Paint.Style.FILL
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var frameRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()

        val rectW = width * 0.82f
        val rectH = min(height * 0.70f, rectW * 1.35f) // примерно лист A4

        val left = (width - rectW) / 2f
        val top = (height - rectH) / 2f
        frameRect = RectF(left, top, left + rectW, top + rectH)
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRoundRect(frameRect, 24f, 24f, clearPaint)
        canvas.drawRoundRect(frameRect, 24f, 24f, framePaint)

        val c = 60f
        val l = frameRect.left
        val t = frameRect.top
        val r = frameRect.right
        val b = frameRect.bottom

        canvas.drawLine(l, t + c, l, t, cornerPaint)
        canvas.drawLine(l, t, l + c, t, cornerPaint)

        canvas.drawLine(r - c, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + c, cornerPaint)

        canvas.drawLine(l, b - c, l, b, cornerPaint)
        canvas.drawLine(l, b, l + c, b, cornerPaint)

        canvas.drawLine(r - c, b, r, b, cornerPaint)
        canvas.drawLine(r, b - c, r, b, cornerPaint)

        canvas.restoreToCount(save)
    }

    fun getFrameRect(): RectF = frameRect
}
