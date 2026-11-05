// app/src/main/java/com/chessghost/app/StockfishEngine.kt

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
        private const val DEPTH    = 20
        private const val LIB_NAME = "libstockfish.so"
    }

    fun start() {
        val binary = File(context.applicationInfo.nativeLibraryDir, LIB_NAME)
        check(binary.exists()) {
            "stockfish binary not found at ${binary.absolutePath} — ensure libstockfish.so is in jniLibs/arm64-v8a/"
        }

        process = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()

        writer = PrintWriter(BufferedWriter(OutputStreamWriter(process!!.outputStream)), true)
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
                if (parts.size >= 2 && parts[1] != "(none)") result = parts[1]
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
            writer = null; reader = null; process = null
        }
    }

    private fun send(cmd: String) { writer?.println(cmd) }
}
