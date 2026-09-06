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
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.domain.model.TileTransform
import com.instantcollabmaker.domain.model.VideoAnalysisResult
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
 * The assignment's actual deliverable screen: every unique person, once each, as a
 * shareable image. Deliberately fits on one screen with no scrolling — back+title,
 * preview, a one-line person count, the background/template picker, and Save/Share, in
 * that order, nothing more. [baseLayout] is pure tile geometry; [background] is UI state
 * layered on top of it for live preview, and the exact same composed [CollageLayout] is
 * what gets handed to [onSave]/[onShare] — never a re-generated or approximate copy.
 */
@Composable
fun FullCollageScreen(
    result: VideoAnalysisResult,
    baseLayout: CollageLayout,
    onBack: () -> Unit,
    onSave: (CollageLayout) -> Unit,
    onShare: (CollageLayout) -> Unit,
) {
    var background by remember { mutableStateOf(CollageBackground.Default) }
    var showColorPicker by remember { mutableStateOf(false) }
    // Per-tile edits, keyed by CollageItem.id (== person.id here) — every tile's zoom/pan
    // and swapped-in photo are independent of every other tile's (Part 4/5).
    var tileTransforms by remember { mutableStateOf(mapOf<String, TileTransform>()) }
    var swappedFrames by remember { mutableStateOf(mapOf<String, SelectedFrame>()) }
    var swapTargetPersonId by remember { mutableStateOf<String?>(null) }

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
    val swapTargetPerson = swapTargetPersonId?.let { id -> result.people.find { it.id == id } }

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
            Text("Your Collage", style = FtType.titleLarge, color = FtColor.TextPrimary)
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
                onSwapRequested = { tileId -> swapTargetPersonId = tileId },
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = Format.count(result.peopleCount, "person", "people"),
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
            text = "Every person, once — best frame, generously cropped.",
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

    if (swapTargetPerson != null) {
        SwapPhotoSheet(
            personDisplayName = swapTargetPerson.displayName,
            options = swapTargetPerson.appearances,
            onSelect = { appearance ->
                swappedFrames = swappedFrames + (swapTargetPerson.id to appearance.bestFrame)
                // A newly-selected photo gets its own fresh transform (Part 5) — never
                // inherits whatever zoom/pan the previous photo in this slot had.
                tileTransforms = tileTransforms - swapTargetPerson.id
                swapTargetPersonId = null
            },
            onDismiss = { swapTargetPersonId = null },
        )
    }
}
