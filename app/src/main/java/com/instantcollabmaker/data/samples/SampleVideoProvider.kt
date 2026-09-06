package com.instantcollabmaker.data.samples

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** One bundled sample clip offered from the home screen's "Choose From Sample Videos" sheet. */
data class SampleVideo(
    val id: String,
    val assetFileName: String,
    val title: String,
    val subtitle: String,
)

/**
 * Ships the assignment's own sample clips *inside the app* (as ordinary assets, not a
 * network download — this app makes no network calls, full stop) so a reviewer can test
 * the real pipeline in one tap without first hunting down a portrait video of their own.
 *
 * Each clip still goes through the exact same [com.instantcollabmaker.domain.processing.VideoProcessor]
 * as any video picked from the system picker — nothing about a sample clip is special-cased
 * in the analysis pipeline itself; this class only exists to get its bytes out of the APK
 * and into a real [Uri] the rest of the app already knows how to read.
 */
class SampleVideoProvider(private val context: Context) {

    val samples: List<SampleVideo> = listOf(
        SampleVideo("sample_1", "sample_videos/sample1.mp4", "Sample 1", "Portrait clip · ~30s"),
        SampleVideo("sample_2", "sample_videos/sample2.mp4", "Sample 2", "Portrait clip · ~30s"),
        SampleVideo("sample_3", "sample_videos/sample3.mp4", "Sample 3", "Portrait clip · ~30s"),
    )

    /**
     * Copies [sample]'s asset into app cache on first use (assets aren't directly
     * addressable by [MediaMetadataRetriever][android.media.MediaMetadataRetriever] via a
     * plain file Uri) and returns a `file://` Uri to that cached copy. Returns `null` if
     * the asset is missing or unreadable — callers should fail soft (a toast), never crash.
     */
    suspend fun resolveUri(sample: SampleVideo): Uri? = withContext(Dispatchers.IO) {
        val cached = File(context.cacheDir, "sample_videos/${sample.id}.mp4")
        if (!cached.exists()) {
            cached.parentFile?.mkdirs()
            try {
                context.assets.open(sample.assetFileName).use { input ->
                    FileOutputStream(cached).use { output -> input.copyTo(output) }
                }
            } catch (_: IOException) {
                return@withContext null
            }
        }
        Uri.fromFile(cached)
    }
}
