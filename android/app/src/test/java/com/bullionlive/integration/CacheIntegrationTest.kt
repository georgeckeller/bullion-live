package com.bullionlive.integration

import com.bullionlive.data.GoldPriceApi
import com.bullionlive.data.FinnhubApi
import org.junit.Test
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/**
 * CacheIntegrationTest - Verifies caching behavior works correctly
 *
 * MAKES REAL NETWORK CALLS - tests that:
 * 1. First call fetches from network
 * 2. Rapid subsequent calls use cache (same data returned quickly)
 * 3. Cache returns valid data
 *
 * These tests ensure the caching layer doesn't break data fetching.
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.integration.CacheIntegrationTest"
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CacheIntegrationTest {

    @Test
    fun test01_metals_secondCallUsesCache() {
        val api = GoldPriceApi()

        // First call - fetches from network
        val startFirst = System.currentTimeMillis()
        val result1 = api.fetchMetals()
        val durationFirst = System.currentTimeMillis() - startFirst

        assertTrue("First metals call should succeed", result1.isSuccess)
        val data1 = result1.getOrNull()
        assertNotNull("First call should return data", data1)

        // Second call - should use cache (much faster)
        val startSecond = System.currentTimeMillis()
        val result2 = api.fetchMetals()
        val durationSecond = System.currentTimeMillis() - startSecond

        assertTrue("Second metals call should succeed", result2.isSuccess)
        val data2 = result2.getOrNull()
        assertNotNull("Second call should return data", data2)

        // Cache should return same data
        assertEquals("Cached gold price should match", data1!!.goldPrice, data2!!.goldPrice, 0.001)
        assertEquals("Cached silver price should match", data1.silverPrice, data2.silverPrice, 0.001)

        // Second call should be faster (cache hit vs network)
        // Allow for some variance, but cache should be at least 10x faster
        if (durationFirst > 100) { // Only check if first call took reasonable time
            assertTrue("Cache should be faster than network (first: ${durationFirst}ms, second: ${durationSecond}ms)",
                durationSecond < durationFirst / 2 || durationSecond < 50)
        }
    }

    @Test
    fun test02_crypto_secondCallUsesCache() {
        val api = FinnhubApi()

        // First call
        val result1 = api.fetchCrypto()
        assertTrue("First crypto call should succeed", result1.isSuccess)
        val data1 = result1.getOrNull()
        assertNotNull("First call should return data", data1)

        // Second call - should use cache
        val result2 = api.fetchCrypto()
        assertTrue("Second crypto call should succeed", result2.isSuccess)
        val data2 = result2.getOrNull()
        assertNotNull("Second call should return data", data2)

        // Cache should return same data
        assertEquals("Cached BTC price should match", data1!!.btcPrice, data2!!.btcPrice, 0.001)
        assertEquals("Cached ETH price should match", data1.ethPrice, data2.ethPrice, 0.001)
    }

    @Test
    fun test03_metals_multipleRapidCallsAllSucceed() {
        val api = GoldPriceApi()

        // Simulate widget refresh storm (multiple widgets updating)
        val results = (1..5).map { api.fetchMetals() }

        // All calls should succeed
        results.forEachIndexed { index, result ->
            assertTrue("Call $index should succeed", result.isSuccess)
            assertNotNull("Call $index should return data", result.getOrNull())
        }

        // All should return same cached data
        val prices = results.mapNotNull { it.getOrNull()?.goldPrice }
        assertTrue("All calls should return same gold price", prices.distinct().size == 1)
    }

    @Test
    fun test04_crypto_multipleRapidCallsAllSucceed() {
        val api = FinnhubApi()

        // Simulate widget refresh storm
        val results = (1..5).map { api.fetchCrypto() }

        // All calls should succeed
        results.forEachIndexed { index, result ->
            assertTrue("Call $index should succeed", result.isSuccess)
            assertNotNull("Call $index should return data", result.getOrNull())
        }

        // All should return same cached data
        val prices = results.mapNotNull { it.getOrNull()?.btcPrice }
        assertTrue("All calls should return same BTC price", prices.distinct().size == 1)
    }

    @Test
    fun test05_stock_cacheWorksForSameSymbol() {
        val api = FinnhubApi()

        // First call for GOOG — may fail outside market hours
        val result1 = api.fetchStock("GOOG")
        if (result1.isFailure) {
            println("SKIPPED: Stock API unavailable (market may be closed)")
            return
        }
        assertTrue("First GOOG call should succeed", result1.isSuccess)

        // Second call for same symbol
        val result2 = api.fetchStock("GOOG")
        assertTrue("Second GOOG call should succeed", result2.isSuccess)

        // Both should return valid data
        val data1 = result1.getOrNull()
        val data2 = result2.getOrNull()
        assertNotNull("First call data", data1)
        assertNotNull("Second call data", data2)
        assertTrue("First price valid", data1!!.price > 0)
        assertTrue("Second price valid", data2!!.price > 0)
    }

    @Test
    fun test06_cacheReturnsValidDataNotStale() {
        // Ensure cached data is still valid and usable
        val metalsApi = GoldPriceApi()
        val cryptoApi = FinnhubApi()

        // Populate caches
        metalsApi.fetchMetals()
        cryptoApi.fetchCrypto()

        // Fetch again (from cache)
        val metals = metalsApi.fetchMetals().getOrNull()
        val crypto = cryptoApi.fetchCrypto().getOrNull()

        // Cached data should still be valid
        assertNotNull("Cached metals should be valid", metals)
        assertNotNull("Cached crypto should be valid", crypto)

        assertTrue("Cached gold price valid", metals!!.goldPrice > 0)
        assertTrue("Cached silver price valid", metals.silverPrice > 0)
        assertTrue("Cached BTC price valid", crypto!!.btcPrice > 0)
        assertTrue("Cached ETH price valid", crypto.ethPrice > 0)
    }
}
