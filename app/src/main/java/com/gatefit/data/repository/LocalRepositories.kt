package com.gatefit.data.repository

import com.gatefit.data.dto.GatefitJson
import com.gatefit.data.local.AssetPaths
import com.gatefit.data.local.BundledData
import com.gatefit.data.local.KeyValueStore
import com.gatefit.data.local.SavedPlacesLocalSource
import com.gatefit.data.local.SearchText
import com.gatefit.data.local.TextAssetSource
import com.gatefit.data.local.VehicleProfileRecord
import com.gatefit.data.local.toDomain
import com.gatefit.data.local.toRecord
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.AppSettings
import com.gatefit.domain.repository.SavedPlacesRepository
import com.gatefit.domain.repository.SettingsRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import com.gatefit.domain.repository.VehicleSpecRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class LocalVehicleProfileRepository(
    private val store: KeyValueStore,
    private val io: CoroutineDispatcher,
) : VehicleProfileRepository {

    private val state = MutableStateFlow(load())
    override val profile: StateFlow<VehicleProfile?> = state.asStateFlow()

    override suspend fun save(profile: VehicleProfile) {
        val json = GatefitJson.encodeToString(VehicleProfileRecord.serializer(), profile.toRecord())
        withContext(io) { store.putString(KEY, json) }
        state.value = profile
    }

    override suspend fun clear() {
        withContext(io) { store.putString(KEY, null) }
        state.value = null
    }

    // 손상된 저장값은 프로필 없음으로 처리해 사용자가 다시 입력하도록 유도
    private fun load(): VehicleProfile? {
        val json = store.getString(KEY) ?: return null
        return try {
            GatefitJson.decodeFromString(VehicleProfileRecord.serializer(), json).toDomain()
        } catch (_: SerializationException) {
            null
        }
    }

    private companion object {
        const val KEY = "vehicle_profile"
    }
}

class LocalSettingsRepository(
    private val store: KeyValueStore,
    private val io: CoroutineDispatcher,
) : SettingsRepository {

    @Serializable
    private data class SettingsRecord(
        val onboardingCompleted: Boolean = false,
        val demoDataEnabled: Boolean = false,
    )

    private val state = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    private val writeLock = Mutex()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        writeLock.withLock {
            val updated = transform(state.value)
            val record = SettingsRecord(updated.onboardingCompleted, updated.demoDataEnabled)
            val json = GatefitJson.encodeToString(SettingsRecord.serializer(), record)
            withContext(io) { store.putString(KEY, json) }
            state.value = updated
        }
    }

    private fun load(): AppSettings {
        val json = store.getString(KEY) ?: return AppSettings()
        return try {
            val record = GatefitJson.decodeFromString(SettingsRecord.serializer(), json)
            AppSettings(record.onboardingCompleted, record.demoDataEnabled)
        } catch (_: SerializationException) {
            AppSettings()
        }
    }

    private companion object {
        const val KEY = "app_settings"
    }
}

class LocalSavedPlacesRepository(
    private val local: SavedPlacesLocalSource,
    private val nowEpochMs: () -> Long,
    private val io: CoroutineDispatcher,
) : SavedPlacesRepository {

    private val favorites = MutableStateFlow<List<String>?>(null)
    private val recents = MutableStateFlow<List<String>?>(null)

    override val favoriteIds: Flow<List<String>> = flow {
        if (favorites.value == null) reloadFavorites()
        emitAll(favorites.filterNotNull())
    }

    override val recentIds: Flow<List<String>> = flow {
        if (recents.value == null) reloadRecents()
        emitAll(recents.filterNotNull())
    }

    override suspend fun setFavorite(facilityId: String, favorite: Boolean) {
        withContext(io) { local.setFavorite(facilityId, favorite, nowEpochMs()) }
        reloadFavorites()
    }

    override suspend fun recordRecent(facilityId: String) {
        withContext(io) { local.recordRecent(facilityId, nowEpochMs(), MAX_RECENTS) }
        reloadRecents()
    }

    override suspend fun clearRecents() {
        withContext(io) { local.clearRecents() }
        reloadRecents()
    }

    private suspend fun reloadFavorites() {
        favorites.value = withContext(io) { local.favoriteIds() }
    }

    private suspend fun reloadRecents() {
        recents.value = withContext(io) { local.recentIds() }
    }

    companion object {
        const val MAX_RECENTS = 20
    }
}

class BundledVehicleSpecRepository(
    private val assets: TextAssetSource,
    private val io: CoroutineDispatcher,
) : VehicleSpecRepository {

    private val loadLock = Mutex()
    private var cached: List<VehicleModelSpec>? = null

    override suspend fun search(text: String): List<VehicleModelSpec> {
        val tokens = SearchText.tokens(text)
        return load().filter { spec ->
            val searchable = SearchText.normalize(
                listOfNotNull(spec.manufacturer, spec.modelName, spec.trimName).joinToString(" ")
            )
            tokens.all { it in searchable }
        }
    }

    private suspend fun load(): List<VehicleModelSpec> = loadLock.withLock {
        cached ?: withContext(io) {
            BundledData.parseVehicleSpecs(assets.read(AssetPaths.VEHICLE_SPECS))
                // 출처가 확인되지 않은 제원은 선택 목록에 노출하지 않음
                .filter { it.hasSource }
                .sortedWith(compareBy({ it.modelName }, { it.dimensions.heightMm }))
        }.also { cached = it }
    }
}
