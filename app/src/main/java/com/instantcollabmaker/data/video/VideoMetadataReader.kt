package com.instantcollabmaker.data.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.instantcollabmaker.domain.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads display name, duration, resolution and size for a picked video.
 *
 * Metadata only — no frame decoding, no analysis. [FrameExtractor] reuses the same
 * duration/resolution values this produces.
 */
interface VideoMetadataReader {
    suspend fun read(uri: Uri): VideoInfo
}

class AndroidVideoMetadataReader(
    private val context: Context,
) : VideoMetadataReader {

    override suspend fun read(uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val (nameFromProvider, sizeFromProvider) = queryOpenableColumns(uri)
        var durationMs = 0L
        var width = 0
        var height = 0
        var rotation = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            rotation = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        } catch (_: Exception) {
            // Unreadable or non-video input: fall through with whatever we have. The
            // processing stage surfaces this as a proper error state.
        } finally {
            runCatching { retriever.release() }
        }

        // Phones report portrait captures as landscape plus a rotation flag.
        val swap = rotation == 90 || rotation == 270
        val displayWidth = if (swap) height else width
        val displayHeight = if (swap) width else height

        VideoInfo(
            uri = uri,
            displayName = nameFromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Selected video",
            durationMs = durationMs,
            width = displayWidth,
            height = displayHeight,
            sizeBytes = sizeFromProvider,
        )
    }

    private fun queryOpenableColumns(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { size = cursor.getLong(it) }
                }
            }
        }
        return name to size
    }

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long =
        extractMetadata(key)?.toLongOrNull() ?: 0L

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0
}
