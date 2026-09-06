package com.instantcollabmaker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectTapGestures
import com.instantcollabmaker.domain.model.Appearance
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

/**
 * "Swap Photo" — shown only for the person the tapped tile belongs to (Part 5/8: never
 * another person's photos). Selecting one immediately replaces that tile's photo; the
 * newly-selected image starts with a fresh, independent zoom/pan state (it is a different
 * [com.instantcollabmaker.domain.model.SelectedFrame.id], so `EditableCollageTile`'s own
 * `remember(frame.id)` keys naturally give it one) — every other tile is untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapPhotoSheet(
    personDisplayName: String,
    options: List<Appearance>,
    onSelect: (Appearance) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FtColor.Surface,
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
            Text(
                text = "Swap Photo",
                style = FtType.titleLarge,
                color = FtColor.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Choose one of ${personDisplayName}'s own photos",
                style = FtType.bodySmall,
                color = FtColor.TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            if (options.isEmpty()) {
                Text(
                    text = "No other photos of ${personDisplayName} are available yet.",
                    style = FtType.bodySmall,
                    color = FtColor.TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.gutter),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(options, key = { it.id }) { appearance ->
                        SwapOptionTile(appearance = appearance, onClick = { onSelect(appearance) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapOptionTile(appearance: Appearance, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(Radius.sm))
            .background(FtColor.SurfaceElevated)
            .pointerInput(appearance.id) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        FrameSurface(
            frame = appearance.bestFrame,
            accent = Color.Transparent,
            radius = Radius.sm,
            showBorder = false,
            contentDescription = "Appearance ${appearance.index}",
        )
    }
}

