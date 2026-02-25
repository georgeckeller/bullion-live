package com.bullionlive.data

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * GoldPriceApi - Precious metals price fetcher for Gold (XAU) and Silver (XAG)
 *
 * API ENDPOINT: https://data-asg.goldprice.org/dbXRates/USD
 * AUTH: None required (public API, CORS-friendly)
 * RATE LIMIT: Generous, no documented limit
 *
 * RESPONSE FORMAT:
 * {
 *   "ts": 1766261892257,
 *   "items": [{
 *     "curr": "USD",
 *     "xauPrice": 4340.105,    // Gold current price
 *     "xagPrice": 67.143,      // Silver current price
 *     "xauClose": 4332.145,    // Gold previous close
 *     "xagClose": 65.2865,     // Silver previous close
 *     "pcXau": 0.18,           // Gold change percent
 *     "pcXag": 2.84            // Silver change percent
 *   }]
 * }
 *
 * CACHING: 5-minute TTL using volatile companion object fields.
 * Prevents duplicate API calls during widget refresh storms.
 *
 * ERROR HANDLING: Returns Result.failure() on network/parse errors.
 * Widget displays "Error" text and retries on next update cycle.
 *
 * USED BY: MetalsWidgetProvider, Web app metals tab
 */
data class MetalsData(
    val goldPrice: Double,
    val goldPreviousClose: Double,
    val goldChangePercent: Double,
    val silverPrice: Double,
    val silverPreviousClose: Double,
    val silverChangePercent: Double
)

class GoldPriceApi {
    companion object {
        private const val TAG = "GoldPriceApi"
        private val CACHE_MAX_AGE_MS get() = AppConfig.CACHE_COMPAT_MS
        // Stale cache can be used for up to 2 hours when API fails
        private val STALE_CACHE_MAX_AGE_MS get() = AppConfig.CACHE_STALE_MS

        @Volatile
        private var cachedMetalsData: MetalsData? = null
        @Volatile
        private var cacheTimestamp: Long = 0L
        
        private val requestQueue = ApiRequestQueue.getInstance()

        private fun isCacheValid(): Boolean {
            return cachedMetalsData != null &&
                   (System.currentTimeMillis() - cacheTimestamp) < CACHE_MAX_AGE_MS
        }

        private fun hasStaleCacheAvailable(): Boolean {
            return cachedMetalsData != null &&
                   (System.currentTimeMillis() - cacheTimestamp) < STALE_CACHE_MAX_AGE_MS
        }

        private fun getCacheAgeSeconds(): Long {
            return (System.currentTimeMillis() - cacheTimestamp) / 1000
        }

        // For testing: allow clearing cache
        @JvmStatic
        fun clearCache() {
            cachedMetalsData = null
            cacheTimestamp = 0L
        }
    }

