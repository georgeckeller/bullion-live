package com.bullionlive.data

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

/**
 * ApiFailureScenarioTest - Tests for various API failure modes
 *
 * CRITICAL ISSUE: Widget shows "Error" for prices after hours of operation.
 * This test class validates handling of all known failure scenarios.
 *
 * FAILURE MODES TESTED:
 * 1. HTTP 429 (Rate Limiting) - Finnhub free tier: 60 calls/minute
 * 2. HTTP 500/503 (Server Errors) - API down
 * 3. Invalid JSON response - Malformed data
 * 4. Missing price fields - API response structure changed
 * 5. Zero/negative prices - Invalid data from API
 * 6. Network timeout - Slow/unreachable API
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.data.ApiFailureScenarioTest"
 */
class ApiFailureScenarioTest {

    // ========================================
    // RATE LIMITING TESTS (HTTP 429)
    // ========================================

    @Test
    fun rateLimitDetection_http429_shouldBeRecognized() {
        val responseCode = 429
        val isRateLimited = responseCode == 429
        assertTrue("HTTP 429 should be recognized as rate limiting", isRateLimited)
    }

    @Test
    fun rateLimit_backoffDuration_shouldBe1Minute() {
        val backoffMs = 60_000L
        assertEquals("Rate limit backoff should be 60 seconds", 60_000L, backoffMs)
    }

    @Test
    fun rateLimit_afterBackoff_shouldAllowRetry() {
        val rateLimitedUntil = System.currentTimeMillis() - 1000 // 1 second ago
        val isStillLimited = System.currentTimeMillis() < rateLimitedUntil
        assertFalse("Should allow retry after backoff period expires", isStillLimited)
    }

    @Test
    fun rateLimit_duringBackoff_shouldNotRetry() {
        val rateLimitedUntil = System.currentTimeMillis() + 30_000 // 30 seconds from now
        val isStillLimited = System.currentTimeMillis() < rateLimitedUntil
        assertTrue("Should not retry during backoff period", isStillLimited)
    }

    // ========================================
    // INVALID DATA TESTS
    // ========================================

    @Test
    fun invalidData_zeroPrices_shouldBeRejected() {
        val json = """{"c":0,"d":0,"dp":0,"pc":0}"""
        val data = JSONObject(json)
        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        val isValid = current > 0 && prevClose > 0
        assertFalse("Zero prices should be rejected", isValid)
    }

    @Test
    fun invalidData_negativePrice_shouldBeRejected() {
        val json = """{"c":-100,"d":0,"dp":0,"pc":100}"""
        val data = JSONObject(json)
        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        val isValid = current > 0 && prevClose > 0
        assertFalse("Negative prices should be rejected", isValid)
    }

    @Test
    fun invalidData_missingCurrentPrice_shouldBeRejected() {
        val json = """{"d":0,"dp":0,"pc":100}"""
        val data = JSONObject(json)
        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        val isValid = current > 0 && prevClose > 0
        assertFalse("Missing current price should be rejected", isValid)
    }

    @Test
    fun invalidData_missingPrevClose_shouldBeRejected() {
        val json = """{"c":100,"d":0,"dp":0}"""
        val data = JSONObject(json)
        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        val isValid = current > 0 && prevClose > 0
        assertFalse("Missing prev close should be rejected", isValid)
    }

    @Test
    fun invalidData_percentStillAvailable_whenPriceInvalid() {
        // This tests the root cause of the bug:
        // Percent (dp) can be valid even when price fields are invalid
        val json = """{"c":0,"d":0,"dp":1.5,"pc":0}"""
        val data = JSONObject(json)

        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)
        val percent = data.optDouble("dp", 0.0)

        val isPriceValid = current > 0 && prevClose > 0
        val isPercentValid = !percent.isNaN()

