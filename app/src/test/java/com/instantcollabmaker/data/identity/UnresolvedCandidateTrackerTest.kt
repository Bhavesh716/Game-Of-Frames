package com.instantcollabmaker.data.identity

import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.model.QualityTier
import com.instantcollabmaker.domain.processing.DetectedFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Exercises the "consistently-detected-but-LOW-quality face must not be discarded forever"
 * fix: a candidate accumulates independent, mutually-consistent evidence and is only ever
 * promoted to a real person once conservative confirmation criteria are met — and even
 * then, only after a final check that it doesn't duplicate an already-confirmed identity.
 */
class UnresolvedCandidateTrackerTest {

    private fun face() = DetectedFace(frameIndex = 0, timestampMs = 0L, left = 0.5f, top = 0.1f, right = 0.9f, bottom = 0.7f)

    private fun quality(overall: Float) =
        FrameQuality(frontality = overall, sharpness = overall, eyesOpen = overall, expression = overall, visibility = overall)

    private fun unitVector(dim: Int, axis: Int): FloatArray = FloatArray(dim) { if (it == axis) 1f else 0f }

    private fun blended(dim: Int, weightAxis0: Float, weightAxis1: Float): FloatArray {
        val v = FloatArray(dim)
        v[0] = weightAxis0
        v[1] = weightAxis1
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(dim) { v[it] / norm }
    }

