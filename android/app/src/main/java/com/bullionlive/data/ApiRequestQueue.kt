package com.bullionlive.data

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ApiRequestQueue - Manages API request rate limiting and queuing
 *
 * PURPOSE:
 * - Prevents rate limit violations (Finnhub: 60 calls/minute)
 * - Coordinates requests from multiple sources (WebView, Widgets)
 * - Implements request deduplication
 * - Tracks in-flight requests to prevent duplicates
 *
 * RATE LIMITS:
 * - Finnhub: 60 calls/minute, 30 calls/second hard limit
 * - Swissquote/Yahoo: No documented limit (generous)
 *
 * USAGE:
 * - Call enqueue() before making API request
 * - Call dequeue() after request completes (success or failure)
 * - Check canMakeRequest() before making request
 */
class ApiRequestQueue private constructor() {
    companion object {
        private const val TAG = "ApiRequestQueue"
        
        // Finnhub rate limits (from AppConfig)
        private val FINNHUB_MAX_PER_MINUTE get() = AppConfig.FINNHUB_MAX_PER_MINUTE
        private val FINNHUB_MAX_PER_SECOND get() = AppConfig.FINNHUB_MAX_PER_SECOND
        private val WINDOW_SIZE_MS get() = AppConfig.RATE_LIMIT_WINDOW_MS
        
        @Volatile
        private var instance: ApiRequestQueue? = null
        
        @JvmStatic
        fun getInstance(): ApiRequestQueue {
            return instance ?: synchronized(this) {
                instance ?: ApiRequestQueue().also { instance = it }
            }
        }
    }
    
    // Track request timestamps for rate limiting
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private val lock = ReentrantLock()
    
    // Track in-flight requests by key (for deduplication)
    private val inFlightRequests = mutableMapOf<String, Long>()
    private val inFlightLock = ReentrantLock()
    
    // Statistics
    private val totalRequests = AtomicInteger(0)
    private val rateLimitedRequests = AtomicInteger(0)
    
    /**
     * Check if a request can be made now without violating rate limits
     * @param apiType "finnhub" or "goldprice" (only finnhub has strict limits)
     * @return true if request can proceed, false if should wait
     */
    fun canMakeRequest(apiType: String = "finnhub"): Boolean {
        if (apiType != "finnhub") {
            // Swissquote/Yahoo have no documented limits
            return true
        }
        
        return lock.withLock {
            val now = System.currentTimeMillis()
            
            // Remove timestamps older than 1 minute
            while (requestTimestamps.isNotEmpty()) {
                val oldest = requestTimestamps.peek() ?: break
                if (now - oldest > WINDOW_SIZE_MS) {
                    requestTimestamps.poll()
                } else {
                    break
                }
            }

            // Check per-minute limit
            if (requestTimestamps.size >= FINNHUB_MAX_PER_MINUTE) {
                val oldestTimestamp = requestTimestamps.peek() ?: return true
                val waitTime = (oldestTimestamp + WINDOW_SIZE_MS) - now
                if (waitTime > 0) {
                    Log.w(TAG, "Rate limit: ${requestTimestamps.size}/$FINNHUB_MAX_PER_MINUTE requests in last minute. Wait ${waitTime}ms")
                    return false
                }
            }
            
            // Check per-second limit (last 1 second)
            val recentRequests = requestTimestamps.count { now - it < 1000 }
            if (recentRequests >= FINNHUB_MAX_PER_SECOND) {
                Log.w(TAG, "Rate limit: $recentRequests/$FINNHUB_MAX_PER_SECOND requests in last second")
                return false
            }
            
            true
        }
    }
    
    /**
     * Enqueue a request (call before making API request)
     * @param requestKey Unique identifier for request (e.g., "finnhub:BTCUSDT")
     * @param apiType "finnhub" or "goldprice"
     * @return true if request can proceed, false if should wait or duplicate
     */
    fun enqueue(requestKey: String, apiType: String = "finnhub"): Boolean {
        // Check for duplicate in-flight request
        inFlightLock.withLock {
            val existing = inFlightRequests[requestKey]
            if (existing != null) {
                val age = System.currentTimeMillis() - existing
                if (age < AppConfig.DEDUP_WINDOW_MS) {
                    return false
                } else {
                    // Request is old, remove it
                    inFlightRequests.remove(requestKey)
                }
            }
            
            // Mark as in-flight
            inFlightRequests[requestKey] = System.currentTimeMillis()
        }
        
        // Check rate limit
        if (!canMakeRequest(apiType)) {
            inFlightLock.withLock {
                inFlightRequests.remove(requestKey)
            }
            rateLimitedRequests.incrementAndGet()
            return false
        }
        
        // Record request timestamp
        lock.withLock {
            requestTimestamps.offer(System.currentTimeMillis())
            totalRequests.incrementAndGet()
        }
        
        return true
    }
    
    /**
     * Dequeue a request (call after request completes)
     * @param requestKey Unique identifier for request
     */
    fun dequeue(requestKey: String) {
        inFlightLock.withLock {
            inFlightRequests.remove(requestKey)
        }
    }
    
    /**
     * Get wait time in milliseconds before next request can be made
     * @param apiType "finnhub" or "goldprice"
     * @return milliseconds to wait, or 0 if can proceed immediately
     */
    fun getWaitTime(apiType: String = "finnhub"): Long {
        if (apiType != "finnhub") {
            return 0
        }
        
        return lock.withLock {
            val now = System.currentTimeMillis()
            
            // Remove old timestamps
            while (requestTimestamps.isNotEmpty()) {
                val oldest = requestTimestamps.peek() ?: break
                if (now - oldest > WINDOW_SIZE_MS) {
                    requestTimestamps.poll()
                } else {
                    break
                }
            }

            if (requestTimestamps.size >= FINNHUB_MAX_PER_MINUTE) {
                val oldestTimestamp = requestTimestamps.peek() ?: return 0
                val waitTime = (oldestTimestamp + WINDOW_SIZE_MS) - now
                return maxOf(0, waitTime)
            }
            
            0
        }
    }
    
    /**
     * Get statistics for monitoring
     */
    fun getStats(): QueueStats {
        return lock.withLock {
            val now = System.currentTimeMillis()
            val requestsLastMinute = requestTimestamps.count { now - it < WINDOW_SIZE_MS }
            val requestsLastSecond = requestTimestamps.count { now - it < 1000 }
            
            QueueStats(
                totalRequests = totalRequests.get(),
                rateLimitedRequests = rateLimitedRequests.get(),
                requestsLastMinute = requestsLastMinute,
                requestsLastSecond = requestsLastSecond,
                inFlightCount = inFlightLock.withLock { inFlightRequests.size }
            )
        }
    }
    
    /**
     * Clear all tracking (for testing)
     */
    fun clear() {
        lock.withLock {
            requestTimestamps.clear()
        }
        inFlightLock.withLock {
            inFlightRequests.clear()
        }
        totalRequests.set(0)
        rateLimitedRequests.set(0)
    }
    
    data class QueueStats(
        val totalRequests: Int,
        val rateLimitedRequests: Int,
        val requestsLastMinute: Int,
        val requestsLastSecond: Int,
        val inFlightCount: Int
    )
}
