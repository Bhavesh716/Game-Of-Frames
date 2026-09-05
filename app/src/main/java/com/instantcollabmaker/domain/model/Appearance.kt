package com.instantcollabmaker.domain.model

/**
 * One continuous on-screen stretch for a person, produced by the temporal appearance
 * tracker. A person can have many of these; the data model never assumes one.
 */
data class Appearance(
    val id: String,
    val personId: String,
    /** 1-based ordinal within the owning person, for display as "Appearance 01". */
    val index: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val bestFrameTimestampMs: Long,
    val bestFrame: SelectedFrame,
    /** Number of sampled frames in which this person was actually detected. */
    val detectedFrameCount: Int = 0,
) {
    val durationMs: Long get() = (endTimestampMs - startTimestampMs).coerceAtLeast(0L)
    val qualityScore: Float get() = bestFrame.qualityScore
}
