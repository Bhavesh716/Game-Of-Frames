package com.instantcollabmaker.domain.model

/**
 * A real frame decoded out of the source video, generously cropped around its subject
 * and cached in app-private storage. The crop is baked in at extraction time (see
 * `com.instantcollabmaker.data.pipeline.RealVideoProcessor`) — deliberately never a tight
 * face box — so every renderer downstream (on-screen tiles, the exported collage bitmap)
 * just decodes [path] and cover-fits it around [anchorX]/[anchorY], with no further crop
 * maths of its own.
 *
 * @param path absolute path to a JPEG in app-private cache storage.
 * @param anchorX normalized (0f..1f) horizontal position of the subject's face center
 *   *within this already-cropped image* — not the raw image center. A renderer that
 *   later has to crop again (e.g. fitting a portrait crop into a wide collage tile) must
 *   crop around this point, not the image's geometric center, or the face silently drifts
 *   off-center. Defaults to 0.5 (the old blind-center behavior) for any image that never
 *   recorded a real anchor.
 * @param anchorY same, vertically. Deliberately not always 0.5 even for a
 *   well-centered face — the capture-time crop leaves more room below the face than
 *   above it (for shoulders), so the true anchor sits slightly above the image's
 *   vertical midpoint by design.
 */
data class FrameImage(
    val path: String,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
)

/** Normalised rectangle, used for collage tile layout geometry. */
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
