package com.instantcollabmaker.data

import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.model.VideoInfo
import com.instantcollabmaker.domain.repository.AnalysisSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime session store: which video is selected and the most recent analysis
 * result, held in memory for as long as the app process is alive.
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

    override fun renamePerson(personId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = _result.value ?: return
        val updatedPeople = current.people.map { person ->
            if (person.id == personId) person.copy(displayName = trimmed) else person
        }
        _result.value = current.copy(people = updatedPeople)
    }
}
