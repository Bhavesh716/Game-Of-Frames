package com.instantcollabmaker.data.identity

import com.instantcollabmaker.domain.model.QualityTier
import kotlin.math.sqrt

/**
 * Deterministic global identity matching: highest cosine similarity wins, one centralized
 * threshold, no clustering machinery.
 *
 * This replaces the earlier quality-tiered/provisional-cluster/gallery/reconcile system.
 * That system's own diagnostics showed it doing exactly the wrong thing — tracking-id-
 * continuity merges fusing unrelated people at similarities as low as 0.20, and a
 * reconciliation pass that could leave a confirmed identity with zero surviving
 * appearances. The fix is not another patch on that machinery; it is this much simpler,
 * fully deterministic replacement:
 *
 * ```
 * face -> embedding -> compare against every known person -> highest similarity -> threshold decision
 * ```
 *
 * There is no clustering, no post-scan reconciliation, no cross-cluster merge, and no use
 * of ML Kit tracking ids anywhere in this decision — every single observation is resolved
 * independently, purely from the recognition model's own embedding.
 *
 * A person keeps up to [maxReferenceEmbeddings] "clean" reference embeddings (never a
 * single running average) so a later observation is compared against the best-matching
 * pose already on file, not one blurred-together vector. Comparing against every stored
 * reference of every known person, then taking the single highest similarity found
 * anywhere, is what "highest similarity wins" means in the presence of multiple people.
 */
