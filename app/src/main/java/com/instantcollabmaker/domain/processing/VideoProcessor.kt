package com.instantcollabmaker.domain.processing

import android.net.Uri
import com.instantcollabmaker.domain.model.ProcessingState
import kotlinx.coroutines.flow.Flow

/**
 * The single entry point the UI knows about for turning a video into people.
 *
 * Bound to `com.instantcollabmaker.data.pipeline.RealVideoProcessor`, which drives
 * [FaceDetector], [FaceEmbedder], `com.instantcollabmaker.data.identity.GlobalIdentityMatcher`,
 * `com.instantcollabmaker.data.appearance.SetDiffAppearanceTracker` and [QualityScorer]
 * over one deterministic ~4 FPS sampled timeline. Because the contract is
 * a cold `Flow<ProcessingState>`, cancellation and back-pressure already work for free —
 * cancelling the collecting coroutine (see `ProcessingViewModel.cancel`) is enough to
 * unwind the whole pipeline and release its resources.
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
