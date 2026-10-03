package com.katharina.weather.ui.util

import androidx.annotation.StringRes
import com.katharina.weather.R
import com.katharina.weather.domain.model.toCompassDirection

@StringRes
fun Int?.toWindDirectionStringRes(): Int? {
    val compass = this?.toCompassDirection() ?: return null
    return compass.toWindDirectionStringRes()
}

@StringRes
fun String?.toWindDirectionStringRes(): Int {
    return when (this?.uppercase()) {
        "N" -> R.string.wind_direction_n
        "NNE" -> R.string.wind_direction_nne
        "NE" -> R.string.wind_direction_ne
        "ENE" -> R.string.wind_direction_ene
        "E" -> R.string.wind_direction_e
        "ESE" -> R.string.wind_direction_ese
        "SE" -> R.string.wind_direction_se
        "SSE" -> R.string.wind_direction_sse
        "S" -> R.string.wind_direction_s
        "SSW" -> R.string.wind_direction_ssw
        "SW" -> R.string.wind_direction_sw
        "WSW" -> R.string.wind_direction_wsw
        "W" -> R.string.wind_direction_w
        "WNW" -> R.string.wind_direction_wnw
        "NW" -> R.string.wind_direction_nw
        "NNW" -> R.string.wind_direction_nnw
        else -> R.string.wind_direction_n
    }
}
