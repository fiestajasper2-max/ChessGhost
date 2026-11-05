// Kotlin · StockfishEngine.kt · Android API 21+
// error=13 → binary copied to filesDir but never marked executable
// fix: chmod +x after copy, before ProcessBuilder.start()

package com.chessghost.app

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class StockfishEngine(private val context: Context) {

    private var process: Process? = null
    private val stockfishBinary: File
        get() = File(context.filesDir, "stockfish")

    /**
     * Call once on first launch (or on every launch — idempotent).
     * Copies the binary from assets and sets executable bit.
     *
     * Assumption: stockfish binary lives at assets/stockfish (arm64-v8a or
     * per-ABI — swap the asset name to match what you ship).
     */
    fun copyAndPrepare() {
        val dest = stockfishBinary
        if (!dest.exists()) {
            context.assets.open("stockfish").use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
        // THIS is the line that was missing — error=13 dies here without it
        if (!dest.canExecute()) {
            dest.setExecutable(true, false)   // owner + group + other
        }
    }

    /** Line 23 equivalent — now safe to call after copyAndPrepare() */
    fun start() {
        copyAndPrepare()   // ← guard: no-op if already executable

        process = ProcessBuilder(stockfishBinary.absolutePath)
            .redirectErrorStream(true)
            .start()
    }

    fun sendCommand(cmd: String) {
        process?.outputStream?.bufferedWriter()?.apply {
            write(cmd)
            newLine()
            flush()
        }
    }

    fun readLine(): String? =
        process?.inputStream?.bufferedReader()?.readLine()

    fun stop() {
        process?.apply {
            outputStream?.bufferedWriter()?.apply {
                write("quit")
                newLine()
                flush()
            }
            destroy()
        }
        process = null
    }
}