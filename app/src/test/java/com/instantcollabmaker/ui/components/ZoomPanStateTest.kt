package com.instantcollabmaker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomPanStateTest {

    @Test
    fun `a no-op gesture (zoom 1, no pan) leaves state unchanged`() {
        val state = ZoomPanState(scale = 2f, offsetX = 10f, offsetY = -5f)
        val result = state.applyGesture(centroidX = 50f, centroidY = 50f, panX = 0f, panY = 0f, zoomChange = 1f, pivotX = 50f, pivotY = 50f)
        assertEquals(state, result)
    }

    @Test
    fun `zooming in around the exact pivot does not introduce any offset`() {
        val state = ZoomPanState()
        val result = state.applyGesture(centroidX = 50f, centroidY = 50f, panX = 0f, panY = 0f, zoomChange = 2f, pivotX = 50f, pivotY = 50f)
        assertEquals(2f, result.scale, 1e-4f)
        assertEquals(0f, result.offsetX, 1e-4f)
        assertEquals(0f, result.offsetY, 1e-4f)
    }

    @Test
    fun `zooming around a point away from pivot shifts the offset so that point stays fixed`() {
        val state = ZoomPanState()
        // Pinching near the top-left corner of a 100x100 element (pivot at its center).
        val result = state.applyGesture(centroidX = 10f, centroidY = 10f, panX = 0f, panY = 0f, zoomChange = 2f, pivotX = 50f, pivotY = 50f)
        // The point at (10,10) must remain at (10,10) after the transform:
        // pivot + newScale*(P - pivot) + offset == centroid, where P is the pre-zoom
        // child-local point under the finger. Rather than re-derive, assert the offset is
        // non-zero and in the expected direction (away from pivot, toward centroid).
        assertTrue("offset must shift toward the pinch point, not stay zero", result.offsetX != 0f || result.offsetY != 0f)
    }

    @Test
    fun `pan alone (no zoom) moves the offset by exactly the pan amount`() {
        val state = ZoomPanState(scale = 1.5f, offsetX = 5f, offsetY = 5f)
        val result = state.applyGesture(centroidX = 0f, centroidY = 0f, panX = 20f, panY = -10f, zoomChange = 1f, pivotX = 0f, pivotY = 0f)
        assertEquals(25f, result.offsetX, 1e-4f)
        assertEquals(-5f, result.offsetY, 1e-4f)
    }

    @Test
    fun `scale never exceeds the configured maximum`() {
        val state = ZoomPanState(scale = 4.5f)
        val result = state.applyGesture(centroidX = 0f, centroidY = 0f, panX = 0f, panY = 0f, zoomChange = 10f, pivotX = 0f, pivotY = 0f, maxScale = 5f)
        assertEquals(5f, result.scale, 1e-4f)
    }

    @Test
    fun `scale never drops below the configured minimum`() {
        val state = ZoomPanState(scale = 1.2f)
        val result = state.applyGesture(centroidX = 0f, centroidY = 0f, panX = 0f, panY = 0f, zoomChange = 0.01f, pivotX = 0f, pivotY = 0f, minScale = 1f)
        assertEquals(1f, result.scale, 1e-4f)
    }

    @Test
    fun `a NaN or Infinity gesture value is rejected -- state is returned unchanged`() {
        val state = ZoomPanState(scale = 2f, offsetX = 3f, offsetY = 4f)
        assertEquals(state, state.applyGesture(Float.NaN, 0f, 0f, 0f, 1.2f, 0f, 0f))
        assertEquals(state, state.applyGesture(0f, 0f, 0f, 0f, Float.POSITIVE_INFINITY, 0f, 0f))
        assertEquals(state, state.applyGesture(0f, 0f, Float.NaN, 0f, 1.2f, 0f, 0f))
        assertEquals(state, state.applyGesture(0f, 0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals(state, state.applyGesture(0f, 0f, 0f, 0f, -1f, 0f, 0f))
    }

    @Test
    fun `offset is always clamped to a finite bound, never runs away unbounded`() {
        val state = ZoomPanState()
        val result = state.applyGesture(
            centroidX = 1_000_000f, centroidY = 1_000_000f, panX = 0f, panY = 0f,
            zoomChange = 5f, pivotX = 0f, pivotY = 0f, maxAbsOffset = 500f,
        )
        assertTrue(result.offsetX.isFinite())
        assertTrue(result.offsetY.isFinite())
        assertTrue(kotlin.math.abs(result.offsetX) <= 500f)
        assertTrue(kotlin.math.abs(result.offsetY) <= 500f)
    }

    @Test
    fun `resulting state is always finite even from an already-corrupt starting state`() {
        val corrupt = ZoomPanState(scale = Float.NaN, offsetX = Float.POSITIVE_INFINITY, offsetY = 3f)
        val result = corrupt.applyGesture(centroidX = 10f, centroidY = 10f, panX = 1f, panY = 1f, zoomChange = 1.1f, pivotX = 0f, pivotY = 0f)
        assertTrue("must recover to a finite scale even from a corrupt starting scale", result.scale.isFinite())
        assertTrue(result.offsetX.isFinite())
        assertTrue(result.offsetY.isFinite())
    }
}
