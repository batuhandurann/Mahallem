package com.batuhanduran.burada.ui.components

private const val MAX_LOCATION_AGE_NANOS = 120_000_000_000L

/**
 * Location.elapsedRealtimeNanos and SystemClock.elapsedRealtimeNanos share a monotonic clock.
 * Future-dated fixes must not bypass the age limit when the subtraction is negative.
 */
internal fun isFreshLocationFix(
    fixElapsedRealtimeNanos: Long,
    nowElapsedRealtimeNanos: Long
): Boolean {
    if (fixElapsedRealtimeNanos < 0L || fixElapsedRealtimeNanos > nowElapsedRealtimeNanos) return false
    return nowElapsedRealtimeNanos - fixElapsedRealtimeNanos <= MAX_LOCATION_AGE_NANOS
}
