package at.smiech.cyanbat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.PREFS_KEY_SOUNDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What [DataStoreSettingsRepository] keeps, and its defaults for settings saved before them. */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + Job())

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(folder.root, "test.preferences_pb")
        }
    }

    @AfterTest
    fun close() {
        dataStoreScope.cancel()
    }

    /** A player who set up audio before vibration was a setting has never turned it off. */
    @Test
    fun `vibration is on for settings saved before it existed`() = runBlocking {
        dataStore.edit {
            it[PREFS_KEY_MUSIC] = false
            it[PREFS_KEY_SOUNDS] = false
        }

        assertTrue(DataStoreSettingsRepository(dataStore).isVibrationEnabled.first())
    }

    @Test
    fun `turning vibration off is kept and leaves the sound alone`() = runBlocking {
        val settings = DataStoreSettingsRepository(dataStore)

        settings.setVibrationEnabled(false)

        assertFalse(settings.isVibrationEnabled.first())
        assertTrue(settings.isSoundEnabled.first())
    }

    /** Before the setting existed the menu was always light; now it follows the system until chosen. */
    @Test
    fun `the theme follows the system for settings saved before it existed`() = runBlocking {
        dataStore.edit { it[PREFS_KEY_MUSIC] = false }

        assertEquals(ThemeMode.SYSTEM, DataStoreSettingsRepository(dataStore).themeMode.first())
    }

    @Test
    fun `a chosen theme is kept`() = runBlocking {
        val settings = DataStoreSettingsRepository(dataStore)

        settings.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
    }

    @Test
    fun `the difficulty last chosen is kept and Normal until then`() = runBlocking {
        val settings = DataStoreSettingsRepository(dataStore)
        assertEquals(Difficulty.NORMAL, settings.difficulty.first())

        settings.setDifficulty(Difficulty.HARD)

        assertEquals(Difficulty.HARD, settings.difficulty.first())
    }

    /** Not chosen is what has the menu ask, before the first run. */
    @Test
    fun `the steering is not chosen until it is, and then kept`() = runBlocking {
        val settings = DataStoreSettingsRepository(dataStore)
        assertEquals(null, settings.touchSteering.first())

        settings.setTouchSteering(TouchSteering.STICK)

        assertEquals(TouchSteering.STICK, settings.touchSteering.first())
    }

    @Test
    fun `the stick's sensitivity is medium until chosen and then kept`() = runBlocking {
        val settings = DataStoreSettingsRepository(dataStore)
        assertEquals(StickSensitivity.MEDIUM, settings.stickSensitivity.first())

        settings.setStickSensitivity(StickSensitivity.LOW)

        assertEquals(StickSensitivity.LOW, settings.stickSensitivity.first())
    }
}
