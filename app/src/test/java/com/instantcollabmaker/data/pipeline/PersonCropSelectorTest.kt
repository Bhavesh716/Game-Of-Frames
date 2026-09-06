package com.instantcollabmaker.data.pipeline

import com.instantcollabmaker.domain.processing.DetectedFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-geometry tests for the multi-person crop/contamination logic — no Bitmap involved,
 * so these exercise exactly the decision-making the pipeline relies on to keep one
 * person's representative image from ever showing another person's face.
 */
class PersonCropSelectorTest {

    private fun face(left: Float, top: Float, right: Float, bottom: Float) = DetectedFace(
        frameIndex = 0, timestampMs = 0L, left = left, top = top, right = right, bottom = bottom,
    )

    @Test
    fun `a lone face gets a generous crop with no contamination concerns`() {
        val target = face(0.40f, 0.30f, 0.55f, 0.50f)
        val chosen = PersonCropSelector.choosePersonCrop(target, emptyList())
        assertTrue(chosen.cropAccepted)
        // Generous: meaningfully larger than the raw face box on every side.
        assertTrue(chosen.rect.left < target.left)
        assertTrue(chosen.rect.right > target.right)
        assertTrue(chosen.rect.top < target.top)
        assertTrue(chosen.rect.bottom > target.bottom)
    }

    @Test
    fun `target face always remains fully inside its own chosen crop`() {
        val target = face(0.40f, 0.30f, 0.55f, 0.50f)
        val other = face(0.60f, 0.30f, 0.75f, 0.50f)
        val chosen = PersonCropSelector.choosePersonCrop(target, listOf(other))
        assertTrue(chosen.rect.left <= target.left + 1e-4f)
        assertTrue(chosen.rect.right >= target.right - 1e-4f)
        assertTrue(chosen.rect.top <= target.top + 1e-4f)
        assertTrue(chosen.rect.bottom >= target.bottom - 1e-4f)
    }

    @Test
    fun `two side-by-side faces produce two independent, mutually-exclusive crops`() {
        val personA = face(0.10f, 0.30f, 0.25f, 0.50f)
        val personB = face(0.70f, 0.30f, 0.85f, 0.50f)

        val cropA = PersonCropSelector.choosePersonCrop(personA, listOf(personB))
        val cropB = PersonCropSelector.choosePersonCrop(personB, listOf(personA))

        assertTrue("A's crop must not substantially include B's face", cropA.cropAccepted)
        assertTrue("B's crop must not substantially include A's face", cropB.cropAccepted)
        assertEquals(0, countContaminating(cropA.rect, listOf(personB)))
        assertEquals(0, countContaminating(cropB.rect, listOf(personA)))
        // The two crops must actually be different regions, not both defaulting to the
        // full merged frame.
        assertTrue(cropA.rect != cropB.rect)
    }

    @Test
    fun `a crop excludes a nearby other person's face when the generous default would not`() {
        // Close enough together that the unconstrained generous crop would definitely
        // overlap the neighbor.
        val target = face(0.35f, 0.30f, 0.48f, 0.50f)
        val neighbor = face(0.50f, 0.30f, 0.63f, 0.50f)

        val unconstrained = PersonCropSelector.generousCropRect(target)
        assertTrue(
            "sanity check: the plain generous crop, with no contamination avoidance, does reach the neighbor",
            PersonCropSelector.rectOverlapFraction(unconstrained, neighbor) > 0f,
        )

        val chosen = PersonCropSelector.choosePersonCrop(target, listOf(neighbor))
        assertEquals(
            "the contamination-aware choice must exclude the neighbor even though the naive crop would not",
            0,
            countContaminating(chosen.rect, listOf(neighbor)),
        )
    }

    @Test
    fun `same source frame produces separate valid crops for two different people`() {
        val a = face(0.05f, 0.25f, 0.30f, 0.55f)
        val b = face(0.65f, 0.25f, 0.90f, 0.55f)
        val chosenA = PersonCropSelector.choosePersonCrop(a, listOf(b))
        val chosenB = PersonCropSelector.choosePersonCrop(b, listOf(a))

        // Both valid, non-degenerate rects from the exact same source frame.
        assertTrue(chosenA.rect.width > 0f && chosenA.rect.height > 0f)
        assertTrue(chosenB.rect.width > 0f && chosenB.rect.height > 0f)
        assertEquals(0, countContaminating(chosenA.rect, listOf(b)))
        assertEquals(0, countContaminating(chosenB.rect, listOf(a)))
    }

    @Test
    fun `ten simultaneous faces each get an independent, non-degenerate crop`() {
        val faces = (0 until 10).map { i -> face(i * 0.09f, 0.30f, i * 0.09f + 0.07f, 0.50f) }
        for (i in faces.indices) {
            val target = faces[i]
            val others = faces.filterIndexed { idx, _ -> idx != i }
            val chosen = PersonCropSelector.choosePersonCrop(target, others)
            assertTrue("crop for face $i must be non-degenerate", chosen.rect.width > 0f && chosen.rect.height > 0f)
            assertTrue("target face $i must remain inside its own crop", chosen.rect.left <= target.left + 1e-3f && chosen.rect.right >= target.right - 1e-3f)
        }
    }

    @Test
    fun `a face near the frame edge still produces a valid, non-degenerate crop`() {
        val edgeFace = face(0.0f, 0.0f, 0.08f, 0.10f)
        val chosen = PersonCropSelector.choosePersonCrop(edgeFace, emptyList())
        assertTrue(chosen.rect.left in 0f..1f)
        assertTrue(chosen.rect.top in 0f..1f)
        assertTrue(chosen.rect.right in 0f..1f)
        assertTrue(chosen.rect.bottom in 0f..1f)
        assertTrue(chosen.rect.width > 0f && chosen.rect.height > 0f)
    }

    @Test
    fun `a degenerate zero-size face still produces a valid non-zero crop`() {
        val degenerate = face(0.5f, 0.5f, 0.5f, 0.5f)
        val rect = PersonCropSelector.generousCropRect(degenerate)
        assertTrue("must never be zero-sized even for a degenerate input face", rect.width > 0f && rect.height > 0f)
        assertTrue(rect.left.isFinite() && rect.top.isFinite() && rect.right.isFinite() && rect.bottom.isFinite())
    }

    private fun countContaminating(rect: com.instantcollabmaker.domain.model.NormalizedRect, others: List<DetectedFace>): Int =
        others.count { PersonCropSelector.rectOverlapFraction(rect, it) >= PersonCropSelector.CONTAMINATION_OVERLAP_THRESHOLD }
}
