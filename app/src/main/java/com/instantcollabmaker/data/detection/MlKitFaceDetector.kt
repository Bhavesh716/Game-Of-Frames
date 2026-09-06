package com.instantcollabmaker.data.detection

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.instantcollabmaker.data.video.VideoFrame
import com.instantcollabmaker.domain.processing.DecodedFrame
import com.instantcollabmaker.domain.processing.DetectedFace
import com.instantcollabmaker.domain.processing.FaceDetector
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit face detection over decoded video frames.
 *
 * Used for detection and face geometry/quality signals ONLY — global identity comes
 * entirely from `com.instantcollabmaker.data.identity.GlobalIdentityMatcher`'s embedding
 * comparison. ML Kit's tracking feature is deliberately left off: an earlier version of
 * this pipeline used its short-term tracking id as an identity-continuity signal, and the
 * diagnostic evidence showed that mechanism fusing unrelated people. Every sampled frame
 * is detected fresh with no cross-frame tracking state at all.
 *
 * Contours are left off (no demonstrated benefit for this task); classification
 * (eyes/smile) is on, since best-frame scoring needs it. Landmarks are on (eyes only,
 * really) specifically so the embedder can rotate each crop to a horizontal eye-line
 * before resizing to the model's input — see `TfLiteFaceEmbedder.alignedFaceCrop`.
 */
class MlKitFaceDetector {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.06f)
                .build()
        )
    }

    val faceDetector: FaceDetector = object : FaceDetector {
        override suspend fun detect(frame: DecodedFrame): List<DetectedFace> {
            val videoFrame = frame as VideoFrame
            val image = InputImage.fromBitmap(videoFrame.bitmap, 0)
            val faces = suspendCancellableCoroutine { cont ->
                detector.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            val w = videoFrame.width.toFloat()
            val h = videoFrame.height.toFloat()
            if (w <= 0f || h <= 0f) return emptyList()
            return faces.map { face ->
                val box = face.boundingBox
                // ML Kit's "left"/"right" eye are subject-relative (the subject's own
                // left/right), which is exactly the anatomical pair alignment needs —
                // not screen-left/screen-right.
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                DetectedFace(
                    frameIndex = frame.index,
                    timestampMs = frame.timestampMs,
                    left = (box.left / w).coerceIn(0f, 1f),
                    top = (box.top / h).coerceIn(0f, 1f),
                    right = (box.right / w).coerceIn(0f, 1f),
                    bottom = (box.bottom / h).coerceIn(0f, 1f),
                    headEulerX = face.headEulerAngleX,
                    headEulerY = face.headEulerAngleY,
                    headEulerZ = face.headEulerAngleZ,
                    leftEyeOpenProbability = face.leftEyeOpenProbability,
                    rightEyeOpenProbability = face.rightEyeOpenProbability,
                    smilingProbability = face.smilingProbability,
                    leftEyeX = leftEye?.let { it.x / w },
                    leftEyeY = leftEye?.let { it.y / h },
                    rightEyeX = rightEye?.let { it.x / w },
                    rightEyeY = rightEye?.let { it.y / h },
                )
            }
        }
    }

    fun release() {
        detector.close()
    }
}
