package com.instantcollabmaker.data.collage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.instantcollabmaker.domain.export.CollageExporter
import com.instantcollabmaker.domain.model.CollageBackground
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.CollageTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Renders a [CollageLayout] to a real, shareable [Bitmap] using classic
 * `android.graphics` calls — the on-screen preview (`CollageCanvas`) is a Compose
 * `DrawScope`, which cannot itself produce a standalone bitmap outside a composition, so
 * this is an independent renderer over the exact same tile geometry and background choice
 * rather than a screenshot of the preview. Both consume the same [CollageLayout], which
 * is what keeps "what you see is what you save" true even though the two drawing
 * backends are different.
 *
 * **Contains zero text, by construction** — there is no font/typeface anywhere in this
 * file. The exported bitmap is background, a template's decorative accents (if any), and
 * the selected person photos, and nothing else: no names, no counts, no watermark.
 */
class CanvasCollageExporter(private val context: Context) : CollageExporter {

    override suspend fun export(layout: CollageLayout, targetWidthPx: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            if (layout.isEmpty) return@withContext null
            try {
                val w = targetWidthPx.coerceAtLeast(MIN_WIDTH_PX)
                val h = (w / layout.aspectRatio).roundToInt()
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawBackground(canvas, layout.background, w, h)
                drawBackgroundArtwork(canvas, layout.background, w, h)
                drawImageBackground(canvas, layout.background, w, h)
                // Part 13: one tile's image failing must never abort the whole export —
                // drawTile already degrades a single failed tile to a plain background
                // fill (see its own try-free but null-checked bitmap handling below)
                // rather than throwing, so every *other* person's tile still renders.
                for (tile in layout.tiles) drawTile(canvas, tile, w, h)
                drawEdgeAccents(canvas, layout.background, w, h)
                bitmap
            } catch (e: Exception) {
                Log.w(TAG, "Collage export failed entirely: ${e::class.simpleName}: ${e.message}")
                null
            }
        }

    private fun drawBackground(canvas: Canvas, background: CollageBackground, w: Int, h: Int) {
        when (background) {
            is CollageBackground.None -> canvas.drawColor(Color.BLACK)
            is CollageBackground.Solid -> canvas.drawColor(background.colorArgb)
            is CollageBackground.Template -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        background.template.backgroundTopArgb,
                        background.template.backgroundBottomArgb,
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            // Plain black fallback if the asset fails to decode; drawImageBackground
            // paints the real photo over this right after.
            is CollageBackground.Image -> canvas.drawColor(Color.BLACK)
        }
    }

