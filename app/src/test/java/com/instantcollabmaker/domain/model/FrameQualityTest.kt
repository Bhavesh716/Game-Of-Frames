package com.instantcollabmaker.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the assignment's explicit quality-ranking requirements: a smiling face must
 * not automatically beat a sharp, open-eyed, frontal one, and eyes-closed-but-smiling
 * must not outscore a sharp neutral frontal frame.
 */
class FrameQualityTest {

    @Test
    fun `sharp open-eyed neutral frontal frame beats blurry smiling frame`() {
        val sharpNeutral = FrameQuality(frontality = 0.95f, sharpness = 0.90f, eyesOpen = 0.95f, expression = 0.10f, visibility = 0.90f)
        val blurrySmiling = FrameQuality(frontality = 0.60f, sharpness = 0.25f, eyesOpen = 0.70f, expression = 0.98f, visibility = 0.70f)
        assertTrue(sharpNeutral.overall > blurrySmiling.overall)
    }

    @Test
    fun `closed eyes are not compensated for by a high smile score`() {
        val eyesClosedSmiling = FrameQuality(frontality = 0.9f, sharpness = 0.9f, eyesOpen = 0.05f, expression = 0.98f, visibility = 0.9f)
        val eyesOpenNeutral = FrameQuality(frontality = 0.9f, sharpness = 0.9f, eyesOpen = 0.95f, expression = 0.5f, visibility = 0.9f)
        assertTrue(eyesOpenNeutral.overall > eyesClosedSmiling.overall)
    }

    @Test
    fun `a clipped, tiny, off-angle face scores far below a clean full-face shot`() {
        val poor = FrameQuality(frontality = 0.2f, sharpness = 0.3f, eyesOpen = 0.5f, expression = 0.5f, visibility = 0.15f)
        val good = FrameQuality(frontality = 0.9f, sharpness = 0.85f, eyesOpen = 0.9f, expression = 0.5f, visibility = 0.95f)
        assertTrue(good.overall - poor.overall > 0.4f)
    }

    @Test
    fun `overall is always clamped to the 0f to 1f range`() {
        val maxed = FrameQuality(1f, 1f, 1f, 1f, 1f)
        val zeroed = FrameQuality(0f, 0f, 0f, 0f, 0f)
        assertTrue(maxed.overall <= 1f)
        assertTrue(zeroed.overall >= 0f)
    }
}
