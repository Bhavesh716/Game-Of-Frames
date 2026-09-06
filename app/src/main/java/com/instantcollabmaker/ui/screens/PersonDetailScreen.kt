package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.instantcollabmaker.domain.model.Appearance
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.ui.components.FrameSurface
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.PrimaryButton
import com.instantcollabmaker.ui.components.RenameDialog
import com.instantcollabmaker.ui.components.SectionLabel
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

/**
 * Inspects one person's appearances — a name-first header (no dominant hero photo, per
 * the product's own instinct that the identity is the name, not one picture of them),
 * a button into their own collage, and a clean 3-column grid of every appearance. Only
 * scrolls if the grid itself overflows the available height (i.e. there are enough
 * appearances to need it) — the header and button never move.
 */
@Composable
fun PersonDetailScreen(
    person: Person,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onViewCollage: (String) -> Unit,
    onOpenAppearance: (appearanceIndex: Int) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    val accent = FtColor.personAccent(person.index)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FtColor.Background)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                onClick = onBack,
            )
            Text(
                text = person.displayName,
                style = FtType.titleLarge,
                color = FtColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                icon = Icons.Default.Edit,
                contentDescription = "Rename ${person.displayName}",
                onClick = { showRename = true },
            )
        }

        Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Create This Character's Collage",
                onClick = { onViewCollage(person.id) },
            )

            Spacer(Modifier.height(Spacing.xl))

            SectionLabel("APPEARANCES · ${person.appearances.size}")

            Spacer(Modifier.height(Spacing.md))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(person.appearances, key = { it.id }) { appearance ->
                AppearanceTile(
                    appearance = appearance,
                    accent = accent,
                    onClick = { onOpenAppearance(appearance.index) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
    }

    if (showRename) {
        RenameDialog(
            currentName = person.displayName,
            onConfirm = { newName -> onRename(newName); showRename = false },
            onDismiss = { showRename = false },
        )
    }
}

@Composable
private fun AppearanceTile(appearance: Appearance, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    FrameSurface(
        frame = appearance.bestFrame,
        accent = accent,
        radius = Radius.sm,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .pointerInput(appearance.id) {
                detectTapGestures(onTap = { onClick() })
            },
        contentDescription = "Appearance ${appearance.index}",
    )
}
