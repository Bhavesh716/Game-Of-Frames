package com.instantcollabmaker.domain.repository

import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the state that outlives a single screen: which video is selected and the most
 * recent analysis result.
 *
 * Navigation passes ids only; every screen reads its data from here, which keeps
 * argument passing trivial.
 */
interface AnalysisSession {
    val selectedVideo: StateFlow<VideoInfo?>
    val result: StateFlow<VideoAnalysisResult?>

    fun selectVideo(video: VideoInfo)
    fun clearVideo()
    fun publishResult(result: VideoAnalysisResult)
    fun clearResult()

    /**
     * Renames a person for the lifetime of the current [result] — no database, no
     * backend: it rewrites that person's `displayName` in the published
     * [VideoAnalysisResult] and republishes it, so every screen collecting [result]
     * (the person list, person detail, both collage screens) picks up the new name
     * immediately and consistently, without any of them needing to know a rename
     * happened. A fresh analysis run replaces the whole result anyway, which is exactly
     * the "persist for the current processed video" scope this is meant to have.
     */
    fun renamePerson(personId: String, newName: String)
}
