package com.instantcollabmaker.data.identity

import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.processing.DetectedFace

/**
 * Accumulates evidence for a face that [GlobalIdentityMatcher.assign] rejected outright —
 * LOW quality tier, no confirmed person matched — instead of discarding it forever.
 *
 * This is NOT clustering or reconciliation of the whole dataset, and it never touches
 * [GlobalIdentityMatcher]'s own matching decision (highest similarity wins, threshold
 * 0.52, unchanged). It solves a narrower, specific problem the diagnostics caught: a
 * person who is consistently detected — often as the second/side face in a multi-face
 * frame, frequently edge-clipped — but whose every individual observation happens to be
 * LOW quality tier. Per [GlobalIdentityMatcher]'s own (unchanged) rule, a LOW-quality
 * observation may never single-handedly create a new confirmed person. Without this
 * tracker, that rule combined with zero cross-frame memory means such a person is
 * re-rejected on every single frame and never gets a chance to accumulate enough evidence
 * to prove they are a real, distinct, repeatedly-observed individual.
 *
 * How it works:
 * 1. An observation only ever reaches [offer] after [GlobalIdentityMatcher.assign] has
 *    already tried and failed to match it to a confirmed person — so by construction, its
 *    similarity to every confirmed person is already known to be below the identity
 *    threshold. This tracker never second-guesses that.
 * 2. The observation is compared against every existing *candidate's* own reference
 *    embeddings (same cosine-similarity mechanism, same [consistencyThreshold] — reusing
 *    the identity threshold rather than inventing a lower one) and joins the
 *    best-matching candidate if it clears that bar, or starts a brand-new candidate if not.
 *    This is what "embeddings must be mutually consistent" means in practice.
 * 3. A candidate is never visible in the final UI and never treated as a real person on
 *    its own. Only once it has accumulated [minObservationsToConfirm] independent,
 *    mutually-consistent observations does the caller (see `RealVideoProcessor`) promote
 *    it — via [GlobalIdentityMatcher.registerConfirmedPerson] — into a real confirmed
 *    identity in the same namespace everything else uses. Before doing that, the caller
 *    re-checks the candidate's accumulated references against every already-confirmed
 *    person one more time (their reference banks may have grown since each individual
 *    observation was first rejected); if one now matches strongly, the candidate is
 *    attached to that existing person instead of creating a duplicate.
 *
 * A candidate that never reaches [minObservationsToConfirm] is simply never promoted —
 * dropped silently at the end of the run, exactly like noise. No placeholder person is
 * ever created for it.
 */
