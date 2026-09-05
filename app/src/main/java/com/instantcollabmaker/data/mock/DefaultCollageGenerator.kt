package com.instantcollabmaker.data.mock

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
 * This is pure geometry and survives into Phase 2 unchanged.
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
                    index == 0 && spec.items.size > 1 -> TileEmphasis.Hero
                    relativeArea >= 0.55f -> TileEmphasis.Standard
                    else -> TileEmphasis.Supporting
                },
            )
        }
        return CollageLayout(
            title = spec.title,
            subtitle = spec.subtitle,
            footerPrimary = spec.footerPrimary,
            footerSecondary = spec.footerSecondary,
            tiles = tiles,
        )
    }

    /** Vertical band the tiles live in, leaving room for the header and footer. */
    private val bandTop = 0.128f
    private val bandBottom = 0.845f
    private val gap = 0.014f

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
     * Maps a fractional slot inside the content band to canvas coordinates and insets it
     * by half a gutter on each shared edge.
     */
    private fun band(x0: Float, x1: Float, y0: Float, y1: Float): NormalizedRect {
        val height = bandBottom - bandTop
        val half = gap / 2f
        return NormalizedRect(
            left = x0 + if (x0 > 0f) half else 0f,
            right = x1 - if (x1 < 1f) half else 0f,
            top = bandTop + y0 * height + if (y0 > 0f) half else 0f,
            bottom = bandTop + y1 * height - if (y1 < 1f) half else 0f,
        )
    }
}