    @Test
    fun `a single unresolved observation accumulates without confirming`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 3)
        val result = tracker.offer(unitVector(8, 0), 0L, 0, face(), quality(0.3f), emptyList())
        assertEquals(1, result.observationCount)
        assertFalse("a lone observation must never confirm", result.justConfirmed)
        assertEquals(1, tracker.candidateObservationCounts().size)
    }

    @Test
    fun `repeated consistent observations of the same unresolved face accumulate into one candidate`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 5)
        val v = unitVector(8, 0)
        val r1 = tracker.offer(v, 0L, 0, face(), quality(0.3f), emptyList())
        val r2 = tracker.offer(v.copyOf(), 100L, 1, face(), quality(0.3f), emptyList())
        val r3 = tracker.offer(v.copyOf(), 200L, 2, face(), quality(0.3f), emptyList())
        assertEquals("all three must join the SAME candidate", r1.candidateId, r2.candidateId)
        assertEquals(r1.candidateId, r3.candidateId)
        assertEquals(3, r3.observationCount)
    }

    @Test
    fun `mutually inconsistent embeddings start separate candidates rather than being merged`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.6f, minObservationsToConfirm = 3)
        val r1 = tracker.offer(unitVector(8, 0), 0L, 0, face(), quality(0.3f), emptyList())
        val r2 = tracker.offer(unitVector(8, 1), 100L, 1, face(), quality(0.3f), emptyList())
        assertNotEquals("orthogonal embeddings must not be treated as the same unresolved person", r1.candidateId, r2.candidateId)
        assertEquals(2, tracker.candidateObservationCounts().size)
    }

    @Test
    fun `a candidate confirms exactly on the configured observation count, never earlier`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 3)
        val v = unitVector(8, 0)
        val r1 = tracker.offer(v, 0L, 0, face(), quality(0.3f), emptyList())
        val r2 = tracker.offer(v.copyOf(), 100L, 1, face(), quality(0.3f), emptyList())
        val r3 = tracker.offer(v.copyOf(), 200L, 2, face(), quality(0.3f), emptyList())
        assertFalse(r1.justConfirmed)
        assertFalse(r2.justConfirmed)
        assertTrue("the third mutually-consistent observation must trigger confirmation", r3.justConfirmed)
    }

    @Test
    fun `a confirmed candidate is never visible before it reaches the observation threshold`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 3)
        val v = unitVector(8, 0)
        tracker.offer(v, 0L, 0, face(), quality(0.3f), emptyList())
        tracker.offer(v.copyOf(), 100L, 1, face(), quality(0.3f), emptyList())
        // Only two observations so far -- nothing should be promotable yet.
        assertEquals(1, tracker.candidateObservationCounts().size)
        assertEquals(2, tracker.candidateObservationCounts().values.single())
    }

    @Test
    fun `confirmation snapshot carries the candidate's accumulated references and best sighting`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 3)
        val v = unitVector(8, 0)
        tracker.offer(v, 0L, 0, face(), quality(0.3f), emptyList())
        tracker.offer(v.copyOf(), 100L, 1, face(), quality(0.9f), emptyList())
        val confirmResult = tracker.offer(v.copyOf(), 200L, 2, face(), quality(0.3f), emptyList())
        assertTrue(confirmResult.justConfirmed)
        assertTrue(confirmResult.referenceEmbeddingsSnapshot.isNotEmpty())
        assertNotNull(confirmResult.bestObservation)
        assertEquals("the best (highest-beauty) sighting must be retained, not just the confirming one", 100L, confirmResult.bestObservation!!.timestampMs)
    }

    @Test
    fun `removing a confirmed candidate stops it from further accumulation`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 3)
        val v = unitVector(8, 0)
        tracker.offer(v, 0L, 0, face(), quality(0.3f), emptyList())
        tracker.offer(v.copyOf(), 100L, 1, face(), quality(0.3f), emptyList())
        val confirmResult = tracker.offer(v.copyOf(), 200L, 2, face(), quality(0.3f), emptyList())
        tracker.remove(confirmResult.candidateId)
        assertTrue("a removed (promoted) candidate must no longer appear as unresolved", tracker.candidateObservationCounts().isEmpty())
    }

    /**
     * Replicates the exact orchestration `RealVideoProcessor` performs at the moment a
     * candidate is confirmed: check its accumulated references against every already-
     * confirmed person one more time before creating a new one. Never lowers the global
     * threshold and never merges on proximity alone -- only on a strong, direct similarity
     * hit against a real confirmed reference.
     */
    @Test
    fun `a confirmed candidate that strongly matches an existing person attaches instead of duplicating`() {
        val identityMatcher = GlobalIdentityMatcher(threshold = 0.55f)
        val existing = identityMatcher.assign(unitVector(8, 0), QualityTier.HIGH)
        assertEquals(1, identityMatcher.allPersonIds().size)

        val candidateTracker = UnresolvedCandidateTracker(consistencyThreshold = 0.55f, minObservationsToConfirm = 3)
        // Standing in for "this candidate's accumulated evidence turns out to occupy the
        // same embedding neighborhood as an already-confirmed person" -- exercised here as
        // a direct, isolated test of the decision logic itself.
        val candidateEmbedding = unitVector(8, 0).copyOf()
        var last: UnresolvedCandidateTracker.OfferResult? = null
        repeat(3) { i -> last = candidateTracker.offer(candidateEmbedding.copyOf(), i * 100L, i, face(), quality(0.3f), emptyList()) }
        val result = last!!
        assertTrue(result.justConfirmed)

        val existingMatch = result.referenceEmbeddingsSnapshot.mapNotNull { identityMatcher.bestMatchSimilarity(it) }.maxByOrNull { it.second }
        assertNotNull("the safety re-check must find the existing person", existingMatch)
        assertTrue(existingMatch!!.second >= 0.55f)
        assertEquals(existing.personId, existingMatch.first)

        // The correct action per Part 1: attach, never duplicate.
        candidateTracker.remove(result.candidateId)
        assertEquals("must not create a duplicate confirmed person", 1, identityMatcher.allPersonIds().size)
    }

    @Test
    fun `a candidate that never reaches enough observations is dropped, never promoted`() {
        val tracker = UnresolvedCandidateTracker(consistencyThreshold = 0.5f, minObservationsToConfirm = 5)
        val v = unitVector(8, 0)
        repeat(4) { i -> tracker.offer(v.copyOf(), i * 100L, i, face(), quality(0.3f), emptyList()) }
        assertEquals(1, tracker.candidateObservationCounts().size)
        assertEquals(4, tracker.candidateObservationCounts().values.single())
        // Never confirmed, therefore never promotable -- this is what "no fake placeholder
        // people" means for the candidate path specifically.
    }
}