class GlobalIdentityMatcher(
    private val threshold: Float = IDENTITY_MATCH_THRESHOLD,
    private val maxReferenceEmbeddings: Int = MAX_REFERENCE_EMBEDDINGS,
    /** Optional DEBUG-only diagnostics sink — never wired to logcat in release builds. */
    private val diagnostics: ((String) -> Unit)? = null,
) {

    /** The outcome of resolving one face observation. [personId] is `null` when the
     * observation was rejected outright — either its embedding was non-finite ([isInvalid]),
     * or it neither matched an existing person nor was eligible to create a new one
     * (Part 7: a severely low-quality/clipped face with no match creates nobody). */
    data class MatchResult(
        val personId: String?,
        val isNewPerson: Boolean,
        /** Highest *per-person* similarity found (each person's own best-matching
         * reference) across every known person; `-2f` if no person existed yet to compare
         * against. */
        val bestSimilarity: Float,
        /** The candidate that produced [bestSimilarity], even when it fell short of the
         * threshold — purely for diagnostics. */
        val bestCandidatePersonId: String?,
        /** The second-highest per-person similarity, across every *other* known person;
         * `-2f` if fewer than two people existed to compare against. */
        val secondBestSimilarity: Float,
        val secondBestCandidatePersonId: String?,
        /** [bestSimilarity] minus [secondBestSimilarity] — how much better the winning
         * candidate is than its nearest rival; `Float.NaN` when there's no second
         * candidate to compare against (fewer than 2 known people). */
        val margin: Float,
        /** True when [embedding] itself contained a NaN/Infinity component — rejected
         * before any comparison, never counted as a match, a new person, or a reference. */
        val isInvalid: Boolean = false,
    )

    private class PersonRecord(val id: String) {
        val referenceEmbeddings = mutableListOf<FloatArray>()
    }

    private val persons = mutableListOf<PersonRecord>()
    private var nextIndex = 0

    /**
     * Resolves one face's embedding to a global identity. Compares [embedding] against
     * *every* known person's reference embeddings, takes the single highest similarity
     * found anywhere, and either assigns to that person (if it clears [threshold]) or opens
     * a new person (if [quality] is not [QualityTier.LOW] — see Part 7) — nothing else.
     */
    fun assign(embedding: FloatArray, quality: QualityTier): MatchResult {
        if (!isFinite(embedding)) {
            // Never let a corrupt embedding (NaN/Infinity — a degenerate model output, or
            // the near-zero-norm edge case) participate in matching, become a reference, or
            // create a new person. Rejected outright, always.
            diagnostics?.invoke("INVALID_EMBEDDING reason=non_finite_values")
            return MatchResult(
                personId = null, isNewPerson = false,
                bestSimilarity = -2f, bestCandidatePersonId = null,
                secondBestSimilarity = -2f, secondBestCandidatePersonId = null,
                margin = Float.NaN, isInvalid = true,
            )
        }

        var best: PersonRecord? = null
        var bestSimilarity = -2f
        var second: PersonRecord? = null
        var secondSimilarity = -2f
        for (person in persons) {
            val similarity = person.referenceEmbeddings.maxOfOrNull { cosine(it, embedding) } ?: continue
            if (similarity > bestSimilarity) {
                second = best
                secondSimilarity = bestSimilarity
                best = person
                bestSimilarity = similarity
            } else if (similarity > secondSimilarity) {
                second = person
                secondSimilarity = similarity
            }
        }
        val margin = if (second != null) bestSimilarity - secondSimilarity else Float.NaN

        if (best != null && bestSimilarity >= threshold) {
            // A LOW-quality hit still resolves identity, but its noisy embedding is not
            // admitted as a future reference — it must not degrade later matching.
            if (quality != QualityTier.LOW) addReference(best, embedding)
            diagnostics?.invoke(
                "MATCH ${best.id} best=%.3f second=%.3f margin=%s quality=$quality"
                    .format(bestSimilarity, secondSimilarity, marginText(margin))
            )
            return MatchResult(
                best.id, isNewPerson = false,
                bestSimilarity = bestSimilarity, bestCandidatePersonId = best.id,
                secondBestSimilarity = secondSimilarity, secondBestCandidatePersonId = second?.id,
                margin = margin,
            )
        }

        if (quality == QualityTier.LOW) {
            // Part 7: never let a severely clipped/tiny/unusable observation manufacture
            // a brand-new confirmed identity. It is still fully compared above (so it CAN
            // still match an existing person); it just can't create one on its own.
            diagnostics?.invoke(
                "REJECTED best=%.3f second=%.3f margin=%s reason=low_quality_no_match bestCandidate=%s threshold=%.3f"
                    .format(bestSimilarity, secondSimilarity, marginText(margin), best?.id ?: "-", threshold)
            )
            return MatchResult(
                null, isNewPerson = false,
                bestSimilarity = bestSimilarity, bestCandidatePersonId = best?.id,
                secondBestSimilarity = secondSimilarity, secondBestCandidatePersonId = second?.id,
                margin = margin,
            )
        }

        val person = PersonRecord("person_${nextIndex++}")
        addReference(person, embedding)
        persons.add(person)
        diagnostics?.invoke(
            "NEW_PERSON personId=${person.id} bestPriorSimilarity=%.3f quality=$quality threshold=%.3f"
                .format(bestSimilarity.coerceAtLeast(0f), threshold)
        )
        return MatchResult(
            person.id, isNewPerson = true,
            bestSimilarity = bestSimilarity, bestCandidatePersonId = person.id,
            secondBestSimilarity = secondSimilarity, secondBestCandidatePersonId = second?.id,
            margin = margin,
        )
    }

    /** Ids of every global identity created this run — every one of these is "confirmed"
     * by construction (see the class doc): there is no separate provisional state. */
    fun allPersonIds(): List<String> = persons.map { it.id }

    /**
     * Registers a brand-new confirmed person directly from already-accumulated evidence.
     *
     * This is the ONLY addition made to this class for the "missing multi-face person"
     * fix — [assign]'s own matching decision (highest similarity wins, one threshold, no
     * clustering) is completely unchanged. It exists solely so
     * `UnresolvedCandidateTracker` can promote a candidate that has accumulated enough
     * independent, mutually-consistent LOW-quality observations into a real person in this
     * same identity namespace, once *it* has decided (independently, conservatively, and
     * never by lowering [threshold]) that the evidence is sufficient. From the moment this
     * returns, the new person is indistinguishable from one [assign] created directly —
     * future observations match against it exactly the same way.
     */
    /**
     * Read-only: the highest per-person similarity [embedding] achieves against every
     * currently confirmed person, without mutating any state (no reference admission, no
     * new person). Used by the candidate-confirmation safety check ("if it later matches
     * an existing confirmed identity strongly, attach it to that identity instead of
     * creating a duplicate") — confirmed persons' reference banks can grow between when an
     * individual observation was first rejected and when a candidate accumulates enough
     * evidence to be considered for promotion, so this is re-checked at that later point
     * rather than trusted from the original (possibly stale) rejection.
     */
    fun bestMatchSimilarity(embedding: FloatArray): Pair<String, Float>? {
        if (!isFinite(embedding)) return null
        var best: PersonRecord? = null
        var bestSimilarity = -2f
        for (person in persons) {
            val similarity = person.referenceEmbeddings.maxOfOrNull { cosine(it, embedding) } ?: continue
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                best = person
            }
        }
        return best?.let { it.id to bestSimilarity }
    }

    fun registerConfirmedPerson(referenceEmbeddings: List<FloatArray>): String {
        val person = PersonRecord("person_${nextIndex++}")
        for (embedding in referenceEmbeddings.take(maxReferenceEmbeddings)) {
            person.referenceEmbeddings.add(embedding)
        }
        persons.add(person)
        diagnostics?.invoke("CANDIDATE_PROMOTED_TO_PERSON personId=${person.id} referenceCount=${person.referenceEmbeddings.size}")
        return person.id
    }

    /** How many reference embeddings [personId] currently holds — never more than
     * [maxReferenceEmbeddings]. Exposed for diagnostics/testing. */
    fun referenceCount(personId: String): Int =
        persons.firstOrNull { it.id == personId }?.referenceEmbeddings?.size ?: 0

    /** Mean pairwise similarity between a person's own stored reference embeddings — an
     * identity-confidence readout for the UI. 1f for a singleton reference set. */
    fun cohesion(personId: String): Float {
        val person = persons.firstOrNull { it.id == personId } ?: return 1f
        val refs = person.referenceEmbeddings
        if (refs.size <= 1) return 1f
        var sum = 0f
        var count = 0
        for (i in refs.indices) for (j in i + 1 until refs.size) {
            sum += cosine(refs[i], refs[j])
            count++
        }
        return if (count > 0) (sum / count).coerceIn(0f, 1f) else 1f
    }

    fun reset() {
        persons.clear()
        nextIndex = 0
    }

    /** Admits [embedding] as a reference for [person], preferring pose diversity: once
     * full, the reference most redundant with the new one (highest internal similarity) is
     * replaced, so the set doesn't just accumulate near-duplicates of one pose forever. */
    private fun addReference(person: PersonRecord, embedding: FloatArray) {
        if (person.referenceEmbeddings.size < maxReferenceEmbeddings) {
            person.referenceEmbeddings.add(embedding)
            return
        }
        var mostSimilarIdx = 0
        var mostSimilar = -2f
        for (i in person.referenceEmbeddings.indices) {
            val s = cosine(person.referenceEmbeddings[i], embedding)
            if (s > mostSimilar) {
                mostSimilar = s
                mostSimilarIdx = i
            }
        }
        person.referenceEmbeddings[mostSimilarIdx] = embedding
    }

    companion object {
        /**
         * The one centralized identity-match threshold — see the class doc. Calibrated to
         * 0.55 against real photos of different public figures (within-person mean 0.70,
         * across-person mean 0.27, across-person max 0.55), then nudged to 0.52 once
         * on-device diagnostics showed clean same-person matches consistently at 0.97-0.99.
         * (A further nudge to 0.50 was tried and reverted — it loosened matching without a
         * corresponding quality win.) Tune this one constant, nowhere else.
         */
        const val IDENTITY_MATCH_THRESHOLD = 0.52f

        /** How many "clean" reference embeddings a person keeps on file. */
        const val MAX_REFERENCE_EMBEDDINGS = 5

        private fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }

        private fun isFinite(v: FloatArray): Boolean {
            for (x in v) if (x.isNaN() || x.isInfinite()) return false
            return true
        }

        private fun marginText(margin: Float): String = if (margin.isNaN()) "N/A" else "%.3f".format(margin)
    }
}
