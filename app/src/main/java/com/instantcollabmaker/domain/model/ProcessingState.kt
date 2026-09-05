package com.instantcollabmaker.domain.model

/**
 * The ordered pipeline stages. The processing UI renders this list directly, so adding
 * a real stage in Phase 2 means adding an entry here and nothing else.
 */
enum class ProcessingStage(
    val title: String,
    /** Inclusive-exclusive progress window this stage owns, as a 0f..1f fraction. */
    val progressStart: Float,
    val progressEnd: Float,
) {
    ReadingVideo("Reading video", 0.00f, 0.15f),
    DetectingFaces("Detecting faces", 0.15f, 0.40f),
    IdentifyingPeople("Identifying people", 0.40f, 0.65f),
    GroupingAppearances("Grouping appearances", 0.65f, 0.80f),
    SelectingBestFrames("Selecting best frames", 0.80f, 0.95f),
    BuildingCollage("Building collage", 0.95f, 1.00f);

    companion object {
        val ordered: List<ProcessingStage> = entries.toList()
    }
}

/** Live counters shown on the processing screen while the pipeline runs. */
data class ProcessingStats(
    val framesAnalyzed: Int = 0,
    val facesDetected: Int = 0,
    val peopleIdentified: Int = 0,
    val appearancesDetected: Int = 0,
) {
    companion object {
        val Empty = ProcessingStats()
    }
}

/**
 * Everything a [com.instantcollabmaker.domain.processing.VideoProcessor] emits.
 *
 * Phase 2 replaces the producer, not this contract.
 */
sealed interface ProcessingState {

    data object Idle : ProcessingState

    data class Running(
        /** Overall completion, 0f..1f. */
        val progress: Float,
        val stage: ProcessingStage,
        val stats: ProcessingStats,
    ) : ProcessingState {
        val percent: Int get() = (progress * 100f).toInt().coerceIn(0, 100)

        /** Stages strictly before [stage] are finished. */
        fun statusOf(candidate: ProcessingStage): StageStatus = when {
            candidate.ordinal < stage.ordinal -> StageStatus.Done
            candidate == stage -> StageStatus.Active
            else -> StageStatus.Pending
        }
    }

    data class Complete(val result: VideoAnalysisResult) : ProcessingState

    data class Failed(
        val message: String,
        val stage: ProcessingStage? = null,
        val cause: Throwable? = null,
    ) : ProcessingState
}

enum class StageStatus { Done, Active, Pending }
