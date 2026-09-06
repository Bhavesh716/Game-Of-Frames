package com.instantcollabmaker.core

/**
 * A [coerceIn] that can never throw `IllegalArgumentException("Cannot coerce value to an
 * empty range...")`.
 *
 * The plain stdlib `coerceIn` requires `minimumValue <= maximumValue` or it throws — fine
 * when both bounds are fixed literals, dangerous when either is *derived* from a runtime
 * measurement (a bitmap's decoded size, a Compose layout's measured size, a scale ratio
 * computed from them). Two ways that can go wrong even when the surrounding code's own
 * logic is correct:
 *
 * 1. **Floating-point rounding.** e.g. a cover-fit scale computed as `max(dstW / srcW, dstH
 *    / srcH)` then multiplied back by `srcW` is *mathematically* guaranteed `>= dstW`, but
 *    IEEE-754 rounding can make the float-multiplied result a few ULPs *less* than `dstW`
 *    — enough to flip a bound that was supposed to be `<= 0f` into a tiny positive number.
 * 2. **A genuine degenerate input** — a zero-width/zero-height bitmap or layout — which
 *    should be treated as a well-defined failure, not an uncaught crash.
 *
 * This never throws: a non-finite bound falls back to the other bound (or to `this` /
 * `0` if both are non-finite), and if the bounds are still inverted after that, they are
 * swapped rather than left to crash `coerceIn`. Use this instead of `coerceIn` for any
 * bound built from a runtime size/scale rather than a fixed literal.
 */
fun Float.coerceInSafe(minimumValue: Float, maximumValue: Float): Float {
    if (!isFinite()) return firstFinite(maximumValue, minimumValue, 0f)
    val hi = if (maximumValue.isFinite()) maximumValue else firstFinite(minimumValue, this, 0f)
    val lo = if (minimumValue.isFinite()) minimumValue else hi
    return if (lo <= hi) coerceIn(lo, hi) else coerceIn(hi, lo)
}

/** Int counterpart — no NaN/Infinity concern, only the inverted-bounds case (e.g. a
 * zero-size source dimension collapsing `max` below `min`). Falls back to [maximumValue]
 * (clamped: never negative) instead of throwing when the bounds are inverted. */
fun Int.coerceInSafe(minimumValue: Int, maximumValue: Int): Int =
    if (minimumValue <= maximumValue) coerceIn(minimumValue, maximumValue) else maximumValue.coerceAtLeast(0)

private fun firstFinite(vararg values: Float): Float = values.firstOrNull { it.isFinite() } ?: 0f
