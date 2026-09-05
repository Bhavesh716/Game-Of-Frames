package com.instantcollabmaker.core

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Every duration, timecode and byte count in the app is formatted here, so the same
 * value never appears in two different shapes on two different screens.
 */
object Format {

    /** Clip length as `mm:ss`, e.g. `00:30`. Rolls into `h:mm:ss` past an hour. */
    fun clock(millis: Long): String {
        val total = (millis / 1000.0).roundToLong().coerceAtLeast(0L)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** Precise in-video position as `mm:ss.d`, e.g. `00:01.2`. */
    fun timecode(millis: Long): String {
        val clamped = millis.coerceAtLeast(0L)
        val minutes = clamped / 60_000
        val seconds = (clamped % 60_000) / 1000
        val tenths = (clamped % 1000) / 100
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }

    /** A span between two positions, e.g. `00:01.2 — 00:04.1`. */
    fun timeRange(startMs: Long, endMs: Long): String =
        timecode(startMs) + " — " + timecode(endMs)

    /** Screen time with one decimal, e.g. `12.8 sec`. */
    fun seconds(millis: Long): String =
        String.format(Locale.US, "%.1f sec", millis.coerceAtLeast(0L) / 1000f)

    /** Bare one-decimal seconds without a unit, for tight metadata rows. */
    fun secondsBare(millis: Long): String =
        String.format(Locale.US, "%.1fs", millis.coerceAtLeast(0L) / 1000f)

    fun resolution(width: Int, height: Int): String = "" + width + " × " + height

    fun fileSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val mb = bytes / 1_048_576.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        }
    }

    fun percent(fraction: Float): String =
        "" + (fraction.coerceIn(0f, 1f) * 100f).toInt() + "%"

    /** Pluralises a count, e.g. `4 appearances` / `1 appearance`. */
    fun count(value: Int, singular: String, plural: String = singular + "s"): String =
        "" + value + " " + (if (value == 1) singular else plural)
}
