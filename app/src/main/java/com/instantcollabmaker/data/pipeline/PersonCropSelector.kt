package com.instantcollabmaker.data.pipeline

import com.instantcollabmaker.domain.model.NormalizedRect
import com.instantcollabmaker.domain.processing.DetectedFace
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Person-specific crop geometry — pure functions over [DetectedFace]/[NormalizedRect], no
 * Bitmap involved, so this is unit-testable independent of the Android framework. Extracted
 * out of `RealVideoProcessor` so the multi-person crop/contamination logic can be verified
 * directly (see `PersonCropSelectorTest`) rather than only indirectly through a full
 * pipeline run.
 *
 * A person's representative crop is never the raw source frame, and never a tight
 * detector bounding box — it is a generous, portrait-friendly window around one specific
 * face, checked against every *other* face detected in that same source frame. If another
 * person's face would end up substantially inside the crop, progressively tighter/shifted
 * alternatives are tried, escalating all the way down to a small-but-still-valid
 * face-containing crop before ever accepting a still-contaminated result.
 */
object PersonCropSelector {

    /** The result of [choosePersonCrop]: the chosen rect, whether it came out clean (no
     * other face substantially inside it), and a human-readable log of every attempt. */
    data class ChosenCrop(val rect: NormalizedRect, val cropAccepted: Boolean, val attemptsLog: String)

    /**
     * Picks the crop rect for [face], checked against [otherFaces]. Tries, in escalating
     * tightness: the normal generous crop; a tighter-but-still-generous crop; the same
     * tighter crop shifted away from the contaminating face(s); and finally a minimal
     * (but never a bare bbox) crop, also shifted away. Returns the first clean attempt, or
     * the last (tightest/shifted) attempt as a best-effort result if none came out clean —
     * [ChosenCrop.cropAccepted] tells the caller which happened, so a caller with other
     * candidate frames available can choose to try a different frame entirely instead of
     * accepting a contaminated best-effort crop.
     */
    fun choosePersonCrop(face: DetectedFace, otherFaces: List<DetectedFace>): ChosenCrop {
        val attempts = buildList {
            add("generous" to generousCropRect(face))
            if (otherFaces.isNotEmpty()) {
                add("tighter" to generousCropRect(face, padX = TIGHTER_PAD_X, padTop = TIGHTER_PAD_TOP, padBottom = TIGHTER_PAD_BOTTOM))
                val (dx, dy) = directionAwayFromContaminators(face, otherFaces)
                add(
                    "tighter_shifted" to generousCropRect(
                        face, padX = TIGHTER_PAD_X, padTop = TIGHTER_PAD_TOP, padBottom = TIGHTER_PAD_BOTTOM,
                        centerShiftX = dx * SHIFT_AWAY_FRACTION, centerShiftY = dy * SHIFT_AWAY_FRACTION,
                    )
                )
                add(
                    "tightest_shifted" to generousCropRect(
                        face, padX = TIGHTEST_PAD_X, padTop = TIGHTEST_PAD_TOP, padBottom = TIGHTEST_PAD_BOTTOM,
                        centerShiftX = dx * SHIFT_AWAY_FRACTION, centerShiftY = dy * SHIFT_AWAY_FRACTION,
                    )
                )
            }
        }

        val logParts = mutableListOf<String>()
        for ((label, rect) in attempts) {
            val contaminating = otherFaces.count { rectOverlapFraction(rect, it) >= CONTAMINATION_OVERLAP_THRESHOLD }
            logParts += "$label(contaminating=$contaminating)"
            if (contaminating == 0) {
                return ChosenCrop(rect, cropAccepted = true, attemptsLog = logParts.joinToString(" -> "))
            }
        }
        return ChosenCrop(attempts.last().second, cropAccepted = false, attemptsLog = logParts.joinToString(" -> "))
    }

