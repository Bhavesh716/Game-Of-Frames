package com.instantcollabmaker.data.video

import android.graphics.Bitmap
import com.instantcollabmaker.domain.processing.DecodedFrame

/**
 * The one concrete [DecodedFrame] producer in the app. Every real [com.instantcollabmaker.domain.processing.FaceDetector],
 * [com.instantcollabmaker.domain.processing.FaceEmbedder] and [com.instantcollabmaker.domain.processing.QualityScorer]
 * downcasts to this type to reach the pixels — see the class doc on `DecodedFrame` for why
 * the interface itself stays free of graphics types.
 */
class VideoFrame(
    override val index: Int,
    override val timestampMs: Long,
    val bitmap: Bitmap,
) : DecodedFrame {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}
