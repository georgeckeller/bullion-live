package com.bullionlive.data

import android.util.Log
import com.bullionlive.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * GoldPriceApi - Precious metals price fetcher for Gold (XAU) and Silver (XAG)
 *
 * TWO-SOURCE STRATEGY (zero cost, no new registration):
 *
 * PRIMARY (spot price): Swissquote public forex feed
 *   XAU: https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAU/USD
 *   XAG: https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAG/USD
 *   Response: [{ spreadProfilePrices: [{ bid: <USD/troy oz> }] }]
 *   Auth: None. No rate limit documented. Very reliable.
 *
 * DAILY CHANGE %: Finnhub GLD/SLV ETF quotes (existing free API key)
 *   GLD = SPDR Gold Shares ETF, tracks spot gold with >99.9% daily correlation.
 *   SLV = iShares Silver Trust ETF, tracks spot silver similarly.
 *   We use dp (daily % change) ONLY — not the ETF price itself.
 *   prevClose is back-calculated: spotPrice / (1 + dp/100)
 *
 * FALLBACK: Swissquote price-only (0% change shown in UI) if Finnhub fails.
 *
 * WHY NOT GoldPrice.org: It returned HTTP 403 after Cloudflare WAF was
 * added in March 2026. All programmatic requests are blocked. No fix possible.
 *
 * CACHING: 1-minute fresh TTL via companion object fields.
 * Prevents duplicate API calls during widget refresh storms.
 *
 * ERROR HANDLING: Returns Result.failure() on network/parse errors.
 * Widget displays stale cache or "Error" text, retries on next cycle.
 *
 * USED BY: MetalsWidgetProvider, FetchReceiver, Web app metals tab
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

        val requestKey = "metals:swissquote"

        // Deduplication: if another call is already in flight, use stale cache
        if (!requestQueue.enqueue(requestKey, "swissquote")) {
            if (hasStaleCacheAvailable()) {
                val cacheAge = getCacheAgeSeconds()
                Log.w(TAG, "Using stale cache (${cacheAge}s old)")
                return Result.success(cachedMetalsData!!)
            }
            return Result.failure(Exception("Request deduplicated, no stale cache"))
        }

        return try {
            val result = fetchMetalsInternal()
            result.getOrNull()?.let {
                cachedMetalsData = it
                cacheTimestamp = System.currentTimeMillis()
            }
            requestQueue.dequeue(requestKey)
            if (result.isFailure && hasStaleCacheAvailable()) {
                val cacheAge = getCacheAgeSeconds()
                Log.w(TAG, "Using stale cache after error (${cacheAge}s old)")
                Result.success(cachedMetalsData!!)
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMetals error: ${e.message}", e)
            requestQueue.dequeue(requestKey)
            if (hasStaleCacheAvailable()) {
                Result.success(cachedMetalsData!!)
            } else {
                Result.failure(e)
            }
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
     * Internal metals fetch: Swissquote spot price + Finnhub GLD/SLV daily change %.
     * Mirrors JS fetchMetals() strategy so widget and web app stay in sync.
     */
    private fun fetchMetalsInternal(): Result<MetalsData> {
        // Try primary: Swissquote + Finnhub GLD/SLV
        val swissResult = fetchSwissquotePrices()
        if (swissResult.isFailure) {
            val err = swissResult.exceptionOrNull() ?: Exception("Swissquote failed")
            Log.e(TAG, "Swissquote failed: ${err.message}")
            return Result.failure(err)
        }

        val (goldPrice, silverPrice) = swissResult.getOrNull()!!

        // Try Finnhub GLD/SLV for daily change %; fall back gracefully if unavailable
        val gldDp = fetchFinnhubDp(AppConfig.GLD_SYMBOL)
        val slvDp = fetchFinnhubDp(AppConfig.SLV_SYMBOL)

        // Back-calculate prevClose from spot price and ETF daily change %
        val goldPrevClose = if (gldDp != null && Math.abs(gldDp) < 20.0) {
            goldPrice / (1.0 + gldDp / 100.0)
        } else {
            goldPrice // 0% change shown if Finnhub unavailable
        }
        val goldChangePct = gldDp ?: 0.0

        val silverPrevClose = if (slvDp != null && Math.abs(slvDp) < 20.0) {
            silverPrice / (1.0 + slvDp / 100.0)
        } else {
            silverPrice
        }
        val silverChangePct = slvDp ?: 0.0

        return Result.success(MetalsData(
            goldPrice = goldPrice,
            goldPreviousClose = goldPrevClose,
            goldChangePercent = goldChangePct,
            silverPrice = silverPrice,
            silverPreviousClose = silverPrevClose,
            silverChangePercent = silverChangePct
        ))
    }

    /** Fetch live XAU and XAG spot bid prices from Swissquote public forex feed. */
    private fun fetchSwissquotePrices(): Result<Pair<Double, Double>> {
        val goldPrice = fetchSwissquoteInstrument(AppConfig.SWISSQUOTE_XAU_URL) ?: return Result.failure(Exception("Swissquote XAU failed"))
        val silverPrice = fetchSwissquoteInstrument(AppConfig.SWISSQUOTE_XAG_URL) ?: return Result.failure(Exception("Swissquote XAG failed"))
        return Result.success(Pair(goldPrice, silverPrice))
    }

    private fun fetchSwissquoteInstrument(url: String): Double? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.METALS_TIMEOUT_MS
            conn.readTimeout = AppConfig.METALS_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.doInput = true

            if (conn.responseCode != 200) {
                Log.e(TAG, "Swissquote HTTP ${conn.responseCode} for $url")
                return null
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(json)
            // Response: [{spreadProfilePrices: [{bid: <price>}]}]
            array.getJSONObject(0)
                .getJSONArray("spreadProfilePrices")
                .getJSONObject(0)
                .getDouble("bid")
                .takeIf { it > 0 }
        } catch (e: Exception) {
            Log.e(TAG, "Swissquote fetch error for $url: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Fetch daily change % from Finnhub for an ETF symbol (GLD or SLV).
     * Returns null on failure — caller treats null as 0% change (price only mode).
     */
    private fun fetchFinnhubDp(symbol: String): Double? {
        var conn: HttpURLConnection? = null
        return try {
            val apiKey = BuildConfig.FINNHUB_API_KEY
            conn = URL("${AppConfig.FINNHUB_BASE_URL}?symbol=$symbol&token=$apiKey").openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.METALS_TIMEOUT_MS
            conn.readTimeout = AppConfig.METALS_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.doInput = true

            if (conn.responseCode != 200) {
                Log.w(TAG, "Finnhub $symbol HTTP ${conn.responseCode}")
                return null
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            if (obj.has("error")) {
                Log.w(TAG, "Finnhub $symbol error: ${obj.getString("error")}")
                return null
            }
            obj.optDouble("dp", Double.NaN).takeIf { !it.isNaN() }
        } catch (e: Exception) {
            Log.w(TAG, "Finnhub $symbol fetch error: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
