package com.instantcollabmaker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Typography scale. Display sizes use negative tracking for a tight, editorial feel;
 * the label styles use wide uppercase tracking for the small metadata captions that
 * carry most of the instrument-panel character.
 */
object FtType {
    private val sans = FontFamily.SansSerif

    val displayLarge = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Light,
        fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-1.4).sp,
    )
    val displayMedium = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Light,
        fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-1.0).sp,
    )
    val displaySmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp,
    )
    val titleLarge = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp,
    )
    val titleMedium = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp,
    )
    val titleSmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
    )
    val body = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.sp,
    )
    val bodySmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp,
    )
    val label = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp,
    )
    val labelTiny = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp,
    )
    val button = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
        textAlign = TextAlign.Center,
    )

    /** Monospace for live counters and timecodes, so digits do not jitter. */
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    )
    val numeral = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Normal,
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.8).sp,
    )
}

/** Material3 typography wired to the Game Of Frames scale so stock components inherit it. */
internal val FtTypography = Typography(
    displayLarge = FtType.displayLarge,
    displayMedium = FtType.displayMedium,
    displaySmall = FtType.displaySmall,
    headlineMedium = FtType.displaySmall,
    titleLarge = FtType.titleLarge,
    titleMedium = FtType.titleMedium,
    titleSmall = FtType.titleSmall,
    bodyLarge = FtType.body,
    bodyMedium = FtType.body,
    bodySmall = FtType.bodySmall,
    labelLarge = FtType.button,
    labelMedium = FtType.titleSmall,
    labelSmall = FtType.label,
)
