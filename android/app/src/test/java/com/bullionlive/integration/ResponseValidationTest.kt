package com.bullionlive.integration

import com.bullionlive.data.GoldPriceApi
import com.bullionlive.data.FinnhubApi
import org.junit.Test
import org.junit.Assert.*
import java.text.NumberFormat
import java.util.Locale

/**
 * ResponseValidationTest - Validates API responses match expected format
 *
 * MAKES REAL NETWORK CALLS - verifies:
 * 1. All required fields are present
 * 2. Data types are correct
 * 3. Values are within expected ranges
 * 4. Data can be formatted for widget display
 *
 * These tests ensure API responses won't cause widget crashes.
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.integration.ResponseValidationTest"
 */
class ResponseValidationTest {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    // ========================================
    // METALS RESPONSE VALIDATION
    // ========================================

    @Test
    fun metals_allFieldsPresent() {
        val result = GoldPriceApi().fetchMetals()
        assertTrue("API call should succeed", result.isSuccess)

        val data = result.getOrNull()!!

        // All fields should be non-zero (indicating they were parsed)
        assertTrue("goldPrice should be set", data.goldPrice != 0.0)
        assertTrue("goldPreviousClose should be set", data.goldPreviousClose != 0.0)
        assertTrue("silverPrice should be set", data.silverPrice != 0.0)
        assertTrue("silverPreviousClose should be set", data.silverPreviousClose != 0.0)
        // Change percent can be zero, so just check it's a valid number
        assertFalse("goldChangePercent should not be NaN", data.goldChangePercent.isNaN())
        assertFalse("silverChangePercent should not be NaN", data.silverChangePercent.isNaN())
    }

    @Test
    fun metals_pricesArePositive() {
        val data = GoldPriceApi().fetchMetals().getOrNull()!!

        assertTrue("Gold price must be positive: ${data.goldPrice}", data.goldPrice > 0)
        assertTrue("Gold prev close must be positive: ${data.goldPreviousClose}", data.goldPreviousClose > 0)
        assertTrue("Silver price must be positive: ${data.silverPrice}", data.silverPrice > 0)
        assertTrue("Silver prev close must be positive: ${data.silverPreviousClose}", data.silverPreviousClose > 0)
    }

    @Test
    fun metals_pricesInRealisticRange() {
        val data = GoldPriceApi().fetchMetals().getOrNull()!!

        // Gold historically between $300-$10,000 per oz
        assertTrue("Gold price too low: ${data.goldPrice}", data.goldPrice > 300)
        assertTrue("Gold price too high: ${data.goldPrice}", data.goldPrice < 20000)

        // Silver historically between $5-$100 per oz
        assertTrue("Silver price too low: ${data.silverPrice}", data.silverPrice > 5)
        assertTrue("Silver price too high: ${data.silverPrice}", data.silverPrice < 200)
    }

    @Test
    fun metals_canBeFormattedForWidget() {
        val data = GoldPriceApi().fetchMetals().getOrNull()!!

        // Widget formats gold with 0 decimals
        currencyFormat.maximumFractionDigits = 0
        val goldFormatted = currencyFormat.format(data.goldPrice)
        assertTrue("Gold should format as currency", goldFormatted.startsWith("$"))
        assertFalse("Gold format should not contain error", goldFormatted.contains("Error"))

        // Widget formats silver with 2 decimals
        currencyFormat.maximumFractionDigits = 2
        val silverFormatted = currencyFormat.format(data.silverPrice)
        assertTrue("Silver should format as currency", silverFormatted.startsWith("$"))
    }

    @Test
    fun metals_changePercentCanBeFormatted() {
        val data = GoldPriceApi().fetchMetals().getOrNull()!!

        // Widget formats change as "+X.XX%" or "-X.XX%"
        val goldSign = if (data.goldChangePercent >= 0) "+" else ""
        val goldChange = String.format("%s%.2f%%", goldSign, data.goldChangePercent)

        assertTrue("Gold change should end with %", goldChange.endsWith("%"))
        assertTrue("Gold change should have sign", goldChange.startsWith("+") || goldChange.startsWith("-"))
    }

    // ========================================
    // CRYPTO RESPONSE VALIDATION
    // ========================================

    @Test
    fun crypto_allFieldsPresent() {
        val result = FinnhubApi().fetchCrypto()
        assertTrue("API call should succeed", result.isSuccess)

        val data = result.getOrNull()!!

        assertTrue("btcPrice should be set", data.btcPrice != 0.0)
        assertTrue("btcPrevClose should be set", data.btcPrevClose != 0.0)
        assertTrue("ethPrice should be set", data.ethPrice != 0.0)
        assertTrue("ethPrevClose should be set", data.ethPrevClose != 0.0)
        assertFalse("btcChangePercent should not be NaN", data.btcChangePercent.isNaN())
        assertFalse("ethChangePercent should not be NaN", data.ethChangePercent.isNaN())
    }

    @Test
    fun crypto_pricesArePositive() {
        val data = FinnhubApi().fetchCrypto().getOrNull()!!

        assertTrue("BTC price must be positive: ${data.btcPrice}", data.btcPrice > 0)
        assertTrue("BTC prev close must be positive: ${data.btcPrevClose}", data.btcPrevClose > 0)
        assertTrue("ETH price must be positive: ${data.ethPrice}", data.ethPrice > 0)
        assertTrue("ETH prev close must be positive: ${data.ethPrevClose}", data.ethPrevClose > 0)
    }

