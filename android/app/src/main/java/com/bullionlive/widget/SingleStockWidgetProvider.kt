package com.bullionlive.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.bullionlive.R
import com.bullionlive.data.FinnhubApi
import com.bullionlive.data.PersistentCacheManager
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * SingleStockWidgetProvider - Small widget displaying GOOG stock price
 *
 * DISPLAYS:
 * - GOOG (Alphabet): Price from FinnhubApi, 2 decimals
 *
 * LAYOUT: widget_single_stock.xml (1x1 grid)
 * UPDATE INTERVAL: 30 minutes (configured in widget_single_stock_info.xml)
 *
 * THREADING: Uses goAsync() + executor to prevent process death during network calls.
 * The PendingResult keeps the process alive until finish() is called.
 *
 * REUSES: FinnhubApi.fetchStock() for API calls
 */
class SingleStockWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val SYMBOL = "GOOG"
        private val executor = Executors.newSingleThreadExecutor()
        const val ACTION_CLICK_STOCK = "com.bullionlive.widget.SingleStockWidgetProvider.CLICK_STOCK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        // Handle widget clicks via broadcast
        if (ACTION_CLICK_STOCK == action) {
            val activityIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                putExtra("tab", "markets")
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
        val views = RemoteViews(context.packageName, R.layout.widget_single_stock)
        views.setTextViewText(R.id.stock_price, "...")
        views.setTextViewText(R.id.stock_change, "")
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
            val result = FinnhubApi().fetchStockWithPersistence(SYMBOL, cacheManager)
            val data = result.getOrNull()

            val updatedViews = RemoteViews(context.packageName, R.layout.widget_single_stock)
            val fmt = NumberFormat.getCurrencyInstance(Locale.US)
            fmt.maximumFractionDigits = 2

            if (data != null) {
                updatedViews.setTextViewText(R.id.stock_price, fmt.format(data.price))
                setChangeText(updatedViews, R.id.stock_change, data.changePercent)
            } else {
                updatedViews.setTextViewText(R.id.stock_price, "Error")
            }

            // FIX 2: Direct Activity intent with simpler approach
            val clickIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("tab", "markets")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val clickPendingIntent = android.app.PendingIntent.getActivity(
                context, 
                widgetId * 100 + 0, // More unique request codes
                clickIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            updatedViews.setOnClickPendingIntent(R.id.stock_container, clickPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.stock_price, clickPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.stock_change, clickPendingIntent)
            updatedViews.setOnClickPendingIntent(R.id.stock_label, clickPendingIntent)

            mgr.updateAppWidget(widgetId, updatedViews)
        } catch (e: Exception) {
            android.util.Log.e("StockWidget", "Widget update failed", e)
            val errorViews = RemoteViews(context.packageName, R.layout.widget_single_stock)
            errorViews.setTextViewText(R.id.stock_price, "Error")
            
            // Add click intent even for error state
            val clickIntent = Intent(context, com.bullionlive.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("tab", "markets")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val clickPendingIntent = android.app.PendingIntent.getActivity(
                context, widgetId * 100 + 1, clickIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            errorViews.setOnClickPendingIntent(R.id.stock_container, clickPendingIntent)
            errorViews.setOnClickPendingIntent(R.id.stock_price, clickPendingIntent)
            errorViews.setOnClickPendingIntent(R.id.stock_label, clickPendingIntent)
            
            mgr.updateAppWidget(widgetId, errorViews)
        }
    }

    private fun setChangeText(views: RemoteViews, viewId: Int, percent: Double) {
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        val color = when {
            percent > 0 -> Color.parseColor("#4CAF50")
            percent < 0 -> Color.parseColor("#F44336")
            else -> Color.parseColor("#888888")
        }
        views.setTextViewText(viewId, text)
        views.setTextColor(viewId, color)
    }
}
