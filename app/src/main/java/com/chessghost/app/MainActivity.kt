// app/src/main/java/com/chessghost/app/MainActivity.kt

package com.chessghost.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    private var pendingStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var running by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("♟", fontSize = 56.sp)
                    Text(
                        "ChessGhost",
                        color = Color(0xFF00FF88),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap START — a floating panel appears over Chess.com.\nEnter the FEN or use the panel controls to get the best move arrow.",
                        color = Color(0xFF666666),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            running = !running
                            if (running) requestAndStart() else stopService(Intent(this@MainActivity, FloatingService::class.java))
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) Color(0xFFFF4444) else Color(0xFF00FF88)
                        ),
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Text(
                            if (running) "■  STOP GHOST" else "▶  START GHOST",
                            color = Color(0xFF0D0D0D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    if (running) {
                        Text("● Floating panel is active", color = Color(0xFF00FF88), fontSize = 12.sp)
                        Text("Switch to Chess.com — panel floats on top", color = Color(0xFF444444), fontSize = 11.sp)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false
            startForegroundService(Intent(this, FloatingService::class.java))
        }
    }

    private fun requestAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, FloatingService::class.java))
    }
}