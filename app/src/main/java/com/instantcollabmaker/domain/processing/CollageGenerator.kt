package com.instantcollabmaker.domain.processing

import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.CollageSpec

/**
 * Turns an analysis result into a concrete collage composition — pure layout geometry,
 * with no opinion on where a tile's pixels come from. `CollageCanvas` (Compose preview)
 * and `CanvasCollageExporter` (bitmap export) both consume the same [CollageLayout].
 */
interface CollageGenerator {
    fun layout(spec: CollageSpec): CollageLayout
}
