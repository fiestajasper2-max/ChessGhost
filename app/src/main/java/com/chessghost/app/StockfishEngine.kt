// Kotlin · com/chessghost/app/StockfishEngine.kt · Android API 21+
// Fixes:
//   1. OverlayService.kt:99 "Unresolved reference: bestMove" — function was missing entirely
//   2. error=13 Permission denied — binary never chmod'd after asset copy

package com.chessghost.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class StockfishEngine(private val context: Context) {

    private var process: Process? = null
    private var reader: BufferedReader? = null

    // ── Binary path ───────────────────────────────────────────────────────────
    private val stockfishBinary: File
        get() = File(context.filesDir, "stockfish")

    // ── Setup ─────────────────────────────────────────────────────────────────

    /**
     * Copies the stockfish binary from assets/stockfish and marks it executable.
     * Idempotent — safe to call every time the service starts.
     *
     * Asset name must match what ships in app/src/main/assets/.
     * For ABI-split APKs use Build.SUPPORTED_ABIS[0] to pick the right binary.
     */
    private fun copyAndPrepare() {
        val dest = stockfishBinary
        if (!dest.exists()) {
            context.assets.open("stockfish").use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
        // THIS was the original runtime crash — file copied as 0644, not executable
        if (!dest.canExecute()) {
            val ok = dest.setExecutable(true, false)
            if (!ok) {
                // Fallback for hardened MIUI/SELinux builds where setExecutable returns false
                Runtime.getRuntime().exec(arrayOf("chmod", "755", dest.absolutePath)).waitFor()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        copyAndPrepare()
        process = ProcessBuilder(stockfishBinary.absolutePath)
            .redirectErrorStream(true)
            .start()
        reader = BufferedReader(InputStreamReader(process!!.inputStream))
        sendCommand("uci")
        waitFor("uciok")
        sendCommand("isready")
        waitFor("readyok")
    }

    fun stop() {
        try {
            sendCommand("quit")
        } catch (_: Exception) {}
        reader?.close()
        process?.destroy()
        reader  = null
        process = null
    }

    // ── Core UCI I/O ──────────────────────────────────────────────────────────

    private fun sendCommand(cmd: String) {
        process?.outputStream?.bufferedWriter()?.apply {
            write(cmd)
            newLine()
            flush()
        }
    }

    /** Drain lines until one starts with [token], return that line. */
    private fun waitFor(token: String): String {
        while (true) {
            val line = reader?.readLine() ?: return ""
            if (line.startsWith(token)) return line
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ask Stockfish for the best move given a FEN position.
     *
     * @param fen     Full FEN string of the current board position.
     * @param moveTimeMs Engine think time in milliseconds (default 1 500 ms).
     * @return Best move in UCI notation (e.g. "e2e4"), or null if engine failed.
     *
     * This is the function OverlayService.kt:99 was calling — it was missing.
     */
    fun bestMove(fen: String, moveTimeMs: Int = 1500): String? {
        if (process == null || process?.isAlive == false) return null

        sendCommand("position fen $fen")
        sendCommand("go movetime $moveTimeMs")

        // Stockfish always terminates analysis with "bestmove <move> [ponder <move>]"
        val line = waitFor("bestmove")
        // "bestmove e2e4 ponder e7e5"  →  tokens[1] = "e2e4"
        val tokens = line.trim().split(" ")
        return if (tokens.size >= 2 && tokens[1] != "(none)") tokens[1] else null
    }

    /**
     * Variant: best move from moves list (no FEN needed if you track moves).
     * Sends "position startpos moves e2e4 e7e5 ..."
     */
    fun bestMoveFromMoves(moves: List<String>, moveTimeMs: Int = 1500): String? {
        if (process == null || process?.isAlive == false) return null

        val movesStr = if (moves.isEmpty()) "" else "moves ${moves.joinToString(" ")}"
        sendCommand("position startpos $movesStr")
        sendCommand("go movetime $moveTimeMs")

        val line = waitFor("bestmove")
        val tokens = line.trim().split(" ")
        return if (tokens.size >= 2 && tokens[1] != "(none)") tokens[1] else null
    }
}