    /**
     * Paints a bundled photographic background ([CollageBackground.Image]) full-bleed,
     * cropped-to-cover the entire canvas — a no-op for every other background variant.
     * Mirrors the Compose preview's `rememberDecodedAsset` + `ContentScale.Crop`, so
     * what's previewed is what gets saved/shared.
     */
    private fun drawImageBackground(canvas: Canvas, background: CollageBackground, w: Int, h: Int) {
        val assetPath = (background as? CollageBackground.Image)?.assetPath ?: return
        val src = try {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.w(TAG, "COLLAGE_BACKGROUND_IMAGE_FAILURE assetPath=$assetPath reason=${e::class.simpleName}: ${e.message}")
            null
        } ?: return
        if (src.width <= 0 || src.height <= 0) {
            src.recycle()
            return
        }
        val targetAspect = w.toFloat() / h
        val srcAspect = src.width.toFloat() / src.height
        val cropRect = if (srcAspect > targetAspect) {
            val cropW = (src.height * targetAspect).roundToInt().coerceIn(1, src.width)
            val x = (src.width - cropW) / 2
            Rect(x, 0, x + cropW, src.height)
        } else {
            val cropH = (src.width / targetAspect).roundToInt().coerceIn(1, src.height)
            val y = (src.height - cropH) / 2
            Rect(0, y, src.width, y + cropH)
        }
        val destRect = Rect(0, 0, w, h)
        canvas.drawBitmap(src, cropRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
    }

    /**
     * A template's full-canvas texture — dots and/or stripes spanning the entire
     * artwork, including the space every tile will later occupy. Drawn strictly before
     * any tile, so a tile simply paints over whatever texture sits underneath it; no
     * decoration is individually positioned to dodge a face, because nothing needs to
     * be — occlusion by the tile itself is the safety mechanism.
     */
    private fun drawBackgroundArtwork(canvas: Canvas, background: CollageBackground, w: Int, h: Int) {
        val template = (background as? CollageBackground.Template)?.template ?: return
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        for (stripe in template.backgroundStripes) {
            stripePaint.color = stripe.colorArgb
            stripePaint.strokeWidth = stripe.widthFraction * w
            canvas.drawLine(stripe.x1 * w, stripe.y1 * h, stripe.x2 * w, stripe.y2 * h, stripePaint)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (decoration in template.backgroundAccents) {
            dotPaint.color = decoration.colorArgb
            canvas.drawCircle(decoration.centerX * w, decoration.centerY * h, decoration.radius * w, dotPaint)
        }
    }

    /** A template's border tint and its edge accents — both confined to the margin
     * [DefaultCollageGenerator] already reserves outside every tile, drawn last so they
     * sit visually in front of the photos' surrounding space. */
    private fun drawEdgeAccents(canvas: Canvas, background: CollageBackground, w: Int, h: Int) {
        val template = (background as? CollageBackground.Template)?.template ?: return
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.006f
            color = template.borderArgb
        }
        val inset = borderPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, w - inset, h - inset),
            w * 0.02f, w * 0.02f, borderPaint,
        )

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (decoration in template.edgeAccents) {
            dotPaint.color = decoration.colorArgb
            canvas.drawCircle(decoration.centerX * w, decoration.centerY * h, decoration.radius * w, dotPaint)
        }
    }

    private fun drawTile(canvas: Canvas, tile: CollageTile, canvasW: Int, canvasH: Int) {
        val rect = RectF(
            tile.rect.left * canvasW,
            tile.rect.top * canvasH,
            tile.rect.right * canvasW,
            tile.rect.bottom * canvasH,
        )
        if (rect.width() <= 1f || rect.height() <= 1f) return
        val radius = canvasW * TILE_CORNER_RADIUS_FRACTION
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(clip)
        canvas.drawColor(Palette.surfaceElevated)

        val bitmap = decodeCover(
            tile.item.frame.image.path,
            rect.width().roundToInt().coerceAtLeast(1),
            rect.height().roundToInt().coerceAtLeast(1),
            tile.item.frame.image.anchorX,
            tile.item.frame.image.anchorY,
        )
        if (bitmap != null) {
            // The user's own pinch-zoom/pan edit for this tile (see EditableCollageTile /
            // TileTransform) — applied around the tile's own center, exactly mirroring the
            // Compose preview's graphicsLayer math, so what was edited is what gets saved.
            // A no-op (identity) when the tile was never touched.
            val transform = tile.item.transform
            canvas.save()
            if (transform.scale != 1f || transform.offsetXFraction != 0f || transform.offsetYFraction != 0f) {
                val pivotX = rect.left + rect.width() / 2f
                val pivotY = rect.top + rect.height() / 2f
                val safeScale = if (transform.scale.isFinite() && transform.scale > 0f) transform.scale else 1f
                val tx = (if (transform.offsetXFraction.isFinite()) transform.offsetXFraction else 0f) * rect.width() * safeScale
                val ty = (if (transform.offsetYFraction.isFinite()) transform.offsetYFraction else 0f) * rect.height() * safeScale
                val matrix = Matrix().apply {
                    postScale(safeScale, safeScale, pivotX, pivotY)
                    postTranslate(tx, ty)
                }
                canvas.concat(matrix)
            }
            canvas.drawBitmap(bitmap, rect.left, rect.top, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            bitmap.recycle()
        } else {
            // Part 13: this tile only — a plain background fill (already drawn above)
            // stands in for the missing photo instead of aborting the whole collage.
            Log.w(TAG, "COLLAGE_IMAGE_FAILURE personId=${tile.item.id} timestamp=${tile.item.frame.timestampMs} reason=decode_or_crop_failed path=${tile.item.frame.image.path}")
        }
        canvas.restore()

        canvas.drawPath(
            clip,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = canvasW * 0.0015f
                color = Palette.stroke
            },
        )
    }

    /**
     * Decodes then crops+scales a source file to exactly fill [targetW]x[targetH],
     * cropping around ([anchorX], [anchorY]) — the subject's face center within that
     * source file — rather than the file's raw geometric center, and clamped so the crop
     * window never runs outside the source bounds. This is what keeps a face centered
     * even when a tile's aspect ratio differs from the aspect ratio the face was
     * originally cropped at (e.g. a wide hero tile fed a portrait-shaped source crop).
     */
    private fun decodeCover(path: String, targetW: Int, targetH: Int, anchorX: Float, anchorY: Float): Bitmap? {
        val src = BitmapFactory.decodeFile(path) ?: return null
        val srcW = src.width
        val srcH = src.height
        if (srcW <= 0 || srcH <= 0) {
            src.recycle()
            return null
        }
        val targetAspect = targetW.toFloat() / targetH
        val srcAspect = srcW.toFloat() / srcH

        val cropRect = if (srcAspect > targetAspect) {
            val cropW = (srcH * targetAspect).roundToInt().coerceIn(1, srcW)
            val x = (anchorX * srcW - cropW / 2f).roundToInt().coerceIn(0, srcW - cropW)
            Rect(x, 0, x + cropW, srcH)
        } else {
            val cropH = (srcW / targetAspect).roundToInt().coerceIn(1, srcH)
            val y = (anchorY * srcH - cropH / 2f).roundToInt().coerceIn(0, srcH - cropH)
            Rect(0, y, srcW, y + cropH)
        }

        val cropped = Bitmap.createBitmap(src, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        val scaled = if (cropped.width == targetW && cropped.height == targetH) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        }
        if (scaled !== cropped) cropped.recycle()
        if (src !== cropped) src.recycle()
        return scaled
    }

    /** Mirrors `FtColor` (`ui/theme/Color.kt`) — kept in sync by hand since this file is
     * outside Compose and cannot reference `androidx.compose.ui.graphics.Color` directly. */
    private object Palette {
        val surfaceElevated = Color.parseColor("#181C24")
        val stroke = Color.parseColor("#23262F")
    }

    companion object {
        private const val TAG = "CollageExportSafety"
        private const val MIN_WIDTH_PX = 360

        /** Matches `Radius.lg` (22dp, the Compose preview's own tile/canvas corner
         * radius) as a fraction of canvas width, so an exported tile's rounding looks
         * the same as what was previewed rather than a noticeably sharper corner. */
        private const val TILE_CORNER_RADIUS_FRACTION = 0.058f
    }
}
