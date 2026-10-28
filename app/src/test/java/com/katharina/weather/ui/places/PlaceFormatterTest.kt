package com.katharina.weather.ui.places

import com.katharina.weather.ui.format.formatPlaceSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceFormatterTest {

    @Test
    fun testFormatPlaceSubtitleStripsUrbanDistrict() {
        assertEquals("Munich, Bavaria", formatPlaceSubtitle("Munich, Bavaria"))
        assertEquals("Munich, Bavaria", formatPlaceSubtitle("Munich, Urban District, Bavaria"))
        assertEquals("Frankfurt am Main, Hesse", formatPlaceSubtitle("Frankfurt am Main, Urban District, Hesse"))
        assertEquals("Frankfurt am Main, Hesse", formatPlaceSubtitle("Frankfurt am Main, urban district, Hesse"))
        assertEquals("Bavaria", formatPlaceSubtitle("Urban District, Bavaria"))
    }

    @Test
    fun testFormatPlaceSubtitleNullAndEmpty() {
        assertEquals("", formatPlaceSubtitle(null))
        assertEquals("", formatPlaceSubtitle(""))
        assertEquals("", formatPlaceSubtitle("   "))
    }
}
