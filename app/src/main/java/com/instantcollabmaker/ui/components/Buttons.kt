package com.instantcollabmaker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Sizes
import com.instantcollabmaker.ui.theme.Spacing

/**
 * The app's button set.
 *
 * Material's stock buttons are deliberately not used: they bring their own ripple,
 * elevation and corner language, which fights the flat cinematic surface treatment.
 * These share one press animation (a subtle scale plus dim) so every tap in the app
 * feels identical.
 */

/** Filled champagne button. One per screen, reserved for the primary action. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.buttonHeight),
        shape = RoundedCornerShape(Radius.md),
        // The one deliberately colorful surface outside the logo/splash: gold sliding
        // into coral, so the single primary action per screen actually reads as an
        // invitation rather than just another dark rectangle.
        background = Brush.horizontalGradient(
            listOf(FtColor.Accent, FtColor.AccentCoral),
        ),
        contentDescription = contentDescription ?: text,
    ) {
        ButtonContent(text, icon, FtColor.TextOnAccent)
    }
}

/** Outlined button on the elevated surface. Used for the alternative action. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.buttonHeight),
        shape = RoundedCornerShape(Radius.md),
        background = Brush.horizontalGradient(listOf(FtColor.SurfaceElevated, FtColor.SurfaceElevated)),
        border = BorderStroke(Sizes.hairline, FtColor.StrokeStrong),
        contentDescription = contentDescription ?: text,
    ) {
        ButtonContent(text, icon, FtColor.TextPrimary)
    }
}

/** Borderless text action, for low-weight choices like "Choose Another". */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = FtColor.TextSecondary,
) {
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Sizes.minTouchTarget),
        shape = RoundedCornerShape(Radius.sm),
        background = Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
        contentDescription = text,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.iconMd))
            }
            Text(text, style = FtType.titleSmall, color = tint)
        }
    }
}

/**
 * Compact side-by-side action, used for the Save / Share pair on the collage screens
 * where two equally weighted actions have to share a row.
 */
@Composable
fun DualAction(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector? = null,
    secondaryIcon: ImageVector? = null,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PrimaryButton(
            text = primaryText,
            onClick = onPrimary,
            icon = primaryIcon,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1.35f),
        )
        SecondaryButton(
            text = secondaryText,
            onClick = onSecondary,
            icon = secondaryIcon,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Circular icon-only button, e.g. the back affordance in the top bar. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FtColor.TextPrimary,
) {
    PressableSurface(
        onClick = onClick,
        enabled = true,
        modifier = modifier.size(Sizes.minTouchTarget),
        shape = RoundedCornerShape(Radius.pill),
        background = Brush.horizontalGradient(listOf(FtColor.Surface, FtColor.Surface)),
        border = BorderStroke(Sizes.hairline, FtColor.Stroke),
        contentDescription = contentDescription,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.iconMd))
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, tint: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.iconMd))
        }
        Text(text = text, style = FtType.button, color = tint, maxLines = 1)
    }
}

/**
 * The shared press behaviour. Scale is kept at 2% and the spring is stiff — enough to
 * feel physical, not enough to look bouncy.
 */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    enabled: Boolean,
    shape: RoundedCornerShape,
    background: Brush,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 1400f),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // A hairline top highlight reads as a lit edge on the dark surface.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = if (pressed) 0.03f else 0.07f))
        )
        content()
    }
}
