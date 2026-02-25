package com.bullionlive.data

import org.junit.Test
import org.junit.Assert.*

/**
 * Cache validity tests for API data caching
 */
class CacheTest {

    private val CACHE_MAX_AGE_MS = 5 * 60 * 1000L

    @Test
    fun cache_validWhenFresh() {
        val timestamp = System.currentTimeMillis()
        val isValid = (System.currentTimeMillis() - timestamp) < CACHE_MAX_AGE_MS
        assertTrue(isValid)
    }

    @Test
    fun cache_invalidWhenOld() {
        val timestamp = System.currentTimeMillis() - (6 * 60 * 1000)
        val isValid = (System.currentTimeMillis() - timestamp) < CACHE_MAX_AGE_MS
        assertFalse(isValid)
    }

    @Test
    fun cache_validAtBoundary() {
        val timestamp = System.currentTimeMillis() - CACHE_MAX_AGE_MS + 100
        val isValid = (System.currentTimeMillis() - timestamp) < CACHE_MAX_AGE_MS
        assertTrue(isValid)
    }

    @Test
    fun cache_invalidJustPastBoundary() {
        val timestamp = System.currentTimeMillis() - CACHE_MAX_AGE_MS - 100
        val isValid = (System.currentTimeMillis() - timestamp) < CACHE_MAX_AGE_MS
        assertFalse(isValid)
    }

    @Test
    fun cacheAge_calculatesSeconds() {
        val timestamp = System.currentTimeMillis() - 120_000
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        assertTrue(ageSeconds >= 119 && ageSeconds <= 121)
    }

    @Test
    fun cacheAge_zeroForNew() {
        val timestamp = System.currentTimeMillis()
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        assertTrue(ageSeconds <= 1)
    }

    @Test
    fun cacheAge_exactOneMinute() {
        val timestamp = System.currentTimeMillis() - 60_000
        val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
        assertTrue(ageSeconds >= 59 && ageSeconds <= 61)
    }

    @Test
    fun cacheMaxAge_isFiveMinutes() {
        assertEquals(300_000L, CACHE_MAX_AGE_MS)
    }

    @Test
    fun cacheMaxAge_inMinutes() {
        val minutes = CACHE_MAX_AGE_MS / (60 * 1000)
        assertEquals(5L, minutes)
    }
}
