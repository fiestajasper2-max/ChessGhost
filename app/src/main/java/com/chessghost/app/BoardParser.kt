package com.chessghost.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

object BoardParser {

    // Chess.com default Green theme — confirmed from pixel sampling
    private const val LIGHT_R = 238; private const val LIGHT_G = 238; private const val LIGHT_B = 210
    private const val DARK_R  = 118; private const val DARK_G  = 150; private const val DARK_B  = 85
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

        // Chess.com mobile board always fills full screen width
        // Cell size is exactly screenWidth / 8
        val cellPx = w / 8
        val boardSize = cellPx * 8   // = w (may differ by rounding)

        // Scan vertically to find board top — only need to check two columns:
        // col 0 (should be light) and col 1 (should be dark)
        // Use the leftmost pixel center of each column
        val cx0 = cellPx / 2           // col 0 center — expect LIGHT
        val cx1 = cellPx + cellPx / 2  // col 1 center — expect DARK

        var boardTop = -1
        var consecutiveHits = 0

        for (y in 0 until h) {
            val px0 = bmp.getPixel(cx0.coerceIn(0, w-1), y)
            val px1 = bmp.getPixel(cx1.coerceIn(0, w-1), y)
            val hit = isLightSquare(px0) && isDarkSquare(px1)
            if (hit) {
                consecutiveHits++
                if (consecutiveHits == 1) boardTop = y  // first hit = top of board
            } else {
                if (consecutiveHits > 0 && consecutiveHits < 5) {
                    // false positive — reset
                    boardTop = -1
                }
                consecutiveHits = 0
            }
            // Once we've confirmed the board (20px of consistent hits), stop
            if (consecutiveHits >= 20) break
        }

        if (boardTop < 0) return null

        // Validate: check that an 8x8 grid scores well from this position
        val score = scoreGrid(bmp, 0, boardTop, cellPx)
        if (score < 20) return null

        return Triple(0, boardTop, boardSize)
    }

    private fun scoreGrid(bmp: Bitmap, startX: Int, startY: Int, cellPx: Int): Int {
        val w = bmp.width; val h = bmp.height
        var score = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                // Sample 4 points per cell: avoid center (piece may be there)
                // instead sample near corners of each square
                val baseCx = startX + col * cellPx
                val baseCy = startY + row * cellPx
                val offsets = listOf(
                    Pair(cellPx / 4, cellPx / 4),
                    Pair(cellPx * 3 / 4, cellPx / 4),
                    Pair(cellPx / 4, cellPx * 3 / 4),
                    Pair(cellPx * 3 / 4, cellPx * 3 / 4)
                )
                val expectLight = (row + col) % 2 == 0
                var cellHits = 0
                for ((ox, oy) in offsets) {
                    val px = bmp.getPixel(
                        (baseCx + ox).coerceIn(0, w-1),
                        (baseCy + oy).coerceIn(0, h-1)
                    )
                    if (expectLight && isLightSquare(px)) cellHits++
                    else if (!expectLight && isDarkSquare(px)) cellHits++
                }
                if (cellHits >= 2) score++
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
        val radius = (cellPx * 0.30f).toInt().coerceAtLeast(2)

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