class UnresolvedCandidateTracker(
    private val consistencyThreshold: Float = GlobalIdentityMatcher.IDENTITY_MATCH_THRESHOLD,
    private val minObservationsToConfirm: Int = MIN_OBSERVATIONS_TO_CONFIRM,
    private val maxReferenceEmbeddings: Int = MAX_REFERENCE_EMBEDDINGS,
    /** Optional DEBUG-only diagnostics sink — never wired to logcat in release builds. */
    private val diagnostics: ((String) -> Unit)? = null,
) {

    /** The single best-quality sighting of a candidate so far — carried through to
     * confirmation so the newly-promoted person's representative selection isn't limited
     * to whatever the exact confirmation frame happened to look like. */
    class BestObservation(
        val timestampMs: Long,
        val frameIndex: Int,
        val face: DetectedFace,
        val quality: FrameQuality,
        val otherFaces: List<DetectedFace>,
    )

    private class Candidate(val id: String) {
        val referenceEmbeddings = mutableListOf<FloatArray>()
        var observationCount = 0
        var bestObservation: BestObservation? = null
    }

    /** The result of [offer]: which candidate this observation joined (or started), how
     * many observations it has now, whether this call just pushed it over the
     * confirmation bar, and — only meaningful when [justConfirmed] is true — a snapshot of
     * its accumulated references and best sighting for the caller to promote. */
    data class OfferResult(
        val candidateId: String,
        val observationCount: Int,
        val justConfirmed: Boolean,
        val referenceEmbeddingsSnapshot: List<FloatArray>,
        val bestObservation: BestObservation?,
    )

    private val candidates = mutableListOf<Candidate>()
    private var nextIndex = 0

    fun offer(
        embedding: FloatArray,
        timestampMs: Long,
        frameIndex: Int,
        face: DetectedFace,
        quality: FrameQuality,
        otherFaces: List<DetectedFace>,
    ): OfferResult {
        var best: Candidate? = null
        var bestSimilarity = -2f
        for (candidate in candidates) {
            val similarity = candidate.referenceEmbeddings.maxOfOrNull { cosine(it, embedding) } ?: continue
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                best = candidate
            }
        }

        val target: Candidate
        if (best != null && bestSimilarity >= consistencyThreshold) {
            target = best
            diagnostics?.invoke(
                "CANDIDATE_OBSERVATION candidateId=${target.id} timestamp=$timestampMs similarityToCandidate=%.3f"
                    .format(bestSimilarity)
            )
        } else {
            target = Candidate("candidate_${nextIndex++}")
            candidates.add(target)
            diagnostics?.invoke(
                "CANDIDATE_CREATED candidateId=${target.id} timestamp=$timestampMs bestPriorCandidateSimilarity=%.3f"
                    .format(bestSimilarity.coerceAtLeast(0f))
            )
        }

        addReference(target, embedding)
        target.observationCount++
        if (target.bestObservation == null || quality.overall > target.bestObservation!!.quality.overall) {
            target.bestObservation = BestObservation(timestampMs, frameIndex, face, quality, otherFaces)
        }
        diagnostics?.invoke("CANDIDATE_OBSERVATION_COUNT candidateId=${target.id} count=${target.observationCount}")
        diagnostics?.invoke("CANDIDATE_BEST_SIMILARITY candidateId=${target.id} bestSimilarityToCandidate=%.3f".format(bestSimilarity.coerceAtLeast(0f)))

        val justConfirmed = target.observationCount == minObservationsToConfirm
        return OfferResult(
            candidateId = target.id,
            observationCount = target.observationCount,
            justConfirmed = justConfirmed,
            referenceEmbeddingsSnapshot = target.referenceEmbeddings.toList(),
            bestObservation = target.bestObservation,
        )
    }

    /** Removes a candidate from further consideration once the caller has resolved it —
     * either by promoting it to a brand-new confirmed person or by attaching it to an
     * already-existing one. Its future observations will be claimed directly by
     * [GlobalIdentityMatcher.assign] instead (the newly-registered/matched person's
     * reference bank now covers them), so there is nothing left for this tracker to do. */
    fun remove(candidateId: String) {
        candidates.removeAll { it.id == candidateId }
    }

    /** Every candidate's current observation count — for the final diagnostic summary,
     * so an unconfirmed (and therefore invisible) candidate's evidence is still visible
     * for inspection. */
    fun candidateObservationCounts(): Map<String, Int> = candidates.associate { it.id to it.observationCount }

    private fun addReference(candidate: Candidate, embedding: FloatArray) {
        if (candidate.referenceEmbeddings.size < maxReferenceEmbeddings) {
            candidate.referenceEmbeddings.add(embedding)
            return
        }
        var mostSimilarIdx = 0
        var mostSimilar = -2f
        for (i in candidate.referenceEmbeddings.indices) {
            val s = cosine(candidate.referenceEmbeddings[i], embedding)
            if (s > mostSimilar) {
                mostSimilar = s
                mostSimilarIdx = i
            }
        }
        candidate.referenceEmbeddings[mostSimilarIdx] = embedding
    }

    companion object {
        /** Independent, mutually-consistent observations required before a candidate is
         * promoted to a real confirmed person — conservative on purpose (Part 1: "multiple
         * independent observations required"). */
        const val MIN_OBSERVATIONS_TO_CONFIRM = 3

        const val MAX_REFERENCE_EMBEDDINGS = GlobalIdentityMatcher.MAX_REFERENCE_EMBEDDINGS

        private fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }
    }
}
