package com.instantcollabmaker.data.pipeline

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * [DIAGNOSTIC-ONLY] Entire file. Added solely for the "why doesn't the final
 * unique-person count match expectations" investigation — delete this file and every
 * reference to [DiagnosticFrameLogger] in [RealVideoProcessor] and [com.instantcollabmaker.data.embedding.TfLiteFaceEmbedder]
 * once that investigation is closed.
 *
 * Writes one complete, chronological, human-readable line per frame/face/identity
 * decision to a plain text file — not just Logcat — because Logcat is easy to miss
 * entirely (wrong tag filter, wrong device selected in a multi-device setup, buffer
 * rotated by the time anyone looks, `adb logcat` never actually run alongside the app).
 * A file can just be pulled and opened in any text editor.
 *
 * **File location**: app-specific external storage, no permission needed on API 26+:
 * ```
 * /sdcard/Android/data/com.instantcollabmaker/files/diagnostics/pipeline_log.txt
 * ```
 * Pull it with:
 * ```
 * adb pull /sdcard/Android/data/com.instantcollabmaker/files/diagnostics/pipeline_log.txt
 * ```
 * A fresh file is written (not appended) at the start of every single analysis run, so
 * it always reflects only the most recent video processed. Every line is also echoed to
 * Logcat under the tag "PipelineDiagnostics" as a second way to see the same data live.
 */
class DiagnosticFrameLogger(context: Context, private val enabled: Boolean) {

    private val file: File? = if (enabled) {
        val dir = (context.getExternalFilesDir("diagnostics") ?: File(context.cacheDir, "diagnostics"))
            .apply { mkdirs() }
        File(dir, "pipeline_log.txt")
    } else {
        null
    }

    private val writer: BufferedWriter? = file?.let {
        runCatching { BufferedWriter(FileWriter(it, false)) }.getOrNull()
    }

    init {
        if (enabled && file != null) {
            Log.d(TAG, "Full per-frame diagnostic log will be written to: ${file.absolutePath}")
            line("=== GAME OF FRAMES PIPELINE LOG — run started ${System.currentTimeMillis()} ===")
        }
    }

    /** Writes one line to both the file and Logcat. A complete no-op when not [enabled],
     * so this never touches disk or Logcat in a release build. */
    fun line(text: String) {
        if (!enabled) return
        Log.d(TAG, text)
        try {
            writer?.write(text)
            writer?.newLine()
        } catch (_: Exception) {
            // Diagnostics must never be able to crash real processing.
        }
    }

    fun close() {
        if (!enabled) return
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "PipelineDiagnostics"
    }
}
