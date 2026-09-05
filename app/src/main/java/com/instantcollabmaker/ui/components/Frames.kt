package com.instantcollabmaker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Sizes

/**
 * The one place a selected frame becomes pixels on screen.
 *
 * Every thumbnail, hero image and collage tile in the app goes through here, so when
 * Phase 2 starts producing real extracted frames only [drawFrameImage] changes.
 *
 * @param scrim adds a bottom-up darkening ramp so captions laid over the image stay
 *   legible without a solid bar.
 */
@Composable
fun FrameSurface(
    frame: SelectedFrame,
    accent: Color,
    modifier: Modifier = Modifier,
    radius: Dp = Radius.md,
    scrim: Boolean = false,
    showBorder: Boolean = true,
    contentDescription: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(FtColor.SurfaceElevated)
            .then(
                if (showBorder) Modifier.border(Sizes.hairline, FtColor.Stroke, shape)
                else Modifier
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawFrameImage(frame.image, accent)
        }
        if (scrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.34f),
                            1f to Color.Black.copy(alpha = 0.74f),
                        )
                    )
            )
        }
        overlay()
    }
}

/**
 * Square-ish rounded thumbnail for list rows.
 *
 * The crop is intentionally generous — the underlying portrait keeps head *and*
 * shoulders in frame — rather than a tight face box, per the product rule.
 */
@Composable
fun FrameThumbnail(
    frame: SelectedFrame,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.personThumb,
    radius: Dp = Radius.md,
    contentDescription: String? = null,
) {
    FrameSurface(
        frame = frame,
        accent = accent,
        modifier = modifier.size(width = size * 0.82f, height = size),
        radius = radius,
        contentDescription = contentDescription,
    )
}

/**
 * A stand-in for the source video itself, used on the home hero and wherever the
 * selected clip is previewed. It reuses the portrait renderer so the app never shows a
 * grey placeholder rectangle, then lays a play glyph and letterbox cues over it.
 */
@Composable
fun VideoPosterSurface(
    seed: Int,
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    accent: Color = FtColor.personAccent(0),
    contentDescription: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(FtColor.SurfaceElevated)
            .border(Sizes.hairline, FtColor.Stroke, shape)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawProceduralPortrait(seed = seed, paletteIndex = 0, accent = accent)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    )
                )
        )
        overlay()
    }
}

/** Kept explicit so callers do not accidentally reach for a bitmap-scaling default. */
internal val FrameContentScale: ContentScale = ContentScale.Crop
