package com.instantcollabmaker.domain.model

/**
 * A unique identity discovered in the video — one global identity produced by
 * `GlobalIdentityMatcher` and enriched with the appearances `SetDiffAppearanceTracker`
 * found for it.
 */
data class Person(
    val id: String,
    val displayName: String,
    /** Ordinal in the result set, used to pick a stable per-person accent colour. */
    val index: Int,
    val representativeFrame: SelectedFrame,
    val appearances: List<Appearance>,
    /** Mean intra-cluster similarity, surfaced as an identity-confidence readout. */
    val identityConfidence: Float,
) {
    val appearanceCount: Int get() = appearances.size

    /** Total visible screen time, summed across all appearances. */
    val totalScreenTimeMs: Long get() = appearances.sumOf { it.durationMs }
}
