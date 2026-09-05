package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.core.Format
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.ui.components.FtCard
import com.instantcollabmaker.ui.components.FtDivider
import com.instantcollabmaker.ui.components.FrameThumbnail
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.PrimaryButton
import com.instantcollabmaker.ui.components.SectionLabel
import com.instantcollabmaker.ui.components.StatTile
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

@Composable
fun ResultsScreen(
    result: VideoAnalysisResult,
    onBack: () -> Unit,
    onPersonClick: (String) -> Unit,
    onViewFullCollage: () -> Unit,
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
                    StatTile(
                        value = result.framesAnalyzed.toString(),
                        label = "Frames",
                    )
                    StatTile(
                        value = result.facesDetected.toString(),
                        label = "Faces",
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            PrimaryButton(
                text = "View Full Collage",
                onClick = onViewFullCollage,
            )

            Spacer(Modifier.height(Spacing.xxl))

            SectionLabel("PEOPLE IDENTIFIED")

            Spacer(Modifier.height(Spacing.md))
        }

        Column {
            result.people.forEachIndexed { index, person ->
                if (index > 0) {
                    FtDivider(modifier = Modifier.padding(horizontal = Spacing.gutter))
                }
                PersonRow(
                    person = person,
                    onClick = { onPersonClick(person.id) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun PersonRow(
    person: Person,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrameThumbnail(
            frame = person.representativeFrame,
            accent = FtColor.personAccent(person.index),
            contentDescription = "Representative frame for ${person.displayName}",
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = person.displayName,
                style = FtType.titleMedium,
                color = FtColor.TextPrimary,
            )

            Text(
                text = "${person.appearances.size} appearance${if (person.appearances.size != 1) "s" else ""}",
                style = FtType.bodySmall,
                color = FtColor.TextSecondary,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(FtColor.SurfaceHighest)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text(
                text = "${(person.identityConfidence * 100).toInt()}%",
                style = FtType.labelTiny,
                color = FtColor.TextMuted,
            )
        }
    }
}
