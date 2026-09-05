package com.instantcollabmaker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.ui.components.PrimaryButton
import com.instantcollabmaker.ui.components.SecondaryButton
import com.instantcollabmaker.ui.components.StatusPill
import com.instantcollabmaker.ui.components.VideoPosterSurface
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Sizes
import com.instantcollabmaker.ui.theme.Spacing

@Composable
fun HomeScreen(
    onChooseVideo: () -> Unit,
    onTrySample: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FtColor.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = "FrameTrace",
            style = FtType.displayLarge,
            color = FtColor.TextPrimary,
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "Instant portrait collages from your videos",
            style = FtType.titleLarge,
            color = FtColor.TextSecondary,
        )

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = "Turn any video into a beautiful collage of everyone in it. Our on-device AI finds each person, tracks their appearances, and selects the best frames — all without uploading a thing.",
            style = FtType.body,
            color = FtColor.TextSecondary,
            lineHeight = FtType.body.fontSize * 1.6f,
        )

        Spacer(Modifier.height(Spacing.xxl))

        VideoPosterSurface(
            seed = 42,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Sizes.PORTRAIT_ASPECT),
            accent = FtColor.Accent,
            contentDescription = "Video preview",
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            FtColor.Surface.copy(alpha = 0.85f),
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        .padding(Spacing.lg)
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = FtColor.TextPrimary,
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        PrimaryButton(
            text = "Choose Video",
            onClick = onChooseVideo,
            icon = null,
        )

        Spacer(Modifier.height(Spacing.md))

        SecondaryButton(
            text = "Try Sample Video",
            onClick = onTrySample,
            icon = null,
        )

        Spacer(Modifier.height(Spacing.xxl))

        StatusPill(
            text = "100% on-device processing",
            dotColor = FtColor.Success,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "No uploads. No cloud processing. Your videos stay on your device.",
            style = FtType.bodySmall,
            color = FtColor.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.xxl))
    }
}
