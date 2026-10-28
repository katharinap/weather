package com.katharina.weather.data.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.katharina.weather.domain.model.Coordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

class GeocoderHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun getPlaceName(coordinates: Coordinates): String {
        if (!Geocoder.isPresent()) return "Current location"
        val geocoder = Geocoder(context, Locale.getDefault())

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        coordinates.latitude,
                        coordinates.longitude,
                        1
                    ) { addresses ->
                        val address = addresses.firstOrNull()
                        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
                        continuation.resume(name ?: "Current location")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                val address = addresses?.firstOrNull()
                val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
                name ?: "Current location"
            }
        } catch (_: Exception) {
            "Current location"
        }
    }
}
