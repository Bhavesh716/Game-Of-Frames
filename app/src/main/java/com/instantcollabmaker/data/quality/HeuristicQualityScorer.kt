package com.instantcollabmaker.data.quality

import android.graphics.Bitmap
import com.instantcollabmaker.data.video.VideoFrame
import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.processing.DecodedFrame
import com.instantcollabmaker.domain.processing.DetectedFace
import com.instantcollabmaker.domain.processing.QualityScorer
import kotlin.math.abs

/**
 * Per-face best-frame scoring, computed fresh for every detected face on every analyzed
 * frame (it feeds both the appearance tracker's running best-frame and the
 * clearly-visible gate that decides whether a detection counts toward an appearance at
 * all).
 *
 * Each component is 0f..1f; [FrameQuality.overall] does the weighting — frontality, eyes
 * and sharpness dominate, expression and visibility are small bonuses, exactly so a sharp
 * neutral frontal frame can outscore a blurry smiling one. [FrameQuality.clippingRatio] is
 * reported separately from [FrameQuality.visibility] because identity matching needs the
 * raw clipping signal directly (see [FrameQuality.tier]), not just as one ingredient of a
 * single blended score.
 */
class HeuristicQualityScorer : QualityScorer {

    override fun score(frame: DecodedFrame, face: DetectedFace): FrameQuality {
        val videoFrame = frame as VideoFrame
        val clipping = clippingRatio(face)
        return FrameQuality(
            frontality = frontalityScore(face.headEulerX, face.headEulerY, face.headEulerZ),
            sharpness = sharpnessScore(videoFrame.bitmap, face),
            eyesOpen = eyeScore(face.leftEyeOpenProbability, face.rightEyeOpenProbability),
            expression = face.smilingProbability ?: NEUTRAL_EXPRESSION_FALLBACK,
            visibility = visibilityScore(face, clipping),
            clippingRatio = clipping,
        )
    }

    /** Smooth (not brittle-binary) falloff from straight-on; yaw matters most, then pitch, then roll. */
    private fun frontalityScore(pitch: Float, yaw: Float, roll: Float): Float {
        val yawScore = (1f - abs(yaw) / MAX_YAW_DEGREES).coerceIn(0f, 1f)
        val pitchScore = (1f - abs(pitch) / MAX_PITCH_DEGREES).coerceIn(0f, 1f)
        val rollScore = (1f - abs(roll) / MAX_ROLL_DEGREES).coerceIn(0f, 1f)
        return (yawScore * 0.55f + pitchScore * 0.30f + rollScore * 0.15f).coerceIn(0f, 1f)
    }

    private fun eyeScore(left: Float?, right: Float?): Float = when {
        left != null && right != null -> (left + right) / 2f
        left != null -> left
        right != null -> right
        // ML Kit couldn't classify (e.g. eyes not clearly resolved) — neutral, doesn't
        // punish a frame just because classification was unavailable.
        else -> 0.6f
    }

    /**
     * How much this face's bounding box is cut off by the frame boundary: 0f well inside
     * the frame, ramping smoothly to 1f once an edge of the box sits exactly on the frame
     * edge. Unlike the old binary "touches edge" flag, a face at 4% from the edge and one
     * exactly at the edge are treated very differently — this smooth ramp is what lets a
     * face that is merely *near* the edge still register as a reasonably valid, if not
     * pristine, observation.
     */
    private fun clippingRatio(face: DetectedFace): Float {
        val margin = CLIP_EDGE_MARGIN
        val left = ((margin - face.left) / margin).coerceIn(0f, 1f)
        val top = ((margin - face.top) / margin).coerceIn(0f, 1f)
        val right = ((margin - (1f - face.right)) / margin).coerceIn(0f, 1f)
        val bottom = ((margin - (1f - face.bottom)) / margin).coerceIn(0f, 1f)
        return maxOf(left, top, right, bottom)
    }

    /** Penalizes a face clipped by the frame edge and a face that is too small to be a good shot. */
    private fun visibilityScore(face: DetectedFace, clipping: Float): Float {
        val sizeScore = (face.area / MIN_GOOD_FACE_AREA).coerceIn(0f, 1f)
        // Even a severely clipped face keeps some visibility credit — it is still a real,
        // usable observation for continuity/matching, just not a good one to feature.
        val edgePenalty = (1f - clipping * 0.75f).coerceIn(0.25f, 1f)
        return (sizeScore * edgePenalty).coerceIn(0f, 1f)
    }

