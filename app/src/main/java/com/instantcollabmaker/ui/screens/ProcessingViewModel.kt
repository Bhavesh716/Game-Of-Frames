package com.instantcollabmaker.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instantcollabmaker.domain.model.ProcessingStage
import com.instantcollabmaker.domain.model.ProcessingState
import com.instantcollabmaker.domain.model.ProcessingStats
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.domain.processing.VideoProcessor
import com.instantcollabmaker.domain.repository.AnalysisSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProcessingViewModel(
    private val processor: VideoProcessor,
    private val session: AnalysisSession,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    fun processVideo(videoUri: Uri) {
        viewModelScope.launch {
            _uiState.value = ProcessingUiState.Running(
                progress = 0f,
                stage = ProcessingStage.ReadingVideo,
                stats = ProcessingStats.Empty,
            )

            processor.process(videoUri).collect { state ->
                when (state) {
                    is ProcessingState.Running -> {
                        _uiState.value = ProcessingUiState.Running(
                            progress = state.progress,
                            stage = state.stage,
                            stats = state.stats,
                        )
                    }

                    is ProcessingState.Complete -> {
                        session.publishResult(state.result)
                        _uiState.value = ProcessingUiState.Complete(state.result)
                    }

                    is ProcessingState.Failed -> {
                        _uiState.value = ProcessingUiState.Failed(state.message)
                    }

                    ProcessingState.Idle -> Unit
                }
            }
        }
    }
}

sealed interface ProcessingUiState {
    data object Idle : ProcessingUiState

    data class Running(
        val progress: Float,
        val stage: ProcessingStage,
        val stats: ProcessingStats,
    ) : ProcessingUiState

    data class Complete(
        val result: VideoAnalysisResult,
    ) : ProcessingUiState

    data class Failed(
        val message: String,
    ) : ProcessingUiState
}
