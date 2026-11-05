// app/src/main/java/com/chessghost/app/FloatingPanelView.kt

package com.chessghost.app

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import kotlinx.coroutines.*

class FloatingPanelView(
    context: Context,
    private val screenW: Int,
    private val engine: StockfishEngine,
    private val scope: CoroutineScope,
    private val arrowView: ArrowOverlayView
) : LinearLayout(context) {

    // 8x8 board state — '.' = empty, 'P'=white pawn, 'p'=black pawn etc
    // We use full piece notation: PNBRQK / pnbrqk
    private val board = Array(8) { row ->
        Array(8) { col ->
            startingPiece(row, col)
        }
    }

    private var selectedPiece: Char = 'P'   // currently selected piece to place
    private var playerWhite = true
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var isDragging = false
    private var onDrag: ((Int, Int) -> Unit)? = null

    // Board display size in the panel
    private val boardPx = 240   // dp-ish, fixed
    private val cellPx = boardPx / 8

    private lateinit var boardView: BoardView
    private lateinit var statusText: TextView
    private lateinit var analyzeBtn: Button

    // Board position on actual screen — user calibrates with +/- buttons
    private var boardScreenY = 520

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(220, 10, 10, 10))
        setPadding(8, 8, 8, 8)
        elevation = 12f
        buildUI()
    }

    fun setOnDragListener(l: (Int, Int) -> Unit) { onDrag = l }

    private fun buildUI() {
        // ── Drag handle ───────────────────────────────────────────────────────
        val handle = TextView(context).apply {
            text = "⠿ ChessGhost  ✕"
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 12f
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.argb(80, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
        }
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = event.rawX; dragStartRawY = event.rawY
                    dragStartX = 0; dragStartY = 0
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragStartRawX).toInt()
                    val dy = (event.rawY - dragStartRawY).toInt()
                    if (!isDragging && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) isDragging = true
                    if (isDragging) {
                        onDrag?.invoke(dx - dragStartX, dy - dragStartY)
                        dragStartX = dx; dragStartY = dy
                    }
                }
            }
            true
        }
        addView(handle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addSpace(4)

        // ── Mini board ────────────────────────────────────────────────────────
        boardView = BoardView(context, board, cellPx) { row, col ->
            board[row][col] = when {
                board[row][col] == '.' -> selectedPiece
                board[row][col] == selectedPiece -> '.'   // tap again to clear
                else -> selectedPiece                      // replace
            }
            boardView.invalidate()
        }
        addView(boardView, LayoutParams(boardPx, boardPx).also {
            it.topMargin = 2; it.bottomMargin = 2
        })

        // ── Piece selector ────────────────────────────────────────────────────
        val whitePieces = listOf('P','N','B','R','Q','K')
        val blackPieces = listOf('p','n','b','r','q','k')
        val pieceLabels = mapOf(
            'P' to "♙", 'N' to "♘", 'B' to "♗", 'R' to "♖", 'Q' to "♕", 'K' to "♔",
            'p' to "♟", 'n' to "♞", 'b' to "♝", 'r' to "♜", 'q' to "♛", 'k' to "♚"
        )

        // White pieces row
        val whiteRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        whitePieces.forEach { piece ->
            val btn = TextView(context).apply {
                text = pieceLabels[piece]
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(if (selectedPiece == piece) Color.parseColor("#00FF88") else Color.TRANSPARENT)
                setTextColor(Color.WHITE)
                setPadding(4, 2, 4, 2)
                setOnClickListener {
                    selectedPiece = piece
                    refreshPieceSelector()
                }
            }
            whiteRow.addView(btn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(whiteRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Black pieces row
        val blackRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        blackPieces.forEach { piece ->
            val btn = TextView(context).apply {
                text = pieceLabels[piece]
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(if (selectedPiece == piece) Color.parseColor("#00FF88") else Color.TRANSPARENT)
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(4, 2, 4, 2)
                setOnClickListener {
                    selectedPiece = piece
                    refreshPieceSelector()
                }
            }
            blackRow.addView(btn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(blackRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addSpace(4)

        // ── Eraser + Reset + Side ─────────────────────────────────────────────
        val ctrlRow = LinearLayout(context).apply { orientation = HORIZONTAL }

        val eraserBtn = makeSmallBtn("✕ Erase") {
            selectedPiece = '.'
            refreshPieceSelector()
        }
        val resetBtn = makeSmallBtn("↺ Reset") {
            for (r in 0..7) for (c in 0..7) board[r][c] = startingPiece(r, c)
            boardView.invalidate()
        }
        val sideBtn = makeSmallBtn(if (playerWhite) "▶ White" else "▶ Black") {
            playerWhite = !playerWhite
            (it as Button).text = if (playerWhite) "▶ White" else "▶ Black"
        }

        ctrlRow.addView(eraserBtn, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        ctrlRow.addView(resetBtn,  LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        ctrlRow.addView(sideBtn,   LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(ctrlRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addSpace(4)

        // ── Board Y calibration ───────────────────────────────────────────────
        val calRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val calLabel = TextView(context).apply { text = "Board Y:"; setTextColor(Color.parseColor("#888888")); textSize = 10f }
        val calValue = TextView(context).apply { text = boardScreenY.toString(); setTextColor(Color.WHITE); textSize = 10f; setPadding(6,0,6,0) }
        val calMinus = makeSmallBtn("-10") { boardScreenY -= 10; calValue.text = boardScreenY.toString() }
        val calPlus  = makeSmallBtn("+10") { boardScreenY += 10; calValue.text = boardScreenY.toString() }
        calRow.addView(calLabel); calRow.addView(calMinus); calRow.addView(calValue); calRow.addView(calPlus)
        addView(calRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addSpace(4)

        // ── Analyze button ────────────────────────────────────────────────────
        analyzeBtn = Button(context).apply {
            text = "▶ ANALYZE"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            setPadding(0, 8, 0, 8)
        }
        analyzeBtn.setOnClickListener { analyze() }
        addView(analyzeBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addSpace(4)

        // ── Status ────────────────────────────────────────────────────────────
        statusText = TextView(context).apply {
            text = "Set up board → ANALYZE"
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
        }
        addView(statusText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun analyze() {
        val fen = boardToFen()
        statusText.text = "Analyzing..."
        analyzeBtn.isEnabled = false

        scope.launch {
            val best = withContext(Dispatchers.IO) { engine.bestMove(fen) }
            analyzeBtn.isEnabled = true
            if (best.length < 4) {
                statusText.text = "No move found — check position"
                return@launch
            }

            val from = uciToCell(best.substring(0, 2))
            val to   = uciToCell(best.substring(2, 4))

            // Arrow uses screenW for board size (Chess.com fills full width)
            arrowView.setMove(from, to, 0, boardScreenY, screenW)
            statusText.text = "Best move: $best"
        }
    }

    private fun boardToFen(): String {
        val sb = StringBuilder()
        for (row in board) {
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
        val side = if (playerWhite) "w" else "b"
        return "$sb $side - - 0 1"
    }

    private fun uciToCell(sq: String): Pair<Int, Int> {
        val col = sq[0] - 'a'
        val row = 8 - (sq[1] - '0')
        return Pair(col, row)
    }

    private fun refreshPieceSelector() {
        // Rebuilding the whole view is expensive — just trigger a redraw
        // The highlight updates on next tap; good enough for a panel
        invalidate()
        (getChildAt(2) as? LinearLayout)?.let { row ->
            for (i in 0 until row.childCount) {
                val pieces = listOf('P','N','B','R','Q','K')
                (row.getChildAt(i) as? TextView)?.setBackgroundColor(
                    if (selectedPiece == pieces[i]) Color.parseColor("#00FF88") else Color.TRANSPARENT
                )
            }
        }
        (getChildAt(3) as? LinearLayout)?.let { row ->
            for (i in 0 until row.childCount) {
                val pieces = listOf('p','n','b','r','q','k')
                (row.getChildAt(i) as? TextView)?.setBackgroundColor(
                    if (selectedPiece == pieces[i]) Color.parseColor("#00FF88") else Color.TRANSPARENT
                )
            }
        }
    }

    private fun makeSmallBtn(label: String, onClick: (android.view.View) -> Unit) =
        Button(context).apply {
            text = label
            textSize = 9f
            setPadding(2, 2, 2, 2)
            setBackgroundColor(Color.argb(80, 255, 255, 255))
            setTextColor(Color.WHITE)
            setOnClickListener(onClick)
        }

    private fun addSpace(dp: Int) {
        addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, dp))
    }

    companion object {
        fun startingPiece(row: Int, col: Int): Char = when (row) {
            0 -> listOf('r','n','b','q','k','b','n','r')[col]
            1 -> 'p'
            6 -> 'P'
            7 -> listOf('R','N','B','Q','K','B','N','R')[col]
            else -> '.'
        }
    }
}