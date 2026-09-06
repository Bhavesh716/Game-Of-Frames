package com.instantcollabmaker.ui.components

/**
 * Independent zoom/pan state for one editable image (a collage tile, or the fullscreen
 * album viewer) — plain floats, no Compose/Android dependency, so the actual math is
 * directly unit-testable. Each editable image gets its own instance; nothing here is
 * shared/global state (Part 4/7: "each collage item maintains its OWN transform state").
 */
data class ZoomPanState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/**
 * Applies one pinch/pan gesture update, zooming around ([centroidX], [centroidY]) — the
 * gesture's own touch centroid, in the element's own top-left-origin coordinate space —
 * rather than always around the element's center, so the point under the user's fingers
 * stays put as they zoom (never center-only zoom). ([pivotX], [pivotY]) is the element's
 * own transform pivot in that same coordinate space (its center, matching Compose's
 * default `TransformOrigin(0.5f, 0.5f)` for a `graphicsLayer` with no override).
 *
 * Always returns a finite, bounded result — a non-finite (NaN/Infinity) gesture value is
 * rejected outright (the state is returned unchanged) rather than propagating a corrupt
 * transform into rendering.
 */
fun ZoomPanState.applyGesture(
    centroidX: Float,
    centroidY: Float,
    panX: Float,
    panY: Float,
    zoomChange: Float,
    pivotX: Float,
    pivotY: Float,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    maxAbsOffset: Float = MAX_ABS_OFFSET,
): ZoomPanState {
    if (!zoomChange.isFinite() || zoomChange <= 0f) return this
    if (!centroidX.isFinite() || !centroidY.isFinite() || !panX.isFinite() || !panY.isFinite()) return this
    if (!pivotX.isFinite() || !pivotY.isFinite()) return this

    // A corrupt starting state (should never happen, but this is the crash-safety net —
    // Part 7/16) is treated as a fresh, untransformed one rather than propagated.
    val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
    val safeOffsetX = if (offsetX.isFinite()) offsetX else 0f
    val safeOffsetY = if (offsetY.isFinite()) offsetY else 0f

    val newScale = (safeScale * zoomChange).coerceIn(minScale.coerceAtLeast(0.01f), maxScale)
    val k = newScale / safeScale

    var newOffsetX = (centroidX - pivotX) * (1f - k) + safeOffsetX * k + panX
    var newOffsetY = (centroidY - pivotY) * (1f - k) + safeOffsetY * k + panY
    if (!newOffsetX.isFinite()) newOffsetX = 0f
    if (!newOffsetY.isFinite()) newOffsetY = 0f

    return ZoomPanState(
        scale = newScale,
        offsetX = newOffsetX.coerceIn(-maxAbsOffset, maxAbsOffset),
        offsetY = newOffsetY.coerceIn(-maxAbsOffset, maxAbsOffset),
    )
}

/** A safety ceiling on raw pixel offset — generous enough to never visibly clip normal
 * panning, tight enough that a corrupt/extreme gesture value can never produce an
 * unbounded translation. */
const val MAX_ABS_OFFSET = 20_000f
