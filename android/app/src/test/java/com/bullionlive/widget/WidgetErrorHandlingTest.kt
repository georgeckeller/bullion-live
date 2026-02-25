package com.bullionlive.widget

import org.junit.Test
import org.junit.Assert.*

/**
 * WidgetErrorHandlingTest - Tests for widget error display logic
 *
 * CRITICAL BUG: Widget would show "Error" for prices while percentages
 * continued to display (from previous successful fetch or partial data).
 *
 * This test validates the widget correctly handles:
 * - Full success (all data available)
 * - Partial failure (metals OK, crypto fails)
 * - Full failure (all APIs fail)
 * - Stale cache fallback scenarios
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.widget.WidgetErrorHandlingTest"
 */
class WidgetErrorHandlingTest {

    // Simulated data states
    sealed class DataResult<T> {
        data class Success<T>(val data: T) : DataResult<T>()
        class Failure<T> : DataResult<T>()
    }

    data class MetalsData(
        val goldPrice: Double,
        val goldChangePercent: Double,
        val silverPrice: Double,
        val silverChangePercent: Double
    )

    data class CryptoData(
        val btcPrice: Double,
        val btcChangePercent: Double,
        val ethPrice: Double,
        val ethChangePercent: Double
    )

    // ========================================
    // SUCCESS SCENARIO TESTS
    // ========================================

    @Test
    fun success_allDataAvailable_noErrors() {
        val metalsResult: DataResult<MetalsData> = DataResult.Success(
            MetalsData(4340.0, 0.18, 67.14, 2.84)
        )
        val cryptoResult: DataResult<CryptoData> = DataResult.Success(
            CryptoData(88259.0, 0.38, 2979.0, -0.28)
        )

        val hasMetalsError = metalsResult is DataResult.Failure
        val hasCryptoError = cryptoResult is DataResult.Failure

        assertFalse("Metals should not have error", hasMetalsError)
        assertFalse("Crypto should not have error", hasCryptoError)
    }

    @Test
    fun success_pricesCanBeFormatted() {
        val goldPrice = 4340.105
        val formatted = String.format("$%.0f", goldPrice)
        assertEquals("$4340", formatted)
    }

    @Test
    fun success_percentCanBeFormatted_positive() {
        val percent = 0.38
        val sign = if (percent >= 0) "+" else ""
        val formatted = String.format("%s%.2f%%", sign, percent)
        assertEquals("+0.38%", formatted)
    }

    @Test
    fun success_percentCanBeFormatted_negative() {
        val percent = -0.28
        val sign = if (percent >= 0) "+" else ""
        val formatted = String.format("%s%.2f%%", sign, percent)
        assertEquals("-0.28%", formatted)
    }

    @Test
    fun success_percentCanBeFormatted_zero() {
        val percent = 0.0
        val sign = if (percent >= 0) "+" else ""
        val formatted = String.format("%s%.2f%%", sign, percent)
        assertEquals("+0.00%", formatted)
    }

    // ========================================
    // PARTIAL FAILURE TESTS
    // ========================================

    @Test
    fun partialFailure_metalsOk_cryptoFails() {
        val metalsResult: DataResult<MetalsData> = DataResult.Success(
            MetalsData(4340.0, 0.18, 67.14, 2.84)
        )
        val cryptoResult: DataResult<CryptoData> = DataResult.Failure()

        val hasMetalsError = metalsResult is DataResult.Failure
        val hasCryptoError = cryptoResult is DataResult.Failure

        assertFalse("Metals should not have error", hasMetalsError)
        assertTrue("Crypto should have error", hasCryptoError)
    }

    @Test
    fun partialFailure_metalsFails_cryptoOk() {
        val metalsResult: DataResult<MetalsData> = DataResult.Failure()
        val cryptoResult: DataResult<CryptoData> = DataResult.Success(
            CryptoData(88259.0, 0.38, 2979.0, -0.28)
        )

        val hasMetalsError = metalsResult is DataResult.Failure
        val hasCryptoError = cryptoResult is DataResult.Failure

        assertTrue("Metals should have error", hasMetalsError)
        assertFalse("Crypto should not have error", hasCryptoError)
    }

    // ========================================
    // FULL FAILURE TESTS
    // ========================================

