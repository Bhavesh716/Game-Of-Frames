package com.instantcollabmaker.ui.components

import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.instantcollabmaker.core.coerceInSafe
import com.instantcollabmaker.ui.theme.FtColor
import kotlin.math.max
import kotlin.math.min

/**
 * Procedural portrait renderer, used only for the decorative home-screen video poster
 * (which has no real frame to show before a video is even picked). Real analysis results
 * always render through [drawFrameImage] instead.
 *
 * Two independent seeds drive the drawing, which is the whole trick:
 *
 *  - `paletteIndex` fixes the *identity* traits (skin tone, hair, build), so Person C
 *    looks like the same human in all four of their appearances.
 *  - `seed` fixes the *shot* traits (framing, zoom, head tilt, key-light direction,
 *    background bokeh), so no two appearances are the same picture.
 *
 * The result reads as a set of moody stills of five different people rather than five
 * repeated icons. Framing is deliberately loose — head and shoulders inside a generous
 * portrait crop — matching the product rule against tight face-only crops.
 *
 * Being a plain [DrawScope] extension, this same code paints the on-screen tiles and
 * the exported collage bitmap, so what the user saves is exactly what they saw.
 */
fun DrawScope.drawProceduralPortrait(
    seed: Int,
    paletteIndex: Int,
    accent: Color,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val identity = Identity.of(paletteIndex)
    val shot = Shot.of(seed)

    drawBackdrop(accent, shot)
    drawBokeh(accent, shot)

    // The subject sits slightly above centre with room above the head and shoulders
    // running off the bottom edge: a generous portrait crop, never a face box.
    val cx = w * (0.5f + shot.offsetX)
    val headRy = h * 0.135f * shot.zoom
    val headRx = headRy * 0.78f * identity.headWidth
    val cy = h * (0.395f + shot.offsetY)

    rotate(degrees = shot.tilt, pivot = Offset(cx, cy + headRy * 2.2f)) {
        drawBody(cx, cy, headRx, headRy, identity, accent, shot)
        drawHead(cx, cy, headRx, headRy, identity, shot)
    }

    drawKeyLight(accent, shot)
    drawVignette()
    drawGrain(shot)
}

// ---------------------------------------------------------------------------------
// Backdrop
// ---------------------------------------------------------------------------------

private fun DrawScope.drawBackdrop(accent: Color, shot: Shot) {
    val top = lerp(FtColor.Background, accent, 0.10f + shot.bgTint * 0.06f)
    val mid = lerp(FtColor.Background, accent, 0.20f + shot.bgTint * 0.10f)
    val bottom = lerp(Color.Black, accent, 0.07f)
    drawRect(
        brush = Brush.verticalGradient(
            0f to top,
            0.55f to mid,
            1f to bottom,
        )
    )
}

