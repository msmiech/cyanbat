package at.smiech.cyanbat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import at.smiech.cyanbat.PREFS_KEY_HIGHEST_STAGE
import at.smiech.cyanbat.PREFS_KEY_LEGACY_HIGH_SCORE
import at.smiech.cyanbat.PREFS_KEY_MUSIC
import at.smiech.cyanbat.prefsKeyStageHighScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreHighscoreStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + Job())

    /** The parent of every save the store launches, so a test can wait for them to land. */
    private val saves = Job()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(folder.root, "test.preferences_pb")
        }
    }

    private fun store() = DataStoreHighscoreStore(dataStore, CoroutineScope(Dispatchers.IO + saves))

    /** Saves, and waits for the write: saveAsync deliberately returns before it has landed. */
    private suspend fun DataStoreHighscoreStore.save(stageId: Int, value: Int) {
        saveAsync(stageId, value)
        saves.children.toList().joinAll()
    }

    @AfterTest
    fun close() {
        saves.cancel()
        dataStoreScope.cancel()
    }

    @Test
    fun `the old single highscore moves onto stage 1`() = runBlocking {
        val old = preferencesOf(PREFS_KEY_LEGACY_HIGH_SCORE to 4125)
        assertTrue(LegacyHighscoreMigration.shouldMigrate(old))

        val migrated = LegacyHighscoreMigration.migrate(old)

        assertEquals(4125, migrated[prefsKeyStageHighScore(1)])
        assertFalse(PREFS_KEY_LEGACY_HIGH_SCORE in migrated)
        assertFalse(LegacyHighscoreMigration.shouldMigrate(migrated))
    }

    @Test
    fun `a better stage 1 score survives the move`() = runBlocking {
        val migrated = LegacyHighscoreMigration.migrate(
            preferencesOf(PREFS_KEY_LEGACY_HIGH_SCORE to 4125, prefsKeyStageHighScore(1) to 9000)
        )
        assertEquals(9000, migrated[prefsKeyStageHighScore(1)])
    }

    @Test
    fun `each stage keeps its own highscore, and a lower score never replaces one`() = runBlocking {
        val store = store()
        store.save(stageId = 1, value = 500)
        store.save(stageId = 2, value = 300)
        store.save(stageId = 1, value = 200)

        assertEquals(mapOf(1 to 500, 2 to 300), store.byStage.first())
        assertEquals(500, store.read(1))
        assertEquals(0, store.read(3))
    }

    @Test
    fun `only highscore keys are read back as stages`() = runBlocking {
        dataStore.edit {
            // The rest of the file's settings, one of them an int just like the scores.
            it[PREFS_KEY_HIGHEST_STAGE] = 2
            it[PREFS_KEY_MUSIC] = true
            it[prefsKeyStageHighScore(1)] = 700
        }
        assertEquals(mapOf(1 to 700), store().byStage.first())
    }
}
