package com.katharina.weather.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun testDistanceBetweenCoordinates() {
        // Berlin Marienfelde to Berlin Alexanderplatz (~13 km)
        val dist = distanceBetweenInKm(52.41, 13.37, 52.52, 13.41)
        assertTrue(dist > 10.0)

        // Munich Marienplatz to Munich Englischer Garten (~3 km)
        val shortDist = distanceBetweenInKm(48.137, 11.575, 48.150, 11.590)
        assertFalse(shortDist > 10.0)
    }
}