    fun fetchMetals(): Result<MetalsData> {
        if (isCacheValid()) {
            return Result.success(cachedMetalsData!!)
        }

        val requestKey = "goldprice:metals"
        
        // Check if request can be made (deduplication only)
        if (!requestQueue.enqueue(requestKey, "goldprice")) {
            if (hasStaleCacheAvailable()) {
                val cacheAge = getCacheAgeSeconds()
                Log.w(TAG, "Using stale cache (${cacheAge}s old)")
                return Result.success(cachedMetalsData!!)
            }
            return Result.failure(Exception("Request deduplicated, no stale cache"))
        }

        var conn: HttpURLConnection? = null
        return try {
            val url = URL(AppConfig.GOLDPRICE_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.CONNECT_TIMEOUT_MS
            conn.readTimeout = AppConfig.READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", AppConfig.USER_AGENT)
            conn.doInput = true

            val responseCode = conn.responseCode

            if (responseCode != 200) {
                requestQueue.dequeue(requestKey)
                // Try stale cache on HTTP error
                if (hasStaleCacheAvailable()) {
                    val cacheAge = getCacheAgeSeconds()
                    Log.w(TAG, "HTTP $responseCode - using stale cache (${cacheAge}s old)")
                    return Result.success(cachedMetalsData!!)
                }
                return Result.failure(Exception("HTTP $responseCode"))
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }

            val root = JSONObject(json)
            val item = root.getJSONArray("items").getJSONObject(0)

            val goldPrice = item.optDouble("xauPrice", 0.0)
            val goldClose = item.optDouble("xauClose", goldPrice)
            val goldPct = item.optDouble("pcXau", 0.0)

            val silverPrice = item.optDouble("xagPrice", 0.0)
            val silverClose = item.optDouble("xagClose", silverPrice)
            val silverPct = item.optDouble("pcXag", 0.0)

            // Validate prices - use stale cache if API returns invalid data
            if (goldPrice <= 0 || silverPrice <= 0) {
                Log.e(TAG, "Invalid prices: gold=$goldPrice, silver=$silverPrice")
                requestQueue.dequeue(requestKey)
                if (hasStaleCacheAvailable()) {
                    val cacheAge = getCacheAgeSeconds()
                    Log.w(TAG, "Using stale cache due to invalid prices (${cacheAge}s old)")
                    return Result.success(cachedMetalsData!!)
                }
                return Result.failure(Exception("Invalid price data"))
            }


            val data = MetalsData(
                goldPrice = goldPrice,
                goldPreviousClose = goldClose,
                goldChangePercent = goldPct,
                silverPrice = silverPrice,
                silverPreviousClose = silverClose,
                silverChangePercent = silverPct
            )
            cachedMetalsData = data
            cacheTimestamp = System.currentTimeMillis()
            requestQueue.dequeue(requestKey)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            requestQueue.dequeue(requestKey)
            // Fallback to stale cache on any exception
            if (hasStaleCacheAvailable()) {
                val cacheAge = getCacheAgeSeconds()
                Log.w(TAG, "Using stale cache after error (${cacheAge}s old)")
                return Result.success(cachedMetalsData!!)
            }
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Fetch metals with persistent cache and retry logic.
     * Use this method from widgets for resilience against process death.
     *
     * @param cacheManager PersistentCacheManager instance (created with widget Context)
     */
    fun fetchMetalsWithPersistence(cacheManager: PersistentCacheManager): Result<MetalsData> {
        // Check fresh cache first
        if (cacheManager.isMetalsCacheFresh()) {
            val cached = cacheManager.getCachedMetals()!!
            return Result.success(cached)
        }

        // Fetch with retry, falling back to stale cache if needed
        return cacheManager.fetchWithRetry(
            fetchOperation = { 
                fetchMetalsInternal().also { result ->
                    result.getOrNull()?.let { cacheManager.saveMetals(it) }
                }
            },
            getCachedFallback = {
                if (cacheManager.isMetalsCacheUsable()) {
                    val age = cacheManager.getCacheAgeSeconds(cacheManager.getMetalsTimestamp())
                    Log.w(TAG, "Using stale persistent metals cache as fallback (${age}s old)")
                    cacheManager.getCachedMetals()
                } else {
                    null
                }
            }
        )
    }

    /**
     * Internal metals fetch with Swissquote fallback (used by fetchMetalsWithPersistence)
     */
    private fun fetchMetalsInternal(): Result<MetalsData> {
        val requestKey = "goldprice:metals"
        
        // Try GoldPrice.org first
        if (requestQueue.enqueue(requestKey, "goldprice")) {
            val primaryResult = fetchGoldPriceOrg()
            requestQueue.dequeue(requestKey)
            
            if (primaryResult.isSuccess) {
                return primaryResult
            }
            Log.w(TAG, "GoldPrice.org failed: ${primaryResult.exceptionOrNull()?.message}, trying Swissquote fallback")
        }
        
        // Try Swissquote fallback
        return fetchSwissquote()
    }

    private fun fetchGoldPriceOrg(): Result<MetalsData> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(AppConfig.GOLDPRICE_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.GOLDPRICE_TIMEOUT_MS
            conn.readTimeout = AppConfig.GOLDPRICE_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", AppConfig.USER_AGENT)
            conn.doInput = true

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                return Result.failure(Exception("HTTP $responseCode"))
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            
            // Check if it's actually HTML (WAF challenge)
            if (json.trim().startsWith("<html", ignoreCase = true)) {
                return Result.failure(Exception("WAF challenge detected (HTML response)"))
            }

            val root = JSONObject(json)
            val item = root.getJSONArray("items").getJSONObject(0)

            val goldPrice = item.optDouble("xauPrice", 0.0)
            val goldClose = item.optDouble("xauClose", goldPrice)
            val goldPct = item.optDouble("pcXau", 0.0)

            val silverPrice = item.optDouble("xagPrice", 0.0)
            val silverClose = item.optDouble("xagClose", silverPrice)
            val silverPct = item.optDouble("pcXag", 0.0)

            if (goldPrice <= 0 || silverPrice <= 0) {
                return Result.failure(Exception("Invalid price data"))
            }

            Result.success(MetalsData(goldPrice, goldClose, goldPct, silverPrice, silverClose, silverPct))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchSwissquote(): Result<MetalsData> {
        var conn: HttpURLConnection? = null
        return try {
            // Swissquote Public API - very reliable secondary source
            val url = URL(AppConfig.SWISSQUOTE_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.GOLDPRICE_TIMEOUT_MS
            conn.readTimeout = AppConfig.GOLDPRICE_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.doInput = true

            if (conn.responseCode != 200) {
                return Result.failure(Exception("Swissquote HTTP ${conn.responseCode}"))
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(json)
            
            var goldPrice = 0.0
            var silverPrice = 0.0
            
            // Swissquote returns array of objects: [{symbol: "XAUUSD", bid: 123, ...}, ...]
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val symbol = obj.getString("symbol")
                if (symbol == "XAUUSD") goldPrice = obj.getDouble("bid")
                if (symbol == "XAGUSD") silverPrice = obj.getDouble("bid")
            }

            if (goldPrice <= 0 || silverPrice <= 0) {
                return Result.failure(Exception("Swissquote missing data"))
            }

            // Swissquote doesn't always provide prevClose, so we use 0.0 and let UI handle it 
            // OR we calculate based on change if available (Swissquote usually lacks this)
            // For now, we use a slightly different URL that includes historical data if needed
            // but for a fast widget update, bid/ask is sufficient.
            
            Result.success(MetalsData(
                goldPrice = goldPrice,
                goldPreviousClose = goldPrice, // UI will show 0% change if close is same as price
                goldChangePercent = 0.0,
                silverPrice = silverPrice,
                silverPreviousClose = silverPrice,
                silverChangePercent = 0.0
            ))
        } catch (e: Exception) {
            Log.e("GoldPriceApi", "Swissquote fallback failed: ${e.message}")
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
