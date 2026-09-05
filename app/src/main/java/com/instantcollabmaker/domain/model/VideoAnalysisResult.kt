package com.instantcollabmaker.domain.model

/** The complete output of one analysis run — the only thing the results UI reads. */
data class VideoAnalysisResult(
    val video: VideoInfo,
    val people: List<Person>,
    /** Total sampled frames pushed through the pipeline. */
    val framesAnalyzed: Int,
    /** Total face detections across all frames, before clustering. */
    val facesDetected: Int,
    val processingDurationMs: Long,
    val completedAtMs: Long,
) {
    val peopleCount: Int get() = people.size
    val appearanceCount: Int get() = people.sumOf { it.appearanceCount }
    val allAppearances: List<Appearance> get() = people.flatMap { it.appearances }

    fun person(id: String): Person? = people.firstOrNull { it.id == id }

    val isEmpty: Boolean get() = people.isEmpty()
}