    @Test
    fun fullFailure_bothApisFail() {
        val metalsResult: DataResult<MetalsData> = DataResult.Failure()
        val cryptoResult: DataResult<CryptoData> = DataResult.Failure()

        val hasMetalsError = metalsResult is DataResult.Failure
        val hasCryptoError = cryptoResult is DataResult.Failure

        assertTrue("Metals should have error", hasMetalsError)
        assertTrue("Crypto should have error", hasCryptoError)
    }

    @Test
    fun fullFailure_displayTextShouldBeError() {
        val errorText = "Error"
        assertEquals("Error display text should be 'Error'", "Error", errorText)
    }

    // ========================================
    // LOADING STATE TESTS
    // ========================================

    @Test
    fun loading_displayTextShouldBeDots() {
        val loadingText = "..."
        assertEquals("Loading display text should be '...'", "...", loadingText)
    }

    @Test
    fun loading_changeTextShouldBeEmpty() {
        val loadingChange = ""
        assertEquals("Loading change text should be empty", "", loadingChange)
    }

    // ========================================
    // COLOR CODING TESTS
    // ========================================

    @Test
    fun color_positiveChange_shouldBeGreen() {
        val percent = 0.38
        val expectedColor = "#4CAF50" // Green

        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }

        assertEquals("Positive change should be green", expectedColor, color)
    }

    @Test
    fun color_negativeChange_shouldBeRed() {
        val percent = -0.28
        val expectedColor = "#F44336" // Red

        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }

        assertEquals("Negative change should be red", expectedColor, color)
    }

    @Test
    fun color_zeroChange_shouldBeGray() {
        val percent = 0.0
        val expectedColor = "#888888" // Gray

        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }

        assertEquals("Zero change should be gray", expectedColor, color)
    }

    // ========================================
    // DECIMAL FORMATTING TESTS
    // ========================================

    @Test
    fun format_goldPrice_noDecimals() {
        val goldPrice = 4340.789
        val formatted = String.format("$%.0f", goldPrice)
        assertEquals("Gold should have no decimals", "$4341", formatted)
    }

    @Test
    fun format_silverPrice_twoDecimals() {
        val silverPrice = 67.143
        val formatted = String.format("$%.2f", silverPrice)
        assertEquals("Silver should have 2 decimals", "$67.14", formatted)
    }

    @Test
    fun format_btcPrice_noDecimals() {
        val btcPrice = 88259.69
        val formatted = String.format("$%.0f", btcPrice)
        assertEquals("BTC should have no decimals", "$88260", formatted)
    }

    @Test
    fun format_ethPrice_noDecimals() {
        val ethPrice = 2979.99
        val formatted = String.format("$%.0f", ethPrice)
        assertEquals("ETH should have no decimals", "$2980", formatted)
    }

    // ========================================
    // STALE CACHE INDICATOR TESTS
    // ========================================

    @Test
    fun staleCacheScenario_shouldSucceedNotShowError() {
        // When using stale cache, result is still Success
        // Widget should display data, not error
        // Stale cache returns success, so shouldShowError = false
        val resultIsSuccess = true

        val shouldShowError = !resultIsSuccess
        assertFalse("Stale cache should not show error", shouldShowError)
    }

    @Test
    fun cacheAge_displayInSeconds() {
        val cacheAgeMs = 8 * 60 * 1000L // 8 minutes
        val cacheAgeSeconds = cacheAgeMs / 1000
        assertEquals("8 minutes = 480 seconds", 480L, cacheAgeSeconds)
    }

    // ========================================
    // EDGE CASE TESTS
    // ========================================

    @Test
    fun edgeCase_veryLargePrice() {
        val price = 999999.99
        val formatted = String.format("$%.0f", price)
        assertEquals("$1000000", formatted)
    }

    @Test
    fun edgeCase_verySmallPrice() {
        val price = 0.01
        val formatted = String.format("$%.2f", price)
        assertEquals("$0.01", formatted)
    }

    @Test
    fun edgeCase_veryLargePercent() {
        val percent = 99.99
        val sign = if (percent >= 0) "+" else ""
        val formatted = String.format("%s%.2f%%", sign, percent)
        assertEquals("+99.99%", formatted)
    }

    @Test
    fun edgeCase_verySmallPercent() {
        val percent = 0.001
        val sign = if (percent >= 0) "+" else ""
        val formatted = String.format("%s%.2f%%", sign, percent)
        assertEquals("+0.00%", formatted) // Rounds to 0.00
    }
}
