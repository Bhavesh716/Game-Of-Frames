package com.instantcollabmaker.domain.processing

import com.instantcollabmaker.domain.model.FrameQuality

/**
 * Real pipeline seams.
 *
 * These stay free of ML Kit / TFLite / Bitmap types in their signatures so the interfaces
 * read as plain contracts; the concrete implementations in the `data` layer are the only
 * place those types appear (they downcast [DecodedFrame] to the one concrete producer,
 * `com.instantcollabmaker.data.video.VideoFrame`, documented on the interface below).
 */

/**
 * A face box found in one sampled frame, in normalised (0f..1f) frame coordinates.
 *
 * Deliberately carries no ML Kit tracking id: global identity comes *only* from the face
 * recognition model's embedding (see `com.instantcollabmaker.data.identity.GlobalIdentityMatcher`)
 * — ML Kit is used for detection and this geometry/quality data only, never as an identity
 * signal. See the deterministic-pipeline rewrite in `RealVideoProcessor` for why.
 */
data class DetectedFace(
    val frameIndex: Int,
    val timestampMs: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Head pitch (nod), degrees. */
    val headEulerX: Float = 0f,
    /** Head yaw (turn), degrees; 0 is straight at the camera. */
    val headEulerY: Float = 0f,
    /** Head roll (tilt), degrees. */
    val headEulerZ: Float = 0f,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val smilingProbability: Float? = null,
    /** Subject's own left/right eye position, normalised frame coordinates — used to
     * rotate the embedding crop to a horizontal eye-line. Null if ML Kit couldn't
     * resolve that landmark (e.g. a heavily turned or occluded face). */
    val leftEyeX: Float? = null,
    val leftEyeY: Float? = null,
    val rightEyeX: Float? = null,
    val rightEyeY: Float? = null,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

/** A fixed-length, L2-normalized face embedding plus the detection it was computed from. */
data class FaceEmbedding(
    val face: DetectedFace,
    val vector: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is FaceEmbedding && face == other.face && vector.contentEquals(other.vector))

    override fun hashCode(): Int = 31 * face.hashCode() + vector.contentHashCode()
}

/** ML Kit face detection over one decoded frame. */
interface FaceDetector {
    suspend fun detect(frame: DecodedFrame): List<DetectedFace>
}

/** MobileFaceNet / TFLite embedding extraction. */
interface FaceEmbedder {
    /** Embedding dimensionality (192 for the bundled MobileFaceNet model). */
    val embeddingSize: Int

    suspend fun embed(frame: DecodedFrame, face: DetectedFace): FaceEmbedding
}

/**
 * One face observation on the single 4 FPS sampled timeline: detection, embedding and
 * beauty/quality score all computed from the exact same sampled frame and the exact same
 * detected face — never from different frames or different timelines. [personId] is
 * `null` when the observation could not be identity-matched *and* was not eligible to
 * create a new person (a severely low-quality/clipped face with no match — see
 * `com.instantcollabmaker.data.identity.GlobalIdentityMatcher`); such an observation still
 * gets a beauty score and a diagnostic line, it just never participates in appearance
 * tracking or the final person list.
 */
data class FaceObservation(
    val frameIndex: Int,
    val timestampMs: Long,
    val faceIndex: Int,
    val face: DetectedFace,
    val personId: String?,
    val identitySimilarity: Float,
    val beauty: FrameQuality,
    /** Every *other* face detected in this same source frame — needed so a later
     * person-specific crop around [face] can be checked for accidentally including one of
     * these other people (a source frame with two or more people is common). */
    val otherFaces: List<DetectedFace>,
)

/**
 * One continuous appearance before its representative frame has been retrieved from the
 * video. The orchestrator turns this into a full
 * [com.instantcollabmaker.domain.model.Appearance] once it has decoded the winning frame's
 * pixels.
 */
data class AppearanceDraft(
    val personId: String,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val bestTimestampMs: Long,
    val bestFrameIndex: Int,
    val bestFace: DetectedFace,
    val bestQuality: FrameQuality,
    val detectedFrameCount: Int,
    /** The other faces present in the source frame [bestFace] came from, if any — see
     * [FaceObservation.otherFaces]. Carried through so the eventual crop for this
     * appearance can be checked for another person's face before it is accepted. */
    val bestOtherFaces: List<DetectedFace> = emptyList(),
)

/** Per-frame best-frame / visibility scoring for one detected face. */
interface QualityScorer {
    fun score(frame: DecodedFrame, face: DetectedFace): FrameQuality
}

/**
 * A frame lifted out of the video. Kept opaque here (no Bitmap in the signature) so this
 * file stays free of graphics types; the concrete producer is
 * `com.instantcollabmaker.data.video.VideoFrame`, and every real [FaceDetector] /
 * [FaceEmbedder] / [QualityScorer] downcasts to it.
 */
interface DecodedFrame {
    val index: Int
    val timestampMs: Long
    val width: Int
    val height: Int
}
