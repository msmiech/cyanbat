package at.smiech.cyanbat.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeSettingsRepository : SettingsRepository {
    val music = MutableStateFlow(true)
    val sound = MutableStateFlow(true)

    override val isMusicEnabled = music
    override val isSoundEnabled = sound

    override suspend fun setMusicEnabled(enabled: Boolean) {
        music.value = enabled
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        sound.value = enabled
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObservedAudioSettingsTest {

    /**
     * Regression: the game ignored both toggles entirely. The environment carried plain booleans
     * that defaulted to true and no host ever passed anything else, so turning music off changed
     * nothing once a run started.
     */
    @Test
    fun `reflects the stored values`() = runTest {
        val repository = FakeSettingsRepository()
        repository.music.value = false

        val settings = ObservedAudioSettings(repository, backgroundScope)
        runCurrent()

        assertFalse(settings.musicEnabled, "music was disabled in the store")
        assertTrue(settings.soundsEnabled, "sound was left enabled")
    }

    @Test
    fun `tracks later changes to either toggle`() = runTest {
        val repository = FakeSettingsRepository()
        val settings = ObservedAudioSettings(repository, backgroundScope)
        runCurrent()
        assertTrue(settings.musicEnabled)
        assertTrue(settings.soundsEnabled)

        repository.setMusicEnabled(false)
        repository.setSoundEnabled(false)
        runCurrent()

        assertFalse(settings.musicEnabled)
        assertFalse(settings.soundsEnabled)
    }

    /**
     * The store is read asynchronously, so a run can start before the first value lands. Audio
     * must default to on there - defaulting to off would silence the game for anyone whose
     * settings read is merely slow.
     */
    @Test
    fun `defaults to enabled before the first value arrives`() = runTest {
        val repository = FakeSettingsRepository()
        repository.music.value = false
        repository.sound.value = false

        val settings = ObservedAudioSettings(repository, backgroundScope)
        // Deliberately no runCurrent: the collectors have not been dispatched yet.

        assertTrue(settings.musicEnabled)
        assertTrue(settings.soundsEnabled)
    }

    @Test
    fun `the all-enabled default has everything on`() {
        assertTrue(AudioSettings.AllEnabled.musicEnabled)
        assertTrue(AudioSettings.AllEnabled.soundsEnabled)
    }
}
