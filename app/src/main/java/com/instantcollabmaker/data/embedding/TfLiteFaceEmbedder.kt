package com.instantcollabmaker.data.embedding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.instantcollabmaker.BuildConfig
import com.instantcollabmaker.data.video.VideoFrame
import com.instantcollabmaker.domain.processing.DecodedFrame
import com.instantcollabmaker.domain.processing.DetectedFace
import com.instantcollabmaker.domain.processing.FaceEmbedder
import com.instantcollabmaker.domain.processing.FaceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-device face embedding via a bundled MobileFaceNet TFLite model.
 *
 * Model: `assets/mobilefacenet.tflite`. Provenance, license and the exact tensor shapes
 * (verified with the TFLite Python interpreter before integration, not assumed) are
 * documented in `assets/NOTICE_mobilefacenet.md` and in the project README. In short:
 * input `[1, 112, 112, 3]` float32 RGB, output `[1, 192]` float32 — a 192-d embedding.
 *
 * A search for a stronger, verifiably-licensed, mobile-practical replacement (AdaFace or
 * an ArcFace-family mobile backbone, per the recognition-quality investigation this
 * preprocessing fix was part of) turned up no trustworthy candidate: no official AdaFace
 * mobile/TFLite export exists, and the concrete ArcFace-family TFLite repos found either
 * carry no license at all (default all-rights-reserved) or a EUPL-1.2 copyleft license
 * paired with a ResNet50 backbone too heavy for "reasonable CPU inference" — see the git
 * history / conversation record for the specific repos checked and why each was rejected.
 * The model itself is therefore unchanged; what changed is *preprocessing* — see
 * [alignedFaceCrop] and [rectCrop] for the two concrete, provable alignment/aspect-ratio
 * gaps that were fixed without touching the model, thresholds, or architecture.
 *
 * The interpreter is created once in [init] and reused for every face in the video;
 * [close] releases it when the pipeline finishes or is cancelled.
 */
