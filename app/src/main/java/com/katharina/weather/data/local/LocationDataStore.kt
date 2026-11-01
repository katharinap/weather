package com.katharina.weather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class LastKnownLocationFix(
    val latitude: Double,
    val longitude: Double,
    val name: String
)

open class LocationDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keyLat = doublePreferencesKey("last_location_lat")
    private val keyLon = doublePreferencesKey("last_location_lon")
    private val keyName = stringPreferencesKey("last_location_name")

    open val lastLocation: Flow<LastKnownLocationFix?> = dataStore.data.map { prefs ->
        val lat = prefs[keyLat]
        val lon = prefs[keyLon]
        val name = prefs[keyName] ?: "Current location"
        if (lat != null && lon != null) {
            LastKnownLocationFix(lat, lon, name)
        } else {
            null
        }
    }

    open suspend fun saveLastLocation(lat: Double, lon: Double, name: String) {
        dataStore.edit { prefs ->
            prefs[keyLat] = lat
            prefs[keyLon] = lon
            prefs[keyName] = name
        }
    }
}
