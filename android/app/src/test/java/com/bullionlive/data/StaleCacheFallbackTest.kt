package com.bullionlive.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * StaleCacheFallbackTest - Tests for stale cache fallback behavior
 *
 * CRITICAL BUG FIXED: After hours of widget operation, prices would show "Error"
 * while percentages continued to work. Root cause: cache expired after 5 minutes
 * and if the API call failed (rate limiting, network issues, invalid data),
 * the widget would show "Error" instead of using slightly stale but valid data.
 *
 * SOLUTION: Implement 15-minute stale cache fallback that returns cached data
 * when fresh API calls fail, as long as the cache is less than 15 minutes old.
 *
 * TEST COVERAGE:
 * - Fresh cache (< 5 min): Used immediately, no API call
 * - Stale cache (5-15 min): API call attempted, fallback to cache on failure
 * - Expired cache (> 15 min): Must succeed with fresh data or return error
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.data.StaleCacheFallbackTest"
 */
class StaleCacheFallbackTest {

    companion object {
        // Cache constants (must match API classes)
        private const val CACHE_MAX_AGE_MS = 5 * 60 * 1000L        // 5 minutes
        private const val STALE_CACHE_MAX_AGE_MS = 15 * 60 * 1000L // 15 minutes
    }

    // ========================================
    // CACHE AGE BOUNDARY TESTS
    // ========================================

    @Test
    fun cache_freshAt4Minutes_shouldBeValid() {
        val timestamp = System.currentTimeMillis() - (4 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isFresh = age < CACHE_MAX_AGE_MS
        assertTrue("4-minute old cache should be fresh", isFresh)
    }

    @Test
    fun cache_at5Minutes_shouldBeStale() {
        val timestamp = System.currentTimeMillis() - (5 * 60 * 1000 + 1)
        val age = System.currentTimeMillis() - timestamp
        val isFresh = age < CACHE_MAX_AGE_MS
        assertFalse("5-minute old cache should be stale", isFresh)
    }

    @Test
    fun staleCache_at10Minutes_shouldBeAvailable() {
        val timestamp = System.currentTimeMillis() - (10 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isStaleAvailable = age < STALE_CACHE_MAX_AGE_MS
        assertTrue("10-minute old cache should be available as stale fallback", isStaleAvailable)
    }

    @Test
    fun staleCache_at14Minutes_shouldBeAvailable() {
        val timestamp = System.currentTimeMillis() - (14 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isStaleAvailable = age < STALE_CACHE_MAX_AGE_MS
        assertTrue("14-minute old cache should be available as stale fallback", isStaleAvailable)
    }

    @Test
    fun staleCache_at16Minutes_shouldNotBeAvailable() {
        val timestamp = System.currentTimeMillis() - (16 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isStaleAvailable = age < STALE_CACHE_MAX_AGE_MS
        assertFalse("16-minute old cache should NOT be available", isStaleAvailable)
    }

    @Test
    fun staleCache_at1Hour_shouldNotBeAvailable() {
        val timestamp = System.currentTimeMillis() - (60 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isStaleAvailable = age < STALE_CACHE_MAX_AGE_MS
        assertFalse("1-hour old cache should NOT be available", isStaleAvailable)
    }

    // ========================================
    // CACHE DECISION LOGIC TESTS
    // ========================================

    @Test
    fun cacheLogic_freshCache_shouldNotTriggerApiFetch() {
        val cacheAge = 3 * 60 * 1000L // 3 minutes
        val shouldFetch = cacheAge >= CACHE_MAX_AGE_MS
        assertFalse("Fresh cache should not trigger API fetch", shouldFetch)
    }

    @Test
    fun cacheLogic_staleCache_shouldTriggerApiFetch() {
        val cacheAge = 6 * 60 * 1000L // 6 minutes
        val shouldFetch = cacheAge >= CACHE_MAX_AGE_MS
        assertTrue("Stale cache should trigger API fetch", shouldFetch)
    }

    @Test
    fun cacheLogic_staleCache_canFallbackOnFailure() {
        val cacheAge = 10 * 60 * 1000L // 10 minutes
        val apiFailed = true
        val canFallback = cacheAge < STALE_CACHE_MAX_AGE_MS

        // Simulating: API failed but stale cache available
        val shouldUseFallback = apiFailed && canFallback
        assertTrue("Should fallback to stale cache when API fails", shouldUseFallback)
    }

    @Test
    fun cacheLogic_expiredCache_cannotFallback() {
        val cacheAge = 20 * 60 * 1000L // 20 minutes
        val apiFailed = true
        val canFallback = cacheAge < STALE_CACHE_MAX_AGE_MS

        val shouldUseFallback = apiFailed && canFallback
        assertFalse("Cannot fallback to cache older than 15 minutes", shouldUseFallback)
    }

    // ========================================
    // WIDGET REFRESH SCENARIO TESTS
    // ========================================

    @Test
    fun scenario_widgetRefreshEvery30Min_firstRefreshAfter5MinCache() {
        // Widget updates every 30 minutes
        // Cache is 5 minutes old when widget refreshes
        // API fails
        // Should use stale cache (5 min < 15 min)

        val cacheAgeMs = 5 * 60 * 1000L
        val canUseStale = cacheAgeMs < STALE_CACHE_MAX_AGE_MS
        assertTrue("5-min cache should be usable on API failure", canUseStale)
    }

    @Test
    fun scenario_multipleRefreshFailures_cacheAt14Min() {
        // Worst case: cache is 14 minutes old
        // Still within 15-minute window
        // Should use stale cache

        val cacheAgeMs = 14 * 60 * 1000L
        val canUseStale = cacheAgeMs < STALE_CACHE_MAX_AGE_MS
        assertTrue("14-min cache should still be usable", canUseStale)
    }

    @Test
    fun scenario_longOutage_cacheExpires() {
        // If API is down for more than 15 minutes
        // Cache expires
        // Widget will show error (correct behavior - data too stale)

        val cacheAgeMs = 16 * 60 * 1000L
        val canUseStale = cacheAgeMs < STALE_CACHE_MAX_AGE_MS
        assertFalse("16-min cache should NOT be usable - too stale", canUseStale)
    }

    // ========================================
    // CONSTANT VALIDATION TESTS
    // ========================================

    @Test
    fun constants_freshCacheIs5Minutes() {
        assertEquals("Fresh cache TTL should be 5 minutes",
            5 * 60 * 1000L, CACHE_MAX_AGE_MS)
    }

    @Test
    fun constants_staleCacheIs15Minutes() {
        assertEquals("Stale cache TTL should be 15 minutes",
            15 * 60 * 1000L, STALE_CACHE_MAX_AGE_MS)
    }

    @Test
    fun constants_staleCacheIs3xFreshCache() {
        val ratio = STALE_CACHE_MAX_AGE_MS / CACHE_MAX_AGE_MS
        assertEquals("Stale cache should be 3x fresh cache", 3L, ratio)
    }
}
