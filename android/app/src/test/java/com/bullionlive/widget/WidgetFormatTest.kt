package com.bullionlive.widget

import org.junit.Test
import org.junit.Assert.*
import java.text.NumberFormat
import java.util.Locale

/**
 * Widget formatting tests for price display and color coding
 */
class WidgetFormatTest {

    private val fmt = NumberFormat.getCurrencyInstance(Locale.US)

    @Test
    fun gold_formatWithNoDecimals() {
        fmt.maximumFractionDigits = 0
        assertEquals("$2,346", fmt.format(2345.67))
    }

    @Test
    fun gold_formatLargeNumber() {
        fmt.maximumFractionDigits = 0
        assertEquals("$4,340", fmt.format(4340.105))
    }

    @Test
    fun silver_formatWithTwoDecimals() {
        fmt.maximumFractionDigits = 2
        assertEquals("$28.46", fmt.format(28.456))
    }

    @Test
    fun silver_formatSmallPrice() {
        fmt.maximumFractionDigits = 2
        assertEquals("$67.14", fmt.format(67.143))
    }

    @Test
    fun btc_formatLargeNumber() {
        fmt.maximumFractionDigits = 0
        assertEquals("$43,568", fmt.format(43567.89))
    }

    @Test
    fun btc_formatVeryLargeNumber() {
        fmt.maximumFractionDigits = 0
        assertEquals("$88,260", fmt.format(88259.69))
    }

    @Test
    fun eth_formatMediumNumber() {
        fmt.maximumFractionDigits = 0
        assertEquals("$2,980", fmt.format(2979.99))
    }

    @Test
    fun changeText_positiveValue() {
        val percent = 1.23
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        assertEquals("+1.23%", text)
    }

    @Test
    fun changeText_negativeValue() {
        val percent = -2.45
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        assertEquals("-2.45%", text)
    }

    @Test
    fun changeText_zeroValue() {
        val percent = 0.0
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        assertEquals("+0.00%", text)
    }

    @Test
    fun changeText_smallPositive() {
        val percent = 0.01
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        assertEquals("+0.01%", text)
    }

    @Test
    fun changeText_smallNegative() {
        val percent = -0.01
        val sign = if (percent >= 0) "+" else ""
        val text = String.format("%s%.2f%%", sign, percent)
        assertEquals("-0.01%", text)
    }

    @Test
    fun color_positiveIsGreen() {
        val percent = 1.5
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#4CAF50", color)
    }

    @Test
    fun color_negativeIsRed() {
        val percent = -1.5
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#F44336", color)
    }

    @Test
    fun color_zeroIsGray() {
        val percent = 0.0
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#888888", color)
    }

    @Test
    fun color_verySmallPositiveIsGreen() {
        val percent = 0.001
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#4CAF50", color)
    }

    @Test
    fun color_verySmallNegativeIsRed() {
        val percent = -0.001
        val color = when {
            percent > 0 -> "#4CAF50"
            percent < 0 -> "#F44336"
            else -> "#888888"
        }
        assertEquals("#F44336", color)
    }
}
