package com.instantcollabmaker.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.R
import com.instantcollabmaker.data.samples.SampleVideo
import com.instantcollabmaker.ui.components.GameOfFramesMark
import com.instantcollabmaker.ui.components.PrimaryButton
import com.instantcollabmaker.ui.components.PressableSurface
import com.instantcollabmaker.ui.components.SampleVideoSheet
import com.instantcollabmaker.ui.components.SecondaryButton
import com.instantcollabmaker.ui.components.StatusPill
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The first-look screen, in three visually distinct bands:
 *
 * 1. **Hero** — the mark, wordmark and pitch, with a small photography-flavored motif
 *    (a periodic shutter-click on the mark, two slowly drifting film-frame chips) doing
 *    the "feels alive" work instead of a generic glowing gradient.
 * 2. **Project links** — Watch It Cook / GitHub, side by side, so a reviewer can jump to
 *    the demo or the source in one tap.
 * 3. **Action panel** — a distinct, elevated black surface with a curved top edge,
 *    holding the actual video-selection controls. Deliberately a different surface from
 *    the hero above it, not just more of the same background.
 */
@Composable
fun HomeScreen(
    onChooseVideo: () -> Unit,
    samples: List<SampleVideo>,
    onChooseSample: (SampleVideo) -> Unit,
    onWatchItCook: () -> Unit,
    onOpenGitHub: () -> Unit,
) {
    var showSamplePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    // A warm, film-toned hero ground — deliberately distinct from the
                    // action panel's neutral black below, so the two surfaces never
                    // read as "the same background, just a curve drawn on it."
                    listOf(Color(0xFF1C1712), Color(0xFF120E0B)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroMark()

                Spacer(Modifier.height(Spacing.lg))

                Text(
                    text = "GAME OF FRAMES",
                    style = FtType.displaySmall,
                    color = FtColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Spacing.xs))

                Text(
                    text = "Every frame has its throne.",
                    style = FtType.titleMedium,
                    color = FtColor.Accent,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Spacing.lg))

                Text(
                    text = "Feed it any video. It finds every face, crowns each one's best " +
                        "frame, and hands you a collage — all without a single byte leaving " +
                        "your phone.",
                    style = FtType.body,
                    color = FtColor.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = FtType.body.fontSize * 1.6f,
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                WatchItCookButton(onClick = onWatchItCook, modifier = Modifier.weight(1f))
                GitHubButton(onClick = onOpenGitHub, modifier = Modifier.weight(1f))
            }
        }

        ActionPanel(
            onChooseVideo = onChooseVideo,
            onChooseSample = { showSamplePicker = true },
        )
    }

    if (showSamplePicker) {
        SampleVideoSheet(
            samples = samples,
            onDismiss = { showSamplePicker = false },
            onSelect = { sample ->
                showSamplePicker = false
                onChooseSample(sample)
            },
        )
    }
}

/**
 * The lower, elevated black panel — a visually separate surface from the hero above it,
 * with a large curved top edge and a soft shadow to read as "popped up" rather than a
 * plain rectangle stuck to the bottom of the screen.
 */
@Composable
private fun ActionPanel(
    onChooseVideo: () -> Unit,
    onChooseSample: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = Radius.xl * 1.6f, topEnd = Radius.xl * 1.6f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 28.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xxl, bottom = Spacing.xl),
    ) {
        PrimaryButton(
            text = "Choose Video",
            onClick = onChooseVideo,
            icon = null,
        )

        Spacer(Modifier.height(Spacing.md))

        SecondaryButton(
            text = "Choose From Sample Videos",
            onClick = onChooseSample,
            icon = null,
        )

        Spacer(Modifier.height(Spacing.xl))

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
    }
}

@Composable
private fun WatchItCookButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableSurface(
        onClick = onClick,
        enabled = true,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(Radius.md),
        background = Brush.horizontalGradient(listOf(FtColor.Accent, FtColor.AccentCoral)),
        contentDescription = "Watch it cook",
    ) {
        Text(
            text = "Watch it cook 😈",
            style = FtType.titleSmall,
            color = FtColor.TextOnAccent,
            maxLines = 1,
        )
    }
}

@Composable
private fun GitHubButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableSurface(
        onClick = onClick,
        enabled = true,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(Radius.md),
        background = Brush.horizontalGradient(listOf(Color(0xFF14161B), Color(0xFF14161B))),
        border = androidx.compose.foundation.BorderStroke(1.dp, FtColor.StrokeStrong),
        contentDescription = "View on GitHub",
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text("GitHub", style = FtType.titleSmall, color = Color.White, maxLines = 1)
        }
    }
}

/**
 * The mark plus its small photography motif: a shutter-click flash every ~4.2s (a quick
 * scale-down and a brief white ring, like a camera firing), and two small film-frame
 * chips drifting slowly in its periphery. All motion is slow and low-amplitude by
 * design — this is meant to feel alive, not to demand attention.
 */
@Composable
private fun HeroMark() {
    val shutter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4200)
            shutter.animateTo(1f, tween(90, easing = LinearEasing))
            shutter.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        FilmFrameChip(
            angleBase = -12f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 22.dp, y = 20.dp),
        )
        FilmFrameChip(
            angleBase = 10f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-22).dp, y = (-20).dp),
        )

        GameOfFramesMark(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val s = 1f - shutter.value * 0.05f
                    scaleX = s
                    scaleY = s
                },
        )

        if (shutter.value > 0f) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = shutter.value * 0.45f)),
            )
        }
    }
}

/** A tiny blank photo/slide chip — the "film frame" peripheral motif — drifting very
 * slowly in rotation and position so the hero reads as quietly alive. */
@Composable
private fun FilmFrameChip(angleBase: Float, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "filmFrameDrift")
    val drift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    Box(
        modifier = modifier
            .size(width = 20.dp, height = 26.dp)
            .graphicsLayer {
                rotationZ = angleBase + drift * 4f
                translationY = drift * 5f
            }
            .clip(RoundedCornerShape(3.dp))
            .background(FtColor.SurfaceElevated)
            .border(1.dp, FtColor.Stroke, RoundedCornerShape(3.dp)),
    )
}
