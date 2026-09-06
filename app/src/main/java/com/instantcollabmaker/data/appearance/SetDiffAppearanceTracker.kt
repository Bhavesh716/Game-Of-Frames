package com.instantcollabmaker.data.appearance

import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.processing.AppearanceDraft
import com.instantcollabmaker.domain.processing.DetectedFace
import com.instantcollabmaker.domain.processing.FaceObservation

/**
 * Deterministic, purely set-diff appearance tracking — no grace period, no temporal
 * smoothing, no minimum-frame noise filter.
 *
 * This replaces `GapTolerantAppearanceTracker`. That tracker's grace period existed to
 * paper over gaps created by a *quality-tiered appearance-visibility gate* that could
 * silently exclude a face from appearance tracking even though it was still a perfectly
 * good identity observation — which is exactly what let a confirmed identity end up with
 * zero appearances. The fix here is architectural, not a bigger grace period: every
 * identity-resolved observation (see [FaceObservation.personId]) counts toward its
 * person's presence in the current frame, full stop. With that gate gone, a real gap only
 * ever means the person was genuinely not detected that frame, so no smoothing is needed —
 * a strict set-diff against the immediately previous frame is deterministic and correct by
 * construction (see the rewrite spec's worked example, reproduced in this class's test).
 *
 * Every frame:
 * 1. every present observation updates (or opens) its person's active appearance and best-
 *    beauty representative candidate;
 * 2. `currentFramePersons` (the frame's *unique* person ids) is compared against
 *    `lastFramePersons` (the *previous* frame's unique person ids, and only that frame —
 *    never a rolling window);
 * 3. anyone in `lastFramePersons - currentFramePersons` has their appearance finalized;
 * 4. `lastFramePersons` is replaced with `currentFramePersons`.
 *
 * A person who reappears after truly leaving (not just missing this one frame) gets a
 * second, independent appearance — there is no cross-frame memory beyond the single
 * previous frame.
 */
