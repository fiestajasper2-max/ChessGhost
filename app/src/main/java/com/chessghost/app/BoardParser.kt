package com.chessghost.app

import android.graphics.Bitmap
import android.graphics.Color

/**
 * BoardParser
 *
 * Input:  Bitmap — full device screenshot
 * Output: FEN string representing the detected board state
 *
 * Method: locate board region by scanning for the largest continuous
 * alternating light/dark square pattern. Sample each of 64 cells via
 * pixel luminance to detect piece presence and colour.
 *
 * Piece-type classification uses luminance signatures only — accurate
 * for detecting piece presence and colour but not piece type.
 * FILL: replace classifyPiece() with a TFLite model for full piece-type
 * accuracy. Without it the FEN encodes all pieces as pawns (P/p) which
 * is enough for Stockfish to find capture/check moves but loses piece
 * mobility for knights/bishops/rooks/queens.
 */
object BoardParser {

    @Volatile private var playerIsWhite: Boolean = true
    @Volatile private var moveCount: Int = 0

    fun setPlayerColor(white: Boolean) {
        playerIsWhite = white
        moveCount = 0
    }

    fun incrementMove() {
        moveCount++
    }

    fun parse(bitmap: Bitmap): String? {
        val board = findBoard(bitmap) ?: return null
        val (boardX, boardY, boardSize) = board
        val cellSize = boardSize / 8f

        val pieces = Array(8) { row ->
            Array(8) { col ->
                val cx = (boardX + col * cellSize + cellSize / 2f).toInt()
                    .coerceIn(0, bitmap.width - 1)
                val cy = (boardY + row * cellSize + cellSize / 2f).toInt()
                    .coerceIn(0, bitmap.height - 1)
                classifyPiece(bitmap, cx, cy, cellSize.toInt())
            }
        }

        return buildFen(pieces)
    }

    // Returns Triple(x, y, size) of board region, or null if not found
    private fun findBoard(bmp: Bitmap): Triple<Int, Int, Int>? {
        val w = bmp.width
        val h = bmp.height
        val scanY = h / 2

        // Find longest run of alternating light/dark pixels at mid-height
        var longestRunStart = 0
        var longestRunLen = 0
        var currentRunStart = 0
        var currentRunLen = 1
        var prevDark = isDark(bmp, 0, scanY)

        for (x in 1 until w) {
            val dark = isDark(bmp, x, scanY)
            if (dark != prevDark) {
                currentRunLen++
            } else {
                if (currentRunLen > longestRunLen) {
                    longestRunLen = currentRunLen
                    longestRunStart = currentRunStart
                }
                currentRunStart = x
                currentRunLen = 1
            }
            prevDark = dark
        }
        if (currentRunLen > longestRunLen) {
            longestRunLen = currentRunLen
            longestRunStart = currentRunStart
        }

        if (longestRunLen < 8) return null

        val cellEst = longestRunLen / 8
        val boardSide = cellEst * 8
        val boardY = ((h - boardSide) / 2).coerceAtLeast(0)

        return Triple(longestRunStart, boardY, boardSide)
    }

    private fun isDark(bmp: Bitmap, x: Int, y: Int): Boolean {
        val px = bmp.getPixel(
            x.coerceIn(0, bmp.width - 1),
            y.coerceIn(0, bmp.height - 1)
        )
        val lum = Color.red(px) * 0.299 +
                  Color.green(px) * 0.587 +
                  Color.blue(px) * 0.114
        return lum < 160
    }

    // Returns 'P' (white piece), 'p' (black piece), or '.' (empty)
    private fun classifyPiece(bmp: Bitmap, cx: Int, cy: Int, cellSize: Int): Char {
        val sample = (cellSize / 5).coerceAtLeast(1)
        var brightCount = 0
        var darkCount = 0
        var total = 0

        for (dx in -sample..sample) {
            for (dy in -sample..sample) {
                val px = bmp.getPixel(
                    (cx + dx).coerceIn(0, bmp.width - 1),
                    (cy + dy).coerceIn(0, bmp.height - 1)
                )
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                val lum = r * 0.299 + g * 0.587 + b * 0.114

                // White pieces are near-white (>210 lum, low saturation)
                val saturation = maxOf(r, g, b) - minOf(r, g, b)
                when {
                    lum > 210 && saturation < 30 -> brightCount++
                    lum < 50  && saturation < 30 -> darkCount++
                }
                total++
            }
        }

        val brightRatio = brightCount.toFloat() / total
        val darkRatio = darkCount.toFloat() / total

        return when {
            brightRatio > 0.12f -> 'P'   // white piece
            darkRatio > 0.12f   -> 'p'   // black piece
            else                -> '.'   // empty
        }
    }

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
        // Remove trailing slash
        if (sb.endsWith('/')) sb.deleteCharAt(sb.length - 1)

        // Side to move: even moveCount = player's turn
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