package at.smiech.cyanbat.data

import at.smiech.engine.Haptics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Haptics that write down every vibration they are asked for. */
private class RecordingHaptics : Haptics {
    val vibrations = mutableListOf<Long>()

    override fun vibrate(durationMillis: Long) {
        vibrations += durationMillis
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObservedHapticsTest {

    @Test
    fun `vibrates while the setting is on`() = runTest {
        val device = RecordingHaptics()
        val haptics = ObservedHaptics(device, FakeSettingsRepository(), backgroundScope)
        runCurrent()

        haptics.vibrate(250L)

        assertEquals(listOf(250L), device.vibrations)
    }

    @Test
    fun `stays still once the player turns vibration off`() = runTest {
        val repository = FakeSettingsRepository()
        val device = RecordingHaptics()
        val haptics = ObservedHaptics(device, repository, backgroundScope)
        runCurrent()

        repository.setVibrationEnabled(false)
        runCurrent()
        haptics.vibrate(250L)

        assertEquals(emptyList(), device.vibrations)
    }

    @Test
    fun `vibrates again once it is turned back on`() = runTest {
        val repository = FakeSettingsRepository()
        repository.vibration.value = false
        val device = RecordingHaptics()
        val haptics = ObservedHaptics(device, repository, backgroundScope)
        runCurrent()
        haptics.vibrate(100L)

        repository.setVibrationEnabled(true)
        runCurrent()
        haptics.vibrate(250L)

        assertEquals(listOf(250L), device.vibrations)
    }

    /**
     * The store is read asynchronously, so the bat can be hit before the first value lands. Like
     * the audio settings, vibration counts as on until then, which is also its default.
     */
    @Test
    fun `vibrates before the first value arrives`() = runTest {
        val repository = FakeSettingsRepository()
        repository.vibration.value = false
        val device = RecordingHaptics()
        val haptics = ObservedHaptics(device, repository, backgroundScope)
        // Deliberately no runCurrent: the collector has not been dispatched yet.

        haptics.vibrate(250L)

        assertEquals(listOf(250L), device.vibrations)
    }
}
