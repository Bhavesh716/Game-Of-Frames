package com.instantcollabmaker.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.instantcollabmaker.domain.model.CollageBackground
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.TileTransform
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Sizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a [CollageLayout] exactly as it will be exported: a fixed-aspect card with the
 * chosen background painted first, tiles placed by their normalised
 * [rect][com.instantcollabmaker.domain.model.CollageTile.rect] on top, and a template's
 * decorative accents (if any) painted last, in its outer margin only. This is the one
 * place [CollageLayout] becomes pixels on screen, so the preview and the saved/shared
 * image never drift apart — and neither one draws a single word of text.
 *
 * Every tile is independently pinch-zoomable/pannable (see [EditableCollageTile]) and can
 * show its own swap icon on tap — [onTransformChange]/[onSwapRequested] report which tile
 * by [com.instantcollabmaker.domain.model.CollageItem.id]; the caller owns the actual
 * per-tile state (see `FullCollageScreen`/`PersonCollageScreen`).
 *
 * Assignment-minimal mode: pass [editable] = `false` (see `SimpleCollageScreen`) to render
 * every tile as a plain, non-interactive [FrameSurface] instead — no gesture detection, no
 * swap icon, [onTransformChange]/[onSwapRequested] simply never fire. The interactive
 * editing path itself is untouched and still fully available for a screen that wants it.
 */
@Composable
fun CollageCanvas(
    layout: CollageLayout,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    onTransformChange: (tileId: String, transform: TileTransform) -> Unit = { _, _ -> },
    onSwapRequested: (tileId: String) -> Unit = {},
) {
    val shape = RoundedCornerShape(Radius.lg)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(layout.aspectRatio)
            .clip(shape)
            .background(flatBackgroundColor(layout.background))
            .border(Sizes.hairline, FtColor.Stroke, shape),
    ) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight

        TemplateArtwork(layout.background)
        ImageBackgroundArt(layout.background)

        layout.tiles.forEach { tile ->
            val accent = FtColor.personAccent(tile.item.accentIndex)
            val tileModifier = Modifier
                .size(
                    width = canvasWidth * tile.rect.width,
                    height = canvasHeight * tile.rect.height,
                )
                .offset(
                    x = canvasWidth * tile.rect.left,
                    y = canvasHeight * tile.rect.top,
                )
            if (editable) {
                EditableCollageTile(
                    frame = tile.item.frame,
                    accent = accent,
                    transform = tile.item.transform,
                    onTransformChange = { onTransformChange(tile.item.id, it) },
                    onSwapRequested = { onSwapRequested(tile.item.id) },
                    // Matches the collage canvas's own outer corner radius (the `shape`
                    // above) so a tile's rounding reads as part of the same design
                    // language as the frame around it, not a different, sharper radius.
                    radius = Radius.lg,
                    contentDescription = tile.item.label,
                    modifier = tileModifier,
                )
            } else {
                // Assignment-minimal mode: read-only, no gesture detection at all.
                FrameSurface(
                    frame = tile.item.frame,
                    accent = accent,
                    radius = Radius.lg,
                    scrim = false,
                    showBorder = false,
                    contentDescription = tile.item.label,
                    modifier = tileModifier,
                )
            }
        }

        if (layout.isEmpty) {
            Text(
                text = "Nothing to show yet",
                style = FtType.bodySmall,
                color = FtColor.TextMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        TemplateEdgeAccents(layout.background)
    }
}

/** The flat container background — for [CollageBackground.Template] this is just the
 * bottom gradient stop, since [TemplatePainting] paints the real gradient on top. */
private fun flatBackgroundColor(background: CollageBackground): Color = when (background) {
    is CollageBackground.None -> Color.Black
    is CollageBackground.Solid -> Color(background.colorArgb)
    is CollageBackground.Template -> Color(background.template.backgroundBottomArgb)
    // Plain black fallback while the asset decodes; ImageBackgroundArt paints over it.
    is CollageBackground.Image -> Color.Black
}

/**
 * Paints a template's top-to-bottom gradient fill plus its full-canvas texture (dots
 * and/or stripes) — drawn *before* any tile is composed on top, so a tile simply covers
 * whatever texture sits underneath it; nothing here needs to know where a face is. A
 * no-op for [CollageBackground.None]/[CollageBackground.Solid], which are flat colors
 * handled by the container's own `background()` in [CollageCanvas].
 */
@Composable
private fun TemplateArtwork(background: CollageBackground) {
    val template = (background as? CollageBackground.Template)?.template ?: return
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(template.backgroundTopArgb), Color(template.backgroundBottomArgb)),
            ),
        )
        for (stripe in template.backgroundStripes) {
            drawLine(
                color = Color(stripe.colorArgb),
                start = Offset(stripe.x1 * size.width, stripe.y1 * size.height),
                end = Offset(stripe.x2 * size.width, stripe.y2 * size.height),
                strokeWidth = stripe.widthFraction * size.width,
            )
        }
        for (decoration in template.backgroundAccents) {
            drawCircle(
                color = Color(decoration.colorArgb),
                radius = decoration.radius * size.width,
                center = Offset(decoration.centerX * size.width, decoration.centerY * size.height),
            )
        }
    }
}

/**
 * Paints a bundled photographic background ([CollageBackground.Image]) full-bleed, cropped
 * to fill the canvas exactly like a tile's own photo — a no-op for every other background
 * variant. Decoding happens off the main thread and is remembered per asset path, same
 * pattern as [rememberDecodedFrame] uses for a person's own cached frame files.
 */
@Composable
private fun ImageBackgroundArt(background: CollageBackground) {
    val assetPath = (background as? CollageBackground.Image)?.assetPath ?: return
    val bitmap = rememberDecodedAsset(assetPath) ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

/** Decodes a bundled asset image off the main thread and remembers the result for as long
 * as [assetPath] is unchanged. Shared by [ImageBackgroundArt] (the full canvas background)
 * and the small picker thumbnails in `CollageControls.kt`. */
@Composable
internal fun rememberDecodedAsset(assetPath: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

/**
 * Paints a template's border tint and its edge accents, confined to the margin
 * [DefaultCollageGenerator][com.instantcollabmaker.data.collage.DefaultCollageGenerator]
 * already reserves outside every tile — drawn last, so it sits visually in front of the
 * photos' surrounding space. A no-op for [CollageBackground.None]/[CollageBackground.Solid].
 */
@Composable
private fun TemplateEdgeAccents(background: CollageBackground) {
    val template = (background as? CollageBackground.Template)?.template ?: return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = size.width * 0.006f
        drawRoundRect(
            color = Color(template.borderArgb),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(size.width * 0.02f, size.width * 0.02f),
            style = Stroke(width = strokeWidth),
        )
        for (decoration in template.edgeAccents) {
            drawCircle(
                color = Color(decoration.colorArgb),
                radius = decoration.radius * size.width,
                center = Offset(decoration.centerX * size.width, decoration.centerY * size.height),
            )
        }
    }
}
