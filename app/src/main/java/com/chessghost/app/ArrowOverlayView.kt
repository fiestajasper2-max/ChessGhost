// app/src/main/java/com/chessghost/app/ArrowOverlayView.kt

package com.chessghost.app

import android.content.Context
import android.graphics.*
import android.view.View

class ArrowOverlayView(context: Context) : View(context) {

    private var fromCell: Pair<Int, Int>? = null
    private var toCell:   Pair<Int, Int>? = null
    private var boardOriginX = 0f
    private var boardOriginY = 0f
    private var cellPx = 0f

    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 255, 136)
        strokeWidth = 16f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 255, 136)
        style = Paint.Style.FILL
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 255, 136)
        style = Paint.Style.FILL
    }
    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 255, 136)
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    fun setMove(from: Pair<Int, Int>, to: Pair<Int, Int>, boardX: Int, boardY: Int, boardSize: Int) {
        cellPx = boardSize / 8f
        boardOriginX = boardX.toFloat()
        boardOriginY = boardY.toFloat()
        fromCell = from
        toCell = to
        postInvalidate()
    }

    fun clear() {
        fromCell = null; toCell = null
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

        canvas.drawCircle(fx, fy, cellPx * 0.38f, circlePaint)
        canvas.drawCircle(fx, fy, cellPx * 0.38f, circleBorderPaint)

        val angle = Math.atan2((ty - fy).toDouble(), (tx - fx).toDouble())
        val headSize = cellPx * 0.40f
        val stopX = (tx - headSize * 0.6 * Math.cos(angle)).toFloat()
        val stopY = (ty - headSize * 0.6 * Math.sin(angle)).toFloat()
        canvas.drawLine(fx, fy, stopX, stopY, shaftPaint)

        val path = Path()
        val spread = 0.42
        path.moveTo(tx, ty)
        path.lineTo((tx - headSize * Math.cos(angle - spread)).toFloat(), (ty - headSize * Math.sin(angle - spread)).toFloat())
        path.lineTo((tx - headSize * Math.cos(angle + spread)).toFloat(), (ty - headSize * Math.sin(angle + spread)).toFloat())
        path.close()
        canvas.drawPath(path, headPaint)
    }
}