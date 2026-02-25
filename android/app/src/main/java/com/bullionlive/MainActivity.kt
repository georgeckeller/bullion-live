package com.bullionlive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bullionlive.data.PersistentCacheManager
import org.json.JSONObject

/**
 * MainActivity - WebView wrapper with JavaScript bridge for unified cache
 *
 * The JavaScript bridge allows the WebView to access the native unified cache,
 * eliminating duplicate API calls between WebView and Widgets.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        // Security: only these tab values are allowed from Intent extras
        private val ALLOWED_TABS = setOf("metals", "crypto", "markets")
    }

    /**
     * Validates and sanitizes the tab parameter from Intent extras.
     * Prevents JS injection by only allowing known tab values.
     */
    private fun sanitizeTab(tab: String?): String? {
        return if (tab in ALLOWED_TABS) tab else null
    }
    
    private lateinit var cacheManager: PersistentCacheManager
    private lateinit var webView: WebView

    private val cacheUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Update WebView
            webView.post {
                webView.evaluateJavascript("if (typeof window.onBullionDataUpdated === 'function') { window.onBullionDataUpdated(); }", null)
            }
            // Update Widgets immediately to keep everything in sync
            context?.let { updateAllWidgets(it) }
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        
        // Update Metals Widget
        val metalsProvider = android.content.ComponentName(context, com.bullionlive.widget.MetalsWidgetProvider::class.java)
        val metalsIds = appWidgetManager.getAppWidgetIds(metalsProvider)
        if (metalsIds.isNotEmpty()) {
            val updateIntent = Intent(context, com.bullionlive.widget.MetalsWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, metalsIds)
            }
            context.sendBroadcast(updateIntent)
        }

        // Update Stock Widget
        val stockProvider = android.content.ComponentName(context, com.bullionlive.widget.SingleStockWidgetProvider::class.java)
        val stockIds = appWidgetManager.getAppWidgetIds(stockProvider)
        if (stockIds.isNotEmpty()) {
            val updateIntent = Intent(context, com.bullionlive.widget.SingleStockWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, stockIds)
            }
            context.sendBroadcast(updateIntent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tab = intent.getStringExtra("tab")

        // Initialize unified cache manager
        cacheManager = PersistentCacheManager(applicationContext)
        cacheManager.loadAllStocksFromPersistence()

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // Security: restrict WebView file access
        webView.settings.allowFileAccessFromFileURLs = false
        webView.settings.allowUniversalAccessFromFileURLs = false
        webView.settings.allowContentAccess = false

        // Add JavaScript bridge for cache access
        webView.addJavascriptInterface(CacheBridge(cacheManager), "BullionCache")

        // Set WebViewClient to wait for page load before switching tabs
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Switch to the requested tab after page loads
                val currentTab = sanitizeTab(intent.getStringExtra("tab"))
                if (currentTab != null && view != null) {
                    // Try multiple times with increasing delays
                    view.postDelayed({
                        view.evaluateJavascript("switchPage('$currentTab');", null)
                    }, 300)
                    view.postDelayed({
                        view.evaluateJavascript("if (typeof switchPage === 'function') { switchPage('$currentTab'); } ", null)
                    }, 600)
                    view.postDelayed({
                        view.evaluateJavascript("switchPage('$currentTab');", null)
                    }, 1000)
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")

        LocalBroadcastManager.getInstance(this).registerReceiver(
            cacheUpdateReceiver,
            IntentFilter("com.bullionlive.CACHE_UPDATED")
        )
        
        setupFetchAlarm()
    }
    
    private fun setupFetchAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, FetchReceiver::class.java).apply {
            action = FetchReceiver.ACTION_FETCH_DATA
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel existing
        alarmManager.cancel(pendingIntent)
        
        // Set repeating alarm for every 60 seconds
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000, // slightly delay first fetch
            60 * 1000L,
            pendingIntent
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(cacheUpdateReceiver)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle tab switch when app is already running
        val tab = sanitizeTab(intent?.getStringExtra("tab"))
        if (tab != null) {
            // Try multiple times
            webView.postDelayed({
                webView.evaluateJavascript("switchPage('$tab');", null)
            }, 100)
            webView.postDelayed({
                webView.evaluateJavascript("if (typeof switchPage === 'function') { switchPage('$tab'); }", null)
            }, 400)
            webView.postDelayed({
                webView.evaluateJavascript("switchPage('$tab');", null)
            }, 700)
        }
    }
    
    /**
     * JavaScript bridge for accessing unified cache from WebView
     */
    inner class CacheBridge(private val cache: PersistentCacheManager) {
        
        // ========== METALS ==========
        
        @JavascriptInterface
        fun getCachedMetals(): String? {
            val data = cache.getCachedMetals()
            return if (data != null) {
                JSONObject().apply {
                    put("goldPrice", data.goldPrice)
                    put("goldPreviousClose", data.goldPreviousClose)
                    put("goldChangePercent", data.goldChangePercent)
                    put("silverPrice", data.silverPrice)
                    put("silverPreviousClose", data.silverPreviousClose)
                    put("silverChangePercent", data.silverChangePercent)
                }.toString()
            } else {
                null
            }
        }
        
        @JavascriptInterface
        fun getMetalsTimestamp(): Long {
            return cache.getMetalsTimestamp()
        }
        
        @JavascriptInterface
        fun isMetalsCacheFresh(): Boolean {
            return cache.isMetalsCacheFresh()
        }
        
        @JavascriptInterface
        fun saveMetals(json: String) {
            try {
                val obj = JSONObject(json)
                val data = com.bullionlive.data.MetalsData(
                    goldPrice = obj.getDouble("goldPrice"),
                    goldPreviousClose = obj.getDouble("goldPreviousClose"),
                    goldChangePercent = obj.getDouble("goldChangePercent"),
                    silverPrice = obj.getDouble("silverPrice"),
                    silverPreviousClose = obj.getDouble("silverPreviousClose"),
                    silverChangePercent = obj.getDouble("silverChangePercent")
                )
                cache.saveMetals(data)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving metals: ${e.message}")
            }
        }
        
        // ========== CRYPTO ==========
        
        @JavascriptInterface
        fun getCachedCrypto(): String? {
            val data = cache.getCachedCrypto()
            return if (data != null) {
                JSONObject().apply {
                    put("btcPrice", data.btcPrice)
                    put("btcPrevClose", data.btcPrevClose)
                    put("btcChangePercent", data.btcChangePercent)
                    put("ethPrice", data.ethPrice)
                    put("ethPrevClose", data.ethPrevClose)
                    put("ethChangePercent", data.ethChangePercent)
                }.toString()
            } else {
                null
            }
        }
        
        @JavascriptInterface
        fun getCryptoTimestamp(): Long {
            return cache.getCryptoTimestamp()
        }
        
        @JavascriptInterface
        fun isCryptoCacheFresh(): Boolean {
            return cache.isCryptoCacheFresh()
        }
        
        @JavascriptInterface
        fun saveCrypto(json: String) {
            try {
                val obj = JSONObject(json)
                val data = com.bullionlive.data.CryptoData(
                    btcPrice = obj.getDouble("btcPrice"),
                    btcPrevClose = obj.getDouble("btcPrevClose"),
                    btcChangePercent = obj.getDouble("btcChangePercent"),
                    ethPrice = obj.getDouble("ethPrice"),
                    ethPrevClose = obj.getDouble("ethPrevClose"),
                    ethChangePercent = obj.getDouble("ethChangePercent")
                )
                cache.saveCrypto(data)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving crypto: ${e.message}")
            }
        }
        
        // ========== STOCKS ==========
        
        @JavascriptInterface
        fun getCachedStock(symbol: String): String? {
            val data = cache.getCachedStock(symbol)
            return if (data != null) {
                JSONObject().apply {
                    put("symbol", data.symbol)
                    put("price", data.price)
                    put("prevClose", data.prevClose)
                    put("changePercent", data.changePercent)
                }.toString()
            } else {
                null
            }
        }
        
        @JavascriptInterface
        fun getStockTimestamp(symbol: String): Long {
            return cache.getStockTimestamp(symbol)
        }
        
        @JavascriptInterface
        fun isStockCacheFresh(symbol: String): Boolean {
            return cache.isStockCacheFresh(symbol)
        }
        
        @JavascriptInterface
        fun saveStock(symbol: String, json: String) {
            try {
                val obj = JSONObject(json)
                val data = com.bullionlive.data.StockData(
                    symbol = symbol,
                    price = obj.getDouble("price"),
                    prevClose = obj.getDouble("prevClose"),
                    changePercent = obj.getDouble("changePercent")
                )
                cache.saveStock(symbol, data)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving stock $symbol: ${e.message}")
            }
        }
        
        @JavascriptInterface
        fun getAllCachedStockSymbols(): String {
            val symbols = cache.getAllCachedStockSymbols()
            return JSONObject().apply {
                put("symbols", org.json.JSONArray(symbols))
            }.toString()
        }
        // ========== CONFIGURATION ==========

        @JavascriptInterface
        fun getFinnhubApiKey(): String {
            return BuildConfig.FINNHUB_API_KEY
        }
    }
}
