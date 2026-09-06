package com.instantcollabmaker.domain.model

import android.net.Uri

/** Everything the UI needs to describe the video the user picked. */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
) {
    val hasResolution: Boolean get() = width > 0 && height > 0
    val hasSize: Boolean get() = sizeBytes > 0
    val isPortrait: Boolean get() = hasResolution && height >= width

    val aspectRatio: Float
        get() = if (hasResolution) width.toFloat() / height.toFloat() else 9f / 16f
}
