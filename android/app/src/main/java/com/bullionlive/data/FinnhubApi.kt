package com.bullionlive.data

import android.util.Log
import com.bullionlive.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * FinnhubApi - Crypto price fetcher for BTC and ETH via Binance symbols
 *
 * API ENDPOINT: https://finnhub.io/api/v1/quote
 * AUTH: API key required (token parameter)
 * RATE LIMIT: 60 calls/minute on free tier
 *
 * SYMBOLS USED:
 * - BINANCE:BTCUSDT (Bitcoin/USD via Binance)
 * - BINANCE:ETHUSDT (Ethereum/USD via Binance)
 *
 * RESPONSE FORMAT:
 * {
 *   "c": 88259.69,     // Current price
 *   "d": 338.87,       // Dollar change
 *   "dp": 0.3854,      // Percent change
 *   "pc": 87920.82,    // Previous close
 *   "t": 1766261913    // Timestamp
 * }
 *
 * CACHING: 5-minute TTL shared across BTC/ETH.
 * Both symbols fetched together, cached as unit.
 *
 * ERROR HANDLING: Returns null QuoteData on per-symbol failure.
 * Both must succeed for Result.success(), else Result.failure().
 *
 * USED BY: MetalsWidgetProvider (crypto section), Web app crypto tab
 *
 * NOTE: Same API is used for stocks in web app, but this class
 * is specifically for widget crypto data (BTC/ETH only).
 */
data class CryptoData(
    val btcPrice: Double,
    val btcPrevClose: Double,
    val btcChangePercent: Double,
    val ethPrice: Double,
    val ethPrevClose: Double,
    val ethChangePercent: Double
)

data class StockData(
    val symbol: String,
    val price: Double,
    val prevClose: Double,
    val changePercent: Double
)

class FinnhubApi {
    companion object {
        private const val TAG = "FinnhubApi"
        private const val API_KEY = BuildConfig.FINNHUB_API_KEY
        private val BASE_URL get() = AppConfig.FINNHUB_BASE_URL

        private val CACHE_MAX_AGE_MS get() = AppConfig.CACHE_COMPAT_MS
        // Stale cache can be used for up to 2 hours when API fails
        private val STALE_CACHE_MAX_AGE_MS get() = AppConfig.CACHE_STALE_MS

        @Volatile
        private var cachedCryptoData: CryptoData? = null
        @Volatile
        private var cacheTimestamp: Long = 0L

        // Track rate limit status
        @Volatile
        private var rateLimitedUntil: Long = 0L

        private fun isCacheValid(): Boolean {
            return cachedCryptoData != null &&
                   (System.currentTimeMillis() - cacheTimestamp) < CACHE_MAX_AGE_MS
        }

        private fun hasStaleCacheAvailable(): Boolean {
            return cachedCryptoData != null &&
                   (System.currentTimeMillis() - cacheTimestamp) < STALE_CACHE_MAX_AGE_MS
        }

        private fun getCacheAgeSeconds(): Long {
            return (System.currentTimeMillis() - cacheTimestamp) / 1000
        }

        private fun isRateLimited(): Boolean {
            return System.currentTimeMillis() < rateLimitedUntil
        }

        // For testing: allow clearing cache
        @JvmStatic
        fun clearCache() {
            cachedCryptoData = null
            cacheTimestamp = 0L
            rateLimitedUntil = 0L
        }
    }

