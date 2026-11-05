// Kotlin · com/chessghost/app/OverlayService.kt · unchanged except lines 99-103
// No other logic changed — only the null-safety guard on `best`

package com.chessghost.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var engine: StockfishEngine
    private lateinit var overlay: MoveOverlay
    private lateinit var projection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler  = Handler(Looper.getMainLooper())

    companion object {
        private const val NOTIF_CHANNEL = "chessghost_channel"
        private const val NOTIF_ID      = 42
        private const val REFRESH_MS    = 500L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data")
            ?: return START_NOT_STICKY

        val projManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projManager.getMediaProjection(resultCode, data)

        engine  = StockfishEngine(this).also { it.start() }
        overlay = MoveOverlay(this).also { it.show() }

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        imageReader = ImageReader.newInstance(
            screenW, screenH, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = projection.createVirtualDisplay(
            "ChessGhostVD",
            screenW, screenH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            mainHandler
        )

        startAnalysisLoop(screenW, screenH)
        return START_NOT_STICKY
    }

    private fun startAnalysisLoop(screenW: Int, screenH: Int) {
        serviceScope.launch {
            while (isActive) {
                delay(REFRESH_MS)

                val image = imageReader.acquireLatestImage()
                if (image == null) continue

                val bitmap: Bitmap
                try {
                    val plane     = image.planes[0]
                    val buffer    = plane.buffer
                    val rowStride = plane.rowStride
                    val pixStride = plane.pixelStride
                    val padding   = rowStride - pixStride * image.width

                    bitmap = Bitmap.createBitmap(
                        image.width + padding / pixStride,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                } finally {
                    image.close()
                }

                val fen = BoardParser.parse(bitmap)
                bitmap.recycle()
                if (fen == null) continue

                // ── THE FIX: bestMove returns String (non-null); guard length anyway ──
                val best = engine.bestMove(fen)
                if (best.length < 4) continue          // "" or "(none)" → skip

                val from = uciToCell(best.substring(0, 2))
                val to   = uciToCell(best.substring(2, 4))

                withContext(Dispatchers.Main) {
                    overlay.drawMove(from, to, screenW, screenH)
                }
            }
        }
    }

    private fun uciToCell(sq: String): Pair<Int, Int> {
        val col = sq[0] - 'a'
        val row = 8 - (sq[1] - '0')
        return Pair(col, row)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlay.hide()
        engine.stop()
        try {
            virtualDisplay.release()
            projection.stop()
            imageReader.close()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIF_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL,
                    "ChessGhost",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("ChessGhost Running")
            .setContentText("Engine active — tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}