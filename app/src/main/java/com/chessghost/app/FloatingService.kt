// app/src/main/java/com/chessghost/app/FloatingService.kt

package com.chessghost.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var panelView: FloatingPanelView
    private lateinit var arrowView: ArrowOverlayView
    private lateinit var engine: StockfishEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val NOTIF_CHANNEL = "chessghost_float"
        private const val NOTIF_ID = 43
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        engine = StockfishEngine(this)
        scope.launch(Dispatchers.IO) { engine.start() }
        setupArrowOverlay()
        setupPanel()
    }

    private fun setupArrowOverlay() {
        arrowView = ArrowOverlayView(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(arrowView, p)
    }

    private fun setupPanel() {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels

        panelView = FloatingPanelView(this, screenW, engine, scope, arrowView)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 0; y = 300 }

        panelView.setOnDragListener { dx, dy ->
            p.x += dx; p.y += dy
            windowManager.updateViewLayout(panelView, p)
        }

        windowManager.addView(panelView, p)
    }

    override fun onDestroy() {
        scope.cancel()
        engine.stop()
        try { windowManager.removeView(panelView) } catch (_: Exception) {}
        try { windowManager.removeView(arrowView) } catch (_: Exception) {}
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
            .setContentText("Tap pieces → get best move arrow")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}