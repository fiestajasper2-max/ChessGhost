// app/src/main/java/com/chessghost/app/FloatingService.kt

package com.chessghost.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var arrowView: ArrowOverlayView
    private lateinit var engine: StockfishEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Panel drag state
    private var dragStartX = 0f; private var dragStartY = 0f
    private var panelStartX = 0; private var panelStartY = 0

    companion object {
        private const val NOTIF_CHANNEL = "chessghost_float"
        private const val NOTIF_ID = 43
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        engine = StockfishEngine(this).also {
            scope.launch(Dispatchers.IO) { it.start() }
        }

        setupArrowOverlay()
        setupFloatingPanel()
    }

    // ── Full-screen transparent arrow layer ───────────────────────────────────

    private fun setupArrowOverlay() {
        arrowView = ArrowOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(arrowView, params)
    }

    // ── Draggable floating panel ──────────────────────────────────────────────

    private fun setupFloatingPanel() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16; y = 200
        }

        // Drag handle
        floatingView.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX; dragStartY = event.rawY
                    panelStartX = params.x;  panelStartY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = panelStartX + (event.rawX - dragStartX).toInt()
                    params.y = panelStartY + (event.rawY - dragStartY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                }
            }
            true
        }

        // FEN input
        val fenInput = floatingView.findViewById<EditText>(R.id.fenInput)
        val statusText = floatingView.findViewById<TextView>(R.id.statusText)
        val analyzeBtn = floatingView.findViewById<Button>(R.id.analyzeBtn)
        val clearBtn = floatingView.findViewById<Button>(R.id.clearBtn)

        // Board position controls
        val boardXInput = floatingView.findViewById<EditText>(R.id.boardX)
        val boardYInput = floatingView.findViewById<EditText>(R.id.boardY)
        val boardSizeInput = floatingView.findViewById<EditText>(R.id.boardSize)

        // Set defaults based on common Chess.com mobile layout (720px wide screen)
        // Sam can adjust these if his phone is different
        boardXInput.setText("0")
        boardYInput.setText("520")
        boardSizeInput.setText("720")

        analyzeBtn.setOnClickListener {
            val fen = fenInput.text.toString().trim()
            if (fen.isEmpty()) {
                statusText.text = "Enter FEN first"
                return@setOnClickListener
            }
            val bx = boardXInput.text.toString().toIntOrNull() ?: 0
            val by = boardYInput.text.toString().toIntOrNull() ?: 520
            val bs = boardSizeInput.text.toString().toIntOrNull() ?: 720

            statusText.text = "Analyzing..."
            analyzeBtn.isEnabled = false

            scope.launch {
                val best = withContext(Dispatchers.IO) { engine.bestMove(fen) }
                analyzeBtn.isEnabled = true
                if (best.length < 4) {
                    statusText.text = "No move found"
                    return@launch
                }
                val from = uciToCell(best.substring(0, 2))
                val to   = uciToCell(best.substring(2, 4))
                arrowView.setMove(from, to, bx, by, bs)
                statusText.text = "Best: $best"
            }
        }

        clearBtn.setOnClickListener {
            arrowView.clear()
            statusText.text = "Ready"
        }

        // Allow keyboard submit
        fenInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                analyzeBtn.performClick(); true
            } else false
        }

        windowManager.addView(floatingView, params)
    }

    private fun uciToCell(sq: String): Pair<Int, Int> {
        val col = sq[0] - 'a'
        val row = 8 - (sq[1] - '0')
        return Pair(col, row)
    }

    override fun onDestroy() {
        scope.cancel()
        engine.stop()
        try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        try { windowManager.removeView(arrowView) }    catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIF_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "ChessGhost", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("ChessGhost Active")
            .setContentText("Floating panel running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}