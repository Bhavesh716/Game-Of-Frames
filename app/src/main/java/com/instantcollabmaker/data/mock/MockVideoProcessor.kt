package com.instantcollabmaker.data.mock

import android.net.Uri
import com.instantcollabmaker.data.video.VideoMetadataReader
import com.instantcollabmaker.domain.model.ProcessingStage
import com.instantcollabmaker.domain.model.ProcessingState
import com.instantcollabmaker.domain.model.ProcessingStats
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import com.instantcollabmaker.domain.processing.VideoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

/**
 * Phase 1 stand-in for the real pipeline.
 *
 * It does not touch a single frame, but it behaves like something that does: work is
 * divided across the six real [ProcessingStage]s with per-stage durations and easing,
 * and the four live counters climb only during the stage that would actually produce
 * them — faces during detection, people during clustering, appearances during grouping.
 *
 * Phase 2 swaps this class out for the real implementation. Nothing above it changes.
 */
class MockVideoProcessor(
    private val metadataReader: VideoMetadataReader,
) : VideoProcessor {

    override fun process(videoUri: Uri): Flow<ProcessingState> = flow {
        emit(ProcessingState.Running(0f, ProcessingStage.ReadingVideo, ProcessingStats.Empty))

        val video = runCatching { metadataReader.read(videoUri) }.getOrNull()

        if (video == null || !video.isAnalyzable) {
            // Reachable for real: a corrupt file or a non-video document.
            delay(700)
            emit(
                ProcessingState.Failed(
                    message = "Something went wrong while analyzing this video.",
                    stage = ProcessingStage.ReadingVideo,
                )
            )
            return@flow
        }

        val target = MockAnalysisFactory.build(video)
        val result = if (video.isTooShortForPeople) target.withNoPeople() else target

        val startedAt = System.currentTimeMillis()
        for (stage in ProcessingStage.ordered) {
            val stageDuration = STAGE_DURATION_MS.getValue(stage)
            var stageElapsed = 0L
            while (stageElapsed < stageDuration) {
                delay(TICK_MS)
                stageElapsed += TICK_MS
                val stageFraction = (stageElapsed.toFloat() / stageDuration).coerceIn(0f, 1f)
                val progress = stage.progressStart +
                    (stage.progressEnd - stage.progressStart) * ease(stageFraction)
                emit(
                    ProcessingState.Running(
                        progress = progress.coerceIn(0f, 1f),
                        stage = stage,
                        stats = statsAt(progress, result),
                    )
                )
            }
        }

        emit(
            ProcessingState.Running(1f, ProcessingStage.BuildingCollage, statsAt(1f, result))
        )
        // A short hold on 100% so the completed state is legible rather than a flash.
        delay(COMPLETION_HOLD_MS)

        emit(
            ProcessingState.Complete(
                result.copy(
                    processingDurationMs = System.currentTimeMillis() - startedAt,
                )
            )
        )
    }.flowOn(Dispatchers.Default)

    /**
     * Counters interpolate over the stage window that would really produce them, so the
     * numbers on screen always agree with the stage label next to them.
     */
    private fun statsAt(progress: Float, result: VideoAnalysisResult): ProcessingStats {
        val frames = ramp(progress, 0.02f, 0.40f) * result.framesAnalyzed
        val faces = ramp(progress, 0.16f, 0.40f) * result.facesDetected
        val people = ramp(progress, 0.41f, 0.64f) * result.peopleCount
        val appearances = ramp(progress, 0.66f, 0.79f) * result.appearanceCount
        return ProcessingStats(
            framesAnalyzed = frames.roundToInt(),
            facesDetected = faces.roundToInt(),
            peopleIdentified = people.roundToInt(),
            appearancesDetected = appearances.roundToInt(),
        )
    }

    /** 0f before [start], 1f after [end], eased in between. */
    private fun ramp(progress: Float, start: Float, end: Float): Float = when {
        progress <= start -> 0f
        progress >= end -> 1f
        else -> ease((progress - start) / (end - start))
    }

    /** Smoothstep. Keeps the bar from looking like a metronome. */
    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private companion object {
        const val TICK_MS = 32L
        const val COMPLETION_HOLD_MS = 520L

        /** Weighted so detection and clustering visibly dominate, as they will for real. */
        val STAGE_DURATION_MS = mapOf(
            ProcessingStage.ReadingVideo to 900L,
            ProcessingStage.DetectingFaces to 2_100L,
            ProcessingStage.IdentifyingPeople to 1_800L,
            ProcessingStage.GroupingAppearances to 1_050L,
            ProcessingStage.SelectingBestFrames to 980L,
            ProcessingStage.BuildingCollage to 680L,
        )
    }
}

/** Metadata sane enough to pretend we analysed it. */
private val VideoInfo.isAnalyzable: Boolean
    get() = durationMs > 0L || isSample

/** Clips this short genuinely would not yield a usable identity cluster. */
private val VideoInfo.isTooShortForPeople: Boolean
    get() = !isSample && durationMs in 1L..1_400L

private fun VideoAnalysisResult.withNoPeople(): VideoAnalysisResult =
    copy(people = emptyList(), facesDetected = 0)
