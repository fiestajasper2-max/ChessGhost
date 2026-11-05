// Kotlin · com/chessghost/app/StockfishEngine.kt · Android API 21+
// bestMove returns String (non-null, "" on failure) — matches OverlayService usage
// chmod fix included — was the original runtime error=13

package com.chessghost.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

class StockfishEngine(private val context: Context) {

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    companion object {
        private const val DEPTH       = 20
        private const val BINARY_NAME = "stockfish"
    }

    fun start() {
        val binary = copyBinaryIfNeeded()
        process = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()

        writer = PrintWriter(
            BufferedWriter(OutputStreamWriter(process!!.outputStream)), true
        )
        reader = BufferedReader(InputStreamReader(process!!.inputStream))

        send("uci")
        send("setoption name Threads value 2")
        send("setoption name Hash value 64")
        send("isready")

        var line: String?
        while (reader!!.readLine().also { line = it } != null) {
            if (line == "readyok") break
        }
    }

    // Returns "" on failure — OverlayService checks length < 4 to skip bad results
    suspend fun bestMove(fen: String): String = withContext(Dispatchers.IO) {
        if (process == null || writer == null || reader == null) return@withContext ""

        send("ucinewgame")
        send("position fen $fen")
        send("go depth $DEPTH")

        var result = ""
        var line: String?
        while (reader!!.readLine().also { line = it } != null) {
            if (line!!.startsWith("bestmove")) {
                val parts = line!!.split(" ")
                if (parts.size >= 2 && parts[1] != "(none)") {
                    result = parts[1]
                }
                break
            }
        }
        result
    }

    fun stop() {
        try {
            send("quit")
            process?.waitFor()
        } catch (_: Exception) {
        } finally {
            writer?.close()
            reader?.close()
            process?.destroy()
            writer = null
            reader = null
            process = null
        }
    }

    private fun send(cmd: String) {
        writer?.println(cmd)
    }

    private fun copyBinaryIfNeeded(): File {
        val dest = File(context.filesDir, BINARY_NAME)
        if (!dest.exists()) {
            context.assets.open(BINARY_NAME).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        // setExecutable(true, false) = owner+group+other; fixes runtime error=13
        if (!dest.canExecute()) {
            val ok = dest.setExecutable(true, false)
            if (!ok) {
                // fallback for hardened MIUI / SELinux builds
                Runtime.getRuntime().exec(arrayOf("chmod", "755", dest.absolutePath)).waitFor()
            }
        }
        return dest
    }
}