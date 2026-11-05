package com.chessghost.app

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.WindowManager

class MoveOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    private var overlayView: OverlayCanvas? = null

    fun show() {
        overlayView = OverlayCanvas(context)
        windowManager.addView(overlayView, params)
    }

    fun hide() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    fun drawMove(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        screenW: Int,
        screenH: Int
    ) {
        overlayView?.setMove(from, to, screenW, screenH)
    }

    fun clear() {
        overlayView?.clear()
    }
}

class OverlayCanvas(context: Context) : View(context) {

    private var fromCell: Pair<Int, Int>? = null
    private var toCell: Pair<Int, Int>? = null
    private var boardOriginX = 0f
    private var boardOriginY = 0f
    private var cellPx = 0f

    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 255, 136)
        strokeWidth = 14f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 255, 136)
        style = Paint.Style.FILL
    }

    private val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 0, 255, 136)
        style = Paint.Style.FILL
    }

    private val originBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 0, 255, 136)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    fun setMove(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        screenW: Int,
        screenH: Int
    ) {
        val boardSide = minOf(screenW, screenH).toFloat()
        cellPx = boardSide / 8f
        boardOriginX = (screenW - boardSide) / 2f
        boardOriginY = (screenH - boardSide) / 2f
        fromCell = from
        toCell = to
        postInvalidate()
    }

    fun clear() {
        fromCell = null
        toCell = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val from = fromCell ?: return
        val to   = toCell   ?: return

        val fx = boardOriginX + from.first  * cellPx + cellPx / 2f
        val fy = boardOriginY + from.second * cellPx + cellPx / 2f
        val tx = boardOriginX + to.first    * cellPx + cellPx / 2f
        val ty = boardOriginY + to.second   * cellPx + cellPx / 2f

        // Origin highlight circle
        canvas.drawCircle(fx, fy, cellPx * 0.38f, originPaint)
        canvas.drawCircle(fx, fy, cellPx * 0.38f, originBorderPaint)

        // Arrow shaft (stop slightly before tip so arrowhead looks clean)
        val angle = Math.atan2((ty - fy).toDouble(), (tx - fx).toDouble())
        val headSize = cellPx * 0.38f
        val stopX = (tx - headSize * 0.6f * Math.cos(angle)).toFloat()
        val stopY = (ty - headSize * 0.6f * Math.sin(angle)).toFloat()
        canvas.drawLine(fx, fy, stopX, stopY, shaftPaint)

        // Arrowhead (filled triangle)
        val path = Path()
        val spread = 0.45
        path.moveTo(tx, ty)
        path.lineTo(
            (tx - headSize * Math.cos(angle - spread)).toFloat(),
            (ty - headSize * Math.sin(angle - spread)).toFloat()
        )
        path.lineTo(
            (tx - headSize * Math.cos(angle + spread)).toFloat(),
            (ty - headSize * Math.sin(angle + spread)).toFloat()
        )
        path.close()
        canvas.drawPath(path, headPaint)
    }
}