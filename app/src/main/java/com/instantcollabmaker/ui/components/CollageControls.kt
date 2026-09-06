package com.instantcollabmaker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.domain.model.CollageBackground
import com.instantcollabmaker.domain.model.CollageBackgroundImages
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

/**
 * The compact row of circular background controls under a collage preview: "no
 * background" (shown with a clear disabled/block glyph, not just a plain black circle),
 * a custom-color picker, then one swatch per [CollageBackgroundImages.all] bundled photo
 * background. Selecting any of them is instant — a background is just data on
 * [CollageLayout] ([CollageBackground]), never a re-run of the layout geometry.
 */
@Composable
fun BackgroundPickerRow(
    selected: CollageBackground,
    onSelectNone: () -> Unit,
    onOpenColorPicker: () -> Unit,
    onSelectImage: (assetPath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            BackgroundSwatch(
                isSelected = selected is CollageBackground.None,
                onClick = onSelectNone,
                content = {
                    Box(
                        Modifier.fillMaxSizeInsetCircle().background(FtColor.SurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = "No background",
                            tint = FtColor.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
        item {
            val isCustomColor = selected is CollageBackground.Solid
            BackgroundSwatch(
                isSelected = isCustomColor,
                onClick = onOpenColorPicker,
                content = {
                    val swatchModifier = Modifier.fillMaxSizeInsetCircle()
                    if (isCustomColor) {
                        Box(swatchModifier.background(Color((selected as CollageBackground.Solid).colorArgb)))
                    } else {
                        Box(swatchModifier.background(Brush.sweepGradient(RAINBOW)))
                    }
                },
            )
        }
        items(CollageBackgroundImages.all) { assetPath ->
            val isSelected = selected is CollageBackground.Image && selected.assetPath == assetPath
            BackgroundSwatch(
                isSelected = isSelected,
                onClick = { onSelectImage(assetPath) },
                content = { BackgroundAssetThumbnail(assetPath, modifier = Modifier.fillMaxSizeInsetCircle()) },
            )
        }
    }
}

/** The small circular preview of one bundled background photo, shown in the picker before
 * the user selects it — decoding is shared with the full-canvas render via
 * [rememberDecodedAsset]. */
@Composable
private fun BackgroundAssetThumbnail(assetPath: String, modifier: Modifier = Modifier) {
    val bitmap = rememberDecodedAsset(assetPath)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(FtColor.SurfaceElevated))
    }
}

private val RAINBOW = listOf(
    Color(0xFFE8622C), Color(0xFFE8C15A), Color(0xFF7FD1A0),
    Color(0xFF7C8C9E), Color(0xFF9A7B8C), Color(0xFFE8622C),
)

@Composable
private fun BackgroundSwatch(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(FtColor.SurfaceElevated)
            .border(
                width = if (isSelected) 2.dp else Spacing.xxs / 2,
                color = if (isSelected) FtColor.Accent else FtColor.Stroke,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun Modifier.fillMaxSizeInsetCircle(): Modifier =
    this.size(34.dp).clip(CircleShape)

/**
 * A minimal, curated-palette color picker — not a full HSV wheel, but genuinely "any of
 * these colors", chosen to stay legible behind photo tiles (nothing so light or so
 * saturated it fights the app's own dark chrome).
 */
@Composable
fun ColorPickerDialog(
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FtColor.Surface,
        title = { Text("Choose a background color", style = FtType.titleMedium, color = FtColor.TextPrimary) },
        text = {
            ColorSwatchGrid(onColorSelected = { color -> onColorSelected(color); onDismiss() })
        },
        confirmButton = {},
    )
}

private val CURATED_PALETTE = listOf(
    0xFF000000, 0xFF1A1A1A, 0xFF07080B, 0xFF181C24, 0xFFD9BC83, 0xFFE8622C,
    0xFFC0392B, 0xFF1B3B2E, 0xFF241933, 0xFF3B1F1A, 0xFF10151F, 0xFF7FD1A0,
    0xFF7C8C9E, 0xFF9A7B8C, 0xFFFAFAF8, 0xFFEDECE8,
).map { it.toInt() }

@Composable
private fun ColorSwatchGrid(onColorSelected: (Int) -> Unit) {
    val rows = CURATED_PALETTE.chunked(4)
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { colorArgb ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(Color(colorArgb))
                            .border(Spacing.xxs / 2, FtColor.Stroke, RoundedCornerShape(Radius.sm))
                            .clickable { onColorSelected(colorArgb) },
                    )
                }
            }
        }
    }
}
