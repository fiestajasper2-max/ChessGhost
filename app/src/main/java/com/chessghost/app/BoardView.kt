// app/src/main/java/com/chessghost/app/BoardView.kt

package com.chessghost.app

import android.content.Context
import android.graphics.*
import android.view.*

class BoardView(
    context: Context,
    private val board: Array<Array<Char>>,
    private val cellPx: Int,
    private val onCellTap: (row: Int, col: Int) -> Unit
) : View(context) {

    private val lightPaint = Paint().apply { color = Color.parseColor("#EEEED2") }
    private val darkPaint  = Paint().apply { color = Color.parseColor("#769656") }
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = cellPx * 0.72f
    }
    private val highlightPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 136)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val pieceSymbols = mapOf(
        'P' to "♙", 'N' to "♘", 'B' to "♗", 'R' to "♖", 'Q' to "♕", 'K' to "♔",
        'p' to "♟", 'n' to "♞", 'b' to "♝", 'r' to "♜", 'q' to "♛", 'k' to "♚"
    )

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val size = cellPx * 8
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val x = col * cellPx.toFloat()
                val y = row * cellPx.toFloat()
                val paint = if ((row + col) % 2 == 0) lightPaint else darkPaint
                canvas.drawRect(x, y, x + cellPx, y + cellPx, paint)

                val piece = board[row][col]
                if (piece != '.') {
                    val sym = pieceSymbols[piece] ?: continue
                    textPaint.color = if (piece.isUpperCase()) Color.WHITE else Color.argb(255, 20, 20, 20)
                    // Draw shadow for visibility
                    textPaint.setShadowLayer(2f, 1f, 1f, if (piece.isUpperCase()) Color.BLACK else Color.WHITE)
                    canvas.drawText(sym, x + cellPx / 2f, y + cellPx * 0.78f, textPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val col = (event.x / cellPx).toInt().coerceIn(0, 7)
            val row = (event.y / cellPx).toInt().coerceIn(0, 7)
            onCellTap(row, col)
            return true
        }
        return super.onTouchEvent(event)
    }
}