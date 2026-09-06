package com.instantcollabmaker.data.appearance

import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.processing.AppearanceDraft
import com.instantcollabmaker.domain.processing.DetectedFace
import com.instantcollabmaker.domain.processing.FaceObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the deterministic set-diff appearance architecture directly against the
 * rewrite spec's own worked example (section 11) plus the same-person-twice-in-one-frame
 * dedup rule (section 12) and independent multi-person tracking (section 13).
 */
class SetDiffAppearanceTrackerTest {

    private fun face(left: Float = 0.3f) = DetectedFace(
        frameIndex = 0, timestampMs = 0L,
        left = left, top = 0.2f, right = left + 0.3f, bottom = 0.6f,
    )

    private fun quality(overall: Float = 0.8f) =
        FrameQuality(frontality = overall, sharpness = overall, eyesOpen = overall, expression = overall, visibility = overall)

    private fun obs(personId: String, beauty: Float = 0.8f, faceIndex: Int = 0, left: Float = 0.3f) = FaceObservation(
        frameIndex = 0, timestampMs = 0L, faceIndex = faceIndex, face = face(left),
        personId = personId, identitySimilarity = 0.9f, beauty = quality(beauty), otherFaces = emptyList(),
    )

    private fun AppearanceDraft.window() = startTimestampMs to endTimestampMs

    @Test
    fun `section 11 worked example produces exactly the specified appearance windows`() {
        val tracker = SetDiffAppearanceTracker()
        // Frame 1: [A]
        tracker.onFrame(0, 0L, listOf(obs("A")))
        // Frame 2: [A]
        tracker.onFrame(1, 100L, listOf(obs("A")))
        // Frame 3: [A,B]
        tracker.onFrame(2, 200L, listOf(obs("A"), obs("B")))
        // Frame 4: [A,B]
        tracker.onFrame(3, 300L, listOf(obs("A"), obs("B")))
        // Frame 5: [B] -- A's appearance must end here, at its LAST time seen (300ms),
        // not at this frame's own timestamp.
        tracker.onFrame(4, 400L, listOf(obs("B")))
        // Frame 6: [B,C]
        tracker.onFrame(5, 500L, listOf(obs("B"), obs("C")))

        val result = tracker.finish()
        val byPerson = result.groupBy { it.personId }

        assertEquals("A must have exactly one appearance (frames 1-4)", 1, byPerson.getValue("A").size)
        assertEquals(0L to 300L, byPerson.getValue("A").single().window())

        assertEquals("B must have exactly one appearance (frames 3-6)", 1, byPerson.getValue("B").size)
        assertEquals(200L to 500L, byPerson.getValue("B").single().window())

        assertEquals("C must have exactly one appearance, starting at frame 6", 1, byPerson.getValue("C").size)
        assertEquals(500L to 500L, byPerson.getValue("C").single().window())
    }

    @Test
    fun `a continued person never starts a second appearance while still present`() {
        val tracker = SetDiffAppearanceTracker()
        tracker.onFrame(0, 0L, listOf(obs("A")))
        tracker.onFrame(1, 100L, listOf(obs("A")))
        tracker.onFrame(2, 200L, listOf(obs("A")))
        val result = tracker.finish()
        assertEquals("continuous presence is exactly one appearance, never re-started per frame", 1, result.size)
    }

    @Test
    fun `same person twice in one frame counts once for appearance but both faces are beauty-compared`() {
        val tracker = SetDiffAppearanceTracker()
        val transition = tracker.onFrame(
            0, 0L,
            listOf(obs("A", beauty = 0.72f, faceIndex = 0, left = 0.1f), obs("A", beauty = 0.91f, faceIndex = 1, left = 0.6f)),
        )
        assertEquals("A counts once in currentFramePersons even with two detections", setOf("A"), transition.currentFramePersons)
        assertEquals(1, tracker.appearancesStartedCount())

        val draft = tracker.finish().single()
        assertEquals("the higher-beauty face region must win as the representative candidate", 0.91f, draft.bestQuality.overall, 1e-6f)
    }

