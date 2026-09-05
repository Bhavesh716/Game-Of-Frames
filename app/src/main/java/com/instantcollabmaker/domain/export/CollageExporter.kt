package com.instantcollabmaker.domain.export

import android.graphics.Bitmap
import com.instantcollabmaker.domain.model.CollageLayout

/**
 * Phase 2 contract for bitmap export.
 *
 * Phase 1 uses a placeholder implementation that immediately returns null, which callers
 * handle by showing a "Feature coming soon" toast. Phase 2 implements the real renderer.
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
