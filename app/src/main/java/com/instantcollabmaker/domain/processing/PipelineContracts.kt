package com.instantcollabmaker.domain.processing

import com.instantcollabmaker.domain.model.Appearance
import com.instantcollabmaker.domain.model.FrameQuality

/**
 * Phase 2 pipeline seams.
 *
 * These are declared now, deliberately narrow and free of any ML Kit / TFLite types, so
 * the real implementations can be dropped in behind them. Phase 1 ships no
 * implementations of these interfaces — `MockVideoProcessor` short-circuits the whole
 * chain — but the shapes fix the boundaries the real code has to respect.
 */

/** A face box found in one sampled frame, in normalised frame coordinates. */
data class DetectedFace(
    val frameIndex: Int,
    val timestampMs: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Head rotation about the vertical axis, degrees; 0 is straight-on. */
    val headEulerY: Float = 0f,
    /** Head tilt, degrees. */
    val headEulerZ: Float = 0f,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val smilingProbability: Float? = null,
)

/** A fixed-length face embedding plus the detection it was computed from. */
data class FaceEmbedding(
    val face: DetectedFace,
    val vector: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is FaceEmbedding && face == other.face && vector.contentEquals(other.vector))

    override fun hashCode(): Int = 31 * face.hashCode() + vector.contentHashCode()
}

/** Phase 2: ML Kit face detection over a decoded frame. */
interface FaceDetector {
    suspend fun detect(frame: DecodedFrame): List<DetectedFace>
}

/** Phase 2: MobileFaceNet / TFLite embedding extraction. */
interface FaceEmbedder {
    /** Embedding dimensionality, e.g. 192 for MobileFaceNet. */
    val embeddingSize: Int

    suspend fun embed(frame: DecodedFrame, face: DetectedFace): FaceEmbedding
}

/**
 * Phase 2: incremental identity clustering by cosine similarity.
 *
 * Implementations are stateful across a single analysis run: each embedding is either
 * matched to an existing cluster or opens a new one.
 */
interface IdentityMatcher {
    /** Cosine-similarity threshold above which two embeddings are the same person. */
    val similarityThreshold: Float

    /** Returns the cluster id this embedding belongs to, creating one if needed. */
    fun assign(embedding: FaceEmbedding): String

    fun clusterIds(): List<String>

    fun reset()
}

/**
 * Phase 2: temporal state machine that turns per-frame identity hits into contiguous
 * appearances, tolerating short detection gaps.
 */
interface AppearanceTracker {
    fun onFrame(frameIndex: Int, timestampMs: Long, clusterIds: Set<String>)

    /** Closes any still-open appearances and returns the full grouping. */
    fun finish(endTimestampMs: Long): List<Appearance>

    fun reset()
}

/** Phase 2: per-frame best-frame scoring. */
interface QualityScorer {
    fun score(frame: DecodedFrame, face: DetectedFace): FrameQuality
}

/**
 * A frame lifted out of the video. Kept opaque here (no Bitmap in the signature) so the
 * domain layer stays free of graphics types; the Phase 2 data layer defines the
 * concrete holder.
 */
interface DecodedFrame {
    val index: Int
    val timestampMs: Long
    val width: Int
    val height: Int
}
