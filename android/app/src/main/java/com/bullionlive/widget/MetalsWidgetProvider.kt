package com.bullionlive.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.bullionlive.R
import com.bullionlive.data.FinnhubApi
import com.bullionlive.data.GoldPriceApi
import com.bullionlive.data.PersistentCacheManager
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * MetalsWidgetProvider - Android home screen widget displaying 4 assets
 *
 * DISPLAYS:
 * - Gold (XAU): Price from GoldPriceApi, no decimals
 * - Silver (XAG): Price from GoldPriceApi, 2 decimals
 * - Bitcoin (BTC): Price from FinnhubApi, no decimals
 * - Ethereum (ETH): Price from FinnhubApi, no decimals
 *
 * LAYOUT: widget_combined.xml (2x2 grid)
 * UPDATE INTERVAL: Configured in widget_combined_info.xml
 *
 * DATA FLOW:
 * 1. onUpdate() called by system at configured interval
 * 2. Show loading state ("..." for all prices)
 * 3. Fetch metals and crypto in background thread using goAsync()
 * 4. Update RemoteViews with prices and color-coded changes
 * 5. On error, display "Error" text
 *
 * COLORS:
 * - Green (#4CAF50): Positive change
 * - Red (#F44336): Negative change
 * - Gray (#888888): No change
 *
 * THREADING: Uses goAsync() + executor to prevent process death during network calls.
 * The PendingResult keeps the process alive until finish() is called.
 *
 * CACHING: Relies on GoldPriceApi/FinnhubApi internal caches (5-min TTL).
 * Multiple widgets share same cache, reducing API calls.
 */
class MetalsWidgetProvider : AppWidgetProvider() {

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        const val ACTION_CLICK_METALS = "com.bullionlive.widget.MetalsWidgetProvider.CLICK_METALS"
        const val ACTION_CLICK_CRYPTO = "com.bullionlive.widget.MetalsWidgetProvider.CLICK_CRYPTO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Handle widget clicks via broadcast
        if (ACTION_CLICK_METALS == action || ACTION_CLICK_CRYPTO == action) {
            val tab = if (ACTION_CLICK_METALS == action) "metals" else "crypto"
            val activityIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                putExtra("tab", tab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(activityIntent)
            return
        }

        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == action) {
            // For widget updates, use goAsync() to keep process alive during network calls
            val pendingResult = goAsync()
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: intArrayOf()

            executor.execute {
                try {
                    for (widgetId in ids) {
                        updateWidgetSync(context, mgr, widgetId)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            // For other actions (enabled, deleted, etc.), use default handling on main thread
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // This is called when widget is first added - use async pattern
        for (widgetId in appWidgetIds) {
            updateWidgetAsync(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidgetAsync(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        // Show loading state immediately
        val views = RemoteViews(context.packageName, R.layout.widget_combined)
        views.setTextViewText(R.id.gold_price, "...")
        views.setTextViewText(R.id.silver_price, "...")
        views.setTextViewText(R.id.btc_price, "...")
        views.setTextViewText(R.id.eth_price, "...")
        views.setTextViewText(R.id.gold_change, "")
        views.setTextViewText(R.id.silver_change, "")
        views.setTextViewText(R.id.btc_change, "")
        views.setTextViewText(R.id.eth_change, "")
        mgr.updateAppWidget(widgetId, views)

        executor.execute {
            fetchAndUpdateWidget(context, mgr, widgetId)
        }
    }

    private fun updateWidgetSync(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        // Skip loading state — FetchService already populated the cache,
        // so fetchAndUpdateWidget will read it instantly. Showing "..."
        // first causes a visible blank flash on every 60s update cycle.
        fetchAndUpdateWidget(context, mgr, widgetId)
    }

    private fun fetchAndUpdateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        try {
            // Use persistent cache manager for resilience against process death
            val cacheManager = PersistentCacheManager(context.applicationContext)

            val metalsResult = GoldPriceApi().fetchMetalsWithPersistence(cacheManager)
            var metalsData = metalsResult.getOrNull()
            
            // FALLBACK: If fetch failed, use cached data only if within 2-hour limit
            if (metalsData == null && cacheManager.isMetalsCacheUsable()) {
                metalsData = cacheManager.getCachedMetals()
                if (metalsData != null) {
                    val age = cacheManager.getCacheAgeSeconds(cacheManager.getMetalsTimestamp())
                    android.util.Log.w("Widget", "Using fallback metals cache (${age}s old)")
                }
            }

            val cryptoResult = FinnhubApi().fetchCryptoWithPersistence(cacheManager)
            var cryptoData = cryptoResult.getOrNull()
            
            // FALLBACK: Same for crypto - only if within 2-hour limit
            if (cryptoData == null && cacheManager.isCryptoCacheUsable()) {
                cryptoData = cacheManager.getCachedCrypto()
                if (cryptoData != null) {
                    val age = cacheManager.getCacheAgeSeconds(cacheManager.getCryptoTimestamp())
                    android.util.Log.w("Widget", "Using fallback crypto cache (${age}s old)")
                }
            }

            val updatedViews = RemoteViews(context.packageName, R.layout.widget_combined)
            val fmt = NumberFormat.getCurrencyInstance(Locale.US)

            // Check if data is stale (> 5 min old) for visual indicator
            val metalsFresh = cacheManager.isMetalsCacheFresh()
            val cryptoFresh = cacheManager.isCryptoCacheFresh()

            if (metalsData != null) {
                fmt.maximumFractionDigits = 0
                updatedViews.setTextViewText(R.id.gold_price, fmt.format(metalsData.goldPrice))
                setChangeText(updatedViews, R.id.gold_change, metalsData.goldChangePercent, metalsFresh)

                fmt.maximumFractionDigits = 2
                updatedViews.setTextViewText(R.id.silver_price, fmt.format(metalsData.silverPrice))
                setChangeText(updatedViews, R.id.silver_change, metalsData.silverChangePercent, metalsFresh)
            } else {
                // Only show "---" if absolutely no data available (first install, never succeeded)
                updatedViews.setTextViewText(R.id.gold_price, "---")
                updatedViews.setTextViewText(R.id.silver_price, "---")
                updatedViews.setTextViewText(R.id.gold_change, "")
                updatedViews.setTextViewText(R.id.silver_change, "")
            }

            if (cryptoData != null) {
                fmt.maximumFractionDigits = 0
                updatedViews.setTextViewText(R.id.btc_price, fmt.format(cryptoData.btcPrice))
                setChangeText(updatedViews, R.id.btc_change, cryptoData.btcChangePercent, cryptoFresh)

                updatedViews.setTextViewText(R.id.eth_price, fmt.format(cryptoData.ethPrice))
                setChangeText(updatedViews, R.id.eth_change, cryptoData.ethChangePercent, cryptoFresh)
            } else {
                updatedViews.setTextViewText(R.id.btc_price, "---")
                updatedViews.setTextViewText(R.id.eth_price, "---")
                updatedViews.setTextViewText(R.id.btc_change, "")
                updatedViews.setTextViewText(R.id.eth_change, "")
            }

            // FIX 2: Direct Activity intent with simpler approach - set on ALL views
            val metalsIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("tab", "metals")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val metalsPendingIntent = android.app.PendingIntent.getActivity(
                context, 
                widgetId * 100 + 0, // More unique request codes
                metalsIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            // Set on container AND all child views
            updatedViews.setOnClickPendingIntent(R.id.metals_container, metalsPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.gold_price, metalsPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.gold_change, metalsPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.gold_label, metalsPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.silver_price, metalsPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.silver_change, metalsPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.silver_label, metalsPendingIntent)

            val cryptoIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("tab", "crypto")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val cryptoPendingIntent = android.app.PendingIntent.getActivity(
                context,
                widgetId * 100 + 1, // More unique request codes
                cryptoIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            // Set on container AND all child views
            updatedViews.setOnClickPendingIntent(R.id.crypto_container, cryptoPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.btc_price, cryptoPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.btc_change, cryptoPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.btc_label, cryptoPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.eth_price, cryptoPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.eth_change, cryptoPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.eth_label, cryptoPendingIntent)

            mgr.updateAppWidget(widgetId, updatedViews)
        } catch (e: Exception) {
            android.util.Log.e("Widget", "Widget update failed", e)
            // Even on exception, try to show cached data if within 2-hour limit
            try {
                val cacheManager = PersistentCacheManager(context.applicationContext)
                val metalsData = if (cacheManager.isMetalsCacheUsable()) cacheManager.getCachedMetals() else null
                val cryptoData = if (cacheManager.isCryptoCacheUsable()) cacheManager.getCachedCrypto() else null
                
                if (metalsData != null || cryptoData != null) {
                    val fmt = NumberFormat.getCurrencyInstance(Locale.US)
                    val errorViews = RemoteViews(context.packageName, R.layout.widget_combined)
                    
                    if (metalsData != null) {
                        fmt.maximumFractionDigits = 0
                        errorViews.setTextViewText(R.id.gold_price, fmt.format(metalsData.goldPrice))
                        setChangeText(errorViews, R.id.gold_change, metalsData.goldChangePercent, false)
                        fmt.maximumFractionDigits = 2
                        errorViews.setTextViewText(R.id.silver_price, fmt.format(metalsData.silverPrice))
                        setChangeText(errorViews, R.id.silver_change, metalsData.silverChangePercent, false)
                    } else {
                        errorViews.setTextViewText(R.id.gold_price, "---")
                        errorViews.setTextViewText(R.id.silver_price, "---")
                    }
                    
                    if (cryptoData != null) {
                        fmt.maximumFractionDigits = 0
                        errorViews.setTextViewText(R.id.btc_price, fmt.format(cryptoData.btcPrice))
                        setChangeText(errorViews, R.id.btc_change, cryptoData.btcChangePercent, false)
                        errorViews.setTextViewText(R.id.eth_price, fmt.format(cryptoData.ethPrice))
                        setChangeText(errorViews, R.id.eth_change, cryptoData.ethChangePercent, false)
                    } else {
                        errorViews.setTextViewText(R.id.btc_price, "---")
                        errorViews.setTextViewText(R.id.eth_price, "---")
                    }
                    
                    // Add click intents for error fallback views too
                    val metalsIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra("tab", "metals")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val metalsPendingIntent = android.app.PendingIntent.getActivity(
                        context, widgetId * 100 + 2, metalsIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    errorViews.setOnClickPendingIntent(R.id.metals_container, metalsPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.gold_price, metalsPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.gold_change, metalsPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.silver_price, metalsPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.silver_change, metalsPendingIntent)

                    val cryptoIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra("tab", "crypto")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val cryptoPendingIntent = android.app.PendingIntent.getActivity(
                        context, widgetId * 100 + 3, cryptoIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    errorViews.setOnClickPendingIntent(R.id.crypto_container, cryptoPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.btc_price, cryptoPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.btc_change, cryptoPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.eth_price, cryptoPendingIntent)
                    errorViews.setOnClickPendingIntent(R.id.eth_change, cryptoPendingIntent)
                    
                    mgr.updateAppWidget(widgetId, errorViews)
                    android.util.Log.w("Widget", "Showing cached data after exception")
                    return
                }
            } catch (e2: Exception) {
                android.util.Log.e("Widget", "Even cache fallback failed", e2)
            }
            
            // Absolute last resort - no usable cached data
            val errorViews = RemoteViews(context.packageName, R.layout.widget_combined)
            errorViews.setTextViewText(R.id.gold_price, "---")
            errorViews.setTextViewText(R.id.silver_price, "---")
            errorViews.setTextViewText(R.id.btc_price, "---")
            errorViews.setTextViewText(R.id.eth_price, "---")
            mgr.updateAppWidget(widgetId, errorViews)
        }
    }

    private fun setChangeText(views: RemoteViews, viewId: Int, percent: Double, isFresh: Boolean = true) {
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        
        // If data is stale, show in gray to indicate it may be outdated
        val color = if (!isFresh) {
            Color.parseColor("#666666")  // Gray for stale data
        } else {
            when {
                percent > 0 -> Color.parseColor("#4CAF50")  // Green
                percent < 0 -> Color.parseColor("#F44336")  // Red
                else -> Color.parseColor("#888888")          // Gray
            }
        }
        views.setTextViewText(viewId, text)
        views.setTextColor(viewId, color)
    }
}
