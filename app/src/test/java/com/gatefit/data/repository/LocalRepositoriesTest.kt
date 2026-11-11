package com.gatefit.data.repository

import com.gatefit.data.testing.BundledAssets
import com.gatefit.data.testing.CountingAssetSource
import com.gatefit.data.testing.InMemoryKeyValueStore
import com.gatefit.data.testing.InMemorySavedPlacesLocalSource
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.SpecSource
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRepositoriesTest {

    private val io = Dispatchers.Unconfined
    private val store = InMemoryKeyValueStore()

    private val profile = VehicleProfile(
        id = "vehicle",
        manufacturer = "현대자동차",
        modelName = "싼타페",
        trimName = "루프랙 적용",
        baseDimensions = VehicleDimensions(lengthMm = 4830, widthMm = 1900, heightMm = 1770),
        specSource = SpecSource.MANUFACTURER,
        specConfidence = SpecConfidence.VERIFIED_BY_MANUFACTURER,
        specSourceName = "현대자동차 공식 카탈로그 (싼타페)",
        specSourceUrl = "https://www.hyundai.com/contents/repn-car/catalog/santafe-catalog.pdf",
        accessories = listOf(
            VehicleAccessory("box", AccessoryType.ROOF_BOX, additionalHeightMm = 450, verified = true, updatedAtEpochMs = 5L)
        ),
        heightCorrectionMm = 20,
        measuredTotalHeightMm = 2250,
        weightKg = 2100,
        safetyMarginMm = 150,
        updatedAtEpochMs = 10L,
    )

    @Test
    fun `saved profile is restored unchanged by a new repository instance`() = runTest {
        LocalVehicleProfileRepository(store, io).save(profile)

        assertEquals(profile, LocalVehicleProfileRepository(store, io).profile.value)
    }

    @Test
    fun `stored profile contains no vehicle identifiers`() = runTest {
        LocalVehicleProfileRepository(store, io).save(profile)

        val keys = Json.parseToJsonElement(store.values.getValue("vehicle_profile")).jsonObject.keys
        assertTrue(keys.none { key -> listOf("vin", "crn", "latitude", "longitude").any { it in key.lowercase() } })
    }

    @Test
    fun `cleared profile stays cleared after restart`() = runTest {
        val repository = LocalVehicleProfileRepository(store, io)
        repository.save(profile)

        repository.clear()

        assertNull(repository.profile.value)
        assertNull(LocalVehicleProfileRepository(store, io).profile.value)
    }

    @Test
    fun `corrupted profile is treated as no profile`() {
        store.putString("vehicle_profile", "{not json")

        assertNull(LocalVehicleProfileRepository(store, io).profile.value)
    }

    @Test
    fun `profile with an unknown confidence code is not usable for analysis`() {
        store.putString("vehicle_profile", """{"id":"vehicle","heightMm":1650,"specConfidence":"TRUST_ME"}""")

        val restored = LocalVehicleProfileRepository(store, io).profile.value!!

        assertEquals(SpecConfidence.UNKNOWN, restored.specConfidence)
        assertFalse(restored.specConfidence.usableForAnalysis)
    }

    @Test
    fun `settings default to demo data off and persist updates`() = runTest {
        val repository = LocalSettingsRepository(store, io)
        assertEquals(AppSettings(onboardingCompleted = false, demoDataEnabled = false), repository.settings.value)

        repository.update { it.copy(demoDataEnabled = true) }
        repository.update { it.copy(onboardingCompleted = true) }

        val restored = LocalSettingsRepository(store, io).settings.value
        assertEquals(AppSettings(onboardingCompleted = true, demoDataEnabled = true), restored)
    }

    @Test
    fun `recent places are newest first, deduplicated and capped`() = runTest {
        var now = 0L
        val repository = LocalSavedPlacesRepository(InMemorySavedPlacesLocalSource(), { ++now }, io)

        repeat(LocalSavedPlacesRepository.MAX_RECENTS + 5) { repository.recordRecent("facility-$it") }
        repository.recordRecent("facility-10")

        val recents = repository.recentIds.first()
        assertEquals(LocalSavedPlacesRepository.MAX_RECENTS, recents.size)
        assertEquals("facility-10", recents.first())
        assertEquals(1, recents.count { it == "facility-10" })
        assertFalse("facility-0" in recents)

        repository.clearRecents()
        assertTrue(repository.recentIds.first().isEmpty())
    }

    @Test
    fun `favorites can be added and removed without affecting recents`() = runTest {
        var now = 0L
        val repository = LocalSavedPlacesRepository(InMemorySavedPlacesLocalSource(), { ++now }, io)

        repository.setFavorite("a", true)
        repository.setFavorite("b", true)
        repository.setFavorite("a", false)

        assertEquals(listOf("b"), repository.favoriteIds.first())
        assertTrue(repository.recentIds.first().isEmpty())
    }

    @Test
    fun `vehicle spec search matches model and trim regardless of spacing`() = runTest {
        val assets = CountingAssetSource(BundledAssets)
        val repository = BundledVehicleSpecRepository(assets, io)

        val all = repository.search("")
        val santaFe = repository.search("싼타페 루프랙")
        val lounge = repository.search("스타리아라운지")

        assertTrue(all.size > santaFe.size)
        assertTrue(santaFe.isNotEmpty() && santaFe.all { it.modelName == "싼타페" && "루프랙" in it.trimName!! })
        assertTrue(lounge.isNotEmpty() && lounge.all { it.modelName == "스타리아 라운지" })
        assertTrue(repository.search("없는차종").isEmpty())
        assertEquals(1, assets.reads.size)
    }
}
