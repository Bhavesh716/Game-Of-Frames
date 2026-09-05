package com.instantcollabmaker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.TileEmphasis
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Sizes
import com.instantcollabmaker.ui.theme.Spacing

/**
 * Renders a [CollageLayout] exactly as it will be exported: one fixed-aspect card with
 * title/subtitle at the top, tiles placed by their normalised [rect][com.instantcollabmaker.domain.model.CollageTile.rect],
 * and the footer pinned to the bottom. This is the one place [CollageLayout] becomes
 * pixels, so the on-screen preview and the saved/shared image never drift apart.
 */
@Composable
fun CollageCanvas(
    layout: CollageLayout,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.lg)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(layout.aspectRatio)
            .clip(shape)
            .background(FtColor.SurfaceElevated)
            .border(Sizes.hairline, FtColor.Stroke, shape),
    ) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight

        layout.tiles.forEach { tile ->
            val accent = FtColor.personAccent(tile.item.accentIndex)
            FrameSurface(
                frame = tile.item.frame,
                accent = accent,
                radius = Radius.xs,
                scrim = true,
                showBorder = false,
                contentDescription = tile.item.label,
                modifier = Modifier
                    .size(
                        width = canvasWidth * tile.rect.width,
                        height = canvasHeight * tile.rect.height,
                    )
                    .offset(
                        x = canvasWidth * tile.rect.left,
                        y = canvasHeight * tile.rect.top,
                    ),
            ) {
                TileCaption(
                    label = tile.item.label,
                    caption = tile.item.caption,
                    emphasis = tile.emphasis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Spacing.sm),
                )
            }
        }

        if (layout.isEmpty) {
            Text(
                text = "Nothing to show yet",
                style = FtType.bodySmall,
                color = FtColor.TextMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = layout.title,
                style = FtType.titleMedium,
                color = FtColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (layout.subtitle.isNotBlank()) {
                Text(
                    text = layout.subtitle,
                    style = FtType.labelTiny,
                    color = FtColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(layout.footerPrimary, style = FtType.labelTiny, color = FtColor.TextMuted)
            Text(layout.footerSecondary, style = FtType.labelTiny, color = FtColor.TextMuted)
        }
    }
}

@Composable
private fun TileCaption(
    label: String,
    caption: String,
    emphasis: TileEmphasis,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = if (emphasis == TileEmphasis.Hero) FtType.titleSmall else FtType.labelTiny,
            color = FtColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption.isNotBlank() && emphasis != TileEmphasis.Supporting) {
            Text(
                text = caption,
                style = FtType.labelTiny,
                color = FtColor.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
