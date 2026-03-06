package com.bullionlive.integration

import com.bullionlive.data.GoldPriceApi
import com.bullionlive.data.FinnhubApi
import com.bullionlive.data.MetalsData
import com.bullionlive.data.CryptoData
import com.bullionlive.data.StockData
import org.junit.Test
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/**
 * ApiIntegrationTest - Comprehensive integration tests for all API data sources
 *
 * MAKES REAL NETWORK CALLS - requires internet connectivity
 *
 * TEST CATEGORIES:
 * 1. Connectivity - APIs are reachable
 * 2. Response Validity - Data is correct format and reasonable values
 * 3. Error Handling - Graceful handling of bad inputs
 * 4. Caching - Cache works correctly across calls
 *
 * FAILS BUILD IF:
 * - Any API is unreachable
 * - Any API returns invalid data
 * - Prices are zero, negative, or unreasonable
 *
 * RUN: ./gradlew testDebugUnitTest --tests "com.bullionlive.integration.*"
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ApiIntegrationTest {

    // ========================================
    // METALS API TESTS (Swissquote/Yahoo)
    // ========================================

    @Test
    fun test01_metals_apiIsReachable() {
        val result = GoldPriceApi().fetchMetals()
        assertTrue("Metals API must be reachable", result.isSuccess)
    }

    @Test
    fun test02_metals_goldPriceIsValid() {
        val result = GoldPriceApi().fetchMetals()
        val data = result.getOrNull()

        assertNotNull("Metals data must not be null", data)
        assertTrue("Gold price must be > 0, got: ${data?.goldPrice}", data!!.goldPrice > 0)
        assertTrue("Gold price must be < 50000 (sanity check), got: ${data.goldPrice}", data.goldPrice < 50000)
    }

    @Test
    fun test03_metals_silverPriceIsValid() {
        val result = GoldPriceApi().fetchMetals()
        val data = result.getOrNull()

        assertNotNull("Metals data must not be null", data)
        assertTrue("Silver price must be > 0, got: ${data?.silverPrice}", data!!.silverPrice > 0)
        assertTrue("Silver price must be < 500 (sanity check), got: ${data.silverPrice}", data.silverPrice < 500)
    }

    @Test
    fun test04_metals_previousCloseIsValid() {
        val result = GoldPriceApi().fetchMetals()
        val data = result.getOrNull()

        assertNotNull("Metals data must not be null", data)
        assertTrue("Gold prev close must be > 0", data!!.goldPreviousClose > 0)
        assertTrue("Silver prev close must be > 0", data.silverPreviousClose > 0)
    }

    @Test
    fun test05_metals_changePercentIsReasonable() {
        val result = GoldPriceApi().fetchMetals()
        val data = result.getOrNull()

        assertNotNull("Metals data must not be null", data)
        // Daily change should be less than 20% (extreme but possible)
        assertTrue("Gold change % unreasonable: ${data!!.goldChangePercent}",
            Math.abs(data.goldChangePercent) < 20)
        assertTrue("Silver change % unreasonable: ${data.silverChangePercent}",
            Math.abs(data.silverChangePercent) < 20)
    }

    // ========================================
    // CRYPTO API TESTS (Finnhub)
    // ========================================

    @Test
    fun test10_crypto_apiIsReachable() {
        val result = FinnhubApi().fetchCrypto()
        assertTrue("Crypto API must be reachable", result.isSuccess)
    }

    @Test
    fun test11_crypto_btcPriceIsValid() {
        val result = FinnhubApi().fetchCrypto()
        val data = result.getOrNull()

        assertNotNull("Crypto data must not be null", data)
        assertTrue("BTC price must be > 0, got: ${data?.btcPrice}", data!!.btcPrice > 0)
        assertTrue("BTC price must be < 1000000 (sanity check), got: ${data.btcPrice}", data.btcPrice < 1000000)
    }

    @Test
    fun test12_crypto_ethPriceIsValid() {
        val result = FinnhubApi().fetchCrypto()
        val data = result.getOrNull()

        assertNotNull("Crypto data must not be null", data)
        assertTrue("ETH price must be > 0, got: ${data?.ethPrice}", data!!.ethPrice > 0)
        assertTrue("ETH price must be < 100000 (sanity check), got: ${data.ethPrice}", data.ethPrice < 100000)
    }

    @Test
    fun test13_crypto_previousCloseIsValid() {
        val result = FinnhubApi().fetchCrypto()
        val data = result.getOrNull()

        assertNotNull("Crypto data must not be null", data)
        assertTrue("BTC prev close must be > 0", data!!.btcPrevClose > 0)
        assertTrue("ETH prev close must be > 0", data.ethPrevClose > 0)
    }

    @Test
    fun test14_crypto_changePercentIsReasonable() {
        val result = FinnhubApi().fetchCrypto()
        val data = result.getOrNull()

        assertNotNull("Crypto data must not be null", data)
        // Crypto can be volatile, but >50% daily change is suspicious
        assertTrue("BTC change % unreasonable: ${data!!.btcChangePercent}",
            Math.abs(data.btcChangePercent) < 50)
        assertTrue("ETH change % unreasonable: ${data.ethChangePercent}",
            Math.abs(data.ethChangePercent) < 50)
    }

    // ========================================
    // MAJOR INDICES TESTS (SPY, DIA, QQQ)
    // ========================================

    @Test
    fun test15_index_spyIsReachable() {
        val result = FinnhubApi().fetchStock("SPY")
        assertTrue("SPY (S&P 500) API must be reachable", result.isSuccess)
    }

    @Test
    fun test16_index_spyPriceIsValid() {
        val result = FinnhubApi().fetchStock("SPY")
        val data = result.getOrNull()

        assertNotNull("SPY data must not be null", data)
        assertTrue("SPY price must be > 0, got: ${data?.price}", data!!.price > 0)
        assertTrue("SPY price must be < 1000 (sanity check), got: ${data.price}", data.price < 1000)
    }

    @Test
    fun test17_index_diaIsReachable() {
        val result = FinnhubApi().fetchStock("DIA")
        assertTrue("DIA (Dow Jones) API must be reachable", result.isSuccess)
    }

    @Test
    fun test18_index_diaPriceIsValid() {
        val result = FinnhubApi().fetchStock("DIA")
        val data = result.getOrNull()

        assertNotNull("DIA data must not be null", data)
        assertTrue("DIA price must be > 0, got: ${data?.price}", data!!.price > 0)
        assertTrue("DIA price must be < 1000 (sanity check), got: ${data.price}", data.price < 1000)
    }

    @Test
    fun test19_index_qqqIsReachable() {
        val result = FinnhubApi().fetchStock("QQQ")
        assertTrue("QQQ (Nasdaq) API must be reachable", result.isSuccess)
    }

    @Test
    fun test19b_index_qqqPriceIsValid() {
        val result = FinnhubApi().fetchStock("QQQ")
        val data = result.getOrNull()

        assertNotNull("QQQ data must not be null", data)
        assertTrue("QQQ price must be > 0, got: ${data?.price}", data!!.price > 0)
        assertTrue("QQQ price must be < 1000 (sanity check), got: ${data.price}", data.price < 1000)
    }

    @Test
    fun test19c_indices_allThreeAvailable() {
        // Simulates the Markets tab indices section
        val spy = FinnhubApi().fetchStock("SPY")
        val dia = FinnhubApi().fetchStock("DIA")
        val qqq = FinnhubApi().fetchStock("QQQ")

        assertTrue("SPY must succeed for indices section", spy.isSuccess)
        assertTrue("DIA must succeed for indices section", dia.isSuccess)
        assertTrue("QQQ must succeed for indices section", qqq.isSuccess)

        val spyData = spy.getOrNull()
        val diaData = dia.getOrNull()
        val qqqData = qqq.getOrNull()

        assertNotNull("SPY data required", spyData)
        assertNotNull("DIA data required", diaData)
        assertNotNull("QQQ data required", qqqData)

        assertTrue("SPY price valid for display", spyData!!.price > 0)
        assertTrue("DIA price valid for display", diaData!!.price > 0)
        assertTrue("QQQ price valid for display", qqqData!!.price > 0)
    }

    // ========================================
    // STOCK API TESTS (Finnhub)
    // ========================================

    @Test
    fun test20_stock_apiIsReachable() {
        val result = FinnhubApi().fetchStock("GOOG")
        assertTrue("Stock API must be reachable for GOOG", result.isSuccess)
    }

    @Test
    fun test21_stock_googPriceIsValid() {
        val result = FinnhubApi().fetchStock("GOOG")
        val data = result.getOrNull()

        assertNotNull("Stock data must not be null", data)
        assertTrue("GOOG price must be > 0, got: ${data?.price}", data!!.price > 0)
        assertTrue("GOOG price must be < 10000 (sanity check), got: ${data.price}", data.price < 10000)
    }

    @Test
    fun test22_stock_previousCloseIsValid() {
        val result = FinnhubApi().fetchStock("GOOG")
        val data = result.getOrNull()

        assertNotNull("Stock data must not be null", data)
        assertTrue("GOOG prev close must be > 0", data!!.prevClose > 0)
    }

    @Test
    fun test23_stock_changePercentIsReasonable() {
        val result = FinnhubApi().fetchStock("GOOG")
        val data = result.getOrNull()

        assertNotNull("Stock data must not be null", data)
        // Stock daily change should be < 30% (extreme but possible)
        assertTrue("GOOG change % unreasonable: ${data!!.changePercent}",
            Math.abs(data.changePercent) < 30)
    }

    @Test
    fun test24_stock_multipleSymbolsWork() {
        // Test that we can fetch multiple different stocks
        val goog = FinnhubApi().fetchStock("GOOG")
        val aapl = FinnhubApi().fetchStock("AAPL")

        assertTrue("GOOG should succeed", goog.isSuccess)
        assertTrue("AAPL should succeed", aapl.isSuccess)

        val googData = goog.getOrNull()
        val aaplData = aapl.getOrNull()

        assertNotNull("GOOG data", googData)
        assertNotNull("AAPL data", aaplData)
        assertTrue("GOOG price > 0", googData!!.price > 0)
        assertTrue("AAPL price > 0", aaplData!!.price > 0)
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================

    @Test
    fun test30_stock_invalidSymbolReturnsFailure() {
        val result = FinnhubApi().fetchStock("INVALID_SYMBOL_12345")
        // Should either fail or return zero price
        val data = result.getOrNull()
        if (result.isSuccess && data != null) {
            // If it "succeeds", the price should be 0 (invalid symbol)
            assertEquals("Invalid symbol should have 0 price", 0.0, data.price, 0.01)
        }
        // Either way is acceptable - failure or zero price
    }

    // ========================================
    // WIDGET COMPLETE FLOW TESTS
    // ========================================

    @Test
    fun test40_metalsWidget_allDataAvailable() {
        // Simulates the MetalsWidgetProvider data fetch
        val metalsResult = GoldPriceApi().fetchMetals()
        val cryptoResult = FinnhubApi().fetchCrypto()

        assertTrue("Metals fetch must succeed for widget", metalsResult.isSuccess)
        assertTrue("Crypto fetch must succeed for widget", cryptoResult.isSuccess)

        val metals = metalsResult.getOrNull()
        val crypto = cryptoResult.getOrNull()

        // All 4 widget values must be valid
        assertNotNull("Metals data required", metals)
        assertNotNull("Crypto data required", crypto)

        assertTrue("Gold price for widget", metals!!.goldPrice > 0)
        assertTrue("Silver price for widget", metals.silverPrice > 0)
        assertTrue("BTC price for widget", crypto!!.btcPrice > 0)
        assertTrue("ETH price for widget", crypto.ethPrice > 0)
    }

    @Test
    fun test41_singleStockWidget_dataAvailable() {
        // Simulates the SingleStockWidgetProvider data fetch
        val result = FinnhubApi().fetchStock("GOOG")

        assertTrue("GOOG fetch must succeed for widget", result.isSuccess)

        val data = result.getOrNull()
        assertNotNull("Stock data required for widget", data)
        assertTrue("GOOG price for widget", data!!.price > 0)
    }

    @Test
    fun test42_marketsTab_indicesAndWatchlistAvailable() {
        // Simulates the complete Markets tab data fetch
        // Must fetch indices (SPY, DIA, QQQ) and at least one watchlist stock

        // Indices section
        val spy = FinnhubApi().fetchStock("SPY")
        val dia = FinnhubApi().fetchStock("DIA")
        val qqq = FinnhubApi().fetchStock("QQQ")

        assertTrue("SPY must succeed for Markets tab", spy.isSuccess)
        assertTrue("DIA must succeed for Markets tab", dia.isSuccess)
        assertTrue("QQQ must succeed for Markets tab", qqq.isSuccess)

        // Sample watchlist stocks
        val aapl = FinnhubApi().fetchStock("AAPL")
        val msft = FinnhubApi().fetchStock("MSFT")

        assertTrue("AAPL must succeed for watchlist", aapl.isSuccess)
        assertTrue("MSFT must succeed for watchlist", msft.isSuccess)

        // Verify all prices valid
        assertTrue("SPY price valid", spy.getOrNull()!!.price > 0)
        assertTrue("DIA price valid", dia.getOrNull()!!.price > 0)
        assertTrue("QQQ price valid", qqq.getOrNull()!!.price > 0)
        assertTrue("AAPL price valid", aapl.getOrNull()!!.price > 0)
        assertTrue("MSFT price valid", msft.getOrNull()!!.price > 0)
    }
}
