package com.bullionlive.data

/**
 * AppConfig — Centralized configuration constants for Bullion Live
 *
 * All API endpoints, timeouts, cache durations, and other config values
 * live here. This eliminates hardcoded values scattered across files.
 *
 * USAGE:
 *   import com.bullionlive.data.AppConfig
 *   val url = URL(AppConfig.GOLDPRICE_URL)
 *   conn.connectTimeout = AppConfig.CONNECT_TIMEOUT_MS
 */
object AppConfig {

    // ========== API Endpoints ==========
    // Primary metals source: Swissquote public forex feed (live spot bid in USD/troy oz, no auth)
    const val SWISSQUOTE_XAU_URL = "https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAU/USD"
    const val SWISSQUOTE_XAG_URL = "https://forex-data-feed.swissquote.com/public-quotes/bboquotes/instrument/XAG/USD"
    // Daily change %: Finnhub GLD/SLV ETF quotes via existing free key (>99.9% daily correlation to spot)
    const val GLD_SYMBOL = "GLD"
    const val SLV_SYMBOL = "SLV"
    const val FINNHUB_BASE_URL = "https://finnhub.io/api/v1/quote"

    // ========== Network ==========
    const val CONNECT_TIMEOUT_MS = 15000
    const val READ_TIMEOUT_MS = 15000
    const val METALS_TIMEOUT_MS = 8000
    const val USER_AGENT = "BullionLive/1.0"

    // ========== Cache Durations ==========
    // Fresh cache: skip API call entirely
    const val CACHE_FRESH_MS = 60 * 1000L                   // 1 minute
    // Stale cache: use as fallback when API fails (15 min — financial data shouldn't be older)
    const val CACHE_STALE_MS = 15 * 60 * 1000L              // 15 minutes
    // Legacy in-memory cache (aligned with fresh TTL)
    const val CACHE_COMPAT_MS = 60 * 1000L                  // 1 minute

    // ========== Retry Configuration ==========
    const val MAX_RETRIES = 5
    const val INITIAL_BACKOFF_MS = 500L
    const val BACKOFF_MULTIPLIER = 1.5

    // ========== Rate Limiting (Finnhub: 60/min free tier) ==========
    // Budget: ~26 calls/cycle (2 crypto + 3 indices + 21 watchlist) = ~43% utilization
    // WebView fetches are cache-deduped, so effective rate stays under 50%
    const val FINNHUB_MAX_PER_MINUTE = 60
    const val FINNHUB_MAX_PER_SECOND = 30
    const val RATE_LIMIT_WINDOW_MS = 60 * 1000L              // 1 minute
    const val RATE_LIMIT_BACKOFF_MS = 60_000L                // 1 minute cooldown on 429
    const val DEDUP_WINDOW_MS = 5000L                        // 5 second deduplication

    // ========== Alarm / Polling ==========
    const val FETCH_INTERVAL_MS = 60 * 1000L                 // 60 seconds
    const val FETCH_STOCK_STAGGER_MS = 500L                  // stagger between stock fetches (safe vs 30/sec burst limit)

    // ========== Broadcast Actions ==========
    const val ACTION_CACHE_UPDATED = "com.bullionlive.CACHE_UPDATED"
    const val ACTION_FETCH_DATA = "com.bullionlive.ACTION_FETCH_DATA"
}
