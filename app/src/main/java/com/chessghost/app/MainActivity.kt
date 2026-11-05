package com.chessghost.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var pendingStart = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            startForegroundService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessGhostUI(
                onStart = { requestPermissionsAndStart() },
                onStop = { stopService(Intent(this, OverlayService::class.java)) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun requestPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}

@Composable
fun ChessGhostUI(onStart: () -> Unit, onStop: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var playerWhite by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {

            Text(
                text = "♟",
                fontSize = 56.sp
            )

            Text(
                text = "ChessGhost",
                color = Color(0xFF00FF88),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Invisible Stockfish overlay\nfor Chess.com rated games",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Player colour toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Playing as:", color = Color(0xFF888888), fontSize = 13.sp)
                Button(
                    onClick = {
                        playerWhite = !playerWhite
                        BoardParser.setPlayerColor(playerWhite)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playerWhite) Color(0xFFF0D9B5) else Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (playerWhite) "⬜ White" else "⬛ Black",
                        color = if (playerWhite) Color(0xFF0D0D0D) else Color(0xFFFFFFFF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Start / Stop button
            Button(
                onClick = {
                    running = !running
                    if (running) onStart() else onStop()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Color(0xFFFF4444) else Color(0xFF00FF88)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    text = if (running) "■  STOP GHOST" else "▶  START GHOST",
                    color = Color(0xFF0D0D0D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            if (running) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "● Engine active  ·  depth 20  ·  500ms refresh",
                    color = Color(0xFF00FF88),
                    fontSize = 11.sp
                )
                Text(
                    text = "Green arrow = best move",
                    color = Color(0xFF444444),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Keep Chess.com visible on screen.\nArrow draws over the board automatically.",
                color = Color(0xFF444444),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}