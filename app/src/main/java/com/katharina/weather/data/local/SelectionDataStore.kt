package com.katharina.weather.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.katharina.weather.domain.model.PlaceSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

open class SelectionDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keySelection = stringPreferencesKey("selected_place")

    open val selection: Flow<PlaceSelection> = dataStore.data.map { prefs ->
        val raw = prefs[keySelection] ?: "none"
        parseSelection(raw)
    }

    open suspend fun saveSelection(selection: PlaceSelection) {
        dataStore.edit { prefs ->
            prefs[keySelection] = when (selection) {
                is PlaceSelection.None -> "none"
                is PlaceSelection.CurrentLocation -> "current"
                is PlaceSelection.Saved -> "saved:${selection.id}"
            }
        }
    }

    companion object {
        fun parseSelection(raw: String): PlaceSelection {
            return when {
                raw == "none" -> PlaceSelection.None
                raw == "current" -> PlaceSelection.CurrentLocation
                raw.startsWith("saved:") -> {
                    val id = raw.substringAfter("saved:").toLongOrNull()
                    if (id != null) PlaceSelection.Saved(id) else PlaceSelection.None
                }
                else -> PlaceSelection.None
            }
        }
    }
}
