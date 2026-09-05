package com.instantcollabmaker.domain.processing

import android.net.Uri
import com.instantcollabmaker.domain.model.ProcessingState
import kotlinx.coroutines.flow.Flow

/**
 * The single entry point the UI knows about for turning a video into people.
 *
 * Phase 1 binds this to `MockVideoProcessor`. Phase 2 binds it to a real
 * implementation that drives [FaceDetector], [FaceEmbedder], [IdentityMatcher],
 * [AppearanceTracker] and [QualityScorer] over sampled frames in one pass. Because the
 * contract is a cold `Flow<ProcessingState>`, cancellation and back-pressure already
 * work and no ViewModel or screen needs to change.
 */
interface VideoProcessor {

    /**
     * Analyses [videoUri], emitting [ProcessingState.Running] updates and terminating
     * with exactly one [ProcessingState.Complete] or [ProcessingState.Failed].
     *
     * Implementations must do their work off the main thread.
     */
    fun process(videoUri: Uri): Flow<ProcessingState>
}
