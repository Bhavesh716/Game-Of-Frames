package com.instantcollabmaker.data.collage

import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.CollageSpec
import com.instantcollabmaker.domain.model.CollageTile
import com.instantcollabmaker.domain.model.NormalizedRect
import com.instantcollabmaker.domain.model.TileEmphasis
import com.instantcollabmaker.domain.processing.CollageGenerator
import kotlin.math.ceil

/**
 * Editorial, deliberately asymmetric collage layouts.
 *
 * Counts 1..6 get a hand-tuned composition (a dominant hero plus supporting tiles);
 * anything larger falls back to a balanced grid so the layout never breaks. All
 * geometry is normalised, so the same layout renders identically on screen and into a
 * 1080x1920 export bitmap.
 *
 * Pure geometry — it knows nothing about where the pixels for a tile come from, and
 * nothing about a background choice beyond passing [CollageSpec.background] straight
 * through, since a background never changes where a tile sits.
 */
class DefaultCollageGenerator : CollageGenerator {

    override fun layout(spec: CollageSpec): CollageLayout {
        val rects = composition(spec.items.size)
        val largestArea = rects.maxOfOrNull { it.width * it.height } ?: 1f
        val tiles = spec.items.mapIndexed { index, item ->
            val rect = rects.getOrElse(index) { NormalizedRect.Full }
            val relativeArea = if (largestArea > 0f) (rect.width * rect.height) / largestArea else 1f
            CollageTile(
                item = item,
                rect = rect,
                emphasis = when {
                    // A solo person always gets the strong single-hero treatment; for
                    // everyone else, only the first (largest) tile is emphasized, so a
                    // 2-person layout reads as balanced rather than hero+supporting.
                    // Callers order items by whatever should earn that hero slot (e.g.
                    // the full-video collage orders by appearance count) — this is
                    // subtle proportional emphasis, never a 4x size difference.
                    spec.items.size == 1 -> TileEmphasis.Hero
                    index == 0 -> TileEmphasis.Hero
                    relativeArea >= 0.55f -> TileEmphasis.Standard
                    else -> TileEmphasis.Supporting
                },
            )
        }
        return CollageLayout(tiles = tiles, background = spec.background)
    }

    /**
     * Outer margin reserved on every side, purely so a template's border tint and edge
     * decorations always have somewhere to live that is never inside a tile. With the
     * old text header/footer gone, tiles otherwise use the entire canvas.
     */
    private val marginH = 0.022f
    private val marginV = 0.026f
    // Wide enough that a template's continuous background artwork actually reads as
    // visible *between* photos, not just a hairline seam — the whole point of the
    // "one designed background, photos placed on top" system (see CollageTemplates).
    private val gap = 0.028f

    private fun composition(count: Int): List<NormalizedRect> = when (count) {
        0 -> emptyList()
        1 -> listOf(band(0f, 1f, 0f, 1f))
        2 -> listOf(
            band(0f, 1f, 0f, 0.58f),
            band(0f, 1f, 0.58f, 1f),
        )
        3 -> listOf(
            band(0f, 0.635f, 0f, 0.62f),
            band(0.635f, 1f, 0f, 0.62f),
            band(0f, 1f, 0.62f, 1f),
        )
        // Hero + two stacked supports + one full-width cinematic still.
        4 -> listOf(
            band(0f, 0.635f, 0f, 0.585f),
            band(0.635f, 1f, 0f, 0.2825f),
            band(0.635f, 1f, 0.2825f, 0.585f),
            band(0f, 1f, 0.585f, 1f),
        )
        // Hero + two stacked supports + two equal tiles beneath. The five-person case.
        5 -> listOf(
            band(0f, 0.60f, 0f, 0.50f),
            band(0.60f, 1f, 0f, 0.245f),
            band(0.60f, 1f, 0.245f, 0.50f),
            band(0f, 0.49f, 0.50f, 1f),
            band(0.49f, 1f, 0.50f, 1f),
        )
        6 -> listOf(
            band(0f, 0.62f, 0f, 0.42f),
            band(0.62f, 1f, 0f, 0.42f),
            band(0f, 0.38f, 0.42f, 0.72f),
            band(0.38f, 1f, 0.42f, 0.72f),
            band(0f, 0.50f, 0.72f, 1f),
            band(0.50f, 1f, 0.72f, 1f),
        )
        else -> grid(count)
    }

    private fun grid(count: Int): List<NormalizedRect> {
        val columns = if (count <= 9) 3 else 4
        val rows = ceil(count / columns.toFloat()).toInt()
        return List(count) { i ->
            val c = i % columns
            val r = i / columns
            band(
                c / columns.toFloat(), (c + 1) / columns.toFloat(),
                r / rows.toFloat(), (r + 1) / rows.toFloat(),
            )
        }
    }

    /**
     * Maps a fractional slot inside the content band to canvas coordinates, insets it by
     * half a gutter on each shared edge, and keeps every edge at least [marginH]/[marginV]
     * from the canvas boundary.
     */
    private fun band(x0: Float, x1: Float, y0: Float, y1: Float): NormalizedRect {
        val width = 1f - marginH * 2f
        val height = 1f - marginV * 2f
        val half = gap / 2f
        return NormalizedRect(
            left = marginH + x0 * width + if (x0 > 0f) half else 0f,
            right = marginH + x1 * width - if (x1 < 1f) half else 0f,
            top = marginV + y0 * height + if (y0 > 0f) half else 0f,
            bottom = marginV + y1 * height - if (y1 < 1f) half else 0f,
        )
    }
}