/** Soft out-of-focus shapes suggesting a real environment behind the subject. */
private fun DrawScope.drawBokeh(accent: Color, shot: Shot) {
    val w = size.width
    val h = size.height
    val tint = lerp(accent, Color.White, 0.35f)
    shot.bokeh.forEach { b ->
        val radius = min(w, h) * b.radius
        // Concentric fades stand in for a real blur without an expensive shader.
        for (ring in 3 downTo 1) {
            drawCircle(
                color = tint.copy(alpha = 0.030f * ring / 3f),
                radius = radius * (0.6f + ring * 0.22f),
                center = Offset(w * b.x, h * b.y),
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// Subject
// ---------------------------------------------------------------------------------

private fun DrawScope.drawBody(
    cx: Float,
    cy: Float,
    headRx: Float,
    headRy: Float,
    identity: Identity,
    accent: Color,
    shot: Shot,
) {
    val h = size.height
    val neckTop = cy + headRy * 0.72f
    val shoulderY = cy + headRy * 2.05f
    val neckHalf = headRx * 0.42f
    val shoulderHalf = headRx * 2.9f * identity.shoulderWidth

    // Neck, in shadowed skin.
    drawRoundedColumn(
        centerX = cx,
        top = neckTop,
        bottom = shoulderY + headRy * 0.2f,
        halfWidth = neckHalf,
        color = identity.skinShadow,
    )

    // Torso: trapezius curving up into the neck, running off the bottom of the frame.
    val clothing = lerp(lerp(FtColor.Background, accent, 0.28f), Color.Black, 0.28f)
    val torso = Path().apply {
        moveTo(cx - shoulderHalf, h)
        lineTo(cx - shoulderHalf, shoulderY + headRy * 0.55f)
        quadraticTo(
            cx - shoulderHalf * 0.62f, shoulderY - headRy * 0.05f,
            cx - neckHalf * 1.25f, neckTop + headRy * 0.35f,
        )
        lineTo(cx + neckHalf * 1.25f, neckTop + headRy * 0.35f)
        quadraticTo(
            cx + shoulderHalf * 0.62f, shoulderY - headRy * 0.05f,
            cx + shoulderHalf, shoulderY + headRy * 0.55f,
        )
        lineTo(cx + shoulderHalf, h)
        close()
    }
    drawPath(torso, clothing)

    // Collar highlight picks the shoulder line out of the dark background.
    drawPath(
        path = torso,
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.055f),
            0.35f to Color.Transparent,
            startY = shoulderY - headRy * 0.4f,
            endY = h,
        ),
    )

    // Long hair falls in front of the shoulders, so it is drawn after the torso.
    if (identity.hairStyle == HairStyle.Long) {
        val fallTo = shoulderY + headRy * 0.85f
        listOf(-1f, 1f).forEach { side ->
            drawRoundedColumn(
                centerX = cx + side * headRx * 0.92f,
                top = cy - headRy * 0.25f,
                bottom = fallTo,
                halfWidth = headRx * 0.34f,
                color = identity.hair,
            )
        }
    }

    drawRimLight(cx, shoulderY, shoulderHalf, headRy, shot)
}

/** A vertical capsule, used for the neck and for hair falling past the jaw. */
private fun DrawScope.drawRoundedColumn(
    centerX: Float,
    top: Float,
    bottom: Float,
    halfWidth: Float,
    color: Color,
) {
    val height = (bottom - top).coerceAtLeast(0f)
    if (height <= 0f || halfWidth <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - halfWidth, top),
        size = Size(halfWidth * 2f, height),
        cornerRadius = CornerRadius(halfWidth, halfWidth),
    )
}

private fun DrawScope.drawHead(
    cx: Float,
    cy: Float,
    headRx: Float,
    headRy: Float,
    identity: Identity,
    shot: Shot,
) {
    val faceRect = Rect(cx - headRx, cy - headRy, cx + headRx, cy + headRy)

    // Hair mass first, then the face oval on top — that overlap creates the hairline.
    val hairSpread = when (identity.hairStyle) {
        HairStyle.Cropped -> 1.03f
        HairStyle.Volume -> 1.24f
        HairStyle.Long -> 1.12f
        HairStyle.Swept -> 1.09f
    }
    val hairLift = when (identity.hairStyle) {
        HairStyle.Volume -> 0.30f
        HairStyle.Cropped -> 0.14f
        else -> 0.20f
    }
    drawOval(
        color = identity.hair,
        topLeft = Offset(cx - headRx * hairSpread, cy - headRy * (1f + hairLift)),
        size = Size(headRx * 2f * hairSpread, headRy * 2f * (1f + hairLift * 0.35f)),
    )

    // Face, with a soft light-to-shadow ramp across it.
    drawOval(
        brush = Brush.linearGradient(
            colors = if (shot.lightFromLeft) {
                listOf(identity.skinLit, identity.skin, identity.skinShadow)
            } else {
                listOf(identity.skinShadow, identity.skin, identity.skinLit)
            },
            start = Offset(faceRect.left, faceRect.top),
            end = Offset(faceRect.right, faceRect.bottom),
        ),
        topLeft = faceRect.topLeft,
        size = faceRect.size,
    )

    // Jaw: a narrower oval low on the face, so heads are not perfect eggs.
    drawOval(
        color = identity.skin.copy(alpha = 0.85f),
        topLeft = Offset(cx - headRx * 0.86f, cy - headRy * 0.1f),
        size = Size(headRx * 1.72f, headRy * 1.05f),
    )

    // Fringe: the part of the hair mass that overlaps the forehead.
    val fringe = Path().apply {
        addOval(
            Rect(
                cx - headRx * hairSpread, cy - headRy * (1f + hairLift),
                cx + headRx * hairSpread, cy - headRy * (0.10f - identity.fringeDepth),
            )
        )
    }
    val faceClip = Path().apply { addOval(faceRect) }
    drawPath(
        path = Path().apply { op(fringe, faceClip, PathOperation.Intersect) },
        color = identity.hair,
    )

    drawFeatures(cx, cy, headRx, headRy, identity, shot)
}

/**
 * Features are drawn at very low contrast on purpose: enough for the eye to read a
 * face, far short of a cartoon.
 */
private fun DrawScope.drawFeatures(
    cx: Float,
    cy: Float,
    headRx: Float,
    headRy: Float,
    identity: Identity,
    shot: Shot,
) {
    val eyeY = cy - headRy * 0.10f
    val eyeDx = headRx * 0.40f
    val eyeRx = headRx * 0.17f
    val eyeRy = headRy * 0.070f

    listOf(-1f, 1f).forEach { side ->
        // Brow shadow.
        drawOval(
            color = identity.skinShadow.copy(alpha = 0.55f),
            topLeft = Offset(cx + side * eyeDx - eyeRx, eyeY - eyeRy * 3.1f),
            size = Size(eyeRx * 2f, eyeRy * 1.5f),
        )
        // Eye.
        drawOval(
            color = identity.feature.copy(alpha = 0.72f),
            topLeft = Offset(cx + side * eyeDx - eyeRx, eyeY - eyeRy),
            size = Size(eyeRx * 2f, eyeRy * 2f),
        )
        // Catchlight, on the lit side only.
        val litSide = if (shot.lightFromLeft) -1f else 1f
        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = eyeRy * 0.34f,
            center = Offset(cx + side * eyeDx + litSide * eyeRx * 0.28f, eyeY - eyeRy * 0.2f),
        )
    }

    // Nose shadow, falling away from the key light.
    val noseSide = if (shot.lightFromLeft) 1f else -1f
    drawOval(
        color = identity.skinShadow.copy(alpha = 0.40f),
        topLeft = Offset(cx + noseSide * headRx * 0.02f, cy + headRy * 0.02f),
        size = Size(headRx * 0.20f, headRy * 0.30f),
    )

    // Mouth. Width tracks the shot's seeded expression value, so a frame selected for
    // a pleasant expression genuinely looks different from a neutral one.
    val mouthW = headRx * (0.34f + shot.smile * 0.22f)
    val mouthY = cy + headRy * 0.50f
    drawOval(
        color = lerp(identity.skinShadow, identity.mouth, 0.7f).copy(alpha = 0.70f),
        topLeft = Offset(cx - mouthW, mouthY - headRy * 0.045f),
        size = Size(mouthW * 2f, headRy * (0.09f + shot.smile * 0.05f)),
    )
    if (shot.smile > 0.5f) {
        // Upturned corners.
        listOf(-1f, 1f).forEach { side ->
            drawCircle(
                color = identity.skinShadow.copy(alpha = 0.35f),
                radius = headRy * 0.035f,
                center = Offset(cx + side * mouthW * 0.98f, mouthY - headRy * 0.03f),
            )
        }
    }
}

