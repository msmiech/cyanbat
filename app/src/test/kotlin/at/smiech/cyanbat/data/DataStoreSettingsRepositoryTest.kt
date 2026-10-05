package at.smiech.cyanbat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
