package com.bullionlive.widget

import org.junit.Test
import org.junit.Assert.*
import java.text.NumberFormat
import java.util.Locale

class SingleStockWidgetTest {

    private val fmt = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
    }

    @Test
    fun formatPrice_twoDecimals() {
        assertEquals("$192.45", fmt.format(192.45))
    }

    @Test
    fun formatPrice_largeNumber() {
        assertEquals("$1,234.56", fmt.format(1234.56))
    }

    @Test
    fun formatPrice_smallNumber() {
        assertEquals("$5.99", fmt.format(5.99))
    }

    @Test
    fun changeText_positive() {
        val percent = 2.34
        val sign = if (percent >= 0) "+" else ""
        assertEquals("+2.34%", String.format("%s%.2f%%", sign, percent))
    }

    @Test
    fun changeText_negative() {
        val percent = -1.56
        val sign = if (percent >= 0) "+" else ""
        assertEquals("-1.56%", String.format("%s%.2f%%", sign, percent))
    }

    @Test
    fun changeText_zero() {
        val percent = 0.0
        val sign = if (percent >= 0) "+" else ""
        assertEquals("+0.00%", String.format("%s%.2f%%", sign, percent))
    }

    @Test
    fun color_positive_isGreen() {
        val percent = 1.5
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#4CAF50", color)
    }

    @Test
    fun color_negative_isRed() {
        val percent = -1.5
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#F44336", color)
    }

    @Test
    fun color_zero_isGray() {
        val percent = 0.0
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#888888", color)
    }
}
