package com.katharina.weather.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindDirectionTest {

    @Test
    fun testCompassDirectionMapping() {
        assertNull(null.toCompassDirection())
        assertEquals("N", 0.toCompassDirection())
        assertEquals("N", 360.toCompassDirection())
        assertEquals("NNE", 22.toCompassDirection())
        assertEquals("NE", 45.toCompassDirection())
        assertEquals("E", 90.toCompassDirection())
        assertEquals("S", 180.toCompassDirection())
        assertEquals("W", 270.toCompassDirection())
        assertEquals("NNW", 345.toCompassDirection())
    }
}
