package com.katharina.weather.ui.home.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {

    @Test
    fun testScaleEqualMinMax() {
        val size = 100f
        assertEquals(50f, scale(10.0, 10.0, 10.0, size), 0.001f)
    }

    @Test
    fun testScaleMinAndMax() {
        val size = 100f
        // value == min -> top is size (100f) because Y increases downwards
        assertEquals(100f, scale(0.0, 0.0, 20.0, size), 0.001f)
        // value == max -> top is 0f
        assertEquals(0f, scale(20.0, 0.0, 20.0, size), 0.001f)
        // value == midpoint
        assertEquals(50f, scale(10.0, 0.0, 20.0, size), 0.001f)
    }

    @Test
    fun testScaleNegativeValues() {
        val size = 100f
        // min = -10.0, max = 10.0, value = 0.0 -> midpoint (50f)
        assertEquals(50f, scale(0.0, -10.0, 10.0, size), 0.001f)
        // min = -20.0, max = -10.0, value = -20.0 -> 100f
        assertEquals(100f, scale(-20.0, -20.0, -10.0, size), 0.001f)
        // min = -20.0, max = -10.0, value = -10.0 -> 0f
        assertEquals(0f, scale(-10.0, -20.0, -10.0, size), 0.001f)
    }

    @Test
    fun testScaleBar() {
        val size = 100f
        assertEquals(0f, scaleBar(0.0, 0.0, size), 0.001f)
        assertEquals(0f, scaleBar(0.0, 10.0, size), 0.001f)
        assertEquals(50f, scaleBar(5.0, 10.0, size), 0.001f)
        assertEquals(100f, scaleBar(10.0, 10.0, size), 0.001f)
    }
}
