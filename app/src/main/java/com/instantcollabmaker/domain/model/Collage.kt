package com.instantcollabmaker.domain.model

/** One thing that must appear in a collage, before any geometry is decided. */
data class CollageItem(
    val id: String,
    val frame: SelectedFrame,
    /** Primary caption drawn on the tile, e.g. "Person A" or "Appearance 02". */
    val label: String,
    /** Secondary caption, e.g. "4 appearances" or "00:09.8 - 00:12.7". */
    val caption: String,
    val accentIndex: Int,
)

/** The request handed to a [com.instantcollabmaker.domain.processing.CollageGenerator]. */
data class CollageSpec(
    val title: String,
    val subtitle: String,
    val footerPrimary: String,
    val footerSecondary: String,
    val items: List<CollageItem>,
)

/** How prominently a tile's captions are drawn. */
enum class TileEmphasis { Hero, Standard, Supporting }

/** A positioned tile. [rect] is normalised against the collage canvas. */
data class CollageTile(
    val item: CollageItem,
    val rect: NormalizedRect,
    val emphasis: TileEmphasis,
)

/**
 * A fully resolved composition: pure geometry plus text, with no graphics types. The
 * on-screen renderer and the export renderer consume the exact same layout, which is
 * what makes "what you see is what you save" true.
 */
data class CollageLayout(
    val title: String,
    val subtitle: String,
    val footerPrimary: String,
    val footerSecondary: String,
    val tiles: List<CollageTile>,
    val aspectRatio: Float = 9f / 16f,
) {
    val isEmpty: Boolean get() = tiles.isEmpty()
}