/** A bright edge separating the subject from the backdrop. Sells the "still frame" look. */
private fun DrawScope.drawRimLight(
    cx: Float,
    shoulderY: Float,
    shoulderHalf: Float,
    headRy: Float,
    shot: Shot,
) {
    val side = if (shot.lightFromLeft) -1f else 1f
    val x = cx + side * shoulderHalf
    for (pass in 3 downTo 1) {
        drawLine(
            color = Color.White.copy(alpha = 0.05f / pass),
            start = Offset(x, shoulderY + headRy * 0.5f),
            end = Offset(x, size.height),
            strokeWidth = pass * min(size.width, size.height) * 0.006f,
        )
    }
}

// ---------------------------------------------------------------------------------
// Atmosphere
// ---------------------------------------------------------------------------------

private fun DrawScope.drawKeyLight(accent: Color, shot: Shot) {
    val w = size.width
    val h = size.height
    val originX = if (shot.lightFromLeft) w * 0.16f else w * 0.84f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                lerp(Color.White, accent, 0.5f).copy(alpha = 0.10f),
                Color.Transparent,
            ),
            center = Offset(originX, h * 0.20f),
            radius = max(w, h) * 0.78f,
        )
    )
}

private fun DrawScope.drawVignette() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            center = Offset(w * 0.5f, h * 0.42f),
            radius = max(w, h) * 0.72f,
        )
    )
}

