package com.katharina.weather.data.repository

import com.katharina.weather.data.local.PlaceDao
import com.katharina.weather.data.local.PlaceEntity
import com.katharina.weather.data.local.SelectionDataStore
import com.katharina.weather.domain.model.PlaceSearchResult
import com.katharina.weather.domain.model.PlaceSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaceRepositoryImplTest {

    private lateinit var dao: FakePlaceDao
    private lateinit var repository: PlaceRepositoryImpl

    @Before
    fun setUp() {
        dao = FakePlaceDao()
        repository = PlaceRepositoryImpl(dao, FakeSelectionDataStore())
    }

    @Test
    fun testSaveIsIdempotentByGeonamesId() = runBlocking {
        val result = PlaceSearchResult(
            geonamesId = 2922582L,
            name = "Garching",
            region = "Munich, Bavaria",
            country = "Germany",
            latitude = 48.248,
            longitude = 11.651
        )

        val firstSave = repository.save(result)
        val secondSave = repository.save(result)

        assertEquals(firstSave.id, secondSave.id)
        assertEquals(1, dao.entities.size)
    }

    @Test
    fun testDeleteSelectedReselectsRemaining() = runBlocking {
        val r1 = PlaceSearchResult(1L, "City1", null, null, 10.0, 10.0)
        val r2 = PlaceSearchResult(2L, "City2", null, null, 20.0, 20.0)

        val p1 = repository.save(r1)
        val p2 = repository.save(r2)

        repository.select(PlaceSelection.Saved(p1.id))
        assertEquals(PlaceSelection.Saved(p1.id), repository.selection.first())

        repository.delete(p1.id)

        val newSelection = repository.selection.first()
        assertTrue(newSelection is PlaceSelection.Saved && newSelection.id == p2.id)
    }
}

class FakePlaceDao : PlaceDao {
    val entities = mutableListOf<PlaceEntity>()
    private val flow = MutableStateFlow<List<PlaceEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PlaceEntity>> = flow

    override suspend fun findByGeonamesId(geonamesId: Long): PlaceEntity? {
        return entities.find { it.geonamesId == geonamesId }
    }

    override suspend fun findById(id: Long): PlaceEntity? {
        return entities.find { it.id == id }
    }

    override suspend fun insert(entity: PlaceEntity): Long {
        val existing = findByGeonamesId(entity.geonamesId)
        if (existing != null) return -1L
        val newEntity = entity.copy(id = nextId++)
        entities.add(newEntity)
        flow.value = entities.toList()
        return newEntity.id
    }

    override suspend fun delete(id: Long): Int {
        val removed = entities.removeAll { it.id == id }
        flow.value = entities.toList()
        return if (removed) 1 else 0
    }
}

class FakeSelectionDataStore : SelectionDataStore(
    dataStore = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        override val data: Flow<androidx.datastore.preferences.core.Preferences> = MutableStateFlow(
            androidx.datastore.preferences.core.emptyPreferences()
        )
        override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
            return androidx.datastore.preferences.core.emptyPreferences()
        }
    }
) {
    private val state = MutableStateFlow<PlaceSelection>(PlaceSelection.None)
    override val selection: Flow<PlaceSelection> = state

    override suspend fun saveSelection(selection: PlaceSelection) {
        state.value = selection
    }
}
