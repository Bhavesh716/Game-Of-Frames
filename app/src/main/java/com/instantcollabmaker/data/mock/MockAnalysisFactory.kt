package com.instantcollabmaker.data.mock

import com.instantcollabmaker.domain.model.Appearance
import com.instantcollabmaker.domain.model.FrameImage
import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Builds a [VideoAnalysisResult] from [MockAnalysisBlueprint].
 *
 * The blueprint is authored for a thirty-second video, so when the user picks a clip of
 * a different length the timeline is scaled to fit. That keeps the mock honest — you
 * never see an appearance at 00:28 inside a nine-second video — and it exercises the
 * same code paths real results will take.
 */
internal object MockAnalysisFactory {

    fun build(video: VideoInfo): VideoAnalysisResult {
        val reference = MockAnalysisBlueprint.REFERENCE_DURATION_MS
        val duration = video.durationMs.takeIf { it > 1_000L } ?: reference
        val scale = duration.toFloat() / reference.toFloat()

        val people = MockAnalysisBlueprint.people.mapIndexed { personIndex, blueprint ->
            buildPerson(personIndex, blueprint, scale, duration)
        }

        return VideoAnalysisResult(
            video = video,
            people = people,
            framesAnalyzed = scaleCount(MockAnalysisBlueprint.FRAMES_ANALYZED, scale),
            facesDetected = scaleCount(MockAnalysisBlueprint.FACES_DETECTED, scale),
            processingDurationMs = MockAnalysisBlueprint.PROCESSING_DURATION_MS,
            completedAtMs = System.currentTimeMillis(),
        )
    }

    /** Frame counts shown on the results screen; kept plausible for the clip length. */
    private fun scaleCount(base: Int, scale: Float): Int =
        (base * scale).roundToInt().coerceAtLeast(8)

    private fun buildPerson(
        personIndex: Int,
        blueprint: MockAnalysisBlueprint.PersonBlueprint,
        scale: Float,
        durationMs: Long,
    ): Person {
        val personId = PERSON_IDS[personIndex % PERSON_IDS.size]

        val appearances = blueprint.windows.mapIndexed { windowIndex, window ->
            val start = (window.startMs * scale).roundToLong().coerceIn(0L, durationMs)
            val end = (window.endMs * scale).roundToLong().coerceIn(start, durationMs)
            val best = (window.bestOffsetMs * scale).roundToLong().coerceIn(start, end)
            val quality = FrameQuality(
                frontality = window.frontality,
                sharpness = window.sharpness,
                eyesOpen = window.eyesOpen,
                expression = window.expression,
                visibility = window.visibility,
            )
            Appearance(
                id = personId + "_app_" + (windowIndex + 1),
                personId = personId,
                index = windowIndex + 1,
                startTimestampMs = start,
                endTimestampMs = end,
                bestFrameTimestampMs = best,
                bestFrame = SelectedFrame(
                    id = personId + "_frame_" + (windowIndex + 1),
                    timestampMs = best,
                    frameIndex = frameIndexFor(best),
                    image = FrameImage.Procedural(
                        // Stable, well-separated seeds so no two tiles ever repeat.
                        seed = personIndex * 977 + (windowIndex + 1) * 131,
                        paletteIndex = personIndex,
                    ),
                    quality = quality,
                ),
                detectedFrameCount = scaleCount(window.detectedFrames, scale),
            )
        }

        // The hero frame is simply the highest-scoring appearance frame, which is what
        // the real best-frame selector will do too.
        val hero = appearances.maxBy { it.bestFrame.qualityScore }.bestFrame

        return Person(
            id = personId,
            displayName = blueprint.displayName,
            index = personIndex,
            representativeFrame = hero,
            appearances = appearances,
            identityConfidence = blueprint.identityConfidence,
        )
    }

    /** Mirrors a ~7fps sampling cadence, matching the frames-analysed figure. */
    private fun frameIndexFor(timestampMs: Long): Int = (timestampMs / 139L).toInt()

    private val PERSON_IDS = listOf(
        "person_a", "person_b", "person_c", "person_d", "person_e",
        "person_f", "person_g", "person_h",
    )
}