class TfLiteFaceEmbedder(
    private val context: Context,
    /**
     * [DIAGNOSTIC-ONLY] Optional sink for `RECOGNITION_INPUT` lines — logs exactly what
     * geometry was fed to the model for one face (alignment path taken, crop pixel size
     * before the forced-square resize, source bbox). Added for the "is the model
     * receiving a proper face crop or a bad/clipped/asymmetric one" investigation; never
     * wired to logcat in a release build. Remove this parameter and the [preprocess] log
     * call once that investigation is closed.
     */
    private val diagnostics: ((String) -> Unit)? = null,
) : AutoCloseable {

    private val interpreter: Interpreter = Interpreter(
        loadModelFile(context, MODEL_ASSET_NAME),
        Interpreter.Options().apply { setNumThreads(4) },
    )

    /**
     * [DIAGNOSTIC-ONLY] Every crop actually fed to the embedding model gets written here
     * in DEBUG builds — `<app>/cache/debug_face_crops/` — so the exact pixels the model
     * saw for a given timestamp/track can be pulled and inspected (correct face? correct
     * rotation? not from another person? not empty/mirrored?) without guessing from the
     * final collage output, which goes through a *different* crop entirely. Remove this
     * field and the save call in [preprocess] once the crop-correctness question in the
     * "6 people -> 2 people" investigation is settled.
     */
    private val debugCropDir: File? by lazy {
        if (BuildConfig.DEBUG) {
            File(context.cacheDir, "debug_face_crops").apply {
                mkdirs()
                // Clear crops from any previous run so a pull always reflects this video only.
                listFiles()?.forEach { it.delete() }
            }
        } else {
            null
        }
    }

    val faceEmbedder: FaceEmbedder = object : FaceEmbedder {
        override val embeddingSize: Int = EMBEDDING_SIZE

        override suspend fun embed(frame: DecodedFrame, face: DetectedFace): FaceEmbedding =
            withContext(Dispatchers.Default) {
                val videoFrame = frame as VideoFrame
                val input = preprocess(videoFrame.bitmap, face, frame.timestampMs)
                val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
                interpreter.run(input, output)
                val normalized = l2Normalize(output[0])
                // [DIAGNOSTIC-ONLY] "verify the embedding norm is valid and finite" — the
                // actual rejection of a non-finite embedding happens in
                // GlobalIdentityMatcher.assign (the identity-decision boundary); this is
                // purely the observability half of that requirement.
                logEmbeddingNorm(normalized)
                FaceEmbedding(face, normalized)
            }
    }

    /** [DIAGNOSTIC-ONLY] Logs the L2 norm of the embedding actually handed to the
     * identity matcher, and whether every component is finite. Expected norm is always
     * ~1.0 (embeddings are L2-normalized just above) except the near-zero-norm edge case
     * [l2Normalize] itself guards against. */
    private fun logEmbeddingNorm(vector: FloatArray) {
        if (diagnostics == null) return
        var sumSq = 0.0
        var finite = true
        for (x in vector) {
            if (x.isNaN() || x.isInfinite()) finite = false
            sumSq += (x * x).toDouble()
        }
        diagnostics.invoke("EMBEDDING_NORM=%.4f finite=$finite".format(sqrt(sumSq).toFloat()))
    }

    /**
     * Produces the model's exact 112x112 input for one face: an eye-line-aligned crop
     * when landmarks are available ([alignedFaceCrop]), else a plain generous rectangular
     * crop ([rectCrop]) — either way with a 30% margin so the network sees hair/chin/ears
     * rather than a razor-tight box, clamped to the frame, resized, and normalized to
     * `(pixel - 128) / 128`.
     *
     * This crop is for recognition only and is never reused as collage output — the
     * generous crop used for the collage tile is computed separately in
     * `RealVideoProcessor` from the full-resolution retrieved frame.
     */
    private fun preprocess(bitmap: Bitmap, face: DetectedFace, timestampMs: Long = -1L): ByteBuffer {
        val aligned = alignedFaceCrop(bitmap, face)
        val crop = aligned ?: rectCrop(bitmap, face)

        // [DIAGNOSTIC-ONLY] see debugCropDir doc above. Filename carries the timestamp,
        // since personId/similarity aren't decided until after this embedding runs —
        // cross-reference against the same timestamp in the pipeline diagnostic log to
        // connect a saved crop to its match decision.
        debugCropDir?.let { dir ->
            runCatching {
                val name = "t${timestampMs}_${(0..9999).random()}.jpg"
                FileOutputStream(File(dir, name)).use { out -> crop.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            }
        }

        // [DIAGNOSTIC-ONLY] Exactly what geometry the model is about to see, before the
        // forced-square resize below. `cropAspect` far from 1.0 means the resize to
        // 112x112 is non-uniformly stretching the face — a real distortion the model was
        // never trained on, independent of alignment/threshold/model choice.
        diagnostics?.invoke(
            "RECOGNITION_INPUT timestamp=$timestampMs alignment=%s bbox=(%.3f,%.3f,%.3f,%.3f) cropPxW=%d cropPxH=%d cropAspect=%.3f inputW=%d inputH=%d"
                .format(
                    if (aligned != null) "ALIGNED" else "RECT_FALLBACK",
                    face.left, face.top, face.right, face.bottom,
                    crop.width, crop.height,
                    crop.width.toFloat() / crop.height.toFloat(),
                    INPUT_SIZE, INPUT_SIZE,
                )
        )

        val resized = Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)

        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat((r - 128f) / 128f)
            buffer.putFloat((g - 128f) / 128f)
            buffer.putFloat((b - 128f) / 128f)
        }
        buffer.rewind()

        if (crop !== bitmap) crop.recycle()
        if (resized !== crop) resized.recycle()
        return buffer
    }

    /**
     * The alignment-free fallback — used whenever ML Kit couldn't resolve both eye
     * landmarks for this face (e.g. a heavily turned or occluded profile).
     *
     * Forced **square** before it's ever handed to [preprocess]'s resize: the raw ML Kit
     * bounding box is rarely square, and resizing a non-square crop directly to 112x112
     * (the old behaviour) silently stretches every face non-uniformly — a real, provable
     * distortion the model was never trained on, independent of alignment/threshold/model
     * choice. Squaring here, not just resizing, is what actually fixes that.
     */
    private fun rectCrop(bitmap: Bitmap, face: DetectedFace): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val marginX = face.width * 0.30f
        val marginY = face.height * 0.30f
        val cropWpx = (face.width + marginX * 2f) * w
        val cropHpx = (face.height + marginY * 2f) * h
        val halfSide = max(cropWpx, cropHpx) / 2f
        val cx = (face.left + face.right) / 2f * w
        val cy = (face.top + face.bottom) / 2f * h
        val left = (cx - halfSide).roundToInt().coerceIn(0, w - 1)
        val top = (cy - halfSide).roundToInt().coerceIn(0, h - 1)
        val right = (cx + halfSide).roundToInt().coerceIn(left + 1, w)
        val bottom = (cy + halfSide).roundToInt().coerceIn(top + 1, h)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    /**
     * Aligns the face to the standard 112x112 face-recognition canonical template — the
     * same convention MobileFaceNet/ArcFace-family models are trained against (rotation
     * **and** scale **and** translation, not rotation only) — by mapping the subject's two
     * eye positions onto fixed canonical eye positions via [Matrix.setPolyToPoly]'s
     * 2-point similarity-transform mode. This directly produces a 112x112 output, so it is
     * never subject to the aspect-ratio distortion [rectCrop] has to defend against.
     *
     * This replaces an earlier rotation-only alignment (level the eye-line, nothing else)
     * that neither normalized scale (eye-to-eye distance) nor guaranteed a square output —
     * two concrete, provable preprocessing gaps identified while diagnosing why difficult
     * faces were separating poorly from different people. The canonical eye coordinates
     * below ([CANONICAL_LEFT_EYE_X]/Y, [CANONICAL_RIGHT_EYE_X]/Y) are the widely-published
     * InsightFace/ArcFace 112x112 5-point alignment template — the same convention this
     * exact model family (including the `sirius-ai/MobileFaceNet_TF` lineage this bundled
     * model comes from) is built around. This has not been re-verified pixel-for-pixel
     * against this specific checkpoint's original training pipeline (no tooling to do that
     * in this environment) but is strictly more correct than rotation-only alignment
     * regardless.
     *
     * Returns null (falls back to [rectCrop]) when ML Kit couldn't resolve both eye
     * landmarks, the eyes are implausibly close together (a degenerate/tiny detection), the
     * implied rotation is implausibly large (more likely noisy landmarks than a real
     * pose), or the transform is singular.
     */
    private fun alignedFaceCrop(bitmap: Bitmap, face: DetectedFace): Bitmap? {
        val lx = face.leftEyeX
        val ly = face.leftEyeY
        val rx = face.rightEyeX
        val ry = face.rightEyeY
        if (lx == null || ly == null || rx == null || ry == null) return null
        if (!lx.isFinite() || !ly.isFinite() || !rx.isFinite() || !ry.isFinite()) return null

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val src = floatArrayOf(lx * w, ly * h, rx * w, ry * h)

        val eyeDx = src[2] - src[0]
        val eyeDy = src[3] - src[1]
        val eyeDist = sqrt(eyeDx * eyeDx + eyeDy * eyeDy)
        if (eyeDist < MIN_EYE_DISTANCE_PX) return null

        val angleDeg = Math.toDegrees(atan2(eyeDy.toDouble(), eyeDx.toDouble())).toFloat()
        if (abs(angleDeg) > MAX_ALIGN_ANGLE_DEGREES) return null

        val dst = floatArrayOf(CANONICAL_LEFT_EYE_X, CANONICAL_LEFT_EYE_Y, CANONICAL_RIGHT_EYE_X, CANONICAL_RIGHT_EYE_Y)
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 2)) return null

        val output = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in vector) sumSq += x * x
        val norm = sqrt(sumSq)
        if (norm < 1e-6f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_ASSET_NAME = "mobilefacenet.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192

        /** For the `RECOGNITION_MODEL=`/`MODEL SUMMARY` diagnostics — see the class doc
         * and `assets/NOTICE_mobilefacenet.md` for the full provenance. */
        const val MODEL_DISPLAY_NAME = "MobileFaceNet (mobilefacenet.tflite)"
        const val MODEL_INPUT_DESCRIPTION = "1x112x112x3 float32, NHWC, RGB"
        const val MODEL_OUTPUT_DESCRIPTION = "1x192 float32, L2-normalized after inference"
        const val MODEL_PREPROCESSING_DESCRIPTION =
            "eye-similarity-transform alignment (rotation+scale+translation to canonical " +
                "112x112 eye positions) when both eye landmarks resolve, else a square-padded " +
                "rect-crop fallback; (pixel-128)/128 per-channel normalization"

        /** Beyond this implied roll, trust ML Kit's landmarks less than a plain crop —
         * a real face this tilted is rare, noisy eye landmarks are not. */
        private const val MAX_ALIGN_ANGLE_DEGREES = 55f

        /** Below this eye-to-eye pixel distance, the detection is too degenerate/tiny for
         * a reliable similarity transform — fall back to [rectCrop] instead of amplifying
         * landmark noise into a wild scale/rotation. */
        private const val MIN_EYE_DISTANCE_PX = 4f

        /**
         * The standard published InsightFace/ArcFace 112x112 alignment template's eye
         * positions — see [alignedFaceCrop]'s doc for why these specific numbers, not a
         * project-specific invention.
         */
        private const val CANONICAL_LEFT_EYE_X = 38.2946f
        private const val CANONICAL_LEFT_EYE_Y = 51.6963f
        private const val CANONICAL_RIGHT_EYE_X = 73.5318f
        private const val CANONICAL_RIGHT_EYE_Y = 51.5014f

        @Throws(IOException::class)
        private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
            context.assets.openFd(assetName).use { fd ->
                FileInputStream(fd.fileDescriptor).use { input ->
                    return input.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength,
                    )
                }
            }
        }
    }
}