    /**
     * A generous, portrait-friendly crop around [face] — never a tight bounding-box crop.
     * Padding is purely proportional to the detected face's own size (no large fixed
     * frame-relative floor): [padX] on each horizontal side, [padTop] above and [padBottom]
     * below, all as a fraction of the face's own width/height. The previous version of this
     * function used a fixed floor (e.g. "at least 34% of the frame width") that was
     * appropriate for a lone, small face but produced an oversized crop for a normally-sized
     * face in a multi-person frame — one wide enough to reliably overlap a neighboring
     * person standing beside them. Removing the floor and padding relative to the face's
     * own size instead is what actually fixes that.
     *
     * The box is shifted (not shrunk) back inside the frame if it would overflow, so it
     * never degrades into a tight crop just because the face sat near an edge.
     */
    fun generousCropRect(
        face: DetectedFace,
        padX: Float = GENEROUS_PAD_X,
        padTop: Float = GENEROUS_PAD_TOP,
        padBottom: Float = GENEROUS_PAD_BOTTOM,
        centerShiftX: Float = 0f,
        centerShiftY: Float = 0f,
    ): NormalizedRect {
        val bw = face.width.coerceAtLeast(0.02f)
        val bh = face.height.coerceAtLeast(0.02f)

        var left = face.left - bw * padX + centerShiftX
        var right = face.right + bw * padX + centerShiftX
        var top = face.top - bh * padTop + centerShiftY
        var bottom = face.bottom + bh * padBottom + centerShiftY

        if (left < 0f) { right -= left; left = 0f }
        if (right > 1f) { left -= (right - 1f); right = 1f }
        left = left.coerceIn(0f, 1f)
        right = right.coerceIn(left, 1f)

        if (top < 0f) { bottom -= top; top = 0f }
        if (bottom > 1f) { top -= (bottom - 1f); bottom = 1f }
        top = top.coerceIn(0f, 1f)
        bottom = bottom.coerceIn(top, 1f)

        return NormalizedRect(left, top, right, bottom)
    }

    /** Fraction of [other]'s own bounding-box area that falls inside [crop] — the measure
     * of "would this other person's face show up substantially in this crop". */
    fun rectOverlapFraction(crop: NormalizedRect, other: DetectedFace): Float {
        val ix0 = max(crop.left, other.left)
        val iy0 = max(crop.top, other.top)
        val ix1 = min(crop.right, other.right)
        val iy1 = min(crop.bottom, other.bottom)
        val iw = (ix1 - ix0).coerceAtLeast(0f)
        val ih = (iy1 - iy0).coerceAtLeast(0f)
        val otherArea = other.width * other.height
        if (otherArea <= 0f) return 0f
        return (iw * ih) / otherArea
    }

    /** Unit direction, in normalized frame coordinates, from the centroid of
     * [contaminators] toward [face] — i.e. the direction a crop center should shift to
     * put more distance between the target face and the other person(s). */
    fun directionAwayFromContaminators(face: DetectedFace, contaminators: List<DetectedFace>): Pair<Float, Float> {
        if (contaminators.isEmpty()) return 0f to 0f
        val tx = (face.left + face.right) / 2f
        val ty = (face.top + face.bottom) / 2f
        var dx = 0f
        var dy = 0f
        for (c in contaminators) {
            dx += tx - (c.left + c.right) / 2f
            dy += ty - (c.top + c.bottom) / 2f
        }
        val len = sqrt(dx * dx + dy * dy)
        return if (len < 1e-4f) 0f to 0f else (dx / len) to (dy / len)
    }

    /** How much of another face's own box must fall inside a candidate crop before that
     * crop is rejected as contaminated. Tightened from an earlier 0.5 — "significantly"
     * visible should trigger avoidance well before the other face is half-shown. */
    const val CONTAMINATION_OVERLAP_THRESHOLD = 0.35f

    /** The default "generous" crop — proportional to face size, per the suggested initial
     * expansion: ~0.65x face width on each side, ~0.85x face height above, ~0.55x below
     * (more room for hair/head above than for the shoulder margin below). */
    const val GENEROUS_PAD_X = 0.65f
    const val GENEROUS_PAD_TOP = 0.85f
    const val GENEROUS_PAD_BOTTOM = 0.55f

    /** The "tighter" contamination-avoidance tier — still clearly generous (head + some
     * shoulder), just smaller than the default so a nearby second face is less likely to
     * fall inside it. */
    const val TIGHTER_PAD_X = 0.30f
    const val TIGHTER_PAD_TOP = 0.40f
    const val TIGHTER_PAD_BOTTOM = 0.25f

    /** The last-resort tier before accepting a best-effort contaminated crop — small, but
     * never a bare face bounding box; the target face still has a visible margin. */
    const val TIGHTEST_PAD_X = 0.12f
    const val TIGHTEST_PAD_TOP = 0.16f
    const val TIGHTEST_PAD_BOTTOM = 0.10f

    /** How far (as a fraction of the face's own size) the shifted attempts nudge the crop
     * center away from a contaminating face's centroid. */
    const val SHIFT_AWAY_FRACTION = 0.20f
}
