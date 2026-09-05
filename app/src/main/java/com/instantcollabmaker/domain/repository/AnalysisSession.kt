package com.instantcollabmaker.domain.repository

import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the state that outlives a single screen: which video is selected and the most
 * recent analysis result.
 *
 * Navigation passes ids only; every screen reads its data from here. That keeps
 * argument passing trivial and means Phase 2 can persist the session (or scope it to a
 * work manager job) without touching the UI.
 */
interface AnalysisSession {
    val selectedVideo: StateFlow<VideoInfo?>
    val result: StateFlow<VideoAnalysisResult?>

    fun selectVideo(video: VideoInfo)
    fun clearVideo()
    fun publishResult(result: VideoAnalysisResult)
    fun clearResult()
}
