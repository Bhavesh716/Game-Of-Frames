package com.instantcollabmaker.domain.export

import android.graphics.Bitmap
import com.instantcollabmaker.domain.model.CollageLayout

/**
 * Renders a [CollageLayout] to a real bitmap suitable for saving or sharing. Implemented
 * by `com.instantcollabmaker.data.collage.CanvasCollageExporter`.
 */
interface CollageExporter {
    /**
     * Renders a [CollageLayout] into a bitmap suitable for saving or sharing.
     *
     * @param layout The collage layout to render
     * @param targetWidthPx Target width in pixels; height is derived from layout aspect ratio
     * @return Rendered bitmap, or null if rendering fails
     */
    suspend fun export(layout: CollageLayout, targetWidthPx: Int): Bitmap?
}
