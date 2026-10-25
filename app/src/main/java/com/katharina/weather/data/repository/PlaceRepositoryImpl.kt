package com.katharina.weather.data.repository

import com.katharina.weather.data.local.PlaceDao
import com.katharina.weather.data.local.PlaceEntity
import com.katharina.weather.data.local.SelectionDataStore
import com.katharina.weather.data.remote.mapper.toPlace
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import com.katharina.weather.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaceRepositoryImpl @Inject constructor(
    private val dao: PlaceDao,
    private val selectionDataStore: SelectionDataStore
) : PlaceRepository {

    override val savedPlaces: Flow<List<Place>> = dao.observeAll().map { entities ->
        entities.map { it.toPlace() }
    }

    override val selection: Flow<PlaceSelection> = selectionDataStore.selection.map { selection ->
        when (selection) {
            is PlaceSelection.Saved -> {
                val exists = dao.findById(selection.id)
                if (exists != null) selection else PlaceSelection.None
            }
            else -> selection
        }
    }

    override suspend fun save(result: PlaceSearchResult): Place {
        val existing = dao.findByGeonamesId(result.geonamesId)
        if (existing != null) {
            return existing.toPlace()
        }
        val entity = PlaceEntity(
            geonamesId = result.geonamesId,
            name = result.name,
            region = result.region,
            country = result.country,
            latitude = result.latitude,
            longitude = result.longitude,
            createdAt = System.currentTimeMillis()
        )
        val newId = dao.insert(entity)
        val savedEntity = dao.findById(newId) ?: entity.copy(id = newId)
        return savedEntity.toPlace()
    }

    override suspend fun delete(id: Long) {
        val currentSelection = selection.first()
        dao.delete(id)
        if (currentSelection is PlaceSelection.Saved && currentSelection.id == id) {
            val remaining = savedPlaces.first()
            if (remaining.isNotEmpty()) {
                select(PlaceSelection.Saved(remaining.first().id))
            } else {
                select(PlaceSelection.None)
            }
        }
    }

    override suspend fun select(selection: PlaceSelection) {
        selectionDataStore.saveSelection(selection)
    }
}