    @Test
    fun `multiple simultaneous people are tracked completely independently`() {
        val tracker = SetDiffAppearanceTracker()
        val ids = ('A'..'J').map { it.toString() }
        val observations = ids.mapIndexed { i, id -> obs(id, faceIndex = i, left = i * 0.05f) }
        val transition = tracker.onFrame(0, 0L, observations)
        assertEquals(ids.toSet(), transition.currentFramePersons)
        assertEquals(ids.size, tracker.appearancesStartedCount())

        val result = tracker.finish()
        assertEquals(ids.size, result.size)
        assertEquals(ids.toSet(), result.map { it.personId }.toSet())
    }

    @Test
    fun `an ended person who later returns starts a genuinely new second appearance`() {
        val tracker = SetDiffAppearanceTracker()
        tracker.onFrame(0, 0L, listOf(obs("A")))
        tracker.onFrame(1, 100L, emptyList())
        tracker.onFrame(2, 200L, listOf(obs("A")))
        val result = tracker.finish()
        assertEquals("A left and came back -- two separate appearances, no smoothing", 2, result.size)
        assertEquals(0L to 0L, result[0].window())
        assertEquals(200L to 200L, result[1].window())
    }

    @Test
    fun `finish closes still-active appearances at their last-seen timestamp`() {
        val tracker = SetDiffAppearanceTracker()
        tracker.onFrame(0, 0L, listOf(obs("A")))
        tracker.onFrame(1, 100L, listOf(obs("A")))
        val result = tracker.finish()
        assertEquals(100L, result.single().endTimestampMs)
    }

    @Test
    fun `a single-frame appearance is kept, never dropped as noise`() {
        val tracker = SetDiffAppearanceTracker()
        tracker.onFrame(0, 0L, listOf(obs("A")))
        val result = tracker.finish()
        assertTrue("no minimum-frame filter -- every started appearance is a real one", result.isNotEmpty())
        assertEquals(1, result.size)
    }

    /**
     * The blur-gate contract: a frame the pipeline decided was too blurry to analyze
     * simply never calls [SetDiffAppearanceTracker.onFrame] at all — this test proves that
     * omission alone is enough to preserve one continuous appearance across the gap,
     * without any change to the tracker itself. Mirrors the exact example from the blur-
     * rejection spec: 1000ms analyzed, 1100ms SKIPPED (blurry, no onFrame call), 1200ms
     * analyzed -- one appearance, not two.
     */
    @Test
    fun `a skipped (blurry) frame does not end an appearance -- simply never calling onFrame preserves continuity`() {
        val tracker = SetDiffAppearanceTracker()
        tracker.onFrame(0, 1000L, listOf(obs("A")))
        // 1100ms: blurry -- the pipeline would skip calling onFrame() here entirely.
        tracker.onFrame(1, 1200L, listOf(obs("A")))
        val result = tracker.finish()
        assertEquals("a skipped frame must never fragment one continuous appearance", 1, result.size)
        assertEquals(1000L to 1200L, result.single().window())
    }

    /**
     * Contrast with the test above: a frame that WAS genuinely analyzed (detection ran)
     * but found no face for this person must still end the appearance normally -- the
     * distinction is "was onFrame() called with this person absent", not "was there a gap
     * in time".
     */
    @Test
    fun `an analyzed frame with no matching face DOES end the appearance, unlike a skipped frame`() {
        val tracker = SetDiffAppearanceTracker()
        tracker.onFrame(0, 1000L, listOf(obs("A")))
        // 1100ms: genuinely analyzed, but the person is not present this frame.
        tracker.onFrame(1, 1100L, emptyList())
        tracker.onFrame(2, 1200L, listOf(obs("A")))
        val result = tracker.finish()
        assertEquals("an analyzed empty frame must end the appearance and start a new one on return", 2, result.size)
        assertEquals(1000L to 1000L, result[0].window())
        assertEquals(1200L to 1200L, result[1].window())
    }
}
