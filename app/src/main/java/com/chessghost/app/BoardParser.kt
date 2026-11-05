package com.chessghost.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

object BoardParser {

    // Sampled from Sam's actual Chess.com screenshot — brown/tan "Wood" theme
    private const val LIGHT_R = 240; private const val LIGHT_G = 217; private const val LIGHT_B = 181
    private const val DARK_R  = 181; private const val DARK_G  = 136; private const val DARK_B  = 99
    private const val COLOR_TOLERANCE = 40

    @Volatile var playerIsWhite: Boolean = true
        private set
    @Volatile var moveCount: Int = 0
        private set

    fun setPlayerColor(white: Boolean) { playerIsWhite = white; moveCount = 0 }
    fun incrementMove() { moveCount++ }

    fun parseWithBounds(bitmap: Bitmap): Pair<String, IntArray>? {
        val board = findBoard(bitmap) ?: return null
        val (boardX, boardY, boardSize) = board
        val cell = boardSize / 8f

        val pieces = Array(8) { row ->
            Array(8) { col ->
                val cx = (boardX + col * cell + cell / 2f).toInt().coerceIn(0, bitmap.width - 1)
                val cy = (boardY + row * cell + cell / 2f).toInt().coerceIn(0, bitmap.height - 1)
                classifyPiece(bitmap, cx, cy, cell.toInt(), (row + col) % 2 == 0)
            }
        }

        return Pair(buildFen(pieces), intArrayOf(boardX, boardY, boardSize))
    }

    private fun findBoard(bmp: Bitmap): Triple<Int, Int, Int>? {
        val w = bmp.width
        val h = bmp.height

        // Board is roughly square, fills screen width on Chess.com mobile
        // Cell size range: board spans ~full width, so cell ≈ w/8
        val minCell = w / 12
        val maxCell = w / 7

        var bestScore = 0
        var bestX = 0; var bestY = 0; var bestCell = 0

        // Fine scan — step by 2px for accuracy
        for (cellPx in maxCell downTo minCell step max(1, (maxCell - minCell) / 16)) {
            val boardSide = cellPx * 8
            if (boardSide > w || boardSide > h) continue

            for (startY in 0 until (h - boardSide) step 2) {
                for (startX in 0 until (w - boardSide) step 2) {
                    val score = scoreGrid(bmp, startX, startY, cellPx)
                    if (score > bestScore) {
                        bestScore = score
                        bestX = startX; bestY = startY; bestCell = cellPx
                    }
                }
            }
        }

        if (bestScore < 40) return null
        return Triple(bestX, bestY, bestCell * 8)
    }

    private fun scoreGrid(bmp: Bitmap, startX: Int, startY: Int, cellPx: Int): Int {
        var score = 0
        // Sample 3x3 points per cell instead of just center — more robust
        val offsets = listOf(0.25f, 0.5f, 0.75f)
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val expectLight = (row + col) % 2 == 0
                var cellScore = 0
                for (ox in offsets) {
                    for (oy in offsets) {
                        val cx = (startX + col * cellPx + ox * cellPx).toInt().coerceIn(0, bmp.width - 1)
                        val cy = (startY + row * cellPx + oy * cellPx).toInt().coerceIn(0, bmp.height - 1)
                        val px = bmp.getPixel(cx, cy)
                        if (expectLight && isLightSquare(px)) cellScore++
                        else if (!expectLight && isDarkSquare(px)) cellScore++
                    }
                }
                // Cell counts only if majority of samples match
                if (cellScore >= 5) score++
            }
        }
        return score
    }

    private fun isLightSquare(px: Int): Boolean {
        val r = Color.red(px); val g = Color.green(px); val b = Color.blue(px)
        return colorDist(r, g, b, LIGHT_R, LIGHT_G, LIGHT_B) < COLOR_TOLERANCE
    }

    private fun isDarkSquare(px: Int): Boolean {
        val r = Color.red(px); val g = Color.green(px); val b = Color.blue(px)
        return colorDist(r, g, b, DARK_R, DARK_G, DARK_B) < COLOR_TOLERANCE
    }

    private fun colorDist(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Int {
        return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
    }

    private fun classifyPiece(
        bmp: Bitmap, cx: Int, cy: Int, cellPx: Int, isLightSquare: Boolean
    ): Char {
        val bgR = if (isLightSquare) LIGHT_R else DARK_R
        val bgG = if (isLightSquare) LIGHT_G else DARK_G
        val bgB = if (isLightSquare) LIGHT_B else DARK_B
        val radius = (cellPx * 0.32f).toInt().coerceAtLeast(2)

        var whitePx = 0; var blackPx = 0; var total = 0

        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                val x = (cx + dx).coerceIn(0, bmp.width - 1)
                val y = (cy + dy).coerceIn(0, bmp.height - 1)
                val px = bmp.getPixel(x, y)
                val r = Color.red(px); val g = Color.green(px); val b = Color.blue(px)
                total++
                if (colorDist(r, g, b, bgR, bgG, bgB) < COLOR_TOLERANCE) continue
                val lum = r * 0.299 + g * 0.587 + b * 0.114
                when {
                    lum > 190 -> whitePx++
                    lum < 90  -> blackPx++
                }
            }
        }

        if (total == 0) return '.'
        val wr = whitePx.toFloat() / total
        val br = blackPx.toFloat() / total
        return when {
            wr > 0.07f && wr > br * 1.4f -> 'P'
            br > 0.07f && br > wr * 1.4f -> 'p'
            else -> '.'
        }
    }

    private fun buildFen(pieces: Array<Array<Char>>): String {
        val sb = StringBuilder()
        for (row in pieces) {
            var empty = 0
            for (piece in row) {
                if (piece == '.') empty++
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(piece)
                }
            }
            if (empty > 0) sb.append(empty)
            sb.append('/')
        }
        if (sb.endsWith('/')) sb.deleteCharAt(sb.length - 1)
        val sideToMove = when {
            moveCount % 2 == 0 && playerIsWhite  -> "w"
            moveCount % 2 == 0 && !playerIsWhite -> "b"
            moveCount % 2 != 0 && playerIsWhite  -> "b"
            else                                 -> "w"
        }
        val fullMove = (moveCount / 2) + 1
        return "$sb $sideToMove - - 0 $fullMove"
    }
}