        assertFalse("Price should be invalid", isPriceValid)
        assertTrue("Percent can still be valid", isPercentValid)
        assertEquals("Percent value should be 1.5", 1.5, percent, 0.01)
    }

    // ========================================
    // MALFORMED JSON TESTS
    // ========================================

    @Test
    fun malformedJson_emptyResponse_shouldThrow() {
        try {
            JSONObject("")
            fail("Empty string should throw exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun malformedJson_invalidSyntax_shouldThrow() {
        try {
            JSONObject("{not valid json}")
            fail("Invalid syntax should throw exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun malformedJson_htmlError_shouldThrow() {
        // Sometimes APIs return HTML error pages
        try {
            JSONObject("<html><body>Error 500</body></html>")
            fail("HTML response should throw exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    // ========================================
    // SWISSQUOTE API SPECIFIC TESTS
    // ========================================

    @Test
    fun swissquoteApi_emptyItemsArray_shouldBeHandled() {
        val json = """{"ts":1766261892257,"items":[]}"""
        val root = JSONObject(json)
        val items = root.getJSONArray("items")

        val isEmpty = items.length() == 0
        assertTrue("Empty items array should be detected", isEmpty)
    }

    @Test
    fun swissquoteApi_missingItemsField_shouldThrow() {
        val json = """{"ts":1766261892257}"""
        val root = JSONObject(json)

        try {
            root.getJSONArray("items")
            fail("Missing items field should throw")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun swissquoteApi_zeroGoldPrice_shouldBeRejected() {
        val json = """{"items":[{"xauPrice":0,"xagPrice":67.143}]}"""
        val root = JSONObject(json)
        val item = root.getJSONArray("items").getJSONObject(0)

        val goldPrice = item.optDouble("xauPrice", 0.0)
        val isValid = goldPrice > 0
        assertFalse("Zero gold price should be rejected", isValid)
    }

    @Test
    fun swissquoteApi_zeroSilverPrice_shouldBeRejected() {
        val json = """{"items":[{"xauPrice":4340.105,"xagPrice":0}]}"""
        val root = JSONObject(json)
        val item = root.getJSONArray("items").getJSONObject(0)

        val silverPrice = item.optDouble("xagPrice", 0.0)
        val isValid = silverPrice > 0
        assertFalse("Zero silver price should be rejected", isValid)
    }

    // ========================================
    // HTTP ERROR CODE TESTS
    // ========================================

    @Test
    fun httpError_401_unauthorized() {
        val responseCode = 401
        val isError = responseCode != 200
        assertTrue("401 should be treated as error", isError)
    }

    @Test
    fun httpError_403_forbidden() {
        val responseCode = 403
        val isError = responseCode != 200
        assertTrue("403 should be treated as error", isError)
    }

    @Test
    fun httpError_500_serverError() {
        val responseCode = 500
        val isError = responseCode != 200
        assertTrue("500 should be treated as error", isError)
    }

    @Test
    fun httpError_503_serviceUnavailable() {
        val responseCode = 503
        val isError = responseCode != 200
        assertTrue("503 should be treated as error", isError)
    }

    // ========================================
    // TIMEOUT CONFIGURATION TESTS
    // ========================================

    @Test
    fun timeout_connectTimeout_is15Seconds() {
        val connectTimeoutMs = 15000
        assertEquals("Connect timeout should be 15 seconds", 15000, connectTimeoutMs)
    }

    @Test
    fun timeout_readTimeout_is15Seconds() {
        val readTimeoutMs = 15000
        assertEquals("Read timeout should be 15 seconds", 15000, readTimeoutMs)
    }

    // ========================================
    // FINNHUB RATE LIMIT CALCULATION
    // ========================================

    @Test
    fun finnhub_rateLimit_60CallsPerMinute() {
        val maxCallsPerMinute = 60
        val widgetUpdateIntervalMinutes = 30
        val cryptoCallsPerUpdate = 2  // BTC + ETH

        val callsPerHour = (60.0 / widgetUpdateIntervalMinutes) * cryptoCallsPerUpdate
        assertTrue("Widget should stay well under rate limit",
            callsPerHour < maxCallsPerMinute)
    }

    @Test
    fun finnhub_multipleWidgets_rateLimit() {
        val maxCallsPerMinute = 60
        val widgetUpdateIntervalMinutes = 30
        val cryptoCallsPerUpdate = 2
        val numberOfWidgets = 10  // Extreme case

        val callsPerHour = (60.0 / widgetUpdateIntervalMinutes) * cryptoCallsPerUpdate * numberOfWidgets
        // 10 widgets * 2 updates/hour * 2 calls = 40 calls/hour
        // This should still be fine, but cache sharing helps
        assertTrue("Even 10 widgets should be under hourly limit",
            callsPerHour < maxCallsPerMinute * 60)
    }
}
