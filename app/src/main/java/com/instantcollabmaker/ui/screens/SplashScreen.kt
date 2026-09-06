package com.instantcollabmaker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.instantcollabmaker.ui.components.GameOfFramesMark
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The cold-open animation: the mark draws itself in (brackets first, then the play
 * glyph), the wordmark and tagline settle in underneath, a short beat to let it land,
 * then [onFinished] fires so the nav graph can move on to [HomeScreen]. Pure Compose —
 * no Lottie, no bundled animation asset — so the whole sequence lives in one readable
 * function and costs nothing in APK size.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val markProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        launch {
            markProgress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
        // The wordmark settles in while the play glyph is still resolving, so the two
        // beats overlap instead of reading as two separate steps.
        launch {
            delay(450)
            launch { textAlpha.animateTo(1f, tween(durationMillis = 550, easing = EaseOutCubic)) }
            launch { textOffset.animateTo(0f, tween(durationMillis = 550, easing = EaseOutCubic)) }
        }
        delay(1650)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FtColor.Background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FtColor.AccentTeal.copy(alpha = 0.16f * markProgress.value),
                        FtColor.AccentCoral.copy(alpha = 0.08f * markProgress.value),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.width * 1.1f,
                ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        GameOfFramesMark(
            modifier = Modifier.size(112.dp),
            progress = markProgress.value,
        )

        Spacer(Modifier.height(Spacing.xl))

        Column(
            modifier = Modifier
                .alpha(textAlpha.value)
                .graphicsLayer { translationY = textOffset.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "GAME OF FRAMES",
                style = FtType.titleLarge,
                color = FtColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Every frame has its throne.",
                style = FtType.bodySmall,
                color = FtColor.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
        }
    }
}
