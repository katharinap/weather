package com.katharina.weather.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.katharina.weather.domain.model.Coordinates
import com.katharina.weather.domain.model.LocationPermissionException
import com.katharina.weather.domain.model.LocationUnavailableException
import com.katharina.weather.domain.repository.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

class LocationProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocationProvider {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Result<Coordinates> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w("LocationProviderImpl", "Permission ACCESS_COARSE_LOCATION not granted")
            return Result.failure(LocationPermissionException())
        }

        return try {
            val cts = CancellationTokenSource()

            // 1. Try battery-efficient balanced accuracy first (Wi-Fi & cell towers)
            val currentLocation = try {
                withTimeoutOrNull(4_000L) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).await()
                }
            } catch (e: Exception) {
                Log.e("LocationProviderImpl", "getCurrentLocation balanced accuracy threw exception", e)
                null
            }

            // 2. Fallback to active GPS update (needed on emulators or where network location is unavailable)
            val freshGpsLoc = if (currentLocation == null) {
                getFreshLocationFromLocationManager(context)
            } else null

            val mgrLoc = getLocationFromLocationManager(context)

            val lastLoc = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                Log.e("LocationProviderImpl", "lastLocation threw exception", e)
                null
            }

            val location = currentLocation ?: freshGpsLoc ?: mgrLoc ?: lastLoc

            if (location != null) {
                Log.d("LocationProviderImpl", "Successfully retrieved location: ${location.latitude}, ${location.longitude}")
                Result.success(Coordinates(location.latitude, location.longitude))
            } else {
                Log.e("LocationProviderImpl", "All location sources returned null")
                Result.failure(LocationUnavailableException())
            }
        } catch (e: Exception) {
            Log.e("LocationProviderImpl", "Outer currentLocation exception", e)
            Result.failure(LocationUnavailableException())
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocationFromLocationManager(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                }

                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                    }
                } catch (e: Exception) {
                    Log.e("LocationProviderImpl", "Error requesting location updates", e)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocationFromLocationManager(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val gpsLoc = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                Log.e("LocationProviderImpl", "Error getting last known for GPS_PROVIDER", e)
                null
            }
            if (gpsLoc != null) {
                Log.d("LocationProviderImpl", "Found location from GPS_PROVIDER: ${gpsLoc.latitude}, ${gpsLoc.longitude}")
                return gpsLoc
            }
        }

        val providers = locationManager.getProviders(true)
        Log.d("LocationProviderImpl", "LocationManager enabled providers: $providers")
        for (provider in providers) {
            if (provider == LocationManager.GPS_PROVIDER) continue
            val l = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: Exception) {
                Log.e("LocationProviderImpl", "Error getting last known for $provider", e)
                null
            }
            if (l != null) {
                Log.d("LocationProviderImpl", "Found last known location from $provider: ${l.latitude}, ${l.longitude}")
                return l
            }
        }
        return null
    }
}
