package com.instantcollabmaker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.instantcollabmaker.ui.theme.FtColor

/**
 * The Game Of Frames mark: camera-viewfinder corner brackets around a play glyph — the
 * two halves of the name, one shape. "Frame" is the brackets a viewfinder draws around a
 * shot; "Game" is the play button sitting inside it, on the throne the brackets form.
 *
 * Two colors, not one: opposite corners share a color (gold / teal), which is what reads
 * as "a viewfinder locking onto something" rather than a flat monochrome frame, and the
 * play glyph gets a warm coral so it pops as the one thing actually *inside* the frame.
 * Confined to this one brand mark — the rest of the app stays quiet — so a little color
 * goes a long way instead of turning into visual noise.
 *
 * Built as a hand-drawn [Canvas], not a static asset, so it can animate: [progress] drives
 * a draw-in sequence (brackets extend from each corner, then the play glyph scales in),
 * used by the splash screen. Anywhere else, pass `progress = 1f` for the finished mark —
 * see `ic_launcher_foreground.xml` for the equivalent static vector used as the launcher
 * icon, kept in the same two-tone-plus-coral scheme by hand.
 */
@Composable
fun GameOfFramesMark(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    primary: Color = FtColor.Accent,
    secondary: Color = FtColor.AccentTeal,
    spark: Color = FtColor.AccentCoral,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val bracketProgress = (clamped / 0.65f).coerceIn(0f, 1f)
    val playProgress = ((clamped - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val glowAlpha = clamped * 0.4f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun p(xFraction: Float, yFraction: Float) = Offset(xFraction * w, yFraction * h)

        // A two-color halo behind the mark — reads as stage light, not a hard shape.
        if (glowAlpha > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = glowAlpha),
                        secondary.copy(alpha = glowAlpha * 0.55f),
                        Color.Transparent,
                    ),
                    center = p(0.5f, 0.5f),
                    radius = w * 0.6f,
                ),
                radius = w * 0.6f,
                center = p(0.5f, 0.5f),
            )
        }

        val strokeWidth = w * 0.045f
        val armLength = 0.15f

        // Each corner is two independent arms so the draw-in animation can extend both
        // at once without needing a single continuous path (an L-bracket, drawn from its
        // corner outward). Opposite corners share a color — top-left/bottom-right in
        // [primary], top-right/bottom-left in [secondary] — so the frame itself carries
        // the two-tone identity, not just a gradient wash.
        fun drawArm(corner: Offset, toward: Offset, color: Color) {
            if (bracketProgress <= 0f) return
            drawLine(
                color = color,
                start = corner,
                end = lerp(corner, toward, bracketProgress),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        val topLeft = p(0.30f, 0.30f)
        val topRight = p(0.70f, 0.30f)
        val bottomLeft = p(0.30f, 0.70f)
        val bottomRight = p(0.70f, 0.70f)

        drawArm(topLeft, p(0.30f, 0.30f + armLength), primary)
        drawArm(topLeft, p(0.30f + armLength, 0.30f), primary)

        drawArm(topRight, p(0.70f, 0.30f + armLength), secondary)
        drawArm(topRight, p(0.70f - armLength, 0.30f), secondary)

        drawArm(bottomLeft, p(0.30f, 0.70f - armLength), secondary)
        drawArm(bottomLeft, p(0.30f + armLength, 0.70f), secondary)

        drawArm(bottomRight, p(0.70f, 0.70f - armLength), primary)
        drawArm(bottomRight, p(0.70f - armLength, 0.70f), primary)

        // The play glyph — the "game" sitting on the throne the brackets frame. Coral,
        // so it reads as the one warm spark actually inside the cool-and-gold frame.
        if (playProgress > 0f) {
            val center = p(0.5f, 0.5f)
            val scale = 0.4f + 0.6f * playProgress
            val top = Offset(center.x - 0.09f * w * scale, center.y - 0.13f * h * scale)
            val bottom = Offset(center.x - 0.09f * w * scale, center.y + 0.13f * h * scale)
            val tip = Offset(center.x + 0.15f * w * scale, center.y)
            val path = Path().apply {
                moveTo(top.x, top.y)
                lineTo(bottom.x, bottom.y)
                lineTo(tip.x, tip.y)
                close()
            }
            drawPath(path, color = spark.copy(alpha = 0.4f + 0.6f * playProgress))
        }
    }
}