    @Test
    fun crypto_pricesInRealisticRange() {
        val data = FinnhubApi().fetchCrypto().getOrNull()!!

        // BTC historically between $1,000-$500,000
        assertTrue("BTC price too low: ${data.btcPrice}", data.btcPrice > 1000)
        assertTrue("BTC price too high: ${data.btcPrice}", data.btcPrice < 500000)

        // ETH historically between $50-$20,000
        assertTrue("ETH price too low: ${data.ethPrice}", data.ethPrice > 50)
        assertTrue("ETH price too high: ${data.ethPrice}", data.ethPrice < 50000)
    }

    @Test
    fun crypto_canBeFormattedForWidget() {
        val data = FinnhubApi().fetchCrypto().getOrNull()!!

        // Widget formats crypto with 0 decimals
        currencyFormat.maximumFractionDigits = 0
        val btcFormatted = currencyFormat.format(data.btcPrice)
        val ethFormatted = currencyFormat.format(data.ethPrice)

        assertTrue("BTC should format as currency", btcFormatted.startsWith("$"))
        assertTrue("ETH should format as currency", ethFormatted.startsWith("$"))
    }

    // ========================================
    // STOCK RESPONSE VALIDATION
    // ========================================

    @Test
    fun stock_allFieldsPresent() {
        val result = FinnhubApi().fetchStock("GOOG")
        // Stock API may fail outside market hours - skip test if unavailable
        if (result.isFailure) {
            println("SKIPPED: Stock API unavailable (market may be closed)")
            return
        }

        val data = result.getOrNull()!!

        assertEquals("Symbol should match", "GOOG", data.symbol)
        assertTrue("price should be set", data.price != 0.0)
        assertTrue("prevClose should be set", data.prevClose != 0.0)
        assertFalse("changePercent should not be NaN", data.changePercent.isNaN())
    }

    @Test
    fun stock_priceIsPositive() {
        val result = FinnhubApi().fetchStock("GOOG")
        // Stock API may fail outside market hours - skip test if unavailable
        if (result.isFailure) {
            println("SKIPPED: Stock API unavailable (market may be closed)")
            return
        }
        val data = result.getOrNull()!!

        assertTrue("Stock price must be positive: ${data.price}", data.price > 0)
        assertTrue("Stock prev close must be positive: ${data.prevClose}", data.prevClose > 0)
    }

    @Test
    fun stock_priceInRealisticRange() {
        val result = FinnhubApi().fetchStock("GOOG")
        // Stock API may fail outside market hours - skip test if unavailable
        if (result.isFailure) {
            println("SKIPPED: Stock API unavailable (market may be closed)")
            return
        }
        val data = result.getOrNull()!!

        // GOOG stock historically between $50-$5,000
        assertTrue("GOOG price too low: ${data.price}", data.price > 50)
        assertTrue("GOOG price too high: ${data.price}", data.price < 5000)
    }

    @Test
    fun stock_canBeFormattedForWidget() {
        val result = FinnhubApi().fetchStock("GOOG")
        // Stock API may fail outside market hours - skip test if unavailable
        if (result.isFailure) {
            println("SKIPPED: Stock API unavailable (market may be closed)")
            return
        }
        val data = result.getOrNull()!!

        // Widget formats stock with 2 decimals
        currencyFormat.maximumFractionDigits = 2
        val formatted = currencyFormat.format(data.price)

        assertTrue("Stock should format as currency", formatted.startsWith("$"))
        assertFalse("Stock format should not contain error", formatted.contains("Error"))
    }

    // ========================================
    // DATA CONSISTENCY TESTS
    // ========================================

    @Test
    fun metals_priceAndCloseAreConsistent() {
        val data = GoldPriceApi().fetchMetals().getOrNull()!!

        // Price and previous close should be within 20% of each other
        val goldRatio = data.goldPrice / data.goldPreviousClose
        assertTrue("Gold price/close ratio suspicious: $goldRatio", goldRatio > 0.8 && goldRatio < 1.2)

        val silverRatio = data.silverPrice / data.silverPreviousClose
        assertTrue("Silver price/close ratio suspicious: $silverRatio", silverRatio > 0.8 && silverRatio < 1.2)
    }

    @Test
    fun crypto_priceAndCloseAreConsistent() {
        val data = FinnhubApi().fetchCrypto().getOrNull()!!

        // Crypto is more volatile, allow 50% variance
        val btcRatio = data.btcPrice / data.btcPrevClose
        assertTrue("BTC price/close ratio suspicious: $btcRatio", btcRatio > 0.5 && btcRatio < 1.5)

        val ethRatio = data.ethPrice / data.ethPrevClose
        assertTrue("ETH price/close ratio suspicious: $ethRatio", ethRatio > 0.5 && ethRatio < 1.5)
    }

    @Test
    fun stock_priceAndCloseAreConsistent() {
        val result = FinnhubApi().fetchStock("GOOG")
        // Stock API may fail outside market hours - skip test if unavailable
        if (result.isFailure) {
            println("SKIPPED: Stock API unavailable (market may be closed)")
            return
        }
        val data = result.getOrNull()!!

        // Stock should be within 30% of previous close
        val ratio = data.price / data.prevClose
        assertTrue("GOOG price/close ratio suspicious: $ratio", ratio > 0.7 && ratio < 1.3)
    }
}
