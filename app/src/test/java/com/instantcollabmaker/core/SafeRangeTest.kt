package com.instantcollabmaker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [coerceInSafe] must never throw "Cannot coerce value to an empty range" regardless of
 * what nonsense a caller passes in — this is the crash-safety net every crop calculation
 * in the pipeline relies on.
 */
class SafeRangeTest {

    @Test
    fun `normal in-range value passes through coerceInSafe unchanged`() {
        assertEquals(5f, 5f.coerceInSafe(0f, 10f), 0f)
    }

    @Test
    fun `value below range clamps to minimum`() {
        assertEquals(0f, (-5f).coerceInSafe(0f, 10f), 0f)
    }

    @Test
    fun `value above range clamps to maximum`() {
        assertEquals(10f, 15f.coerceInSafe(0f, 10f), 0f)
    }

    @Test
    fun `inverted bounds (minimum greater than maximum) never throw`() {
        val result = 5f.coerceInSafe(10f, 0f)
        assertTrue(result.isFinite())
    }

    @Test
    fun `a tiny positive minimum with a zero maximum never throws`() {
        // The exact shape of the reported crash: max 0.0 < min 1.5258789E-5.
        val result = (-3f).coerceInSafe(1.5258789E-5f, 0f)
        assertTrue(result.isFinite())
    }

    @Test
    fun `NaN value never throws and returns a finite result`() {
        val result = Float.NaN.coerceInSafe(0f, 10f)
        assertTrue(result.isFinite())
    }

    @Test
    fun `Infinity bounds never throw and return a finite result`() {
        assertTrue(5f.coerceInSafe(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).isFinite())
        assertTrue(5f.coerceInSafe(0f, Float.POSITIVE_INFINITY) >= 0f)
    }

    @Test
    fun `NaN bound never throws`() {
        val result = 5f.coerceInSafe(Float.NaN, 10f)
        assertTrue(result.isFinite())
    }

    @Test
    fun `Int coerceInSafe with inverted bounds never throws`() {
        val result = 5.coerceInSafe(10, 0)
        assertTrue(result >= 0)
    }

    @Test
    fun `Int coerceInSafe normal case behaves like coerceIn`() {
        assertEquals(5, 5.coerceInSafe(0, 10))
        assertEquals(0, (-3).coerceInSafe(0, 10))
        assertEquals(10, 15.coerceInSafe(0, 10))
    }

    @Test
    fun `zero-width Int range (left equals right minus one edge case) never throws`() {
        // Mirrors a zero-size source dimension collapsing max below min in crop math.
        val result = 0.coerceInSafe(0, -1)
        assertTrue(result >= 0)
    }
}
