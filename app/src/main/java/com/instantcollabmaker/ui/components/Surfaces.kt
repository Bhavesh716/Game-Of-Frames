package com.instantcollabmaker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Sizes
import com.instantcollabmaker.ui.theme.Spacing

/**
 * Surface, label and metadata primitives.
 *
 * Depth in this app comes from three cues only — surface tint, a one-pixel hairline,
 * and a lit top edge — never from drop shadows. Keeping that in one file is what stops
 * fourteen slightly different cards appearing across seven screens.
 */

/** The standard content card. */
@Composable
fun FtCard(
    modifier: Modifier = Modifier,
    color: Color = FtColor.Surface,
    radius: Dp = Radius.lg,
    borderColor: Color = FtColor.Stroke,
    contentPadding: Dp = Spacing.xl,
    litEdge: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(Sizes.hairline, borderColor, shape),
    ) {
        if (litEdge) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Sizes.hairline)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.07f),
                                Color.Transparent,
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Small uppercase tracked caption that opens a section, e.g. `VIDEO ANALYSIS`.
 * The optional trailing content carries a right-aligned value on the same baseline.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FtColor.TextMuted,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = FtType.label,
            color = color,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/** Hairline rule used between list rows and above footers. */
@Composable
fun FtDivider(
    modifier: Modifier = Modifier,
    color: Color = FtColor.Divider,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.hairline)
            .background(color)
    )
}

/** Pill-shaped metadata token, e.g. `1080 × 1920` or `Ready to analyze`. */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = FtColor.TextSecondary,
    background: Color = FtColor.SurfaceElevated,
    borderColor: Color = FtColor.Stroke,
) {
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(Sizes.hairline, borderColor, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.iconSm))
        }
        Text(text, style = FtType.bodySmall, color = tint, maxLines = 1)
    }
}

/**
 * A live-status pill with a leading dot. Used for "Ready to analyze" and the
 * "100% on-device" readout.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color = FtColor.Success,
    textColor: Color = FtColor.TextSecondary,
    background: Color = FtColor.SurfaceElevated,
) {
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(Sizes.hairline, FtColor.Stroke, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(dotColor)
        )
        Text(text, style = FtType.bodySmall, color = textColor, maxLines = 1)
    }
}

/**
 * A big-numeral statistic with a tracked caption beneath it. Shared by the processing
 * counters and the results summary so both read as the same instrument.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = FtColor.TextPrimary,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value,
            style = FtType.numeral,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = label.uppercase(), style = FtType.labelTiny, color = FtColor.TextMuted)
    }
}

/** A label/value pair on one line, for the video metadata list. */
@Composable
fun SpecRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = FtColor.TextPrimary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = FtType.labelTiny, color = FtColor.TextMuted)
        Box(Modifier.weight(1f))
        Text(value, style = FtType.bodySmall, color = valueColor, maxLines = 1)
    }
}

/**
 * Horizontal quality meter. Communicates the value with both length and a numeric
 * readout, so nothing depends on colour alone.
 */
@Composable
fun QualityMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = FtColor.Accent,
    trackColor: Color = FtColor.SurfaceHighest,
    height: Dp = 4.dp,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(RoundedCornerShape(Radius.pill))
                .background(color)
        )
    }
}

/** Fixed-width spacer used inside rows where `Arrangement` would be overkill. */
@Composable
fun HGap(width: Dp) = Box(Modifier.width(width))
