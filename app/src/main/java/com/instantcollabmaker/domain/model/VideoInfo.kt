package com.instantcollabmaker.domain.model

import android.net.Uri

/**
 * Everything the UI needs to describe the input video, independent of how it was
 * obtained (gallery pick, bundled sample, or — in Phase 2 — anything else).
 */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** True when this came from the bundled demo entry rather than the user gallery. */
    val isSample: Boolean = false,
) {
    val hasResolution: Boolean get() = width > 0 && height > 0
    val hasSize: Boolean get() = sizeBytes > 0
    val isPortrait: Boolean get() = hasResolution && height >= width

    val aspectRatio: Float
        get() = if (hasResolution) width.toFloat() / height.toFloat() else 9f / 16f
}
