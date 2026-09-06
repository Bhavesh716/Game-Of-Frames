package com.instantcollabmaker.domain.model

/**
 * Coarse evidence tier derived from [FrameQuality]. This is the mechanism the identity
 * pipeline uses to stop a bad observation from ever being treated the same as a good one:
 * it governs how much *authority* an observation has to create a brand-new identity, and
 * how much evidence is required for it to attach to an existing one. It does **not** gate
 * whether a face can match an existing person at all — a LOW-quality glimpse can and
 * should still resolve to a person we already know, just with a higher bar.
 */
enum class QualityTier { HIGH, MEDIUM, LOW }

/**
 * The best-frame scoring breakdown. Each component is normalised to 0f..1f.
 *
 * Computed per detected face by `HeuristicQualityScorer` from ML Kit head-pose/eye/smile
 * classification plus a Laplacian-variance sharpness measure over the face crop.
 */
data class FrameQuality(
    val frontality: Float,
    val sharpness: Float,
    val eyesOpen: Float,
    val expression: Float,
    val visibility: Float,
    /**
     * 0f (well inside frame) .. 1f (bounding box sits exactly on the frame edge) — how
     * badly this face is cut off by the frame boundary, e.g. a selfie-mode shot where the
     * subject drifts toward the edge. Already folded into [visibility] as a penalty, but
     * kept as its own field because identity matching needs it directly (see [tier]),
     * not just as a component of one blended score.
     */
    val clippingRatio: Float = 0f,
) {
    /**
     * Weighted aggregate used for ranking frames within an appearance. Frontality, eyes
     * and sharpness dominate so a sharp, open-eyed, in-focus face beats a merely smiling
     * one; expression and visibility are smaller bonuses.
     */
    val overall: Float
        get() = (
            frontality * 0.30f +
                eyesOpen * 0.25f +
                sharpness * 0.25f +
                expression * 0.10f +
                visibility * 0.10f
            ).coerceIn(0f, 1f)

    /**
     * A heavily clipped face is always LOW regardless of its other scores — a partial
     * face near the frame edge is exactly the case that must never get to unilaterally
     * create a new person (see `GlobalIdentityMatcher`). Otherwise HIGH/MEDIUM/LOW is a
     * simple banding of [overall], with a slightly tighter clipping allowance for HIGH so
     * a merely-decent-but-still-somewhat-clipped face can't count as top-tier evidence.
     */
    val tier: QualityTier
        get() = when {
            clippingRatio >= 0.60f -> QualityTier.LOW
            overall >= 0.58f && clippingRatio <= 0.20f -> QualityTier.HIGH
            overall >= 0.36f -> QualityTier.MEDIUM
            else -> QualityTier.LOW
        }

    companion object {
        val Unknown = FrameQuality(0f, 0f, 0f, 0f, 0f, 0f)
    }
}
