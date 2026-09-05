package com.instantcollabmaker.data

import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import com.instantcollabmaker.domain.repository.AnalysisSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime session store. Sufficient for Phase 1; Phase 2 can back this with
 * disk without changing a single consumer.
 */
class InMemoryAnalysisSession : AnalysisSession {

    private val _selectedVideo = MutableStateFlow<VideoInfo?>(null)
    override val selectedVideo: StateFlow<VideoInfo?> = _selectedVideo.asStateFlow()

    private val _result = MutableStateFlow<VideoAnalysisResult?>(null)
    override val result: StateFlow<VideoAnalysisResult?> = _result.asStateFlow()

    override fun selectVideo(video: VideoInfo) {
        _selectedVideo.value = video
        // A new input invalidates any previous analysis.
        _result.value = null
    }

    override fun clearVideo() {
        _selectedVideo.value = null
        _result.value = null
    }

    override fun publishResult(result: VideoAnalysisResult) {
        _result.value = result
    }

    override fun clearResult() {
        _result.value = null
    }
}
