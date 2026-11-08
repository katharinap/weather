package com.katharina.weather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

open class NotificationDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keyAlertsEnabled = booleanPreferencesKey("alert_notifications_enabled")

    open val alertNotificationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keyAlertsEnabled] ?: false
    }

    open suspend fun setAlertNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[keyAlertsEnabled] = enabled
        }
    }
}
