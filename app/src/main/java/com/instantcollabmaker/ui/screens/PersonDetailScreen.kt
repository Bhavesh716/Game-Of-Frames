package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.core.Format
import com.instantcollabmaker.domain.model.Appearance
import com.instantcollabmaker.domain.model.Person
import com.instantcollabmaker.ui.components.FtCard
import com.instantcollabmaker.ui.components.FrameSurface
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.PrimaryButton
import com.instantcollabmaker.ui.components.QualityMeter
import com.instantcollabmaker.ui.components.SectionLabel
import com.instantcollabmaker.ui.components.SpecRow
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Sizes
import com.instantcollabmaker.ui.theme.Spacing

@Composable
fun PersonDetailScreen(
    person: Person,
    onBack: () -> Unit,
    onViewCollage: (String) -> Unit,
) {
    val accent = FtColor.personAccent(person.index)

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
                text = person.displayName,
                style = FtType.displayMedium,
                color = FtColor.TextPrimary,
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "${person.appearances.size} appearance${if (person.appearances.size != 1) "s" else ""} · ${(person.identityConfidence * 100).toInt()}% confidence",
                style = FtType.body,
                color = FtColor.TextSecondary,
            )

            Spacer(Modifier.height(Spacing.xxl))

            FrameSurface(
                frame = person.representativeFrame,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(Sizes.PORTRAIT_ASPECT),
                contentDescription = "Representative frame for ${person.displayName}",
            )

            Spacer(Modifier.height(Spacing.xl))

            PrimaryButton(
                text = "View ${person.displayName}'s Collage",
                onClick = { onViewCollage(person.id) },
            )

            Spacer(Modifier.height(Spacing.xxl))

            SectionLabel("APPEARANCE TIMELINE")

            Spacer(Modifier.height(Spacing.md))
        }

        person.appearances.forEachIndexed { index, appearance ->
            AppearanceCard(
                appearance = appearance,
                accent = accent,
            )
            if (index < person.appearances.size - 1) {
                Spacer(Modifier.height(Spacing.md))
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun AppearanceCard(
    appearance: Appearance,
    accent: androidx.compose.ui.graphics.Color,
) {
    FtCard(
        modifier = Modifier.padding(horizontal = Spacing.gutter),
        contentPadding = Spacing.lg,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Appearance ${appearance.index}",
                style = FtType.titleSmall,
                color = FtColor.TextPrimary,
            )

            Box(
                modifier = Modifier
                    .background(
                        FtColor.SurfaceHighest,
                        androidx.compose.foundation.shape.RoundedCornerShape(com.instantcollabmaker.ui.theme.Radius.pill)
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    text = Format.timecode(appearance.bestFrameTimestampMs),
                    style = FtType.labelTiny,
                    color = accent,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        FrameSurface(
            frame = appearance.bestFrame,
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Sizes.PORTRAIT_ASPECT),
            contentDescription = "Frame at ${Format.timecode(appearance.bestFrameTimestampMs)}",
        )

        Spacer(Modifier.height(Spacing.md))

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "QUALITY",
                    style = FtType.labelTiny,
                    color = FtColor.TextMuted,
                )
                Text(
                    text = "${(appearance.qualityScore * 100).toInt()}/100",
                    style = FtType.bodySmall,
                    color = FtColor.TextPrimary,
                )
            }

            QualityMeter(
                fraction = appearance.qualityScore,
                color = accent,
            )
        }

        Spacer(Modifier.height(Spacing.md))

        SpecRow(
            label = "Duration",
            value = Format.seconds(appearance.durationMs),
        )

        SpecRow(
            label = "Frames",
            value = appearance.detectedFrameCount.toString(),
        )

        SpecRow(
            label = "Range",
            value = Format.timeRange(appearance.startTimestampMs, appearance.endTimestampMs),
        )
    }
}
