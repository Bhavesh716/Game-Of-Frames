package com.instantcollabmaker.domain.model

/**
 * The best-frame scoring breakdown. Each component is normalised to 0f..1f.
 *
 * Phase 1 supplies these from mock data; Phase 2's `QualityScorer` computes them from
 * ML Kit landmarks/classification plus a sharpness measure, and nothing in the UI has
 * to change because the shape is already correct.
 */
data class FrameQuality(
    val frontality: Float,
    val sharpness: Float,
    val eyesOpen: Float,
    val expression: Float,
    val visibility: Float,
) {
    /**
     * Weighted aggregate used for ranking frames within an appearance. Weights favour
     * a clean, front-facing, in-focus face over a merely smiling one.
     */
    val overall: Float
        get() = (
            frontality * 0.28f +
                sharpness * 0.24f +
                eyesOpen * 0.18f +
                expression * 0.14f +
                visibility * 0.16f
            ).coerceIn(0f, 1f)

    companion object {
        val Unknown = FrameQuality(0f, 0f, 0f, 0f, 0f)
    }
}
