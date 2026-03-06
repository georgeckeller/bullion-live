package com.bullionlive.data

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

/**
 * ApiTest - API response parsing validation for all data sources
 *
 * TEST COVERAGE:
 * - Metals Tab: Swissquote response parsing (4 tests)
 * - Crypto Tab: Finnhub BTC/ETH response parsing (5 tests)
 * - Stocks Tab: Finnhub stock quote parsing (3 tests)
 * - Widget: Combined data validation and formatting (2 tests)
 * - Error Handling: Invalid JSON, missing fields (2 tests)
 *
 * RUN: ./gradlew testDebugUnitTest
 *
 * TEST DATA: Uses realistic JSON responses matching actual API formats.
 * Prices are representative values, not live data.
 *
 * VALIDATION RULES:
 * - All prices must be > 0
 * - Change percent must be reasonable (< 50% for stocks)
 * - Currency formatting: Gold/BTC/ETH no decimals, Silver 2 decimals
 *
 * NOTE: These are unit tests for parsing logic only.
 * Live API connectivity is tested by validate-apis.sh.
 */
class ApiTest {

    @Test
    fun metals_swissquote_parsesGoldPrice() {
        val json = """{"ts":1766261892257,"items":[{"curr":"USD","xauPrice":4340.105,"xagPrice":67.143,"xauClose":4332.145,"xagClose":65.2865}]}"""
        val root = JSONObject(json)
        val item = root.getJSONArray("items").getJSONObject(0)

        val goldPrice = item.getDouble("xauPrice")

        assertTrue("Gold price must be > 0", goldPrice > 0)
        assertEquals(4340.105, goldPrice, 0.01)
    }

    @Test
    fun metals_swissquote_parsesSilverPrice() {
        val json = """{"ts":1766261892257,"items":[{"curr":"USD","xauPrice":4340.105,"xagPrice":67.143,"xauClose":4332.145,"xagClose":65.2865}]}"""
        val root = JSONObject(json)
        val item = root.getJSONArray("items").getJSONObject(0)

        val silverPrice = item.getDouble("xagPrice")

        assertTrue("Silver price must be > 0", silverPrice > 0)
        assertEquals(67.143, silverPrice, 0.01)
    }

    @Test
    fun metals_swissquote_parsesPreviousClose() {
        val json = """{"ts":1766261892257,"items":[{"curr":"USD","xauPrice":4340.105,"xagPrice":67.143,"xauClose":4332.145,"xagClose":65.2865}]}"""
        val root = JSONObject(json)
        val item = root.getJSONArray("items").getJSONObject(0)

        val goldClose = item.optDouble("xauClose", 0.0)
        val silverClose = item.optDouble("xagClose", 0.0)

        assertTrue("Gold close must be > 0", goldClose > 0)
        assertTrue("Silver close must be > 0", silverClose > 0)
    }

    @Test
    fun metals_swissquote_handlesEmptyItems() {
        val json = """{"ts":1766261892257,"items":[]}"""
        val root = JSONObject(json)
        val items = root.getJSONArray("items")

        assertEquals("Empty items array should have length 0", 0, items.length())
    }

    @Test
    fun crypto_finnhub_parsesBtcPrice() {
        val json = """{"c":88259.69,"d":338.87,"dp":0.3854,"pc":87920.82,"t":1766261913}"""
        val data = JSONObject(json)

        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        assertTrue("BTC price must be > 0", current > 0)
        assertTrue("BTC prev close must be > 0", prevClose > 0)
        assertEquals(88259.69, current, 0.01)
    }

    @Test
    fun crypto_finnhub_parsesEthPrice() {
        val json = """{"c":2979.99,"d":-8.38,"dp":-0.2804,"pc":2988.37,"t":1766261913}"""
        val data = JSONObject(json)

        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        assertTrue("ETH price must be > 0", current > 0)
        assertEquals(2979.99, current, 0.01)
    }

    @Test
    fun crypto_finnhub_parsesChangePercent() {
        val json = """{"c":88259.69,"d":338.87,"dp":0.3854,"pc":87920.82}"""
        val data = JSONObject(json)

        val changePercent = data.optDouble("dp", 0.0)

        assertEquals(0.3854, changePercent, 0.0001)
    }

