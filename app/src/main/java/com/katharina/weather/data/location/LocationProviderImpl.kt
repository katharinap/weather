package com.katharina.weather.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.katharina.weather.domain.model.Coordinates
import com.katharina.weather.domain.model.LocationPermissionException
import com.katharina.weather.domain.model.LocationUnavailableException
import com.katharina.weather.domain.repository.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class LocationProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocationProvider {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    override suspend fun currentLocation(): Result<Coordinates> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return Result.failure(LocationPermissionException())
        }

        return try {
            val cts = CancellationTokenSource()
            val currentLocation = withTimeoutOrNull(10_000L) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
            }

            val location = currentLocation ?: fusedLocationClient.lastLocation.await()

            if (location != null) {
                Result.success(Coordinates(location.latitude, location.longitude))
            } else {
                Result.failure(LocationUnavailableException())
            }
        } catch (_: Exception) {
            Result.failure(LocationUnavailableException())
        }
    }
}
