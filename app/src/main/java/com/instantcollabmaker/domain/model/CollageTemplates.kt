package com.instantcollabmaker.domain.model

import kotlin.random.Random

/**
 * The fixed set of prebuilt creative backgrounds offered on the collage screen.
 *
 * Each one is built as a single continuous piece of background art — a gradient plus a
 * texture that spans the *entire* canvas — rather than a handful of independent stickers
 * placed around each photo. See [CollageTemplate]'s doc for exactly how that's kept safe
 * (texture is drawn before tiles, so tiles simply occlude whatever's underneath them; a
 * decoration is never individually positioned to "dodge" a face).
 */
object CollageTemplates {

    val Christmas = CollageTemplate(
        id = "christmas",
        displayName = "Christmas",
        backgroundTopArgb = 0xFF1B3B2E.toInt(),
        backgroundBottomArgb = 0xFF0A1810.toInt(),
        borderArgb = 0xFFC0392B.toInt(),
        backgroundAccents = snowfall(),
        edgeAccents = edgeDots(color = 0xFFE8C15A.toInt(), count = 12, radiusFraction = 0.007f),
    )

    val MinimalWhite = CollageTemplate(
        id = "minimal_white",
        displayName = "Minimal Editorial",
        backgroundTopArgb = 0xFFFAFAF8.toInt(),
        backgroundBottomArgb = 0xFFEDECE8.toInt(),
        borderArgb = 0xFFD8D6CF.toInt(),
        // Deliberately textureless — "minimal" is the whole point of this one.
    )

    val Sports = CollageTemplate(
        id = "sports",
        displayName = "Sports",
        backgroundTopArgb = 0xFF10151F.toInt(),
        backgroundBottomArgb = 0xFF060810.toInt(),
        borderArgb = 0xFFE8622C.toInt(),
        backgroundStripes = diagonalStripes(),
        edgeAccents = cornerMarks(color = 0xFFE8622C.toInt()),
    )

    val Celebration = CollageTemplate(
        id = "celebration",
        displayName = "Celebration",
        backgroundTopArgb = 0xFF241933.toInt(),
        backgroundBottomArgb = 0xFF120B1C.toInt(),
        borderArgb = 0xFFD9BC83.toInt(),
        backgroundAccents = confetti(),
    )

    val Festival = CollageTemplate(
        id = "festival",
        displayName = "Festival",
        backgroundTopArgb = 0xFF3B1F1A.toInt(),
        backgroundBottomArgb = 0xFF190C0A.toInt(),
        borderArgb = 0xFFE8A23D.toInt(),
        backgroundAccents = emberLights(),
    )

    val all: List<CollageTemplate> = listOf(Christmas, MinimalWhite, Sports, Celebration, Festival)

    /** A fine, full-canvas scatter of two snow tones — dense enough to read as a texture,
     * not a handful of isolated dots. */
    private fun snowfall(): List<TemplateDecoration> {
        val random = Random(1)
        val warm = 0x99E8C15A.toInt()
        val cool = 0x88FFFFFF.toInt()
        return (0 until 70).map { i ->
            TemplateDecoration(
                centerX = random.nextFloat(),
                centerY = random.nextFloat(),
                radius = 0.003f + random.nextFloat() * 0.006f,
                colorArgb = if (i % 3 == 0) warm else cool,
            )
        }
    }

    /** A set of parallel diagonal stripes spanning the whole canvas, like a jersey
     * pattern — one repeating motif, not scattered marks. */
    private fun diagonalStripes(): List<TemplateStripe> {
        val color = 0x1FE8622C.toInt()
        val count = 7
        return (0 until count).map { i ->
            val offset = (i - count / 2) * 0.32f
            TemplateStripe(
                x1 = -0.2f + offset,
                y1 = 1.2f,
                x2 = 0.5f + offset,
                y2 = -0.2f,
                widthFraction = 0.045f,
                colorArgb = color,
            )
        }
    }

    /** A short dashed accent tucked into two opposite corners — a signature mark, not a
     * border that runs the whole way round. */
    private fun cornerMarks(color: Int): List<TemplateDecoration> =
        (0 until 4).map { i ->
            val t = i / 3f
            TemplateDecoration(0.025f + t * 0.09f, 0.02f + t * 0.08f, 0.0055f, color)
        } + (0 until 4).map { i ->
            val t = i / 3f
            TemplateDecoration(0.975f - t * 0.09f, 0.98f - t * 0.08f, 0.0055f, color)
        }

    /** A full-canvas confetti scatter in the app's own filmic palette, so it reads as
     * "this app's celebration," not generic clip art. */
    private fun confetti(): List<TemplateDecoration> {
        val palette = intArrayOf(0xFFE8622C.toInt(), 0xFFD9BC83.toInt(), 0xFF7FD1A0.toInt(), 0xFF9A7B8C.toInt(), 0xFF2FD9C4.toInt())
        val random = Random(42)
        return (0 until 55).map { i ->
            TemplateDecoration(
                centerX = random.nextFloat(),
                centerY = random.nextFloat(),
                radius = 0.004f + random.nextFloat() * 0.006f,
                colorArgb = palette[i % palette.size],
            )
        }
    }

    /** Warm, varied-size amber dots across the whole canvas — reads as string lights /
     * diyas rather than confetti, purely by warmth and size variety. */
    private fun emberLights(): List<TemplateDecoration> {
        val random = Random(7)
        val tones = intArrayOf(0xAAE8A23D.toInt(), 0x88FF6F91.toInt(), 0x99FFD08A.toInt())
        return (0 until 45).map { i ->
            TemplateDecoration(
                centerX = random.nextFloat(),
                centerY = random.nextFloat(),
                radius = 0.003f + random.nextFloat() * 0.008f,
                colorArgb = tones[i % tones.size],
            )
        }
    }

    private fun edgeDots(color: Int, count: Int, radiusFraction: Float): List<TemplateDecoration> =
        (0 until count).flatMap { i ->
            val x = (i + 0.5f) / count
            listOf(
                TemplateDecoration(x, 0.012f, radiusFraction, color),
                TemplateDecoration(x, 0.988f, radiusFraction, color),
            )
        }
}
