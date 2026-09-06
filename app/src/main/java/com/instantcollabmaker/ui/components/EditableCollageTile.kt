package com.instantcollabmaker.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.domain.model.TileTransform
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.Radius
import com.instantcollabmaker.ui.theme.Sizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One independently-editable collage portrait: pinch-to-zoom, pinch-to-shrink and
 * two-finger pan, entirely local to this tile — [transform] is owned by the caller (one
 * per tile, keyed by [SelectedFrame.id]) so editing one photo never touches any other
 * tile's state. Tapping shows a small swap icon over a subtle dark scrim in the top-left
 * corner (never a permanent block over the photo); tapping the icon requests a swap via
 * [onSwapRequested].
 *
 * The zoom/pan a user applies here is exactly what gets baked into the exported/shared
 * image — see [com.instantcollabmaker.data.collage.CanvasCollageExporter], which applies
 * the identical scale+translate math around the same pivot.
 */
@Composable
fun EditableCollageTile(
    frame: SelectedFrame,
    accent: Color,
    transform: TileTransform,
    onTransformChange: (TileTransform) -> Unit,
    onSwapRequested: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    contentDescription: String? = null,
) {
    var showSwapOverlay by remember(frame.id) { mutableStateOf(false) }
    // Seeded from the incoming transform's scale only; offset is re-derived in raw pixels
    // once this tile's size is known (see the LaunchedEffect below) since TileTransform's
    // offset is stored resolution-independent (a fraction of the drawn size).
    var zoomPan by remember(frame.id) { mutableStateOf(ZoomPanState(scale = transform.scale.coerceAtLeast(1f))) }
    var sizePx by remember(frame.id) { mutableStateOf(IntSize.Zero) }

    // Reports the edit back to the caller in resolution-independent fraction units (see
    // TileTransform's doc) so it survives being re-applied at a different render size
    // (on-screen preview vs. the export bitmap).
    LaunchedEffect(zoomPan, sizePx) {
        if (sizePx.width <= 0 || sizePx.height <= 0) return@LaunchedEffect
        val drawnW = sizePx.width * zoomPan.scale
        val drawnH = sizePx.height * zoomPan.scale
        onTransformChange(
            TileTransform(
                scale = zoomPan.scale,
                offsetXFraction = if (drawnW > 0f) zoomPan.offsetX / drawnW else 0f,
                offsetYFraction = if (drawnH > 0f) zoomPan.offsetY / drawnH else 0f,
            )
        )
    }

    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(FtColor.SurfaceElevated)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            ),
    ) {
        val decoded = rememberDecodedFrame(frame.image.path)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates -> sizePx = coordinates.size }
                .graphicsLayer {
                    scaleX = zoomPan.scale
                    scaleY = zoomPan.scale
                    translationX = zoomPan.offsetX
                    translationY = zoomPan.offsetY
                }
                .pointerInput(frame.id) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        zoomPan = zoomPan.applyGesture(
                            centroidX = centroid.x, centroidY = centroid.y,
                            panX = pan.x, panY = pan.y, zoomChange = zoom,
                            pivotX = size.width / 2f, pivotY = size.height / 2f,
                            maxAbsOffset = size.width.coerceAtLeast(size.height) * 4f,
                        )
                    }
                }
                .pointerInput(frame.id) {
                    detectTapGestures(onTap = { showSwapOverlay = !showSwapOverlay })
                },
        ) {
            drawFrameImage(decoded, accent, Offset(frame.image.anchorX, frame.image.anchorY))
        }

        if (showSwapOverlay) {
            Box(
                modifier = Modifier
                    .padding(Sizes.hairline * 6)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .size(36.dp)
                    .pointerInput(frame.id) {
                        detectTapGestures(onTap = { onSwapRequested() })
                    }
                    .semantics { this.contentDescription = "Swap photo" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberDecodedFrame(path: String): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}
