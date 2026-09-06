package com.instantcollabmaker.domain.model

/**
 * One thing that must appear in a collage, before any geometry is decided.
 *
 * [label] is accessibility metadata only (a screen-reader content description) — nothing
 * in this app draws it onto the collage image itself. The exported/saved/shared bitmap
 * contains only background, decorative template graphics and the person photos; see
 * [CollageBackground] for the only visual customization on offer.
 */
data class CollageItem(
    val id: String,
    val frame: SelectedFrame,
    val label: String,
    val accentIndex: Int,
    /** The user's own pinch-zoom/pan edit for this specific tile, on top of the normal
     * face-anchored auto-fit — defaults to untouched. Applied identically by the Compose
     * preview ([com.instantcollabmaker.ui.components.CollageCanvas]) and the classic-Canvas
     * exporter, so what the user edits is exactly what gets saved/shared. */
    val transform: TileTransform = TileTransform(),
)

/**
 * One tile's user-applied zoom/pan, independent of every other tile's. [offsetXFraction]/
 * [offsetYFraction] are expressed as a fraction of the *zoomed* image's own drawn
 * width/height (not raw pixels), so the same edit reproduces identically at any render
 * resolution — the preview and the exported bitmap are never rendered at the same pixel
 * size, so a raw-pixel offset would not transfer between them.
 */
data class TileTransform(
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
)

/**
 * One small decorative accent (a filled circle). Pure data — an ARGB packed color int,
 * not a graphics-framework type — so it stays usable from both the Compose preview and
 * the classic-Canvas exporter. Where it's allowed to appear depends on which list of a
 * [CollageTemplate] it lives in: see [CollageTemplate.backgroundAccents] vs.
 * [CollageTemplate.edgeAccents].
 */
data class TemplateDecoration(
    val centerX: Float,
    val centerY: Float,
    /** Radius as a fraction of the canvas width. */
    val radius: Float,
    val colorArgb: Int,
)

/** One straight decorative stripe, drawn as part of the full-canvas background artwork
 * (e.g. the Sports template's diagonal pattern) — normalized endpoints and a width
 * fraction of the canvas width, so it scales cleanly to any export resolution. */
data class TemplateStripe(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val widthFraction: Float,
    val colorArgb: Int,
)

/**
 * One prebuilt creative background — designed as **one continuous artwork**, not a
 * collection of independent stickers scattered around each photo:
 *
 * 1. [backgroundTopArgb]/[backgroundBottomArgb] paint a full-canvas gradient.
 * 2. [backgroundAccents] and [backgroundStripes] paint texture across the *entire*
 *    canvas — including where a photo tile will sit. They are drawn strictly before any
 *    tile, so a tile simply paints over whatever texture is underneath it; nothing has
 *    to be manually kept out of a tile's bounds; a face can never end up with a dot
 *    drawn over it because tiles always render on top.
 * 3. Tiles render on top of all of the above.
 * 4. [edgeAccents] render last, confined to the canvas's outer margin (the same margin
 *    [com.instantcollabmaker.data.collage.DefaultCollageGenerator] already reserves
 *    outside every tile) — a border-tint stroke plus a few corner/edge flourishes that
 *    sit visually "in front", never over a photo.
 *
 * Removing the tiles from a template's render should still look like a complete,
 * intentional piece of background art — that's the test for whether a template counts
 * as "one continuous design" rather than a scattering of stickers.
 */
data class CollageTemplate(
    val id: String,
    val displayName: String,
    val backgroundTopArgb: Int,
    val backgroundBottomArgb: Int,
    val borderArgb: Int,
    val backgroundAccents: List<TemplateDecoration> = emptyList(),
    val backgroundStripes: List<TemplateStripe> = emptyList(),
    val edgeAccents: List<TemplateDecoration> = emptyList(),
)

/** What paints the space between and around tiles — the only customizable part of a
 * collage's look, since the tiles themselves are just the selected photos. */
sealed class CollageBackground {
    data object None : CollageBackground()
    data class Solid(val colorArgb: Int) : CollageBackground()
    /** Assignment-minimal mode: no longer offered in [com.instantcollabmaker.ui.components.BackgroundPickerRow]
     * — superseded by [Image] — but not deleted; the rendering path (`TemplateArtwork`/
     * `TemplateEdgeAccents`, `CanvasCollageExporter`'s template drawing) still fully works
     * for any layout that already carries one. */
    data class Template(val template: CollageTemplate) : CollageBackground()
    /** A full-bleed photographic background loaded from a bundled asset — [assetPath] is
     * relative to the assets root, e.g. `"collage_bgs/bg1.jpg"` (see
     * [CollageBackgroundImages]). */
    data class Image(val assetPath: String) : CollageBackground()

    companion object {
        val Default: CollageBackground = None
    }
}

/**
 * The bundled photographic collage backgrounds (see `app/src/main/assets/collage_bgs/`),
 * offered in the background picker right after "no background" and the color picker.
 * Extensions vary per file — `bg2` is a `.png`, the rest are `.jpg` — so each entry is its
 * own full asset path rather than one shared filename pattern.
 */
object CollageBackgroundImages {
    val all: List<String> = listOf(
        "collage_bgs/bg1.jpg",
        "collage_bgs/bg2.png",
        "collage_bgs/bg3.jpg",
        "collage_bgs/bg4.jpg",
        "collage_bgs/bg5.jpg",
        "collage_bgs/bg6.jpg",
        "collage_bgs/bg7.jpg",
    )
}

/** The request handed to a [com.instantcollabmaker.domain.processing.CollageGenerator]. */
data class CollageSpec(
    val items: List<CollageItem>,
    val background: CollageBackground = CollageBackground.Default,
)

/** How prominently a tile is sized — proportional emphasis only, never a caption. */
enum class TileEmphasis { Hero, Standard, Supporting }

/** A positioned tile. [rect] is normalised against the collage canvas. */
data class CollageTile(
    val item: CollageItem,
    val rect: NormalizedRect,
    val emphasis: TileEmphasis,
)

/**
 * A fully resolved composition: pure geometry plus a background choice, no graphics
 * types. The on-screen renderer and the export renderer consume the exact same layout,
 * which is what makes "what you see is what you save" true. Switching [background] never
 * needs the tile geometry recomputed — `layout.copy(background = next)` is enough — since
 * a background choice never moves a single tile.
 */
data class CollageLayout(
    val tiles: List<CollageTile>,
    val background: CollageBackground = CollageBackground.Default,
    val aspectRatio: Float = 9f / 16f,
) {
    val isEmpty: Boolean get() = tiles.isEmpty()
}
