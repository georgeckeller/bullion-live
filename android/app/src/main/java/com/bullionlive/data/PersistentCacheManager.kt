package com.bullionlive.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject

/**
 * PersistentCacheManager - Cache that survives app process death
 *
 * FEATURES:
 * - SharedPreferences-backed persistent storage
 * - In-memory fast path for frequent access
 * - Extended 2-hour stale cache expiration (up from 15 minutes)
 * - Retry logic with exponential backoff
 *
 * CACHE TIERS:
 * - Fresh (< 5 min): Use immediately without API call
 * - Stale (5 min - 2 hours): API call attempted, fallback to cache on failure
 * - Expired (> 2 hours): Must fetch fresh data or return failure
 *
 * WHY THIS EXISTS:
 * Android kills app processes after extended inactivity. When the widget
 * updates and the process is restarted, in-memory cache is empty. If the
 * API call fails at that moment, widgets showed "Error". This persistent
 * cache ensures we always have fallback data available.
 */
class PersistentCacheManager(private val context: Context) {
    companion object {
        private const val TAG = "PersistentCache"
        private const val PREFS_NAME = "bullion_price_cache"

        // Cache keys
        private const val KEY_METALS_DATA = "metals_data"
        private const val KEY_METALS_TIMESTAMP = "metals_timestamp"
        private const val KEY_CRYPTO_DATA = "crypto_data"
        private const val KEY_CRYPTO_TIMESTAMP = "crypto_timestamp"
        private const val KEY_STOCK_PREFIX = "stock_"
        private const val KEY_STOCK_TIMESTAMP_PREFIX = "stock_ts_"

        // Cache durations (sourced from AppConfig for single source of truth)
        val CACHE_FRESH_MS get() = AppConfig.CACHE_FRESH_MS
        val CACHE_STALE_MS get() = AppConfig.CACHE_STALE_MS

        // Retry configuration (sourced from AppConfig)
        val MAX_RETRIES get() = AppConfig.MAX_RETRIES
        val INITIAL_BACKOFF_MS get() = AppConfig.INITIAL_BACKOFF_MS
        val BACKOFF_MULTIPLIER get() = AppConfig.BACKOFF_MULTIPLIER
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory cache for fast path (avoids SharedPreferences disk read on every call)
    @Volatile private var cachedMetals: MetalsData? = null
    @Volatile private var metalsTimestamp: Long = 0L
    @Volatile private var cachedCrypto: CryptoData? = null
    @Volatile private var cryptoTimestamp: Long = 0L
    @Volatile private var cachedStocks: MutableMap<String, StockData> = mutableMapOf()
    @Volatile private var stockTimestamps: MutableMap<String, Long> = mutableMapOf()

    init {
        // Load from SharedPreferences on construction
        loadFromPersistence()
    }

    // ========== METALS ==========

    fun getCachedMetals(): MetalsData? = cachedMetals

    fun getMetalsTimestamp(): Long = metalsTimestamp

    fun isMetalsCacheFresh(): Boolean {
        return cachedMetals != null &&
               (System.currentTimeMillis() - metalsTimestamp) < CACHE_FRESH_MS
    }

    fun isMetalsCacheUsable(): Boolean {
        return cachedMetals != null &&
               (System.currentTimeMillis() - metalsTimestamp) < CACHE_STALE_MS
    }

    fun saveMetals(data: MetalsData, timestamp: Long = System.currentTimeMillis()) {
        cachedMetals = data
        metalsTimestamp = timestamp

        // Persist to SharedPreferences
        prefs.edit()
            .putString(KEY_METALS_DATA, serializeMetals(data))
            .putLong(KEY_METALS_TIMESTAMP, metalsTimestamp)
            .apply()

        broadcastUpdate()
    }

    // ========== CRYPTO ==========

    fun getCachedCrypto(): CryptoData? = cachedCrypto

    fun getCryptoTimestamp(): Long = cryptoTimestamp

    fun isCryptoCacheFresh(): Boolean {
        return cachedCrypto != null &&
               (System.currentTimeMillis() - cryptoTimestamp) < CACHE_FRESH_MS
    }

    fun isCryptoCacheUsable(): Boolean {
        return cachedCrypto != null &&
               (System.currentTimeMillis() - cryptoTimestamp) < CACHE_STALE_MS
    }

    fun saveCrypto(data: CryptoData, timestamp: Long = System.currentTimeMillis()) {
        cachedCrypto = data
        cryptoTimestamp = timestamp

        prefs.edit()
            .putString(KEY_CRYPTO_DATA, serializeCrypto(data))
            .putLong(KEY_CRYPTO_TIMESTAMP, cryptoTimestamp)
            .apply()

        broadcastUpdate()
    }

    // ========== STOCKS ==========

    fun getCachedStock(symbol: String): StockData? = cachedStocks[symbol]

    fun getStockTimestamp(symbol: String): Long = stockTimestamps[symbol] ?: 0L

    fun isStockCacheFresh(symbol: String): Boolean {
        val ts = stockTimestamps[symbol] ?: 0L
        return cachedStocks[symbol] != null &&
               (System.currentTimeMillis() - ts) < CACHE_FRESH_MS
    }

    fun isStockCacheUsable(symbol: String): Boolean {
        val ts = stockTimestamps[symbol] ?: 0L
        return cachedStocks[symbol] != null &&
               (System.currentTimeMillis() - ts) < CACHE_STALE_MS
    }

    fun saveStock(symbol: String, data: StockData, timestamp: Long = System.currentTimeMillis()) {
        cachedStocks[symbol] = data
        stockTimestamps[symbol] = timestamp

        prefs.edit()
            .putString("$KEY_STOCK_PREFIX$symbol", serializeStock(data))
            .putLong("$KEY_STOCK_TIMESTAMP_PREFIX$symbol", timestamp)
            .apply()

        broadcastUpdate()
    }

    private fun broadcastUpdate() {
        val intent = Intent("com.bullionlive.CACHE_UPDATED")
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    // ========== PERSISTENCE LOAD ==========

    private fun loadFromPersistence() {
        try {
            // Load metals
            val metalsJson = prefs.getString(KEY_METALS_DATA, null)
            metalsTimestamp = prefs.getLong(KEY_METALS_TIMESTAMP, 0L)
            if (metalsJson != null && metalsTimestamp > 0) {
                cachedMetals = deserializeMetals(metalsJson)
            }

            // Load crypto
            val cryptoJson = prefs.getString(KEY_CRYPTO_DATA, null)
            cryptoTimestamp = prefs.getLong(KEY_CRYPTO_TIMESTAMP, 0L)
            if (cryptoJson != null && cryptoTimestamp > 0) {
                cachedCrypto = deserializeCrypto(cryptoJson)
            }

            // Load GOOG stock (primary stock widget symbol)
            loadStockFromPersistence("GOOG")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading from persistence: ${e.message}")
        }
    }

    private fun loadStockFromPersistence(symbol: String) {
        val stockJson = prefs.getString("$KEY_STOCK_PREFIX$symbol", null)
        val ts = prefs.getLong("$KEY_STOCK_TIMESTAMP_PREFIX$symbol", 0L)
        if (stockJson != null && ts > 0) {
            try {
                cachedStocks[symbol] = deserializeStock(stockJson)
                stockTimestamps[symbol] = ts
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing $symbol: ${e.message}")
            }
        }
    }

    // ========== SERIALIZATION ==========

    private fun serializeMetals(data: MetalsData): String {
        return JSONObject().apply {
            put("goldPrice", data.goldPrice)
            put("goldPreviousClose", data.goldPreviousClose)
            put("goldChangePercent", data.goldChangePercent)
            put("silverPrice", data.silverPrice)
            put("silverPreviousClose", data.silverPreviousClose)
            put("silverChangePercent", data.silverChangePercent)
        }.toString()
    }

    private fun deserializeMetals(json: String): MetalsData {
        val obj = JSONObject(json)
        return MetalsData(
            goldPrice = obj.getDouble("goldPrice"),
            goldPreviousClose = obj.getDouble("goldPreviousClose"),
            goldChangePercent = obj.getDouble("goldChangePercent"),
            silverPrice = obj.getDouble("silverPrice"),
            silverPreviousClose = obj.getDouble("silverPreviousClose"),
            silverChangePercent = obj.getDouble("silverChangePercent")
        )
    }

    private fun serializeCrypto(data: CryptoData): String {
        return JSONObject().apply {
            put("btcPrice", data.btcPrice)
            put("btcPrevClose", data.btcPrevClose)
            put("btcChangePercent", data.btcChangePercent)
            put("ethPrice", data.ethPrice)
            put("ethPrevClose", data.ethPrevClose)
            put("ethChangePercent", data.ethChangePercent)
        }.toString()
    }

    private fun deserializeCrypto(json: String): CryptoData {
        val obj = JSONObject(json)
        return CryptoData(
            btcPrice = obj.getDouble("btcPrice"),
            btcPrevClose = obj.getDouble("btcPrevClose"),
            btcChangePercent = obj.getDouble("btcChangePercent"),
            ethPrice = obj.getDouble("ethPrice"),
            ethPrevClose = obj.getDouble("ethPrevClose"),
            ethChangePercent = obj.getDouble("ethChangePercent")
        )
    }

    private fun serializeStock(data: StockData): String {
        return JSONObject().apply {
            put("symbol", data.symbol)
            put("price", data.price)
            put("prevClose", data.prevClose)
            put("changePercent", data.changePercent)
        }.toString()
    }

    private fun deserializeStock(json: String): StockData {
        val obj = JSONObject(json)
        return StockData(
            symbol = obj.getString("symbol"),
            price = obj.getDouble("price"),
            prevClose = obj.getDouble("prevClose"),
            changePercent = obj.getDouble("changePercent")
        )
    }

    // ========== RETRY LOGIC ==========

    /**
     * Execute a fetch operation with exponential backoff retry.
     * Returns the result or falls back to cached data if available.
     *
     * @param maxRetries Maximum number of retry attempts
     * @param fetchOperation The API call to execute
     * @param getCachedFallback Returns cached data if available for fallback
     */
    fun <T> fetchWithRetry(
        maxRetries: Int = MAX_RETRIES,
        fetchOperation: () -> Result<T>,
        getCachedFallback: () -> T?
    ): Result<T> {
        var lastException: Exception? = null
        var backoffMs = INITIAL_BACKOFF_MS

        for (attempt in 1..maxRetries) {
            try {
                val result = fetchOperation()
                if (result.isSuccess) {
                    if (attempt > 1) {
                    }
                    return result
                }
                lastException = result.exceptionOrNull() as? Exception
                Log.w(TAG, "Fetch attempt $attempt failed: ${lastException?.message}")
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Fetch attempt $attempt threw exception: ${e.message}")
            }

            // Don't sleep after last attempt
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * BACKOFF_MULTIPLIER).toLong()
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        // All retries failed - try fallback
        val fallback = getCachedFallback()
        return if (fallback != null) {
            Log.w(TAG, "All $maxRetries retries failed, using cached fallback")
            Result.success(fallback)
        } else {
            Log.e(TAG, "All $maxRetries retries failed, no fallback available")
            Result.failure(lastException ?: Exception("Fetch failed after $maxRetries attempts"))
        }
    }

    // ========== UTILITIES ==========

    fun getCacheAgeSeconds(timestamp: Long): Long {
        return (System.currentTimeMillis() - timestamp) / 1000
    }

    /**
     * Load all stocks from persistence (for JavaScript bridge)
     */
    fun loadAllStocksFromPersistence() {
        try {
            val allKeys = prefs.all.keys
            val stockKeys = allKeys.filter { it.startsWith(KEY_STOCK_PREFIX) && !it.startsWith(KEY_STOCK_TIMESTAMP_PREFIX) }
            
            stockKeys.forEach { key ->
                val symbol = key.removePrefix(KEY_STOCK_PREFIX)
                loadStockFromPersistence(symbol)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all stocks: ${e.message}")
        }
    }
    
    /**
     * Get all cached stock symbols (for JavaScript bridge)
     */
    fun getAllCachedStockSymbols(): List<String> {
        return cachedStocks.keys.toList()
    }
    
    /**
     * Clear all cached data (for testing)
     */
    fun clearAll() {
        cachedMetals = null
        metalsTimestamp = 0L
        cachedCrypto = null
        cryptoTimestamp = 0L
        cachedStocks.clear()
        stockTimestamps.clear()
        prefs.edit().clear().apply()
    }
}
