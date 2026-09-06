package com.instantcollabmaker.data.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.instantcollabmaker.BuildConfig
import com.instantcollabmaker.core.coerceInSafe
import com.instantcollabmaker.data.appearance.SetDiffAppearanceTracker
import com.instantcollabmaker.data.detection.MlKitFaceDetector
import com.instantcollabmaker.data.embedding.TfLiteFaceEmbedder
import com.instantcollabmaker.data.identity.GlobalIdentityMatcher
import com.instantcollabmaker.data.identity.UnresolvedCandidateTracker
import com.instantcollabmaker.data.quality.HeuristicQualityScorer
import com.instantcollabmaker.data.video.FrameExtractor
import com.instantcollabmaker.data.video.VideoFrame
import com.instantcollabmaker.data.video.VideoMetadataReader
import com.instantcollabmaker.domain.model.Appearance
import com.instantcollabmaker.domain.model.FrameImage
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.domain.model.ProcessingStage
import com.instantcollabmaker.domain.model.ProcessingState
import com.instantcollabmaker.domain.model.ProcessingStats
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import com.instantcollabmaker.domain.processing.AppearanceDraft
import com.instantcollabmaker.domain.processing.DetectedFace
import com.instantcollabmaker.domain.processing.FaceObservation
import com.instantcollabmaker.domain.processing.VideoProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The real pipeline: ONE deterministic ~10 FPS sampled timeline
 * ([FrameExtractor.ANALYSIS_FPS]). Every sampled frame that survives the blur/quality gate
 * (see below) performs face detection, face embedding/identity matching AND beauty/quality
 * scoring together, from the exact same frame and the exact same detected face — never
 * separate cadences or separate timelines for different stages. See [FaceObservation],
 * which is the single record carrying all three for one face.
 *
 * This is a from-scratch rewrite of the identity/appearance architecture, not a patch on
 * the previous one. The previous system's own diagnostics showed its temporal-tracking and
 * reconciliation machinery actively causing wrong results (tracking-id-forced merges of
 * unrelated people, confirmed identities ending up with zero appearances). Removed
 * entirely, with nothing left running in parallel:
 *
 * - ML Kit tracking ids — no longer requested from the detector, no longer part of
 *   [DetectedFace] at all. Global identity comes only from
 *   [GlobalIdentityMatcher]'s embedding comparison.
 * - The quality-tiered cluster/gallery/provisional/reconcile system
 *   (`CosineIdentityMatcher`) — replaced by [GlobalIdentityMatcher]: compare one embedding
 *   against every known person, highest similarity wins, one centralized threshold, no
 *   clustering, no post-scan merge.
 * - The grace-period temporal appearance tracker (`GapTolerantAppearanceTracker`) —
 *   replaced by [SetDiffAppearanceTracker]: a strict set-diff between one frame and the
 *   frame immediately before it, no smoothing window.
 * - The separate embedding-interval schedule — every sampled frame that clears the blur
 *   gate embeds every detected face, unconditionally.
 *
 * ## The blur/quality gate
 *
 * A sampled frame that is genuinely too blurry to be useful (see [BLUR_SHARPNESS_THRESHOLD]
 * and [HeuristicQualityScorer.frameSharpness]) is skipped *before* face detection ever
 * runs: no detection, no embedding, no identity match, no reference-bank admission, and —
 * critically — [SetDiffAppearanceTracker.onFrame] is never called for it at all. That last
 * point is what keeps a blurry frame from ever looking like "this person left the frame":
 * the tracker's own `lastFramePersons` simply isn't touched by a skipped frame, so the very
 * next *analyzed* frame's set-diff still sees the same `lastFramePersons` it would have if
 * the blurry frame had never been sampled — one continuous appearance, not an artificial
 * end+restart. This required no change to [SetDiffAppearanceTracker] itself (see its class
 * doc): the guarantee comes entirely from simply not calling it for a skipped frame.
 *
 * Faces within a frame are processed independently and in a fixed left-to-right order
 * (sorted by bounding-box left edge) so a run is fully deterministic — no assumption
 * anywhere that a frame contains exactly one person.
 *
 * ## Person-specific cropping (multi-person frames)
 *
 * [FrameImage] is never a crop of the raw source frame — it is always cropped around one
 * specific person's own face bounding box ([PersonCropSelector.choosePersonCrop]),
 * generously (never a tight face box) but checked against every *other* face detected in
 * that same source frame ([FaceObservation.otherFaces] / [AppearanceDraft.bestOtherFaces]):
 * if another person's face would end up substantially inside the crop, progressively
 * tighter/shifted crops are tried before ever accepting a still-contaminated result.
 *
 * ## Never crashes on missing/invalid image data
 *
 * Every stage that touches a bitmap, a crop rectangle or a layout size is defensive
 * against zero/invalid dimensions (see [retrieveAndCropFrame] and
 * [com.instantcollabmaker.core.coerceInSafe]): a failure here is always a `null`/skip plus
 * a diagnostic line, never an exception. A person for whom literally no image could ever
 * be retrieved is excluded with an explicit log line — never faked with a blank tile.
 */
