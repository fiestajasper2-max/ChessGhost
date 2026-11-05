package com.chessghost.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/**
 * BoardParser — Chess.com specific
 *
 * Detection strategy:
 *   1. Find board by scanning for Chess.com's known square colors:
 *      light squares: ~#EEEED2 (cream)   dark squares: ~#769656 (green)
 *   2. Confirm 8x8 grid by checking color pattern consistency
 *   3. Sample each cell center for piece presence using contrast against
 *      the known background square color
 *   4. Determine piece color (white/black) by luminance of piece pixels
 *
 * Works for Chess.com default "Green" board theme.
 * If Sam uses a different theme, the LIGHT_* and DARK_* constants need updating.
 */
object BoardParser {

    // Chess.com default board colors (Green theme) — tweak if using another theme
    private const val LIGHT_R = 238; private const val LIGHT_G = 238; private const val LIGHT_B = 210
    private const val DARK_R  = 118; private const val DARK_G  = 150; private const val DARK_B  = 86

    // How close a pixel must be to the known square color to count as a match
    private const val COLOR_TOLERANCE = 35

    @Volatile var playerIsWhite: Boolean = true
        private set
    @Volatile var moveCount: Int = 0
        private set

    fun setPlayerColor(white: Boolean) { playerIsWhite = white; moveCount = 0 }
    fun incrementMove() { moveCount++ }

    // ── Public entry point ────────────────────────────────────────────────────

    fun parse(bitmap: Bitmap): String? {
        val board = findBoard(bitmap) ?: return null
        val (boardX, boardY, boardSize) = board
        val cell = boardSize / 8f

        val pieces = Array(8) { row ->
            Array(8) { col ->
                val cx = (boardX + col * cell + cell / 2f).toInt().coerceIn(0, bitmap.width - 1)
                val cy = (boardY + row * cell + cell / 2f).toInt().coerceIn(0, bitmap.height - 1)
                val isLightSquare = (row + col) % 2 == 0
                classifyPiece(bitmap, cx, cy, cell.toInt(), isLightSquare)
            }
        }

        return buildFen(pieces)
    }

    // ── Board detection ───────────────────────────────────────────────────────

    /**
     * Scan the bitmap for Chess.com's distinctive green/cream checkerboard.
     * Strategy: for each candidate top-left corner (sampled every 8px),
     * check whether a grid of 8x8 cells matches the expected color pattern.
     * Return the best-scoring candidate.
     */
    private fun findBoard(bmp: Bitmap): Triple<Int, Int, Int>? {
        val w = bmp.width
        val h = bmp.height

        // Board is roughly square and fills most of the screen width on mobile
        // Try cell sizes from h/10 to h/6 (boards are 8 cells, so board = 8*cell)
        val minCell = h / 14
        val maxCell = w / 8

        var bestScore = 0
        var bestX = 0; var bestY = 0; var bestCell = 0

        // Coarse scan — check every 4px for top-left, every step in cell size
        for (cellPx in maxCell downTo minCell step max(1, (maxCell - minCell) / 12)) {
            val boardSide = cellPx * 8
            if (boardSide > w || boardSide > h) continue

            for (startY in 0 until (h - boardSide) step max(4, cellPx / 4)) {
                for (startX in 0 until (w - boardSide) step max(4, cellPx / 4)) {
                    val score = scoreGrid(bmp, startX, startY, cellPx)
                    if (score > bestScore) {
                        bestScore = score
                        bestX = startX; bestY = startY; bestCell = cellPx
                    }
                }
            }
        }

        // Need at least 32 of 64 squares matching (half the board)
        if (bestScore < 32) return null
        return Triple(bestX, bestY, bestCell * 8)
    }

    /**
     * Score how well an 8x8 grid starting at (startX, startY) with cellPx-sized cells
     * matches Chess.com's checkerboard. Checks center pixel of each cell.
     */
    private fun scoreGrid(bmp: Bitmap, startX: Int, startY: Int, cellPx: Int): Int {
        var score = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val cx = startX + col * cellPx + cellPx / 2
                val cy = startY + row * cellPx + cellPx / 2
                if (cx >= bmp.width || cy >= bmp.height) continue
                val px = bmp.getPixel(cx, cy)
                val expectLight = (row + col) % 2 == 0
                if (expectLight && isLightSquare(px)) score++
                else if (!expectLight && isDarkSquare(px)) score++
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

    // ── Piece classification ──────────────────────────────────────────────────

    /**
     * Sample a ring of pixels around the cell center, ignoring the square's
     * background color. Pixels that differ significantly from the background
     * are part of a piece — classify by their luminance.
     */
    private fun classifyPiece(
        bmp: Bitmap, cx: Int, cy: Int, cellPx: Int, isLightSquare: Boolean
    ): Char {
        val bgR = if (isLightSquare) LIGHT_R else DARK_R
        val bgG = if (isLightSquare) LIGHT_G else DARK_G
        val bgB = if (isLightSquare) LIGHT_B else DARK_B

        val radius = (cellPx * 0.30f).toInt().coerceAtLeast(2)
        var whitePiecePixels = 0
        var blackPiecePixels = 0
        var total = 0

        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue   // circle mask
                val x = (cx + dx).coerceIn(0, bmp.width - 1)
                val y = (cy + dy).coerceIn(0, bmp.height - 1)
                val px = bmp.getPixel(x, y)
                val r = Color.red(px); val g = Color.green(px); val b = Color.blue(px)

                // Skip pixels that look like the background square
                if (colorDist(r, g, b, bgR, bgG, bgB) < COLOR_TOLERANCE) { total++; continue }

                val lum = r * 0.299 + g * 0.587 + b * 0.114
                when {
                    lum > 180 -> whitePiecePixels++   // white piece
                    lum < 80  -> blackPiecePixels++   // black piece (dark outline/fill)
                }
                total++
            }
        }

        if (total == 0) return '.'
        val whiteRatio = whitePiecePixels.toFloat() / total
        val blackRatio = blackPiecePixels.toFloat() / total

        return when {
            whiteRatio > 0.08f && whiteRatio > blackRatio * 1.5f -> 'P'  // white piece
            blackRatio > 0.08f && blackRatio > whiteRatio * 1.5f -> 'p'  // black piece
            else -> '.'
        }
    }

    // ── FEN builder ───────────────────────────────────────────────────────────

    private fun buildFen(pieces: Array<Array<Char>>): String {
        val sb = StringBuilder()
        for (row in pieces) {
            var empty = 0
            for (piece in row) {
                if (piece == '.') {
                    empty++
                } else {
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
