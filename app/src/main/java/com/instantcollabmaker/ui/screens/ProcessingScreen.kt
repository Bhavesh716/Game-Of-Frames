package com.instantcollabmaker.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.domain.model.ProcessingStage
import com.instantcollabmaker.domain.model.ProcessingStats
import com.instantcollabmaker.ui.components.FtCard
import com.instantcollabmaker.ui.components.SectionLabel
import com.instantcollabmaker.ui.components.StatTile
import com.instantcollabmaker.ui.components.TertiaryButton
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

@Composable
fun ProcessingScreen(
    progress: Float,
    stage: ProcessingStage,
    stats: ProcessingStats,
    onCancel: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FtColor.Background)
            .systemBarsPadding()
            .padding(horizontal = Spacing.gutter),
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = "Analyzing Video",
            style = FtType.displayMedium,
            color = FtColor.TextPrimary,
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "On-device processing in progress",
            style = FtType.body,
            color = FtColor.TextSecondary,
        )

        Spacer(Modifier.height(Spacing.xxl))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(Radius.pill)),
            color = FtColor.Accent,
            trackColor = FtColor.SurfaceHighest,
            strokeCap = StrokeCap.Round,
        )

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = FtType.titleLarge,
            color = FtColor.Accent,
        )

        Spacer(Modifier.height(Spacing.xxl))

        FtCard {
            Text(
                text = stage.title,
                style = FtType.titleMedium,
                color = FtColor.TextPrimary,
            )

            Spacer(Modifier.height(Spacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatTile(
                    value = stats.framesAnalyzed.toString(),
                    label = "Frames",
                )
                StatTile(
                    value = stats.facesDetected.toString(),
                    label = "Faces",
                )
                StatTile(
                    value = stats.peopleIdentified.toString(),
                    label = "Temp IDs",
                )
                StatTile(
                    value = stats.appearancesDetected.toString(),
                    label = "Appearances",
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        SectionLabel("PROCESSING STAGES")

        Spacer(Modifier.height(Spacing.md))

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ProcessingStage.ordered.forEach { s ->
                StageRow(
                    stage = s,
                    isActive = s == stage,
                    isComplete = s.progressEnd <= progress,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        TertiaryButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun StageRow(
    stage: ProcessingStage,
    isActive: Boolean,
    isComplete: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(
                    when {
                        isComplete -> FtColor.Success
                        isActive -> FtColor.Accent
                        else -> FtColor.SurfaceHighest
                    }
                )
        )

        Spacer(Modifier.width(Spacing.md))

        Text(
            text = stage.title,
            style = FtType.body,
            color = when {
                isActive -> FtColor.TextPrimary
                isComplete -> FtColor.TextSecondary
                else -> FtColor.TextMuted
            },
        )
    }
}
