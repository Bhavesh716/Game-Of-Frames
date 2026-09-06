package com.instantcollabmaker.domain.model

/**
 * A single frame that the pipeline chose to keep — the representative shot for an
 * appearance, or a person's overall hero frame.
 */
data class SelectedFrame(
    val id: String,
    /** Position of this frame within the source video. */
    val timestampMs: Long,
    /** Index of the analysis-pass sampled frame this came from, useful for debugging. */
    val frameIndex: Int,
    val image: FrameImage,
    val quality: FrameQuality,
) {
    val qualityScore: Float get() = quality.overall
}