/** A sparse deterministic dot field standing in for film grain. */
private fun DrawScope.drawGrain(shot: Shot) {
    val w = size.width
    val h = size.height
    val dot = max(min(w, h) * 0.0035f, 0.5f)
    val rng = Rng(shot.grainSeed)
    repeat(GRAIN_DOTS) {
        drawCircle(
            color = Color.White.copy(alpha = 0.018f + rng.next() * 0.020f),
            radius = dot,
            center = Offset(rng.next() * w, rng.next() * h),
        )
    }
}

private const val GRAIN_DOTS = 90

// ---------------------------------------------------------------------------------
// Seeded traits
// ---------------------------------------------------------------------------------

private enum class HairStyle { Cropped, Volume, Long, Swept }

/** Traits that make one person recognisable across all of their appearances. */
private class Identity(
    val skin: Color,
    val skinLit: Color,
    val skinShadow: Color,
    val hair: Color,
    val mouth: Color,
    val feature: Color,
    val hairStyle: HairStyle,
    val headWidth: Float,
    val shoulderWidth: Float,
    val fringeDepth: Float,
) {
    companion object {
        private val skinTones = listOf(
            Color(0xFFC49A7E),
            Color(0xFF8A5F45),
            Color(0xFFD8B394),
            Color(0xFF6E4B37),
            Color(0xFFB0836A),
            Color(0xFF9B7256),
            Color(0xFFE0C0A4),
        )
        private val hairTones = listOf(
            Color(0xFF1E1A18),
            Color(0xFF2E2320),
            Color(0xFF4A342A),
            Color(0xFF15120F),
            Color(0xFF3A2A22),
            Color(0xFF262019),
            Color(0xFF5A4436),
        )

        fun of(paletteIndex: Int): Identity {
            val i = ((paletteIndex % skinTones.size) + skinTones.size) % skinTones.size
            val rng = Rng(paletteIndex * 7919 + 17)
            val skin = skinTones[i]
            return Identity(
                // Everything is pulled toward the dark backdrop; these are night stills.
                skin = lerp(skin, Color.Black, 0.30f),
                skinLit = lerp(skin, Color.White, 0.14f),
                skinShadow = lerp(skin, Color.Black, 0.58f),
                hair = lerp(hairTones[i], Color.Black, 0.20f),
                mouth = lerp(skin, Color(0xFF7A3B36), 0.55f),
                feature = Color(0xFF14100E),
                hairStyle = HairStyle.entries[i % HairStyle.entries.size],
                headWidth = rng.range(0.94f, 1.06f),
                shoulderWidth = rng.range(0.92f, 1.12f),
                fringeDepth = rng.range(0.02f, 0.16f),
            )
        }
    }
}

private class BokehBlob(val x: Float, val y: Float, val radius: Float)

/** Traits that make one shot different from the next shot of the same person. */
private class Shot(
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
    val tilt: Float,
    val lightFromLeft: Boolean,
    val bgTint: Float,
    val smile: Float,
    val grainSeed: Int,
    val bokeh: List<BokehBlob>,
) {
    companion object {
        fun of(seed: Int): Shot {
            val rng = Rng(seed)
            return Shot(
                zoom = rng.range(0.88f, 1.14f),
                offsetX = rng.range(-0.075f, 0.075f),
                offsetY = rng.range(-0.045f, 0.055f),
                tilt = rng.range(-6.5f, 6.5f),
                lightFromLeft = rng.next() < 0.5f,
                bgTint = rng.next(),
                smile = rng.next(),
                grainSeed = seed * 31 + 7,
                bokeh = List(3) {
                    BokehBlob(
                        x = rng.range(-0.05f, 1.05f),
                        y = rng.range(0.02f, 0.7f),
                        radius = rng.range(0.10f, 0.26f),
                    )
                },
            )
        }
    }
}

