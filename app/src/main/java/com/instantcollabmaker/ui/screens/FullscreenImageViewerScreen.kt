package com.instantcollabmaker.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.domain.export.GallerySaver
import com.instantcollabmaker.domain.model.SelectedFrame
import com.instantcollabmaker.ui.components.IconAction
import com.instantcollabmaker.ui.components.ZoomPanState
import com.instantcollabmaker.ui.components.applyGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

/**
 * A proper fullscreen photo viewer for one appearance/representative frame: black
 * background, the image centered at rest, pinch-zoom around whatever point the user
 * touches (never center-only), pan while zoomed, close (top-left) and download
 * (top-right, saves the real source-resolution image — never the 112x112 recognition
 * crop, since [SelectedFrame.image] is never that file in the first place).
 */
@Composable
fun FullscreenImageViewerScreen(
    frame: SelectedFrame,
    fileNameStem: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoomPan by remember(frame.id) { mutableStateOf(ZoomPanState()) }
    var sizePx by remember(frame.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var bitmap by remember(frame.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(frame.image.path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(frame.image.path)?.asImageBitmap() }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeCapture { sizePx = it }
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
                            minScale = 1f, maxScale = 6f,
                            maxAbsOffset = size.width.coerceAtLeast(size.height) * 5f,
                        )
                    }
                },
        ) {
            val img = bitmap ?: return@Canvas
            val srcW = img.width.toFloat()
            val srcH = img.height.toFloat()
            if (srcW <= 0f || srcH <= 0f) return@Canvas
            // Plain "contain, centered" at rest — a real photo viewer, not a cropped tile.
            val fitScale = kotlin.math.min(size.width / srcW, size.height / srcH)
            val drawW = srcW * fitScale
            val drawH = srcH * fitScale
            drawImage(
                image = img,
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    ((size.width - drawW) / 2f).toInt(),
                    ((size.height - drawH) / 2f).toInt(),
                ),
                dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)),
            )
        }

        IconAction(
            icon = Icons.Default.Close,
            contentDescription = "Close",
            tint = androidx.compose.ui.graphics.Color.White,
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp),
        )

        IconAction(
            icon = Icons.Default.Download,
            contentDescription = "Download",
            tint = androidx.compose.ui.graphics.Color.White,
            onClick = {
                scope.launch {
                    val decoded = withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(frame.image.path) }.getOrNull()
                    }
                    if (decoded == null) {
                        Toast.makeText(context, "Couldn't load this image.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val uri = GallerySaver(context).save(decoded, "${fileNameStem}_${System.currentTimeMillis()}")
                    decoded.recycle()
                    Toast.makeText(
                        context,
                        if (uri != null) "Saved to Pictures/Game Of Frames" else "Couldn't save to the gallery.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp),
        )
    }
}

private fun Modifier.onSizeCapture(onSize: (androidx.compose.ui.unit.IntSize) -> Unit): Modifier =
    this.onGloballyPositioned { coordinates -> onSize(coordinates.size) }
