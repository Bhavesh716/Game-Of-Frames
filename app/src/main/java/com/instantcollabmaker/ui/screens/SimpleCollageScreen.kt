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
import com.instantcollabmaker.domain.model.CollageBackground
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.ui.components.BackgroundPickerRow
import com.instantcollabmaker.ui.components.CollageCanvas
import com.instantcollabmaker.ui.components.ColorPickerDialog
import com.instantcollabmaker.ui.components.DualAction
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Spacing

/**
 * Assignment-minimal mode: the final collage screen — presentation, background/template
 * design, and export, but no per-tile editor.
 *
 * The primary flow's "Create Collage" now opens this screen directly instead of the full
 * interactive [FullCollageScreen]. Background/color/template picking is a core, explicitly
 * requested part of the presentation and stays here in full; what's genuinely dropped is
 * the per-tile editor — no pinch-zoom/pan, no swap, no individual tile transform state.
 *
 * [FullCollageScreen] (and its editing components — `EditableCollageTile`,
 * `SwapPhotoSheet`, `TileTransform`) are NOT deleted; they are simply no longer reachable
 * from this simplified flow. See `AppNavigation`'s comment at the `Screen.Results` route
 * for exactly where that entry point was disabled.
 */
@Composable
fun SimpleCollageScreen(
    baseLayout: CollageLayout,
    onBack: () -> Unit,
    onSave: (CollageLayout) -> Unit,
    onShare: (CollageLayout) -> Unit,
) {
    var background by remember { mutableStateOf(CollageBackground.Default) }
    var showColorPicker by remember { mutableStateOf(false) }
    val displayLayout = remember(baseLayout, background) { baseLayout.copy(background = background) }

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
            // editable = false: a plain, non-interactive render — no gesture detection,
            // no swap icon. See CollageCanvas's own doc for what that toggle does. The
            // background/template choice below still applies live, same as the full editor.
            CollageCanvas(
                layout = displayLayout,
                editable = false,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(displayLayout.aspectRatio, matchHeightConstraintsFirst = true),
            )
        }

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

        Spacer(Modifier.height(Spacing.lg))
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onColorSelected = { argb -> background = CollageBackground.Solid(argb) },
            onDismiss = { showColorPicker = false },
        )
    }
}