    fun fetchCrypto(): Result<CryptoData> {
        if (isCacheValid()) {
            return Result.success(cachedCryptoData!!)
        }

        // If rate limited, use stale cache immediately
        if (isRateLimited() && hasStaleCacheAvailable()) {
            val cacheAge = getCacheAgeSeconds()
            Log.w(TAG, "Rate limited - using stale cache (${cacheAge}s old)")
            return Result.success(cachedCryptoData!!)
        }

        return try {
            val btc = fetchQuote("BINANCE:BTCUSDT")
            val eth = fetchQuote("BINANCE:ETHUSDT")

            if (btc != null && eth != null) {
                val data = CryptoData(
                    btcPrice = btc.price,
                    btcPrevClose = btc.prevClose,
                    btcChangePercent = btc.changePercent,
                    ethPrice = eth.price,
                    ethPrevClose = eth.prevClose,
                    ethChangePercent = eth.changePercent
                )
                cachedCryptoData = data
                cacheTimestamp = System.currentTimeMillis()
                Result.success(data)
            } else {
                Log.e(TAG, "Failed: btc=${btc != null}, eth=${eth != null}")
                // Fallback to stale cache when API fails
                if (hasStaleCacheAvailable()) {
                    val cacheAge = getCacheAgeSeconds()
                    Log.w(TAG, "Using stale cache after fetch failure (${cacheAge}s old)")
                    return Result.success(cachedCryptoData!!)
                }
                Result.failure(Exception("Failed to fetch crypto"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            // Fallback to stale cache on any exception
            if (hasStaleCacheAvailable()) {
                val cacheAge = getCacheAgeSeconds()
                Log.w(TAG, "Using stale cache after error (${cacheAge}s old)")
                return Result.success(cachedCryptoData!!)
            }
            Result.failure(e)
        }
    }

    fun fetchStock(symbol: String): Result<StockData> {
        return try {
            val quote = fetchQuote(symbol)
            if (quote != null) {
                Result.success(StockData(symbol, quote.price, quote.prevClose, quote.changePercent))
            } else {
                Log.e(TAG, "Failed to fetch $symbol")
                Result.failure(Exception("Failed to fetch $symbol"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "$symbol error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch crypto with persistent cache and retry logic.
     * Use this method from widgets for resilience against process death.
     *
     * @param cacheManager PersistentCacheManager instance (created with widget Context)
     */
    fun fetchCryptoWithPersistence(cacheManager: PersistentCacheManager): Result<CryptoData> {
        // Check fresh cache first
        if (cacheManager.isCryptoCacheFresh()) {
            val cached = cacheManager.getCachedCrypto()!!
            return Result.success(cached)
        }

        // Fetch with retry, falling back to stale cache if needed
        return cacheManager.fetchWithRetry(
            fetchOperation = { 
                fetchCryptoInternal().also { result ->
                    result.getOrNull()?.let { cacheManager.saveCrypto(it) }
                }
            },
            getCachedFallback = {
                if (cacheManager.isCryptoCacheUsable()) {
                    val age = cacheManager.getCacheAgeSeconds(cacheManager.getCryptoTimestamp())
                    Log.w(TAG, "Using stale persistent crypto cache as fallback (${age}s old)")
                    cacheManager.getCachedCrypto()
                } else {
                    null
                }
            }
        )
    }

    /**
     * Internal crypto fetch without caching (used by fetchCryptoWithPersistence)
     */
    private fun fetchCryptoInternal(): Result<CryptoData> {
        return try {
            val btc = fetchQuote("BINANCE:BTCUSDT")
            val eth = fetchQuote("BINANCE:ETHUSDT")

            if (btc != null && eth != null) {
                val data = CryptoData(
                    btcPrice = btc.price,
                    btcPrevClose = btc.prevClose,
                    btcChangePercent = btc.changePercent,
                    ethPrice = eth.price,
                    ethPrevClose = eth.prevClose,
                    ethChangePercent = eth.changePercent
                )
                Result.success(data)
            } else {
                Log.e(TAG, "Failed: btc=${btc != null}, eth=${eth != null}")
                Result.failure(Exception("Failed to fetch crypto"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch stock with persistent cache and retry logic.
     * Use this method from widgets for resilience against process death.
     *
     * IMPORTANT: The original fetchStock() had NO caching at all, causing
     * the stock widget to show "Error" after process death. This method
     * provides the same caching as crypto.
     *
     * @param symbol Stock symbol (e.g., "GOOG")
     * @param cacheManager PersistentCacheManager instance (created with widget Context)
     */
    fun fetchStockWithPersistence(symbol: String, cacheManager: PersistentCacheManager): Result<StockData> {
        // Check fresh cache first
        if (cacheManager.isStockCacheFresh(symbol)) {
            val cached = cacheManager.getCachedStock(symbol)!!
            return Result.success(cached)
        }

        // Fetch with retry, falling back to stale cache if needed
        return cacheManager.fetchWithRetry(
            fetchOperation = { 
                fetchStockInternal(symbol).also { result ->
                    result.getOrNull()?.let { cacheManager.saveStock(symbol, it) }
                }
            },
            getCachedFallback = {
                if (cacheManager.isStockCacheUsable(symbol)) {
                    val age = cacheManager.getCacheAgeSeconds(cacheManager.getStockTimestamp(symbol))
                    Log.w(TAG, "Using stale persistent $symbol cache as fallback (${age}s old)")
                    cacheManager.getCachedStock(symbol)
                } else {
                    null
                }
            }
        )
    }

    /**
     * Internal stock fetch without caching (used by fetchStockWithPersistence)
     */
    private fun fetchStockInternal(symbol: String): Result<StockData> {
        return try {
            val quote = fetchQuote(symbol)
            if (quote != null) {
                Result.success(StockData(symbol, quote.price, quote.prevClose, quote.changePercent))
            } else {
                Log.e(TAG, "Failed to fetch $symbol")
                Result.failure(Exception("Failed to fetch $symbol"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "$symbol error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private data class QuoteData(val price: Double, val prevClose: Double, val changePercent: Double)
    
    private val requestQueue = ApiRequestQueue.getInstance()

    private fun fetchQuote(symbol: String): QuoteData? {
        val requestKey = "finnhub:$symbol"
        
        // Check if request can be made (rate limit + deduplication)
        if (!requestQueue.enqueue(requestKey, "finnhub")) {
            val waitTime = requestQueue.getWaitTime("finnhub")
            if (waitTime > 0) {
                Log.w(TAG, "$symbol: Rate limit - wait ${waitTime}ms")
            } else {
            }
            return null
        }
        
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$BASE_URL?symbol=$symbol&token=$API_KEY")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.CONNECT_TIMEOUT_MS
            conn.readTimeout = AppConfig.READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", AppConfig.USER_AGENT)
            conn.doInput = true

            val responseCode = conn.responseCode
            if (responseCode == 429) {
                // Rate limited - back off for 1 minute
                Log.w(TAG, "$symbol: Rate limited (HTTP 429)")
                rateLimitedUntil = System.currentTimeMillis() + AppConfig.RATE_LIMIT_BACKOFF_MS
                requestQueue.dequeue(requestKey)
                return null
            }
            if (responseCode != 200) {
                Log.e(TAG, "$symbol: HTTP $responseCode")
                requestQueue.dequeue(requestKey)
                return null
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(json)
            val current = data.optDouble("c", 0.0)
            val prevClose = data.optDouble("pc", 0.0)
            val changePercent = data.optDouble("dp", 0.0)

            if (current > 0 && prevClose > 0) {
                requestQueue.dequeue(requestKey)
                QuoteData(current, prevClose, changePercent)
            } else {
                Log.e(TAG, "$symbol: invalid c=$current pc=$prevClose")
                requestQueue.dequeue(requestKey)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "$symbol error: ${e.message}")
            requestQueue.dequeue(requestKey)
            null
        } finally {
            conn?.disconnect()
        }
    }
}
