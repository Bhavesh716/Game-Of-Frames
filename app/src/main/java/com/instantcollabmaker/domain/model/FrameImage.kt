package com.instantcollabmaker.domain.model

/**
 * Where the pixels for a [SelectedFrame] come from.
 *
 * This indirection is the single seam that lets Phase 1 ship without any video
 * decoding: the UI renders whichever variant it is handed. Phase 2 extracts real
 * frames, writes them to app-private storage, and returns [Extracted] instead —
 * no screen or component signature changes.
 */
sealed interface FrameImage {

    /**
     * Phase 1 placeholder: a deterministic procedural portrait rendered on-device from
     * a seed, so every appearance looks different and nothing has to be downloaded.
     */
    data class Procedural(
        val seed: Int,
        val paletteIndex: Int,
    ) : FrameImage

    /**
     * Phase 2: a real frame decoded out of the source video and cached on disk.
     *
     * @param path absolute path in app-private storage.
     * @param cropRect normalised (0f..1f) generous portrait crop around the subject —
     *   deliberately not a tight face box, per the product requirement.
     */
    data class Extracted(
        val path: String,
        val cropRect: NormalizedRect = NormalizedRect.Full,
    ) : FrameImage
}

/** Normalised rectangle in source-image space. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
    }
}
