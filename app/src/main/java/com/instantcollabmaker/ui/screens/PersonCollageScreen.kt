package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.instantcollabmaker.core.Format
import com.instantcollabmaker.domain.model.CollageBackground
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.domain.model.TileTransform
import com.instantcollabmaker.ui.components.BackgroundPickerRow
import com.instantcollabmaker.ui.components.CollageCanvas
import com.instantcollabmaker.ui.components.ColorPickerDialog
import com.instantcollabmaker.ui.components.DualAction
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.SwapPhotoSheet
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Spacing

/**
 * One person's own appearance collage — same one-screen, no-scroll, zero-text-in-image
 * treatment as [FullCollageScreen], just scoped to a single [Person]'s appearances
 * instead of every unique person in the video.
 */
@Composable
fun PersonCollageScreen(
    person: Person,
    baseLayout: CollageLayout,
    onBack: () -> Unit,
    onSave: (CollageLayout) -> Unit,
    onShare: (CollageLayout) -> Unit,
) {
    var background by remember { mutableStateOf(CollageBackground.Default) }
    var showColorPicker by remember { mutableStateOf(false) }
    // Per-tile edits, keyed by CollageItem.id (== appearance.id here) — independent per
    // tile (Part 4/5), same pattern as FullCollageScreen.
    var tileTransforms by remember { mutableStateOf(mapOf<String, TileTransform>()) }
    var swappedFrames by remember { mutableStateOf(mapOf<String, SelectedFrame>()) }
    var swapTargetTileId by remember { mutableStateOf<String?>(null) }

    val displayLayout = remember(baseLayout, background, tileTransforms, swappedFrames) {
        baseLayout.copy(
            background = background,
            tiles = baseLayout.tiles.map { tile ->
                tile.copy(
                    item = tile.item.copy(
                        frame = swappedFrames[tile.item.id] ?: tile.item.frame,
                        transform = tileTransforms[tile.item.id] ?: TileTransform(),
                    )
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FtColor.Background)
            .systemBarsPadding()
            .padding(horizontal = Spacing.gutter),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                onClick = onBack,
            )
            Text("${person.displayName}'s Collage", style = FtType.titleLarge, color = FtColor.TextPrimary)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CollageCanvas(
                layout = displayLayout,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(displayLayout.aspectRatio, matchHeightConstraintsFirst = true),
                onTransformChange = { tileId, transform ->
                    tileTransforms = tileTransforms + (tileId to transform)
                },
                onSwapRequested = { tileId -> swapTargetTileId = tileId },
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = Format.count(person.appearances.size, "appearance"),
            style = FtType.titleSmall,
            color = FtColor.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))

        BackgroundPickerRow(
            selected = background,
            onSelectNone = { background = CollageBackground.None },
            onOpenColorPicker = { showColorPicker = true },
            onSelectImage = { assetPath -> background = CollageBackground.Image(assetPath) },
        )

        Spacer(Modifier.height(Spacing.md))

        DualAction(
            primaryText = "Save to Gallery",
            onPrimary = { onSave(displayLayout) },
            secondaryText = "Share",
            onSecondary = { onShare(displayLayout) },
            secondaryIcon = Icons.Default.Share,
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "Every appearance of ${person.displayName}, best frame first.",
            style = FtType.bodySmall,
            color = FtColor.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onColorSelected = { argb -> background = CollageBackground.Solid(argb) },
            onDismiss = { showColorPicker = false },
        )
    }

    if (swapTargetTileId != null) {
        SwapPhotoSheet(
            personDisplayName = person.displayName,
            options = person.appearances,
            onSelect = { appearance ->
                val tileId = swapTargetTileId
                if (tileId != null) {
                    swappedFrames = swappedFrames + (tileId to appearance.bestFrame)
                    tileTransforms = tileTransforms - tileId
                }
                swapTargetTileId = null
            },
            onDismiss = { swapTargetTileId = null },
        )
    }
}
