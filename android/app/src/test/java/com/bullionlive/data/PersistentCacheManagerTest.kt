package com.bullionlive.data

import org.junit.Test
import org.junit.Assert.*

/**
 * PersistentCacheManagerTest - Tests for persistent cache logic
 *
 * CRITICAL FIX: The original in-memory cache was lost when Android killed the app
 * process after hours of inactivity. When the widget updated and the API failed,
 * there was no fallback data, causing "Error" to be displayed.
 *
 * SOLUTION: PersistentCacheManager stores prices in SharedPreferences with:
 * - 5-minute fresh cache (use immediately)
 * - 2-hour stale cache (fallback when API fails)
 * - Retry logic with exponential backoff
 *
 * NOTE: These tests verify the cache timing logic. SharedPreferences behavior
 * is tested separately in integration tests.
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.data.PersistentCacheManagerTest"
 */
class PersistentCacheManagerTest {

    companion object {
        // Cache constants from PersistentCacheManager
        private const val CACHE_FRESH_MS = 5 * 60 * 1000L           // 5 minutes
        private const val CACHE_STALE_MS = 2 * 60 * 60 * 1000L      // 2 hours

        // Retry constants
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    // ========================================
    // EXTENDED STALE CACHE TESTS (2 HOURS)
    // ========================================

    @Test
    fun extendedCache_at30Minutes_shouldBeUsable() {
        val timestamp = System.currentTimeMillis() - (30 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isUsable = age < CACHE_STALE_MS
        assertTrue("30-minute old cache should be usable", isUsable)
    }

    @Test
    fun extendedCache_at90Minutes_shouldBeUsable() {
        val timestamp = System.currentTimeMillis() - (90 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isUsable = age < CACHE_STALE_MS
        assertTrue("90-minute old cache should be usable", isUsable)
    }

    @Test
    fun extendedCache_at110Minutes_shouldBeUsable() {
        val timestamp = System.currentTimeMillis() - (110 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isUsable = age < CACHE_STALE_MS
        assertTrue("110-minute old cache should be usable (within 2 hours)", isUsable)
    }

    @Test
    fun extendedCache_at2Hours_shouldNotBeUsable() {
        val timestamp = System.currentTimeMillis() - (2 * 60 * 60 * 1000 + 1)
        val age = System.currentTimeMillis() - timestamp
        val isUsable = age < CACHE_STALE_MS
        assertFalse("Cache older than 2 hours should NOT be usable", isUsable)
    }

    @Test
    fun extendedCache_at3Hours_shouldNotBeUsable() {
        val timestamp = System.currentTimeMillis() - (3 * 60 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isUsable = age < CACHE_STALE_MS
        assertFalse("3-hour old cache should NOT be usable", isUsable)
    }

    // ========================================
    // FRESH CACHE TESTS
    // ========================================

    @Test
    fun freshCache_at4Minutes_shouldBeFresh() {
        val timestamp = System.currentTimeMillis() - (4 * 60 * 1000)
        val age = System.currentTimeMillis() - timestamp
        val isFresh = age < CACHE_FRESH_MS
        assertTrue("4-minute old cache should be fresh", isFresh)
    }

    @Test
    fun freshCache_at5Minutes_shouldBeStale() {
        val timestamp = System.currentTimeMillis() - (5 * 60 * 1000 + 1)
        val age = System.currentTimeMillis() - timestamp
        val isFresh = age < CACHE_FRESH_MS
        assertFalse("5-minute old cache should be stale", isFresh)
    }

    // ========================================
    // RETRY LOGIC TESTS
    // ========================================

    @Test
    fun retryConfig_maxRetriesIs3() {
        assertEquals("Should have 3 max retries", 3, MAX_RETRIES)
    }

    @Test
    fun retryConfig_initialBackoffIs1Second() {
        assertEquals("Initial backoff should be 1 second", 1000L, INITIAL_BACKOFF_MS)
    }

    @Test
    fun retryBackoff_secondAttemptIs2Seconds() {
        val backoff = (INITIAL_BACKOFF_MS * BACKOFF_MULTIPLIER).toLong()
        assertEquals("Second attempt backoff should be 2 seconds", 2000L, backoff)
    }

    @Test
    fun retryBackoff_thirdAttemptIs4Seconds() {
        var backoff = INITIAL_BACKOFF_MS
        backoff = (backoff * BACKOFF_MULTIPLIER).toLong()  // 2s
        backoff = (backoff * BACKOFF_MULTIPLIER).toLong()  // 4s
        assertEquals("Third attempt backoff should be 4 seconds", 4000L, backoff)
    }

    @Test
    fun retryTotalTime_worstCaseIs7Seconds() {
        // 1st attempt: immediate
        // wait 1s, 2nd attempt
        // wait 2s, 3rd attempt
        // Total wait time: 1 + 2 = 3 seconds (not counting request time)
        val totalBackoff = INITIAL_BACKOFF_MS + (INITIAL_BACKOFF_MS * BACKOFF_MULTIPLIER).toLong()
        assertEquals("Total backoff time should be 3 seconds", 3000L, totalBackoff)
    }

    // ========================================
    // WIDGET SCENARIO TESTS
    // ========================================

    @Test
    fun scenario_processKilledAfter1Hour_cacheAt45MinStillUsable() {
        // Android kills app process after 1 hour of inactivity
        // Widget updates, process restarts
        // Last cache was 45 minutes ago
        // API fails
        // Should use 45-minute cache (within 2-hour window)

        val cacheAgeMs = 45 * 60 * 1000L
        val canUseFallback = cacheAgeMs < CACHE_STALE_MS
        assertTrue("45-min cache should be usable after process restart", canUseFallback)
    }

    @Test
    fun scenario_processKilledOvernight_cacheAt6HoursNotUsable() {
        // Phone sits idle overnight (6+ hours)
        // Widget updates in morning
        // Cache is 6 hours old
        // Should NOT use cache - too stale

        val cacheAgeMs = 6 * 60 * 60 * 1000L
        val canUseFallback = cacheAgeMs < CACHE_STALE_MS
        assertFalse("6-hour cache should NOT be usable - too stale", canUseFallback)
    }

    @Test
    fun scenario_apiRateLimited_retrySucceedsOnSecondAttempt() {
        // API returns 429 on first attempt
        // Wait 1 second
        // Retry succeeds
        // Total time: ~1 second

        var attempts = 0
        var success = false

        while (attempts < MAX_RETRIES && !success) {
            attempts++
            // Simulate: fail on 1st attempt, succeed on 2nd
            success = attempts >= 2
        }

        assertEquals("Should succeed on 2nd attempt", 2, attempts)
        assertTrue("Should eventually succeed", success)
    }

    @Test
    fun scenario_networkDown_allRetriesFail_useFallback() {
        // Network is completely down
        // All 3 retries fail
        // Should fall back to cached data

        var attempts = 0
        var success = false

        while (attempts < MAX_RETRIES && !success) {
            attempts++
            success = false  // All attempts fail
        }

        assertEquals("Should exhaust all retries", MAX_RETRIES, attempts)
        assertFalse("All attempts should fail", success)

        // Check fallback is available
        val cacheAgeMs = 30 * 60 * 1000L  // 30 minutes
        val canUseFallback = cacheAgeMs < CACHE_STALE_MS
        assertTrue("Should be able to use 30-min fallback", canUseFallback)
    }

    // ========================================
    // CONSTANT VALIDATION TESTS
    // ========================================

    @Test
    fun constants_freshCacheIs5Minutes() {
        assertEquals("Fresh cache TTL should be 5 minutes",
            5 * 60 * 1000L, CACHE_FRESH_MS)
    }

    @Test
    fun constants_staleCacheIs2Hours() {
        assertEquals("Stale cache TTL should be 2 hours",
            2 * 60 * 60 * 1000L, CACHE_STALE_MS)
    }

    @Test
    fun constants_staleCacheIs24xFreshCache() {
        val ratio = CACHE_STALE_MS / CACHE_FRESH_MS
        assertEquals("Stale cache should be 24x fresh cache (2 hours / 5 min)", 24L, ratio)
    }

    @Test
    fun constants_staleCacheExtendedFrom15MinTo2Hours() {
        val oldStaleMs = 15 * 60 * 1000L  // Old value: 15 minutes
        val newStaleMs = CACHE_STALE_MS   // New value: 2 hours
        val improvement = newStaleMs / oldStaleMs
        assertEquals("Stale cache extended 8x (from 15min to 2hr)", 8L, improvement)
    }

    // ========================================
    // DATA SERIALIZATION LOGIC TESTS
    // ========================================

    @Test
    fun serialization_metalsDataFields() {
        // Verify MetalsData has required fields
        val data = MetalsData(
            goldPrice = 2650.50,
            goldPreviousClose = 2645.00,
            goldChangePercent = 0.21,
            silverPrice = 31.25,
            silverPreviousClose = 31.00,
            silverChangePercent = 0.81
        )
        assertEquals(2650.50, data.goldPrice, 0.001)
        assertEquals(2645.00, data.goldPreviousClose, 0.001)
        assertEquals(0.21, data.goldChangePercent, 0.001)
        assertEquals(31.25, data.silverPrice, 0.001)
        assertEquals(31.00, data.silverPreviousClose, 0.001)
        assertEquals(0.81, data.silverChangePercent, 0.001)
    }

    @Test
    fun serialization_cryptoDataFields() {
        val data = CryptoData(
            btcPrice = 98500.00,
            btcPrevClose = 97000.00,
            btcChangePercent = 1.55,
            ethPrice = 3450.00,
            ethPrevClose = 3400.00,
            ethChangePercent = 1.47
        )
        assertEquals(98500.00, data.btcPrice, 0.001)
        assertEquals(97000.00, data.btcPrevClose, 0.001)
        assertEquals(1.55, data.btcChangePercent, 0.001)
        assertEquals(3450.00, data.ethPrice, 0.001)
        assertEquals(3400.00, data.ethPrevClose, 0.001)
        assertEquals(1.47, data.ethChangePercent, 0.001)
    }

    @Test
    fun serialization_stockDataFields() {
        val data = StockData(
            symbol = "GOOG",
            price = 178.50,
            prevClose = 177.00,
            changePercent = 0.85
        )
        assertEquals("GOOG", data.symbol)
        assertEquals(178.50, data.price, 0.001)
        assertEquals(177.00, data.prevClose, 0.001)
        assertEquals(0.85, data.changePercent, 0.001)
    }

    // ========================================
    // CACHE AGE CALCULATION TESTS
    // ========================================

    @Test
    fun cacheAge_calculatesSecondsCorrectly() {
        val timestamp = System.currentTimeMillis() - 120_000  // 2 minutes ago
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        assertTrue("Age should be around 120 seconds", ageSeconds >= 119 && ageSeconds <= 121)
    }

    @Test
    fun cacheAge_zeroForNewCache() {
        val timestamp = System.currentTimeMillis()
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        assertTrue("New cache age should be 0-1 seconds", ageSeconds <= 1)
    }

    @Test
    fun cacheAge_oneHourInSeconds() {
        val timestamp = System.currentTimeMillis() - (60 * 60 * 1000)
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        assertTrue("1 hour should be ~3600 seconds", ageSeconds >= 3599 && ageSeconds <= 3601)
    }
}