    @Test
    fun crypto_finnhub_handlesNegativeChange() {
        val json = """{"c":2979.99,"d":-8.38,"dp":-0.2804,"pc":2988.37}"""
        val data = JSONObject(json)

        val change = data.optDouble("d", 0.0)
        val changePercent = data.optDouble("dp", 0.0)

        assertTrue("Change should be negative", change < 0)
        assertTrue("Change percent should be negative", changePercent < 0)
    }

    @Test
    fun crypto_finnhub_handlesZeroValues() {
        val json = """{"c":0,"d":0,"dp":0,"pc":0}"""
        val data = JSONObject(json)

        val current = data.optDouble("c", 0.0)

        assertEquals("Zero price should be 0", 0.0, current, 0.0)
    }

    @Test
    fun stocks_finnhub_parsesStockPrice() {
        val json = """{"c":248.96,"d":3.25,"dp":1.32,"h":250.80,"l":246.42,"o":247.50,"pc":245.71,"t":1766261913}"""
        val data = JSONObject(json)

        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)
        val changePercent = data.optDouble("dp", 0.0)

        assertTrue("Stock price must be > 0", current > 0)
        assertTrue("Stock prev close must be > 0", prevClose > 0)
        assertTrue("Change percent should be reasonable", Math.abs(changePercent) < 50)
    }

    @Test
    fun stocks_finnhub_validatesExtremeChanges() {
        val json = """{"c":100,"d":50,"dp":100,"pc":50}"""
        val data = JSONObject(json)

        val changePercent = data.optDouble("dp", 0.0)

        assertTrue("100% change should fail validation", Math.abs(changePercent) > 50)
    }

    @Test
    fun stocks_finnhub_handlesMarketClosed() {
        val json = """{"c":248.96,"d":0,"dp":0,"pc":248.96}"""
        val data = JSONObject(json)

        val current = data.optDouble("c", 0.0)
        val prevClose = data.optDouble("pc", 0.0)

        assertEquals("Price should equal prev close when market closed", current, prevClose, 0.01)
    }

    @Test
    fun widget_allDataSourcesReturnValidPrices() {
        val metalsJson = """{"items":[{"xauPrice":4340.105,"xagPrice":67.143,"xauClose":4332.145,"xagClose":65.2865}]}"""
        val btcJson = """{"c":88259.69,"pc":87920.82}"""
        val ethJson = """{"c":2979.99,"pc":2988.37}"""

        val metals = JSONObject(metalsJson).getJSONArray("items").getJSONObject(0)
        val goldPrice = metals.getDouble("xauPrice")
        val silverPrice = metals.getDouble("xagPrice")

        val btc = JSONObject(btcJson)
        val eth = JSONObject(ethJson)
        val btcPrice = btc.optDouble("c", 0.0)
        val ethPrice = eth.optDouble("c", 0.0)

        assertTrue("Gold > 0", goldPrice > 0)
        assertTrue("Silver > 0", silverPrice > 0)
        assertTrue("BTC > 0", btcPrice > 0)
        assertTrue("ETH > 0", ethPrice > 0)
    }

    @Test
    fun widget_pricesFormattedCorrectly() {
        val goldPrice = 4340.105
        val silverPrice = 67.143
        val btcPrice = 88259.69
        val ethPrice = 2979.99

        val goldFormatted = String.format("$%.0f", goldPrice)
        assertEquals("$4340", goldFormatted)

        val silverFormatted = String.format("$%.2f", silverPrice)
        assertEquals("$67.14", silverFormatted)

        val btcFormatted = String.format("$%.0f", btcPrice)
        assertEquals("$88260", btcFormatted)

        val ethFormatted = String.format("$%.0f", ethPrice)
        assertEquals("$2980", ethFormatted)
    }

    @Test
    fun error_handlesInvalidJson() {
        try {
            JSONObject("not valid json")
            fail("Should throw exception for invalid JSON")
        } catch (e: Exception) {
        }
    }

    @Test
    fun error_handlesMissingFields() {
        val json = """{"c":100}"""
        val data = JSONObject(json)

        val prevClose = data.optDouble("pc", 0.0)

        assertEquals("Missing field should return default", 0.0, prevClose, 0.0)
    }
}
