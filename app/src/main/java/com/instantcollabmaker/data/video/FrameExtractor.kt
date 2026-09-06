package com.instantcollabmaker.data.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pulls individual frames out of a video on demand, without ever holding the whole clip
 * (or more than one frame) in memory. Backed by [MediaMetadataRetriever], which the
 * assignment explicitly allows as "an appropriate Android-native frame extraction
 * mechanism" — a full [MediaCodec]-based decoder would be faster but is a large amount of
 * additional surface area for a task where correctness matters far more than shaving
 * seconds off a background analysis pass.
 *
 * One instance is opened per video and reused for every sampled timestamp during the
 * analysis pass, then reused again afterwards to retrieve the handful of winning frames —
 * satisfying "do not run a second complete video analysis pass" without needing a second
 * decoder.
 */
class FrameExtractor(private val context: Context) {

    private var retriever: MediaMetadataRetriever? = null

    fun open(uri: Uri): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            retriever = r
            true
        } catch (_: Exception) {
            runCatching { r.release() }
            false
        }
    }

    /**
     * Decodes the frame nearest [timestampMs], downscaled so its longer edge is at most
     * [maxDimensionPx]. Used at analysis resolution (small, fast) during the sampling
     * pass, and again at a larger [maxDimensionPx] to retrieve a crisp winning frame for
     * the collage.
     */
    suspend fun frameAt(timestampMs: Long, maxDimensionPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val r = retriever ?: return@withContext null
        val timeUs = timestampMs * 1000L
        val raw = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                r.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    maxDimensionPx,
                    maxDimensionPx,
                )
            } else {
                r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Already scaled by the platform.
            raw
        } else {
            downscale(raw, maxDimensionPx)
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimensionPx) return bitmap
        val scale = maxDimensionPx.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun release() {
        runCatching { retriever?.release() }
        retriever = null
    }

    companion object {
        /** Resolution the detector/quality scorer run against — plenty for ML Kit + Laplacian sharpness. */
        const val ANALYSIS_MAX_DIMENSION_PX = 640

        /** Resolution winning frames are retrieved at for the collage — crisp but bounded. */
        const val EXPORT_MAX_DIMENSION_PX = 1440

        /**
         * The single deterministic sampling rate for the entire pipeline: detection,
         * embedding/identity matching AND beauty scoring all run on this exact same
         * timeline for every sampled frame — never separate cadences for different
         * stages. See `RealVideoProcessor`'s class doc for why that matters.
         */
        const val ANALYSIS_FPS = 10f

        /** Hard bounds so a very long or very short clip still analyzes in a reasonable time. */
        private const val MIN_ANALYSIS_FRAMES = 24
        private const val MAX_ANALYSIS_FRAMES = 400

        /**
         * Sampled timestamps for the analysis pass, in milliseconds. Targets
         * [ANALYSIS_FPS], but is clamped to [MIN_ANALYSIS_FRAMES]..[MAX_ANALYSIS_FRAMES]
         * total frames so a five-minute clip does not turn into a fifty-minute analysis,
         * and a two-second clip still gets a usable number of samples.
         */
        fun analysisTimestampsMs(durationMs: Long): List<Long> {
            if (durationMs <= 0L) return emptyList()
            val naiveCount = (durationMs / 1000.0 * ANALYSIS_FPS).roundToInt()
            val targetCount = naiveCount.coerceIn(MIN_ANALYSIS_FRAMES, MAX_ANALYSIS_FRAMES)
            val stepMs = (durationMs.toDouble() / targetCount).coerceAtLeast(1.0)
            val out = ArrayList<Long>(targetCount)
            var t = 0.0
            while (t < durationMs && out.size < targetCount) {
                out.add(t.roundToLong())
                t += stepMs
            }
            return out
        }
    }
}
