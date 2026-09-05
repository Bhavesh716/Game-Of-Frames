package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.VideoAnalysisResult
import com.instantcollabmaker.ui.components.CollageCanvas
import com.instantcollabmaker.ui.components.DualAction
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.SectionLabel
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Spacing

@Composable
fun FullCollageScreen(
    result: VideoAnalysisResult,
    layout: CollageLayout,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
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
                text = "Full Collage",
                style = FtType.displayMedium,
                color = FtColor.TextPrimary,
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "${result.peopleCount} people · ${result.appearanceCount} appearances",
                style = FtType.body,
                color = FtColor.TextSecondary,
            )

            Spacer(Modifier.height(Spacing.xxl))

            CollageCanvas(
                layout = layout,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.xxl))

            DualAction(
                primaryText = "Save to Gallery",
                onPrimary = onSave,
                secondaryText = "Share",
                onSecondary = onShare,
                secondaryIcon = Icons.Default.Share,
            )

            Spacer(Modifier.height(Spacing.xxl))

            SectionLabel("ABOUT THIS COLLAGE")

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "This collage shows everyone identified in your video, with the best frame from each of their appearances. The asymmetric editorial layout adapts to the number of people and appearances discovered.",
                style = FtType.body,
                color = FtColor.TextSecondary,
                lineHeight = FtType.body.fontSize * 1.6f,
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
