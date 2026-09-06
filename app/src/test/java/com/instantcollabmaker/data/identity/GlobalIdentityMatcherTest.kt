package com.instantcollabmaker.data.identity

import com.instantcollabmaker.domain.model.QualityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Exercises the deterministic "highest similarity wins" architecture: no clustering, no
 * reconciliation, one centralized threshold, and low-quality observations that can match
 * but never single-handedly create a new confirmed identity.
 */
class GlobalIdentityMatcherTest {

    @Test
    fun `the centralized identity threshold is exactly 0-52`() {
        assertEquals(0.52f, GlobalIdentityMatcher.IDENTITY_MATCH_THRESHOLD, 1e-6f)
    }

    @Test
    fun `assign always compares a face against every known person and picks the highest similarity, not the first match`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.4f)
        val a = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        val b = matcher.assign(unitVector(8, 1), QualityTier.HIGH)
        matcher.assign(unitVector(8, 2), QualityTier.HIGH)
        // Closer to axis 1 (person B) than to A or C, even though A was created first.
        val probe = blended(8, 0.05f, 0.95f)
        val result = matcher.assign(probe, QualityTier.HIGH)
        assertEquals("must pick the highest-similarity candidate, not the first one created", b.personId, result.personId)
        assertNotEquals(a.personId, result.personId)
    }

    private fun unitVector(dim: Int, axis: Int): FloatArray =
        FloatArray(dim) { if (it == axis) 1f else 0f }

    private fun blended(dim: Int, weightAxis0: Float, weightAxis1: Float): FloatArray {
        val v = FloatArray(dim)
        v[0] = weightAxis0
        v[1] = weightAxis1
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(dim) { v[it] / norm }
    }

    @Test
    fun `identical HIGH-quality embeddings match the same person`() {
        val matcher = GlobalIdentityMatcher()
        val v = unitVector(8, 0)
        val r1 = matcher.assign(v, QualityTier.HIGH)
        val r2 = matcher.assign(v.copyOf(), QualityTier.HIGH)
        assertTrue(r1.isNewPerson)
        assertNotNull(r1.personId)
        assertEquals(r1.personId, r2.personId)
        assertEquals(1, matcher.allPersonIds().size)
    }

    @Test
    fun `orthogonal HIGH-quality embeddings create two separate people`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val r1 = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        val r2 = matcher.assign(unitVector(8, 1), QualityTier.HIGH)
        assertNotEquals(r1.personId, r2.personId)
        assertEquals(2, matcher.allPersonIds().size)
    }

    @Test
    fun `a HIGH-quality embedding just above threshold joins the existing person`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val r1 = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        // cos(angle) ~= 0.6 between this blended vector and axis 0.
        val r2 = matcher.assign(blended(8, 0.6f, 0.8f), QualityTier.HIGH)
        assertEquals(r1.personId, r2.personId)
        assertEquals(1, matcher.allPersonIds().size)
    }

    @Test
    fun `a HIGH-quality embedding just below threshold opens a new person`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val r1 = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        // cos(angle) ~= 0.5 between this blended vector and axis 0.
        val r2 = matcher.assign(blended(8, 0.5f, 0.866f), QualityTier.HIGH)
        assertNotEquals(r1.personId, r2.personId)
        assertTrue(r2.isNewPerson)
    }

    @Test
    fun `highest similarity candidate wins among many known people`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val a = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        val b = matcher.assign(unitVector(8, 1), QualityTier.HIGH)
        matcher.assign(unitVector(8, 2), QualityTier.HIGH)
        // Clearly closer to B (axis 1) than to A or the third person.
        val probe = blended(8, 0.05f, 0.95f)
        val result = matcher.assign(probe, QualityTier.HIGH)
        assertEquals(b.personId, result.personId)
        assertEquals(3, matcher.allPersonIds().size)
    }

    @Test
    fun `a LOW-quality observation with no match never creates a new person`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val result = matcher.assign(unitVector(8, 0), QualityTier.LOW)
        assertNull("a lone LOW-quality glimpse must not create a confirmed identity", result.personId)
        assertTrue(matcher.allPersonIds().isEmpty())
    }

    @Test
    fun `a LOW-quality observation can still match an already-known person`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val a = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        val result = matcher.assign(unitVector(8, 0).copyOf(), QualityTier.LOW)
        assertEquals("identity matching must still work for a low-quality face", a.personId, result.personId)
    }

    @Test
    fun `a MEDIUM-quality observation with no match still opens a new person`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val result = matcher.assign(unitVector(8, 0), QualityTier.MEDIUM)
        assertNotNull("only LOW is disqualified from creating a new identity", result.personId)
        assertEquals(1, matcher.allPersonIds().size)
    }

    @Test
    fun `cohesion is 1 for a single-reference person and drops as references diversify`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.5f)
        val result = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        assertEquals(1f, matcher.cohesion(result.personId!!), 1e-6f)

        matcher.assign(blended(8, 0.9f, 0.436f), QualityTier.HIGH)
        assertTrue("cohesion should drop below 1 once references diversify", matcher.cohesion(result.personId!!) < 1f)
    }

    @Test
    fun `reset clears all known people`() {
        val matcher = GlobalIdentityMatcher()
        matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        matcher.reset()
        assertTrue(matcher.allPersonIds().isEmpty())
    }

    @Test
    fun `second-best similarity and margin are reported for a clear match`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val a = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        val b = matcher.assign(unitVector(8, 1), QualityTier.HIGH)
        // Strongly matches A, weakly similar to B.
        val probe = blended(8, 0.95f, 0.05f)
        val result = matcher.assign(probe, QualityTier.HIGH)
        assertEquals(a.personId, result.personId)
        assertEquals(b.personId, result.secondBestCandidatePersonId)
        assertTrue("margin should be positive for an unambiguous match", result.margin > 0f)
        assertEquals(result.bestSimilarity - result.secondBestSimilarity, result.margin, 1e-6f)
    }

    @Test
    fun `margin is NaN when fewer than two people exist to compare against`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val result = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        assertTrue("no second candidate exists yet for the very first person", result.margin.isNaN())
        assertNull(result.secondBestCandidatePersonId)
    }

    @Test
    fun `a NaN embedding is rejected outright and never becomes a person or reference`() {
        val matcher = GlobalIdentityMatcher()
        val bad = unitVector(8, 0).also { it[3] = Float.NaN }
        val result = matcher.assign(bad, QualityTier.HIGH)
        assertNull(result.personId)
        assertTrue(result.isInvalid)
        assertTrue("a non-finite embedding must never create a person", matcher.allPersonIds().isEmpty())
    }

    @Test
    fun `an Infinity embedding is rejected outright and never becomes a person or reference`() {
        val matcher = GlobalIdentityMatcher()
        val bad = unitVector(8, 0).also { it[2] = Float.POSITIVE_INFINITY }
        val result = matcher.assign(bad, QualityTier.HIGH)
        assertNull(result.personId)
        assertTrue(result.isInvalid)
        assertTrue(matcher.allPersonIds().isEmpty())
    }

    @Test
    fun `an invalid embedding never corrupts an already-known person's references`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.55f)
        val a = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        matcher.assign(FloatArray(8) { Float.NaN }, QualityTier.HIGH)
        assertEquals("cohesion must be unaffected by a rejected invalid embedding", 1f, matcher.cohesion(a.personId!!), 1e-6f)
    }

    @Test
    fun `reference bank never grows past its configured capacity`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.3f, maxReferenceEmbeddings = 3)
        val first = matcher.assign(unitVector(16, 0), QualityTier.HIGH)
        assertEquals(1, matcher.referenceCount(first.personId!!))
        // Five more clean observations of the same person, similar enough to match but
        // distinct enough to keep diversifying the bank.
        repeat(5) { i ->
            val probe = blended(16, 1f - i * 0.05f, 0.05f + i * 0.02f)
            val r = matcher.assign(probe, QualityTier.HIGH)
            assertEquals(first.personId, r.personId)
            assertTrue("reference bank must never exceed its configured capacity", matcher.referenceCount(first.personId) <= 3)
        }
        assertEquals(3, matcher.referenceCount(first.personId))
    }

    @Test
    fun `a LOW-quality matched observation is never admitted as a reference`() {
        val matcher = GlobalIdentityMatcher(threshold = 0.5f)
        val a = matcher.assign(unitVector(8, 0), QualityTier.HIGH)
        assertEquals(1, matcher.referenceCount(a.personId!!))
        // Same-direction embedding (would trivially match) but LOW quality -- must not be
        // admitted as a new reference.
        val result = matcher.assign(unitVector(8, 0).copyOf(), QualityTier.LOW)
        assertEquals(a.personId, result.personId)
        assertEquals("a LOW-quality matched hit must not grow the reference bank", 1, matcher.referenceCount(a.personId))
    }
}
