package com.instantcollabmaker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Game Of Frames palette.
 *
 * A cinematic near-black neutral ramp and warm off-white type stay the quiet base — but
 * the brand marks (logo, splash, the primary call-to-action) get a real two-color pop:
 * champagne gold against a vivid teal, with a coral used sparingly as a third spark.
 * Deliberately confined to those few brand moments rather than smeared across every
 * surface, so the app reads as "a little colorful, on purpose" rather than busy.
 */
object FtColor {
    // Neutral ramp
    val Background = Color(0xFF07080B)
    val BackgroundElevated = Color(0xFF0C0E13)
    val Surface = Color(0xFF11141A)
    val SurfaceElevated = Color(0xFF181C24)
    val SurfaceHighest = Color(0xFF20242E)
    val Scrim = Color(0xCC05060A)

    // Hairlines and dividers
    val Stroke = Color(0xFF23262F)
    val StrokeStrong = Color(0xFF3A404D)
    val Divider = Color(0xFF1B1E26)

    // Type
    val TextPrimary = Color(0xFFF6F5F2)
    val TextSecondary = Color(0xFFA2A5AE)
    val TextMuted = Color(0xFF6B6F7A)
    val TextOnAccent = Color(0xFF14100A)

    // Accent — the brand's primary champagne gold, plus two vivid sparks reserved for
    // the logo mark, splash glow and the one primary call-to-action per screen.
    val Accent = Color(0xFFD9BC83)
    val AccentSoft = Color(0xFF3A3223)
    val AccentDim = Color(0xFF8C7A55)
    val AccentTeal = Color(0xFF2FD9C4)
    val AccentCoral = Color(0xFFFF6F91)

    // Semantic
    val Success = Color(0xFF7FD1A0)
    val SuccessSoft = Color(0xFF1B2C22)
    val Error = Color(0xFFE0786C)
    val ErrorSoft = Color(0xFF2E1A18)

    /**
     * Filmic, desaturated per-person accents. Deliberately muted so a five-tile
     * collage reads as one photograph set rather than a colour wheel.
     */
    val PersonAccents = listOf(
        Color(0xFF7C8C9E), // slate blue
        Color(0xFFA98467), // warm taupe
        Color(0xFF6F8B77), // sage
        Color(0xFF9A7B8C), // dusty plum
        Color(0xFFB08968), // sand
        Color(0xFF8792A6), // steel
        Color(0xFF9C8AA0), // mauve
    )

    fun personAccent(index: Int): Color =
        PersonAccents[((index % PersonAccents.size) + PersonAccents.size) % PersonAccents.size]
}
