package com.bullionlive.data

import android.util.Log
import com.bullionlive.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
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

        @Volatile
        private var cachedGcPrevClose: Double? = null
        @Volatile
        private var cachedSiPrevClose: Double? = null
        @Volatile
        private var cachedPrevCloseDate: String = ""

        private fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
        
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
     * Internal metals fetch: Swissquote spot price + Yahoo Finance GC=F/SI=F prevClose.
     * Mirrors JS fetchMetals() strategy so widget and web app stay in sync.
     *
     * Why Yahoo Finance GC=F/SI=F instead of Finnhub GLD/SLV:
     *   GLD/SLV ETF dp% measures 9:30AM-4PM US market only. Spot metals trade 23h/day.
     *   On days with Asian/European sessions driving moves, GLD/SLV shows wrong direction.
     *   GC=F/SI=F chartPreviousClose covers the full 24h trading session.
     *   Trade-off: Yahoo Finance v8 is unofficial/undocumented. Fallback = price-only mode.
     */
    private fun fetchMetalsInternal(): Result<MetalsData> {
        val swissResult = fetchSwissquotePrices()
        if (swissResult.isFailure) {
            val err = swissResult.exceptionOrNull() ?: Exception("Swissquote failed")
            Log.e(TAG, "Swissquote failed: ${err.message}")
            return Result.failure(err)
        }

        val (goldPrice, silverPrice) = swissResult.getOrNull()!!

        val todayDate = getTodayDateString()
        if (todayDate != cachedPrevCloseDate) {
            cachedGcPrevClose = null
            cachedSiPrevClose = null
            cachedPrevCloseDate = todayDate
        }

        // Yahoo Finance GC=F/SI=F for 24h-accurate prevClose (cached daily)
        val gcPrevClose = cachedGcPrevClose ?: fetchYahooPrevClose(AppConfig.YAHOO_GOLD_SYMBOL).also {
            if (it != null) cachedGcPrevClose = it
        }
        val siPrevClose = cachedSiPrevClose ?: fetchYahooPrevClose(AppConfig.YAHOO_SILVER_SYMBOL).also {
            if (it != null) cachedSiPrevClose = it
        }

        // Calculate prevClose: if futures prevClose is within 10% of spot, use it.
        // dp% = (spotPrice - prevClose) / prevClose
        // xauClose passed to MetalsData = spotPrice / (1 + dp) so changePct is derived correctly
        val MAX_DIVERGENCE = 0.10
        val goldPrevClose = if (gcPrevClose != null && gcPrevClose > 0 &&
            Math.abs((goldPrice - gcPrevClose) / gcPrevClose) < MAX_DIVERGENCE) {
            val dp = (goldPrice - gcPrevClose) / gcPrevClose
            goldPrice / (1.0 + dp)  // = gcPrevClose, just expressed consistently
        } else {
            goldPrice // 0% change shown if Yahoo unavailable
        }
        val goldChangePct = if (gcPrevClose != null && goldPrevClose != goldPrice) {
            (goldPrice - goldPrevClose) / goldPrevClose * 100.0
        } else 0.0

        val silverPrevClose = if (siPrevClose != null && siPrevClose > 0 &&
            Math.abs((silverPrice - siPrevClose) / siPrevClose) < MAX_DIVERGENCE) {
            val dp = (silverPrice - siPrevClose) / siPrevClose
            silverPrice / (1.0 + dp)
        } else {
            silverPrice
        }
        val silverChangePct = if (siPrevClose != null && silverPrevClose != silverPrice) {
            (silverPrice - silverPrevClose) / silverPrevClose * 100.0
        } else 0.0

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
     * Fetch chartPreviousClose from Yahoo Finance for a futures symbol (GC=F or SI=F).
     * This reflects the 24h trading session, unlike ETF dp% which is US market hours only.
     * Returns null on failure — caller treats null as 0% change (price-only mode).
     *
     * Response path: chart.result[0].meta.chartPreviousClose
     * Unofficial API — no SLA. Degrade gracefully if unavailable.
     */
    private fun fetchYahooPrevClose(symbol: String): Double? {
        var conn: HttpURLConnection? = null
        return try {
            val encodedSymbol = java.net.URLEncoder.encode(symbol, "UTF-8")
            conn = URL("${AppConfig.YAHOO_FINANCE_BASE_URL}/$encodedSymbol?interval=1d&range=2d")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = AppConfig.METALS_TIMEOUT_MS
            conn.readTimeout = AppConfig.METALS_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.setRequestProperty("Accept", "application/json")
            conn.doInput = true

            if (conn.responseCode != 200) {
                Log.w(TAG, "Yahoo Finance $symbol HTTP ${conn.responseCode}")
                return null
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val meta = root
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")

            val prevClose = meta.optDouble("chartPreviousClose", Double.NaN)
            prevClose.takeIf { !it.isNaN() && it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "Yahoo Finance $symbol fetch error: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
