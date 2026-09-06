package com.instantcollabmaker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.data.samples.SampleVideo
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing

/**
 * The "Choose From Sample Videos" sheet. Exists purely so a reviewer can test the real
 * pipeline in one tap without first finding a portrait video of their own — every clip
 * listed here still runs through the exact same on-device analysis as anything picked
 * from the system video picker; see [SampleVideoProvider] for why that's true by
 * construction, not just by convention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleVideoSheet(
    samples: List<SampleVideo>,
    onDismiss: () -> Unit,
    onSelect: (SampleVideo) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FtColor.Surface,
        contentColor = FtColor.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = "Choose From Sample Videos",
                style = FtType.titleLarge,
                color = FtColor.TextPrimary,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(FtColor.SurfaceElevated)
                    .padding(Spacing.lg),
            ) {
                Text(
                    text = "Don't worry — the results are not hard-coded, and are generated " +
                        "at runtime. You can always upload new videos from the video picker " +
                        "as well.",
                    style = FtType.bodySmall,
                    color = FtColor.TextSecondary,
                    lineHeight = FtType.bodySmall.fontSize * 1.5f,
                )
            }

            samples.forEach { sample ->
                SampleVideoRow(sample = sample, onClick = { onSelect(sample) })
            }
        }
    }
}

@Composable
private fun SampleVideoRow(sample: SampleVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(FtColor.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(FtColor.Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = FtColor.Accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(sample.title, style = FtType.titleSmall, color = FtColor.TextPrimary)
            Text(sample.subtitle, style = FtType.bodySmall, color = FtColor.TextMuted)
        }
    }
}
