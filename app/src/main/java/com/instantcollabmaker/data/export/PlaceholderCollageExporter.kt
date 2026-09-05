package com.instantcollabmaker.data.export

import android.graphics.Bitmap
import com.instantcollabmaker.domain.export.CollageExporter
import com.instantcollabmaker.domain.model.CollageLayout

/**
 * Phase 1 placeholder that always returns null, signaling "not yet implemented".
 *
 * Phase 2 replaces this with a real renderer that draws the collage layout to a bitmap
 * using the same procedural portrait code that powers the on-screen tiles.
 */
class PlaceholderCollageExporter : CollageExporter {
    override suspend fun export(layout: CollageLayout, targetWidthPx: Int): Bitmap? {
        return null
    }
}