class SetDiffAppearanceTracker(
    /** Optional DEBUG-only diagnostics sink — never wired to logcat in release builds. */
    private val diagnostics: ((String) -> Unit)? = null,
) {

    /** What one call to [onFrame] found, for diagnostics (section 22's required fields). */
    data class FrameTransition(
        val currentFramePersons: Set<String>,
        val previousFramePersons: Set<String>,
        val newAppearancePersons: Set<String>,
        val endedAppearancePersons: Set<String>,
        val continuedPersons: Set<String>,
    )

    private class Active(
        var startMs: Long,
        var lastSeenMs: Long,
        var frameCount: Int,
        var bestBeauty: Float,
        var bestTimestampMs: Long,
        var bestFrameIndex: Int,
        var bestFace: DetectedFace,
        var bestQuality: FrameQuality,
        var bestOtherFaces: List<DetectedFace>,
    )

    private val active = mutableMapOf<String, Active>()
    private val completed = mutableListOf<AppearanceDraft>()
    private var lastFramePersons: Set<String> = emptySet()

    /**
     * Call once per sampled frame with every observation that received a personId this
     * frame (see [FaceObservation.personId] — a rejected/unidentified observation is never
     * passed here). Not deduplicated by the caller; deduplication into
     * [FrameTransition.currentFramePersons] happens inside, per Part 12 — two observations
     * of the same person in one frame count once for appearance purposes, but each still
     * independently updates the running best-beauty representative candidate.
     */
    fun onFrame(frameIndex: Int, timestampMs: Long, observations: List<FaceObservation>): FrameTransition {
        val previousFramePersons = lastFramePersons
        val currentFramePersons = observations.mapNotNull { it.personId }.toSet()

        // Start-or-continue: every present person's active appearance and best-beauty
        // candidate are updated before the transition is computed, per the spec's "the
        // entire current frame must first be processed, then perform set comparison".
        // Grouped by person first (Part 12): the same person detected twice in one frame
        // (e.g. a detector double-hit) still only advances `frameCount` once for that
        // frame, even though every one of their face regions is independently compared
        // for the best-beauty representative candidate.
        for ((personId, obsForPerson) in observations.filter { it.personId != null }.groupBy { it.personId!! }) {
            val entry = active.getOrPut(personId) {
                diagnostics?.invoke("APPEARANCE_START personId=$personId timestamp=$timestampMs")
                Active(
                    startMs = timestampMs,
                    lastSeenMs = timestampMs,
                    frameCount = 0,
                    bestBeauty = -1f,
                    bestTimestampMs = timestampMs,
                    bestFrameIndex = frameIndex,
                    bestFace = obsForPerson.first().face,
                    bestQuality = obsForPerson.first().beauty,
                    bestOtherFaces = obsForPerson.first().otherFaces,
                )
            }
            entry.lastSeenMs = timestampMs
            entry.frameCount++
            for (obs in obsForPerson) {
                if (obs.beauty.overall > entry.bestBeauty) {
                    entry.bestBeauty = obs.beauty.overall
                    entry.bestTimestampMs = timestampMs
                    entry.bestFrameIndex = frameIndex
                    entry.bestFace = obs.face
                    entry.bestQuality = obs.beauty
                    entry.bestOtherFaces = obs.otherFaces
                }
            }
        }

        val newAppearancePersons = currentFramePersons - previousFramePersons
        val endedAppearancePersons = previousFramePersons - currentFramePersons
        val continuedPersons = currentFramePersons intersect previousFramePersons

        for (personId in endedAppearancePersons) finalize(personId)

        lastFramePersons = currentFramePersons

        return FrameTransition(currentFramePersons, previousFramePersons, newAppearancePersons, endedAppearancePersons, continuedPersons)
    }

    /**
     * Offers a historical best-quality sighting as this person's representative candidate,
     * if it beats whatever the currently-active appearance already has. A no-op if
     * [personId] has no active appearance right now (nothing to attach it to).
     *
     * Purely additive — does not touch new/continued/ended semantics at all. Exists solely
     * so `UnresolvedCandidateTracker` can hand over the best-quality sighting it collected
     * *before* a candidate was confirmed: without this, a newly-promoted person's
     * representative selection would be limited to whatever their confirmation-frame
     * observation happened to look like, even if an earlier (pre-confirmation, still
     * genuinely theirs) sighting was better.
     */
    fun seedBestIfBetter(
        personId: String,
        timestampMs: Long,
        frameIndex: Int,
        face: DetectedFace,
        quality: FrameQuality,
        otherFaces: List<DetectedFace>,
    ) {
        val entry = active[personId] ?: return
        if (quality.overall > entry.bestBeauty) {
            entry.bestBeauty = quality.overall
            entry.bestTimestampMs = timestampMs
            entry.bestFrameIndex = frameIndex
            entry.bestFace = face
            entry.bestQuality = quality
            entry.bestOtherFaces = otherFaces
        }
    }

    /** Appearances started so far (open or already closed) — for live processing stats. */
    fun appearancesStartedCount(): Int = active.size + completed.size

    /** Closes any still-open appearances (at their last-seen timestamp) and returns all of them. */
    fun finish(): List<AppearanceDraft> {
        for (id in active.keys.toList()) finalize(id)
        return completed.sortedWith(compareBy({ it.personId }, { it.startTimestampMs }))
    }

    fun reset() {
        active.clear()
        completed.clear()
        lastFramePersons = emptySet()
    }

    /** Ends [personId]'s current appearance at its last actually-seen timestamp. No
     * minimum-frame-count filter — see the class doc: every started appearance is kept,
     * because a confirmed identity must never end up with zero appearances. */
    private fun finalize(personId: String) {
        val entry = active.remove(personId) ?: return
        diagnostics?.invoke(
            "APPEARANCE_END personId=$personId start=${entry.startMs} end=${entry.lastSeenMs} " +
                "count=${entry.frameCount} bestTimestamp=${entry.bestTimestampMs} bestBeautyScore=%.3f"
                    .format(entry.bestBeauty)
        )
        completed.add(
            AppearanceDraft(
                personId = personId,
                startTimestampMs = entry.startMs,
                endTimestampMs = entry.lastSeenMs,
                bestTimestampMs = entry.bestTimestampMs,
                bestFrameIndex = entry.bestFrameIndex,
                bestFace = entry.bestFace,
                bestQuality = entry.bestQuality,
                detectedFrameCount = entry.frameCount,
                bestOtherFaces = entry.bestOtherFaces,
            )
        )
    }
}