/** Tiny deterministic LCG. Same seed, same picture, on every device and every frame. */
private class Rng(seed: Int) {
    private var state: Int = seed * 1_664_525 + 1_013_904_223

    fun next(): Float {
        state = state * 1_664_525 + 1_013_904_223
        return ((state ushr 8) and 0xFFFFFF) / 16_777_216f
    }

    fun range(from: Float, to: Float): Float = from + (to - from) * next()
}

// ---------------------------------------------------------------------------------
// Entry point used by the tiles
// ---------------------------------------------------------------------------------

/**
 * Draws a decoded real video frame, cover-fitted (scaled so the shorter axis fills the
 * surface, no distortion) and positioned so [anchor] — the subject's face center within
 * the source image, normalized 0f..1f — lands as close to the surface's own center as
 * the source image's bounds allow. This is deliberately *not* a blind image-center crop:
 * a tile shaped differently than the source crop (e.g. a wide hero slot fed a portrait
 * crop) would otherwise cut off exactly the part of the image the face lives in.
 * [image] is `null` while the bitmap is still being decoded off the main thread, or if
 * decoding failed — an honest empty plate in that case, never a fake portrait.
 */
fun DrawScope.drawFrameImage(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    accent: Color,
    anchor: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
) {
    if (image == null) {
        drawRect(FtColor.SurfaceElevated)
        return
    }
    val srcW = image.width.toFloat()
    val srcH = image.height.toFloat()
    val dstW = size.width
    val dstH = size.height
    // Must never crash regardless of input data: a zero-size decoded bitmap (a corrupt
    // or truncated cache file) or a zero-size layout (measured before this composable's
    // real constraints are known) are both real possibilities, not just theoretical ones
    // — render the same honest empty plate as the null-image case rather than drawing
    // nothing silently or, worse, dividing by zero below.
    if (srcW <= 0f || srcH <= 0f) {
        Log.w(RENDER_SAFETY_TAG, "INVALID_FRAME_RENDER reason=zero_bitmap_size srcW=$srcW srcH=$srcH")
        drawRect(FtColor.SurfaceElevated)
        return
    }
    if (dstW <= 0f || dstH <= 0f) return
    val scale = max(dstW / srcW, dstH / srcH)
    val drawW = srcW * scale
    val drawH = srcH * scale
    // Anchor is untrusted input (persisted to disk, could in principle carry a stale/
    // corrupt NaN if a future writer ever miscalculates it) — never let a non-finite
    // value reach the arithmetic below.
    val anchorX = if (anchor.x.isFinite()) anchor.x.coerceIn(0f, 1f) else 0.5f
    val anchorY = if (anchor.y.isFinite()) anchor.y.coerceIn(0f, 1f) else 0.5f
    // Where the anchor would land if drawn at its own natural position, then clamped so
    // the scaled image always still fully covers the destination (no letterboxing) —
    // the same guarantee a blind center-crop gives, just centered on the face instead of
    // the raw image when there's room to do so.
    //
    // `dstW - drawW` and `dstH - drawH` are *mathematically* guaranteed <= 0 here (drawW/
    // drawH cover dstW/dstH by construction via `scale`), but float rounding on the
    // scale multiplication can leave a few ULPs of positive slop — exactly the crash this
    // guards against ("Cannot coerce value to an empty range: maximum 0.0 is less than
    // minimum 1.5258789E-5"). coerceInSafe absorbs that instead of throwing.
    val idealOffsetX = dstW / 2f - anchorX * drawW
    val idealOffsetY = dstH / 2f - anchorY * drawH
    val offsetX = idealOffsetX.coerceInSafe(dstW - drawW, 0f)
    val offsetY = idealOffsetY.coerceInSafe(dstH - drawH, 0f)
    drawImage(
        image = image,
        dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)),
    )
}

private const val RENDER_SAFETY_TAG = "FrameRenderSafety"
