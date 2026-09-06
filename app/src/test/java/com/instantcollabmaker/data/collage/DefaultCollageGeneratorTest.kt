package com.instantcollabmaker.data.collage

import com.instantcollabmaker.domain.model.CollageBackground
import com.instantcollabmaker.domain.model.CollageItem
import com.instantcollabmaker.domain.model.CollageSpec
import com.instantcollabmaker.domain.model.FrameImage
import com.instantcollabmaker.domain.model.FrameQuality
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.domain.model.TileEmphasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCollageGeneratorTest {

    private fun item(id: String) = CollageItem(
        id = id,
        frame = SelectedFrame(
            id = "${id}_frame",
            timestampMs = 0L,
            frameIndex = 0,
            image = FrameImage(path = "/tmp/$id.jpg"),
            quality = FrameQuality(0.8f, 0.8f, 0.8f, 0.5f, 0.8f),
        ),
        label = id,
        accentIndex = 0,
    )

    @Test
    fun `layout produces exactly one tile per item, for every supported count`() {
        val generator = DefaultCollageGenerator()
        for (count in 1..9) {
            val spec = CollageSpec(items = (1..count).map { item("p$it") })
            val layout = generator.layout(spec)
            assertEquals("count=$count", count, layout.tiles.size)
        }
    }

    @Test
    fun `a single person gets a full-canvas hero tile`() {
        val generator = DefaultCollageGenerator()
        val layout = generator.layout(CollageSpec(items = listOf(item("solo"))))
        val tile = layout.tiles.single()
        assertEquals(TileEmphasis.Hero, tile.emphasis)
        assertTrue(tile.rect.width > 0.9f)
    }

    @Test
    fun `the first tile is emphasized as hero whenever there is more than one person`() {
        val generator = DefaultCollageGenerator()
        for (count in 2..7) {
            val spec = CollageSpec(items = (1..count).map { item("p$it") })
            val layout = generator.layout(spec)
            assertEquals("count=$count", TileEmphasis.Hero, layout.tiles.first().emphasis)
        }
    }

    @Test
    fun `every tile stays within the normalised canvas bounds`() {
        val generator = DefaultCollageGenerator()
        val spec = CollageSpec(items = (1..5).map { item("p$it") })
        val layout = generator.layout(spec)
        for (tile in layout.tiles) {
            assertTrue(tile.rect.left >= 0f && tile.rect.right <= 1f)
            assertTrue(tile.rect.top >= 0f && tile.rect.bottom <= 1f)
            assertTrue(tile.rect.width > 0f && tile.rect.height > 0f)
        }
    }

    @Test
    fun `an empty item list produces an empty layout, not a crash`() {
        val generator = DefaultCollageGenerator()
        val layout = generator.layout(CollageSpec(items = emptyList()))
        assertTrue(layout.isEmpty)
    }

    @Test
    fun `large counts fall back to a grid without breaking the one-tile-per-item invariant`() {
        val generator = DefaultCollageGenerator()
        val spec = CollageSpec(items = (1..14).map { item("p$it") })
        val layout = generator.layout(spec)
        assertEquals(14, layout.tiles.size)
    }

    @Test
    fun `the layout carries the spec's background straight through`() {
        val generator = DefaultCollageGenerator()
        val background = CollageBackground.Solid(colorArgb = 0xFF112233.toInt())
        val layout = generator.layout(CollageSpec(items = listOf(item("solo")), background = background))
        assertEquals(background, layout.background)
    }
}
