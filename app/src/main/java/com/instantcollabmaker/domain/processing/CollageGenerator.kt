package com.instantcollabmaker.domain.processing

import com.instantcollabmaker.domain.model.CollageLayout
import com.instantcollabmaker.domain.model.CollageSpec

/**
 * Turns an analysis result into a concrete collage composition.
 *
 * Phase 1 computes a layout that the Compose renderer draws with procedural portraits;
 * Phase 2 keeps the same layout maths and swaps in real extracted frames.
 */
interface CollageGenerator {
    fun layout(spec: CollageSpec): CollageLayout
}
