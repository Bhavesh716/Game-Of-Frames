package com.instantcollabmaker.data.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

/** [FrameExtractor.analysisTimestampsMs] is pure math (no Android dependency), so the
 * deterministic ~10 FPS timeline is directly verifiable here. */
class FrameExtractorTest {

    @Test
    fun `analysis fps constant is exactly 10`() {
        assertEquals(10f, FrameExtractor.ANALYSIS_FPS, 0f)
    }

    @Test
    fun `sampled timestamps land at approximately 100ms spacing for a mid-length clip`() {
        // Long enough that the frame-count clamp doesn't kick in and distort spacing.
        val durationMs = 5_000L
        val timestamps = FrameExtractor.analysisTimestampsMs(durationMs)
        assertTrue(timestamps.size > 1)
        val expectedStepMs = 1000.0 / FrameExtractor.ANALYSIS_FPS
        for (i in 1 until timestamps.size) {
            val actualStep = timestamps[i] - timestamps[i - 1]
            assertEquals(
                "step $i should be ~${expectedStepMs}ms (10 FPS)",
                expectedStepMs, actualStep.toDouble(), 1.0,
            )
        }
    }

    @Test
    fun `first sampled timestamp is 0ms`() {
        val timestamps = FrameExtractor.analysisTimestampsMs(5_000L)
        assertEquals(0L, timestamps.first())
    }

    @Test
    fun `every sampled timestamp stays within the clip duration`() {
        val durationMs = 5_000L
        val timestamps = FrameExtractor.analysisTimestampsMs(durationMs)
        for (t in timestamps) assertTrue(t < durationMs)
    }

    @Test
    fun `zero or negative duration produces no timestamps`() {
        assertTrue(FrameExtractor.analysisTimestampsMs(0L).isEmpty())
        assertTrue(FrameExtractor.analysisTimestampsMs(-100L).isEmpty())
    }

    @Test
    fun `expected 10 FPS millisecond spacing is approximately 100ms`() {
        val stepMs = (1000.0 / FrameExtractor.ANALYSIS_FPS).roundToLong()
        assertEquals(100L, stepMs)
    }
}