class RealVideoProcessor(
    private val context: Context,
    private val metadataReader: VideoMetadataReader,
) : VideoProcessor {

    /**
     * Mutable per-run counters/extrema, threaded through the main loop purely for
     * diagnostics — this is what the final `MODEL SUMMARY` block is built from.
     * "highest rejected similarity" and "largest rejected similarity" are the same
     * quantity (the requested field list names it both ways); tracked once, printed twice.
     */
    private class PipelineCounters {
        var embeddingsGenerated = 0
        var framesSkippedBlurry = 0
        var lowestAcceptedSimilarity = Float.POSITIVE_INFINITY
        var highestRejectedSimilarity = Float.NEGATIVE_INFINITY
        var smallestAcceptedMargin = Float.POSITIVE_INFINITY

        fun recordAccepted(similarity: Float, margin: Float) {
            if (similarity < lowestAcceptedSimilarity) lowestAcceptedSimilarity = similarity
            if (!margin.isNaN() && margin < smallestAcceptedMargin) smallestAcceptedMargin = margin
        }

        fun recordRejected(similarity: Float) {
            if (similarity > highestRejectedSimilarity) highestRejectedSimilarity = similarity
        }
    }

    override fun process(videoUri: Uri): Flow<ProcessingState> = flow {
        emit(ProcessingState.Running(0f, ProcessingStage.ReadingVideo, ProcessingStats.Empty))
        val startedAt = System.currentTimeMillis()

        val video = runCatching { metadataReader.read(videoUri) }.getOrNull()
        if (video == null || video.durationMs <= 0L) {
            emit(
                ProcessingState.Failed(
                    message = "Couldn't read this video. Try a different file.",
                    stage = ProcessingStage.ReadingVideo,
                )
            )
            return@flow
        }

        // [DIAGNOSTIC-ONLY] See DiagnosticFrameLogger's doc for the full explanation and
        // exact pull path.
        val diagLogger = DiagnosticFrameLogger(context, enabled = BuildConfig.DEBUG)

        val extractor = FrameExtractor(context)
        val mlKitDetector = MlKitFaceDetector()
        val tfliteEmbedder = TfLiteFaceEmbedder(context, diagnostics = diagLogger::line)
        val identityMatcher = GlobalIdentityMatcher(diagnostics = diagLogger::line)
        val candidateTracker = UnresolvedCandidateTracker(diagnostics = diagLogger::line)
        val appearanceTracker = SetDiffAppearanceTracker(diagnostics = diagLogger::line)
        val qualityScorer = HeuristicQualityScorer()
        val cacheDir = frameCacheDir(context).also { clearDir(it) }
        val counters = PipelineCounters()

        try {
            if (!extractor.open(videoUri)) {
                emit(
                    ProcessingState.Failed(
                        message = "This video format isn't supported on this device.",
                        stage = ProcessingStage.ReadingVideo,
                    )
                )
                return@flow
            }

            // The single deterministic ~10 FPS source of truth — see the class doc.
            val timestamps = FrameExtractor.analysisTimestampsMs(video.durationMs)
            if (timestamps.isEmpty()) {
                emit(ProcessingState.Complete(emptyResult(video, startedAt)))
                return@flow
            }

            // [DIAGNOSTIC-ONLY] Model-identification block — printed once, before any
            // frame is processed.
            diagLogger.line("RECOGNITION_MODEL=${TfLiteFaceEmbedder.MODEL_DISPLAY_NAME}")
            diagLogger.line("MODEL_INPUT=${TfLiteFaceEmbedder.MODEL_INPUT_DESCRIPTION}")
            diagLogger.line("MODEL_OUTPUT=${TfLiteFaceEmbedder.MODEL_OUTPUT_DESCRIPTION}")
            diagLogger.line("MODEL_PREPROCESSING=${TfLiteFaceEmbedder.MODEL_PREPROCESSING_DESCRIPTION}")
            diagLogger.line("ANALYSIS_FPS=${FrameExtractor.ANALYSIS_FPS}")
            diagLogger.line("IDENTITY_MATCH_THRESHOLD=${GlobalIdentityMatcher.IDENTITY_MATCH_THRESHOLD}")
            diagLogger.line("BLUR_SHARPNESS_THRESHOLD=$BLUR_SHARPNESS_THRESHOLD")

            var framesAnalyzed = 0
            var facesDetected = 0

            for ((frameIdx, timestampMs) in timestamps.withIndex()) {
                val bitmap = extractor.frameAt(timestampMs, FrameExtractor.ANALYSIS_MAX_DIMENSION_PX)
                if (bitmap == null) {
                    // [DIAGNOSTIC-ONLY]
                    diagLogger.line("FRAME idx=$frameIdx t=${timestampMs}ms DECODE_FAILED (frame skipped entirely)")
                    emitProgressIfDue(frameIdx, timestamps.size, framesAnalyzed, facesDetected, identityMatcher, appearanceTracker) { emit(it) }
                    continue
                }

                // Section 2: the blur/quality gate runs BEFORE face detection — a
                // genuinely unusable frame never reaches detection, embedding, identity
                // matching, reference-bank admission, or appearance tracking at all. See
                // the class doc for exactly why skipping (rather than "analyzing with zero
                // faces") is what protects appearance continuity across a blurry frame.
                val sharpness = qualityScorer.frameSharpness(bitmap)
                if (sharpness < BLUR_SHARPNESS_THRESHOLD) {
                    counters.framesSkippedBlurry++
                    diagLogger.line(
                        "FRAME_SKIPPED reason=BLURRY timestamp=$timestampMs sharpness=%.4f threshold=%.4f"
                            .format(sharpness, BLUR_SHARPNESS_THRESHOLD)
                    )
                    bitmap.recycle()
                    emitProgressIfDue(frameIdx, timestamps.size, framesAnalyzed, facesDetected, identityMatcher, appearanceTracker) { emit(it) }
                    continue
                }
                diagLogger.line("FRAME_ACCEPTED_FOR_ANALYSIS timestamp=$timestampMs sharpness=%.4f".format(sharpness))

                val videoFrame = VideoFrame(frameIdx, timestampMs, bitmap)
                val detected = runCatching { mlKitDetector.faceDetector.detect(videoFrame) }
                    .getOrDefault(emptyList())
                // Section 2: fixed left-to-right order, so processing is deterministic
                // and never assumes a frame holds exactly one person.
                val faces = detected.sortedBy { it.left }
                facesDetected += faces.size
                framesAnalyzed++

                // [DIAGNOSTIC-ONLY]
                diagLogger.line("FRAME idx=$frameIdx t=${timestampMs}ms facesDetected=${faces.size}")
                if (faces.size >= 2) {
                    diagLogger.line("MULTI_FACE_FRAME timestamp=$timestampMs faces=${faces.size}")
                }

                val observations = ArrayList<FaceObservation>(faces.size)
                // Collected during the per-face loop below, applied to the appearance
                // tracker only *after* this frame's onFrame() call — a just-promoted
                // person's Active entry doesn't exist until that call runs, so seeding its
                // representative candidate has to happen afterward (see the loop below).
                val pendingRepresentativeSeeds = ArrayList<Pair<String, UnresolvedCandidateTracker.BestObservation>>()

                for ((faceIndex, face) in faces.withIndex()) {
                    // Beauty/quality and identity BOTH come from this exact same
                    // sampled frame and this exact same detected face — see
                    // FaceObservation's doc. No separate cadence, no separate frame.
                    // Every face is independent: one face's rejection or match never
                    // affects any other face's processing this frame (Part 2).
                    val beauty = qualityScorer.score(videoFrame, face)
                    val otherFaces = faces.filterIndexed { i, _ -> i != faceIndex }

                    counters.embeddingsGenerated++
                    val embedding = tfliteEmbedder.faceEmbedder.embed(videoFrame, face)
                    val match = identityMatcher.assign(embedding.vector, beauty.tier)

                    // Part 1: a face GlobalIdentityMatcher rejected outright (LOW quality,
                    // no confirmed-person match — never touched for any other rejection
                    // reason) gets one more chance via the unresolved-candidate
                    // accumulator instead of being discarded forever. This never overrides
                    // a genuine match or a genuine new-person creation — both already
                    // returned non-null above and skip this entirely.
                    var resolvedPersonId = match.personId
                    var candidateId: String? = null
                    if (resolvedPersonId == null && !match.isInvalid) {
                        val offerResult = candidateTracker.offer(embedding.vector, timestampMs, frameIdx, face, beauty, otherFaces)
                        candidateId = offerResult.candidateId
                        if (offerResult.justConfirmed) {
                            // Safety check (Part 1's explicit requirement): a confirmed
                            // person's own reference bank may have grown since each of
                            // this candidate's individual observations was first rejected
                            // — re-check now, before creating a possible duplicate.
                            val existingMatch = offerResult.referenceEmbeddingsSnapshot
                                .mapNotNull { identityMatcher.bestMatchSimilarity(it) }
                                .maxByOrNull { it.second }
                            resolvedPersonId = if (existingMatch != null && existingMatch.second >= GlobalIdentityMatcher.IDENTITY_MATCH_THRESHOLD) {
                                diagLogger.line(
                                    "CANDIDATE_MERGED_TO_EXISTING candidateId=$candidateId personId=${existingMatch.first} similarity=%.3f"
                                        .format(existingMatch.second)
                                )
                                existingMatch.first
                            } else {
                                val newPersonId = identityMatcher.registerConfirmedPerson(offerResult.referenceEmbeddingsSnapshot)
                                diagLogger.line(
                                    "CANDIDATE_CONFIRMED candidateId=$candidateId personId=$newPersonId observationCount=${offerResult.observationCount}"
                                )
                                newPersonId
                            }
                            candidateTracker.remove(candidateId)
                            offerResult.bestObservation?.let { pendingRepresentativeSeeds.add(resolvedPersonId to it) }
                        }
                    }

                    if (resolvedPersonId != null) {
                        counters.recordAccepted(match.bestSimilarity, match.margin)
                    } else if (!match.isInvalid) {
                        counters.recordRejected(match.bestSimilarity)
                        // [DIAGNOSTIC-ONLY] Exact rejected-face field set.
                        diagLogger.line(
                            ("REJECTED_FACE_DETAIL timestamp=$timestampMs faceIndex=$faceIndex qualityTier=${beauty.tier} " +
                                "sharpness=%.4f bbox=(%.3f,%.3f,%.3f,%.3f) bboxSize=%.4f clipping=%.4f bestSimilarity=%.3f " +
                                "secondBestSimilarity=%.3f margin=%s bestPerson=%s candidateId=%s rejectionReason=low_quality_no_match")
                                .format(
                                    beauty.sharpness, face.left, face.top, face.right, face.bottom, face.area, beauty.clippingRatio,
                                    match.bestSimilarity, match.secondBestSimilarity,
                                    if (match.margin.isNaN()) "N/A" else "%.3f".format(match.margin),
                                    match.bestCandidatePersonId ?: "-", candidateId ?: "-",
                                )
                        )
                    }

                    // [DIAGNOSTIC-ONLY] Exact per-face field set.
                    diagLogger.line(
                        ("  FACE faceIndex=$faceIndex t=${timestampMs}ms bbox=(%.3f,%.3f,%.3f,%.3f) qualityTier=%s " +
                            "embeddingNorm=%.4f bestPerson=%s bestSimilarity=%.3f secondBestPerson=%s " +
                            "secondBestSimilarity=%.3f similarityMargin=%s candidateId=%s assignedPerson=%s")
                            .format(
                                face.left, face.top, face.right, face.bottom, beauty.tier,
                                embeddingNorm(embedding.vector),
                                match.bestCandidatePersonId ?: "-", match.bestSimilarity,
                                match.secondBestCandidatePersonId ?: "-", match.secondBestSimilarity,
                                if (match.margin.isNaN()) "N/A" else "%.3f".format(match.margin),
                                candidateId ?: "-",
                                resolvedPersonId ?: "REJECTED",
                            )
                    )

                    observations.add(
                        FaceObservation(
                            frameIndex = frameIdx,
                            timestampMs = timestampMs,
                            faceIndex = faceIndex,
                            face = face,
                            personId = resolvedPersonId,
                            identitySimilarity = match.bestSimilarity,
                            beauty = beauty,
                            otherFaces = otherFaces,
                        )
                    )
                }

                // [DIAGNOSTIC-ONLY] Two spatially-separate faces in the SAME frame
                // resolving to the SAME personId is always a recognition error (two
                // simultaneously-visible faces can never be one physical person) — and
                // unlike a cross-frame merge, it's directly checkable from a single
                // frame's own data. This never changes behavior, only surfaces it.
                logSameFramePersonCollisions(frameIdx, timestampMs, observations, diagLogger)

                // Only an ANALYZED frame (decoded + passed the blur gate) ever reaches
                // this call — see the class doc's "blur/quality gate" section for why a
                // SKIPPED frame must never call this at all.
                val transition = appearanceTracker.onFrame(frameIdx, timestampMs, observations)
                // A just-promoted/attached candidate's Active entry now exists (created or
                // updated by the onFrame() call above for its current-frame observation) —
                // only now can its pre-confirmation best sighting be offered as a better
                // representative candidate.
                for ((personId, bestObs) in pendingRepresentativeSeeds) {
                    appearanceTracker.seedBestIfBetter(personId, bestObs.timestampMs, bestObs.frameIndex, bestObs.face, bestObs.quality, bestObs.otherFaces)
                }
                // [DIAGNOSTIC-ONLY] The exact set-diff fields the deterministic
                // architecture is built around.
                diagLogger.line(
                    "  currentFramePersons=${transition.currentFramePersons} " +
                        "lastFramePersons=${transition.previousFramePersons} " +
                        "newAppearancePersons=${transition.newAppearancePersons} " +
                        "endedAppearancePersons=${transition.endedAppearancePersons} " +
                        "continuedPersons=${transition.continuedPersons}"
                )
                bitmap.recycle()

                emitProgressIfDue(frameIdx, timestamps.size, framesAnalyzed, facesDetected, identityMatcher, appearanceTracker) { emit(it) }
            }

            val statsAfterAnalysis = ProcessingStats(
                framesAnalyzed = framesAnalyzed,
                facesDetected = facesDetected,
                peopleIdentified = identityMatcher.allPersonIds().size,
                appearancesDetected = appearanceTracker.appearancesStartedCount(),
            )
            emit(ProcessingState.Running(ANALYSIS_PROGRESS_END, ProcessingStage.SelectingBestFrames, statsAfterAnalysis))

            val drafts = appearanceTracker.finish()
            val confirmedIds = identityMatcher.allPersonIds()
            val people = buildPeople(drafts, confirmedIds, extractor, cacheDir, identityMatcher, diagLogger)

            // [DIAGNOSTIC-ONLY] MODEL SUMMARY block, for evaluating whether a
            // preprocessing/sampling change actually improved identity separation.
            diagLogger.line("================ MODEL SUMMARY ================")
            diagLogger.line("modelName=${TfLiteFaceEmbedder.MODEL_DISPLAY_NAME}")
            diagLogger.line("input=${TfLiteFaceEmbedder.MODEL_INPUT_DESCRIPTION}")
            diagLogger.line("output=${TfLiteFaceEmbedder.MODEL_OUTPUT_DESCRIPTION}")
            diagLogger.line("analysisFPS=${FrameExtractor.ANALYSIS_FPS}")
            diagLogger.line("threshold=${GlobalIdentityMatcher.IDENTITY_MATCH_THRESHOLD}")
            diagLogger.line("blurSharpnessThreshold=$BLUR_SHARPNESS_THRESHOLD")
            diagLogger.line("")
            diagLogger.line("totalFrames=${timestamps.size}")
            diagLogger.line("framesSkippedBlurry=${counters.framesSkippedBlurry}")
            diagLogger.line("facesDetected=$facesDetected")
            diagLogger.line("embeddingsGenerated=${counters.embeddingsGenerated}")
            diagLogger.line("confirmedIdentities=${confirmedIds.size}")
            diagLogger.line("totalAppearances=${drafts.size}")
            diagLogger.line("")
            diagLogger.line(
                "lowest accepted similarity=" +
                    if (counters.lowestAcceptedSimilarity.isFinite()) "%.3f".format(counters.lowestAcceptedSimilarity) else "N/A (no accepted matches)"
            )
            diagLogger.line(
                "highest rejected similarity=" +
                    if (counters.highestRejectedSimilarity.isFinite()) "%.3f".format(counters.highestRejectedSimilarity) else "N/A (no rejections)"
            )
            diagLogger.line(
                "smallest accepted-vs-second margin=" +
                    if (counters.smallestAcceptedMargin.isFinite()) "%.3f".format(counters.smallestAcceptedMargin) else "N/A"
            )
            diagLogger.line(
                "largest rejected similarity=" +
                    if (counters.highestRejectedSimilarity.isFinite()) "%.3f".format(counters.highestRejectedSimilarity) else "N/A (no rejections)"
            )
            diagLogger.line("=================================================")

            diagLogger.line("================ GAME OF FRAMES DIAGNOSTIC ================")
            diagLogger.line("Frames sampled: ${timestamps.size}")
            diagLogger.line("Frames skipped (blurry): ${counters.framesSkippedBlurry}")
            diagLogger.line("Faces detected: $facesDetected")
            diagLogger.line("Embeddings generated: ${counters.embeddingsGenerated}")
            diagLogger.line("Unique confirmed identities: ${confirmedIds.size}")
            diagLogger.line("Total appearances: ${drafts.size}")
            diagLogger.line("Appearances per person:")
            drafts.groupBy { it.personId }.forEach { (personId, personDrafts) ->
                diagLogger.line("  $personId: ${personDrafts.size}")
            }
            diagLogger.line("Final representative per person:")
            people.forEach { p ->
                diagLogger.line(
                    "  ${p.id} name=${p.displayName} appearances=${p.appearanceCount} " +
                        "representativeTimestamp=${p.representativeFrame.timestampMs} confidence=${p.identityConfidence}"
                )
            }
            if (people.size < confirmedIds.size) {
                diagLogger.line(
                    "NOTE: ${confirmedIds.size - people.size} confirmed identity(ies) did not reach the final UI list " +
                        "— see EXCLUDED_NO_VALID_IMAGE lines above for the explicit reason for each."
                )
            }
            // [DIAGNOSTIC-ONLY] Part 11: makes it obvious whether any consistently-
            // detected person is sitting just short of confirmation when the run ends —
            // never surfaced in the final UI (Part 1: no fake/placeholder people), but
            // visible here for inspection.
            val unresolvedCandidates = candidateTracker.candidateObservationCounts()
            diagLogger.line("Unresolved candidates (never confirmed): ${unresolvedCandidates.size}")
            unresolvedCandidates.forEach { (candidateId, count) ->
                diagLogger.line("  $candidateId: $count observation(s), needed ${UnresolvedCandidateTracker.MIN_OBSERVATIONS_TO_CONFIRM}")
            }
            diagLogger.line("=============================================================")

            emit(
                ProcessingState.Running(
                    ProcessingStage.BuildingCollage.progressStart,
                    ProcessingStage.BuildingCollage,
                    statsAfterAnalysis.copy(peopleIdentified = people.size, appearancesDetected = people.sumOf { it.appearanceCount }),
                )
            )

            val result = VideoAnalysisResult(
                video = video,
                people = people,
                framesAnalyzed = framesAnalyzed,
                facesDetected = facesDetected,
                processingDurationMs = System.currentTimeMillis() - startedAt,
                completedAtMs = System.currentTimeMillis(),
            )
            emit(ProcessingState.Complete(result))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagLogger.line("EXCEPTION during processing: ${e::class.simpleName}: ${e.message}")
            emit(
                ProcessingState.Failed(
                    message = "Something went wrong while analyzing this video.",
                    cause = e,
                )
            )
        } finally {
            extractor.release()
            mlKitDetector.release()
            tfliteEmbedder.close()
            diagLogger.close() // [DIAGNOSTIC-ONLY]
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun emitProgressIfDue(
        frameIdx: Int,
        totalFrames: Int,
        framesAnalyzed: Int,
        facesDetected: Int,
        identityMatcher: GlobalIdentityMatcher,
        appearanceTracker: SetDiffAppearanceTracker,
        emit: suspend (ProcessingState) -> Unit,
    ) {
        if (frameIdx % PROGRESS_EMIT_STRIDE != 0 && frameIdx != totalFrames - 1) return
        val fraction = (frameIdx + 1).toFloat() / totalFrames
        val progress = ANALYSIS_PROGRESS_START + fraction * (ANALYSIS_PROGRESS_END - ANALYSIS_PROGRESS_START)
        val stage = ProcessingStage.ordered.firstOrNull { progress < it.progressEnd } ?: ProcessingStage.SelectingBestFrames
        emit(
            ProcessingState.Running(
                progress = progress,
                stage = stage,
                stats = ProcessingStats(
                    framesAnalyzed = framesAnalyzed,
                    facesDetected = facesDetected,
                    peopleIdentified = identityMatcher.allPersonIds().size,
                    appearancesDetected = appearanceTracker.appearancesStartedCount(),
                ),
            )
        )
    }

    private data class Built(val personId: String, val representativeFrame: SelectedFrame, val appearances: List<Appearance>)

    /**
     * Retrieves and crops every winning frame, then assembles [Person]s — one entry per
     * [confirmedIds], each with every appearance [SetDiffAppearanceTracker] produced for
     * them. A person's representative must be a *solo* best frame — one where
     * [AppearanceDraft.bestOtherFaces] is empty, i.e. nobody else was detected alongside
     * them in that frame — the highest-quality one among those wins. A confirmed identity
     * whose every single appearance's best frame had someone else in it never gets a solo
     * photo to represent them with, and is excluded from the results entirely (logged,
     * never shown with a two-person photo) — in practice that's almost always a spurious
     * identity to begin with (e.g. a stray detection that only ever rode along on someone
     * else's face), not a real person worth keeping.
     */
    private suspend fun buildPeople(
        drafts: List<AppearanceDraft>,
        confirmedIds: List<String>,
        extractor: FrameExtractor,
        cacheDir: File,
        identityMatcher: GlobalIdentityMatcher,
        diagLogger: DiagnosticFrameLogger,
    ): List<Person> {
        val withImages = drafts.mapNotNull { draft ->
            val image = retrieveAndCropFrame(
                extractor, cacheDir, draft.personId, draft.bestTimestampMs, draft.bestFace, diagLogger,
            )
            if (image == null) {
                diagLogger.line(
                    "FILTERED_APPEARANCE personId=${draft.personId} timestamp=${draft.bestTimestampMs} " +
                        "reason=frame_retrieval_or_crop_failed",
                )
                null
            } else {
                draft to image
            }
        }
        val byPerson = withImages.groupBy { it.first.personId }

        val orderedPersonIds = confirmedIds.sortedBy { id ->
            byPerson[id]?.minOf { it.first.startTimestampMs } ?: Long.MAX_VALUE
        }

        val built = orderedPersonIds.mapNotNull { personId ->
            val entries = byPerson[personId]?.sortedBy { it.first.startTimestampMs }
            if (entries.isNullOrEmpty()) {
                // Only ever excluded for a genuine, logged infrastructure failure (every
                // one of this person's appearance frames failed extraction/crop) — never
                // faked with a blank placeholder tile. No confirmed identity is ever
                // manufactured or padded to reach a target count.
                diagLogger.line("EXCLUDED_NO_VALID_IMAGE personId=$personId reason=every_appearance_image_retrieval_failed")
                return@mapNotNull null
            }
            val appearances = entries.mapIndexed { i, (draft, image) ->
                val selected = SelectedFrame(
                    id = "${personId}_appearance_${i + 1}",
                    timestampMs = draft.bestTimestampMs,
                    frameIndex = draft.bestFrameIndex,
                    image = image,
                    quality = draft.bestQuality,
                )
                Appearance(
                    id = "${personId}_appearance_${i + 1}",
                    personId = personId,
                    index = i + 1,
                    startTimestampMs = draft.startTimestampMs,
                    endTimestampMs = draft.endTimestampMs,
                    bestFrameTimestampMs = draft.bestTimestampMs,
                    bestFrame = selected,
                    detectedFrameCount = draft.detectedFrameCount,
                )
            }

            // Only a solo best-frame (nobody else detected alongside this person in it) is
            // eligible to represent them — never a two-/multi-person frame. Among those,
            // the highest-quality one wins.
            val soloIndices = entries.indices.filter { entries[it].first.bestOtherFaces.isEmpty() }
            if (soloIndices.isEmpty()) {
                diagLogger.line(
                    "EXCLUDED_ONLY_MULTI_PERSON_FRAMES personId=$personId " +
                        "reason=every_appearance_best_frame_contained_another_persons_face"
                )
                return@mapNotNull null
            }
            val heroIndex = soloIndices.maxBy { entries[it].first.bestQuality.overall }
            val hero = appearances[heroIndex].bestFrame
            Built(personId, hero, appearances)
        }

        return built.mapIndexed { personIndex, b ->
            Person(
                id = b.personId,
                displayName = displayNameFor(personIndex),
                index = personIndex,
                representativeFrame = b.representativeFrame,
                appearances = b.appearances,
                identityConfidence = identityMatcher.cohesion(b.personId).coerceIn(0f, 1f),
            )
        }
    }

    /**
     * Retrieves the source frame at [timestampMs] and produces a generous, portrait-
     * friendly crop around [face] via [PersonCropSelector.generousCropRect] — never the
     * raw source frame, and never a tight face box. This is deliberately a single,
     * uniform crop with no other-face-avoidance tiering: a person is only ever
     * represented by a frame where they were the sole detected face to begin with (see
     * [buildPeople]), so there is nothing left here to crop around. Every failure mode —
     * extraction failure, a zero-size bitmap, an empty resulting crop rectangle — returns
     * `null` with a diagnostic line instead of throwing.
     */
    private suspend fun retrieveAndCropFrame(
        extractor: FrameExtractor,
        cacheDir: File,
        personId: String,
        timestampMs: Long,
        face: DetectedFace,
        diagLogger: DiagnosticFrameLogger,
    ): FrameImage? {
        val bitmap = extractor.frameAt(timestampMs, FrameExtractor.EXPORT_MAX_DIMENSION_PX)
        if (bitmap == null) {
            diagLogger.line("INVALID_FRAME_RENDER personId=$personId reason=frame_extraction_failed timestamp=$timestampMs")
            return null
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            diagLogger.line("INVALID_FRAME_RENDER personId=$personId reason=zero_bitmap_size timestamp=$timestampMs")
            bitmap.recycle()
            return null
        }

        val cropRect = PersonCropSelector.generousCropRect(face)
        val w = bitmap.width
        val h = bitmap.height
        val left = (cropRect.left * w).roundToInt().coerceInSafe(0, w - 1)
        val top = (cropRect.top * h).roundToInt().coerceInSafe(0, h - 1)
        val right = (cropRect.right * w).roundToInt().coerceInSafe(left + 1, w)
        val bottom = (cropRect.bottom * h).roundToInt().coerceInSafe(top + 1, h)
        if (right <= left || bottom <= top) {
            diagLogger.line("INVALID_CROP personId=$personId reason=empty_crop_rect timestamp=$timestampMs")
            bitmap.recycle()
            return null
        }

        // The face's own position within the *final* (already edge-clamped) crop —
        // never assumed to be the crop's geometric center.
        val faceCenterX = (face.left + face.right) / 2f
        val faceCenterY = (face.top + face.bottom) / 2f
        val anchorX = ((faceCenterX * w - left) / (right - left)).coerceInSafe(0f, 1f)
        val anchorY = ((faceCenterY * h - top) / (bottom - top)).coerceInSafe(0f, 1f)

        diagLogger.line(
            ("PERSON_IMAGE personId=$personId sourceTimestamp=$timestampMs " +
                "targetFaceBBox=(%.3f,%.3f,%.3f,%.3f) finalCropRect=(%.3f,%.3f,%.3f,%.3f) finalCropWidth=%d finalCropHeight=%d")
                .format(
                    face.left, face.top, face.right, face.bottom,
                    cropRect.left, cropRect.top, cropRect.right, cropRect.bottom,
                    right - left, bottom - top,
                )
        )

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val file = File(cacheDir, "f_${personId}_${timestampMs}_${(0..9999).random()}.jpg")
        return try {
            FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            FrameImage(file.absolutePath, anchorX = anchorX, anchorY = anchorY)
        } catch (_: Exception) {
            diagLogger.line("INVALID_FRAME_RENDER personId=$personId reason=crop_encode_failed timestamp=$timestampMs")
            null
        } finally {
            if (bitmap !== cropped) bitmap.recycle()
            cropped.recycle()
        }
    }

    /**
     * [DIAGNOSTIC-ONLY] Flags any personId assigned to two or more spatially-separate
     * faces within the same frame — this is always a recognition error (two
     * simultaneously-visible detections can never be the same physical person), and it is
     * the one identity-recognition failure mode checkable from a single frame's own data
     * without needing cross-frame history. Never alters matching behavior.
     */
    private fun logSameFramePersonCollisions(
        frameIdx: Int,
        timestampMs: Long,
        observations: List<FaceObservation>,
        diagLogger: DiagnosticFrameLogger,
    ) {
        val byPerson = observations.filter { it.personId != null }.groupBy { it.personId!! }
        for ((personId, group) in byPerson) {
            if (group.size < 2) continue
            for (i in group.indices) for (j in i + 1 until group.size) {
                val a = group[i]
                val b = group[j]
                val overlap = boundingBoxOverlapFraction(a.face, b.face)
                if (overlap < DUPLICATE_FACE_OVERLAP_THRESHOLD) {
                    diagLogger.line(
                        ("SAME_FRAME_PERSON_COLLISION personId=$personId frameIdx=$frameIdx timestamp=$timestampMs " +
                            "faceA=(%.3f,%.3f,%.3f,%.3f) simA=%.3f faceB=(%.3f,%.3f,%.3f,%.3f) simB=%.3f overlap=%.3f " +
                            "reason=two_spatially_separate_faces_matched_same_person_in_one_frame")
                            .format(
                                a.face.left, a.face.top, a.face.right, a.face.bottom, a.identitySimilarity,
                                b.face.left, b.face.top, b.face.right, b.face.bottom, b.identitySimilarity,
                                overlap,
                            )
                    )
                }
            }
        }
    }

    /** Fraction of the smaller of [a]/[b]'s own area that the two boxes' intersection
     * covers — near 0 for two genuinely separate faces, near 1 for two detections of the
     * same physical face. */
    private fun boundingBoxOverlapFraction(a: DetectedFace, b: DetectedFace): Float {
        val ix0 = max(a.left, b.left)
        val iy0 = max(a.top, b.top)
        val ix1 = min(a.right, b.right)
        val iy1 = min(a.bottom, b.bottom)
        val iw = (ix1 - ix0).coerceAtLeast(0f)
        val ih = (iy1 - iy0).coerceAtLeast(0f)
        val interArea = iw * ih
        val smallerArea = min(a.width * a.height, b.width * b.height)
        if (smallerArea <= 0f) return 0f
        return interArea / smallerArea
    }

    /** L2 norm of a raw embedding vector — expected ~1.0 since [TfLiteFaceEmbedder]
     * L2-normalizes its output; purely for the per-face diagnostic line. */
    private fun embeddingNorm(vector: FloatArray): Float {
        var sumSq = 0.0
        for (x in vector) sumSq += (x * x).toDouble()
        return sqrt(sumSq).toFloat()
    }

    private fun displayNameFor(index: Int): String {
        val letters = StringBuilder()
        var n = index
        do {
            letters.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        } while (n >= 0)
        return "Person $letters"
    }

    private fun emptyResult(video: VideoInfo, startedAt: Long) = VideoAnalysisResult(
        video = video,
        people = emptyList(),
        framesAnalyzed = 0,
        facesDetected = 0,
        processingDurationMs = System.currentTimeMillis() - startedAt,
        completedAtMs = System.currentTimeMillis(),
    )

    private fun frameCacheDir(context: Context): File =
        File(context.cacheDir, "collage_frames").apply { mkdirs() }

    private fun clearDir(dir: File) {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val PROGRESS_EMIT_STRIDE = 2

        /**
         * Below this whole-frame normalized Laplacian-variance sharpness
         * ([HeuristicQualityScorer.frameSharpness]), a sampled frame is rejected outright
         * before face detection ever runs — see the class doc's "blur/quality gate"
         * section. Deliberately conservative (reject only genuine heavy motion-blur/
         * out-of-focus frames, not merely "not perfectly sharp") since a false rejection
         * here silently loses a whole frame's worth of evidence, and a smooth/low-texture
         * background can drag whole-frame sharpness down even when the actual subject is
         * sharp (see [HeuristicQualityScorer.frameSharpness]'s own documented limitation).
         * A single centralized constant — tune this one value, nowhere else.
         */
        private const val BLUR_SHARPNESS_THRESHOLD = 0.08f

        /** Below this bbox overlap, two faces in the same frame are spatially separate —
         * used only to distinguish a genuine double-identity collision from an incidental
         * double-detection of one physical face (see [logSameFramePersonCollisions]). */
        private const val DUPLICATE_FACE_OVERLAP_THRESHOLD = 0.3f

        private val ANALYSIS_PROGRESS_START = ProcessingStage.DetectingFaces.progressStart
        private val ANALYSIS_PROGRESS_END = ProcessingStage.SelectingBestFrames.progressStart
    }
}