    /** Per-face sharpness — see [sharpnessRegionScore] for the actual measure. */
    private fun sharpnessScore(bitmap: Bitmap, face: DetectedFace): Float {
        val w = bitmap.width
        val h = bitmap.height
        val left = (face.left * w).toInt().coerceIn(0, w - 1)
        val top = (face.top * h).toInt().coerceIn(0, h - 1)
        val right = (face.right * w).toInt().coerceIn(left + 1, w)
        val bottom = (face.bottom * h).toInt().coerceIn(top + 1, h)
        return sharpnessRegionScore(bitmap, left, top, right, bottom)
    }

    /**
     * Whole-frame sharpness, via the exact same Laplacian-variance measure as per-face
     * sharpness ([sharpnessRegionScore]) — never a different/invented blur metric. Used as
     * a pre-detection blur gate (`RealVideoProcessor`'s `BLUR_SHARPNESS_THRESHOLD`) so a
     * genuinely unusable frame never reaches face detection, embedding, or appearance
     * tracking at all.
     *
     * Known limitation, honestly documented rather than silently accepted: this measures
     * the *whole* frame, including background — a frame with a sharp subject in front of a
     * naturally smooth/out-of-focus background (or a large flat-color area) can score lower
     * than a face-region-only measurement would. [com.instantcollabmaker.data.pipeline.RealVideoProcessor.BLUR_SHARPNESS_THRESHOLD]
     * is deliberately conservative for exactly this reason — it's tuned to catch genuine
     * heavy motion-blur/out-of-focus frames, not to be a strict quality bar.
     */
    fun frameSharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return 0f
        return sharpnessRegionScore(bitmap, 0, 0, w, h)
    }

    /**
     * Laplacian-variance sharpness over a small grayscale downscale of [left]/[top]/[right]/
     * [bottom] within [bitmap]. Cheap by construction: the region is resized to
     * [SHARPNESS_SAMPLE_SIZE] before the convolution, so cost is independent of the source
     * region's own resolution.
     *
     * [SHARPNESS_VARIANCE_NORMALIZER] is a qualitative calibration (typical in-focus
     * portrait crops at this sample size land in the low hundreds to low thousands of
     * variance; heavy motion blur collapses toward zero) rather than a value derived from
     * a formal benchmark — documented as a limitation in the README.
     */
    private fun sharpnessRegionScore(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Float {
        val cropW = right - left
        val cropH = bottom - top
        if (cropW < 2 || cropH < 2) return 0f

        val crop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        val size = SHARPNESS_SAMPLE_SIZE
        val small = Bitmap.createScaledBitmap(crop, size, size, true)
        try {
            val pixels = IntArray(size * size)
            small.getPixels(pixels, 0, size, 0, 0, size, size)
            val lum = FloatArray(size * size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }
            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            for (y in 1 until size - 1) {
                for (x in 1 until size - 1) {
                    val idx = y * size + x
                    val laplacian = -4f * lum[idx] + lum[idx - 1] + lum[idx + 1] + lum[idx - size] + lum[idx + size]
                    sum += laplacian
                    sumSq += laplacian.toDouble() * laplacian
                    count++
                }
            }
            if (count == 0) return 0f
            val mean = sum / count
            val variance = (sumSq / count) - mean * mean
            return (variance / SHARPNESS_VARIANCE_NORMALIZER).toFloat().coerceIn(0f, 1f)
        } finally {
            if (crop !== bitmap) crop.recycle()
            if (small !== crop) small.recycle()
        }
    }

    companion object {
        private const val MAX_YAW_DEGREES = 45f
        private const val MAX_PITCH_DEGREES = 35f
        private const val MAX_ROLL_DEGREES = 30f
        private const val NEUTRAL_EXPRESSION_FALLBACK = 0.5f

        /** A face occupying at least ~8% of the frame's normalised area scores full marks for size. */
        private const val MIN_GOOD_FACE_AREA = 0.08f

        /**
         * Edge band (as a fraction of frame width/height) within which a face is
         * considered progressively clipped. Wider than a literal "touching the edge" test
         * (was 1.5%) because a face whose box starts drifting toward the edge — the
         * selfie-mode partial-face case — is already a materially worse, less reliable
         * observation well before it is truly cut off.
         */
        private const val CLIP_EDGE_MARGIN = 0.08f

        private const val SHARPNESS_SAMPLE_SIZE = 64
        private const val SHARPNESS_VARIANCE_NORMALIZER = 1200.0
    }
}
