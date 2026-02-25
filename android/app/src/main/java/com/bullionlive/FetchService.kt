package com.bullionlive

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import android.util.Log
import com.bullionlive.data.AppConfig
import com.bullionlive.data.FinnhubApi
import com.bullionlive.data.GoldPriceApi
import com.bullionlive.data.PersistentCacheManager

class FetchService : JobIntentService() {
    companion object {
        private const val JOB_ID = 1001

        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, FetchService::class.java, JOB_ID, work)
        }
    }

    override fun onHandleWork(intent: Intent) {
        val cacheManager = PersistentCacheManager(applicationContext)

        // Force fetch fresh data bypassing the fresh TTL manually or using existing logic if 1 minute passed.
        // We'll just call the API classes which already use the updated 1-minute TTL.
        GoldPriceApi().fetchMetalsWithPersistence(cacheManager)
        FinnhubApi().fetchCryptoWithPersistence(cacheManager)

        // Fetch all watched stocks
        val symbols = cacheManager.getAllCachedStockSymbols()
        for (symbol in symbols) {
            FinnhubApi().fetchStockWithPersistence(symbol, cacheManager)
            // Stagger slightly to avoid bursting
            Thread.sleep(AppConfig.FETCH_STOCK_STAGGER_MS)
        }
        
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        // Force MetalsWidget update
        val metalsProvider = ComponentName(applicationContext, com.bullionlive.widget.MetalsWidgetProvider::class.java)
        val metalsIds = appWidgetManager.getAppWidgetIds(metalsProvider)
        if (metalsIds.isNotEmpty()) {
            val updateIntent = Intent(applicationContext, com.bullionlive.widget.MetalsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, metalsIds)
            }
            sendBroadcast(updateIntent)
        }

        // Force SingleStockWidget update
        val stockProvider = ComponentName(applicationContext, com.bullionlive.widget.SingleStockWidgetProvider::class.java)
        val stockIds = appWidgetManager.getAppWidgetIds(stockProvider)
        if (stockIds.isNotEmpty()) {
            val updateIntent = Intent(applicationContext, com.bullionlive.widget.SingleStockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, stockIds)
            }
            sendBroadcast(updateIntent)
        }
    }
}
