package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.core.Format
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.ui.components.FrameSurface
import com.instantcollabmaker.ui.components.FtCard
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.PrimaryButton
import com.instantcollabmaker.ui.components.SectionLabel
import com.instantcollabmaker.ui.components.StatTile
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

/**
 * Assignment-minimal mode: [onPersonClick] (the per-person appearance-gallery entry
 * point), [onViewPersonCollage] and [onDownloadPersonCollage] are kept in this signature
 * for source compatibility with the navigation graph and for a possible future
 * re-enablement, but are intentionally never invoked by this screen below — see
 * [PersonCard]. The simplified final flow shows a photo + appearance count per person and
 * nothing else; tapping a person no longer opens their appearance gallery, and the
 * per-card overflow menu no longer offers a personal collage. None of that
 * implementation was deleted — see `PersonDetailScreen`, `PersonCollageScreen`.
 */
@Composable
fun ResultsScreen(
    result: VideoAnalysisResult,
    onBack: () -> Unit,
    onPersonClick: (String) -> Unit,
    onViewFullCollage: () -> Unit,
    onRenamePerson: (String, String) -> Unit,
    onViewPersonCollage: (String) -> Unit,
    onDownloadPersonCollage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FtColor.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                onClick = onBack,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        ) {
            Text(
                text = "Analysis Complete",
                style = FtType.displayMedium,
                color = FtColor.TextPrimary,
            )

            Spacer(Modifier.height(Spacing.sm))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = FtColor.Success,
                    modifier = Modifier.height(18.dp),
                )
                Text(
                    text = "Completed in ${Format.seconds(result.processingDurationMs)}",
                    style = FtType.body,
                    color = FtColor.TextSecondary,
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            // Assignment-minimal mode: trimmed to the two numbers the simplified results
            // screen actually needs (people + appearances). Frame/face diagnostic counts
            // are still fully computed and logged (see DiagnosticFrameLogger) — just not
            // surfaced in this simplified UI anymore.
            FtCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatTile(
                        value = result.peopleCount.toString(),
                        label = "People",
                        valueColor = FtColor.Accent,
                    )
                    StatTile(
                        value = result.appearanceCount.toString(),
                        label = "Appearances",
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            PrimaryButton(
                text = "Create Collage",
                onClick = onViewFullCollage,
            )

            Spacer(Modifier.height(Spacing.xxl))

            SectionLabel("PEOPLE IDENTIFIED")

            Spacer(Modifier.height(Spacing.md))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Two per row — each a big square-photo-first card, not a thin list row.
            // Chunked manually (rather than a LazyVerticalGrid) since this screen is
            // already one plain scrolling Column; a nested lazy grid would need its own
            // scroll handling for no real benefit at this list size.
            result.people.chunked(2).forEach { rowOfPeople ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    rowOfPeople.forEach { person ->
                        // Assignment-minimal mode: the appearance-gallery tap and the
                        // personal-collage overflow items are intentionally not wired here
                        // anymore — see PersonCard and the doc comment above. onPersonClick
                        // / onViewPersonCollage / onDownloadPersonCollage are retained
                        // parameters, simply unused by this simplified screen.
                        PersonCard(
                            person = person,
                            modifier = Modifier.weight(1f),
                            onRename = { newName -> onRenamePerson(person.id, newName) },
                        )
                    }
                    // Odd count: keep the last card at half-width instead of stretching it
                    // to fill the row alone.
                    if (rowOfPeople.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * A big, square-photo-first character card: the person's best shot fills the top of the
 * card edge-to-edge, with appearance count / screen time stacked underneath — two of these
 * per row (see the grid above), not a thin horizontal list row. No hairline border, a big
 * soft radius, generous breathing room — a quiet, minimal, "luxury" surface.
 *
 * Assignment-minimal mode: no name label and no overflow/rename menu — per-person display
 * names ("Person A", "Person B", ...) aren't shown here anymore, just the photo and its
 * metadata. [Person.displayName] and the rename flow ([RenameDialog], [PersonOverflowMenu])
 * are untouched and still reachable wherever else the app uses them; this card simply
 * doesn't surface them. This card is also intentionally NOT clickable (the appearance-
 * gallery detail screen it used to open is disabled for the simplified final flow — see
 * `PersonDetailScreen`, still fully implemented, just not navigated to from here).
 */
@Composable
private fun PersonCard(
    person: Person,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.xl)

    Column(
        modifier = modifier
            .clip(shape)
            .background(FtColor.SurfaceElevated),
    ) {
        // Big, square, edge-to-edge — the card's own outer clip rounds its top corners;
        // no separate radius needed on the frame itself. FrameSurface (not the fixed-size
        // FrameThumbnail) is used here specifically so the image can fill the card's own
        // width/aspect ratio instead of a small hardcoded thumbnail size.
        FrameSurface(
            frame = person.representativeFrame,
            accent = FtColor.personAccent(person.index),
            radius = 0.dp,
            showBorder = false,
            contentDescription = "Best photo of ${person.displayName}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = "Total ${Format.count(person.appearanceCount, "appearance")}",
                style = FtType.bodySmall,
                color = FtColor.AccentTeal,
            )
            Text(
                text = "Total screen time - ${Format.seconds(person.totalScreenTimeMs)}",
                style = FtType.bodySmall,
                color = FtColor.AccentCoral,
            )
        }
    }
}
