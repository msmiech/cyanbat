package at.smiech.cyanbat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.PREFS_KEY_CLEARS_KEPT
import at.smiech.cyanbat.PREFS_KEY_HIGHEST_STAGE
import at.smiech.cyanbat.prefsKeyStageCleared
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

/** [DataStoreStageUnlockStore]'s clears, and the migration that keeps those from before them. */
class DataStoreStageUnlockStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + Job())

    /** The parent of every write the store launches, so a test can wait for them to land. */
    private val writes = Job()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(folder.root, "test.preferences_pb")
        }
    }

    private fun store() =
        DataStoreStageUnlockStore(dataStore, CoroutineScope(Dispatchers.IO + writes))

    /** Records, and waits for the write: recordClearAsync deliberately returns before it lands. */
    private suspend fun DataStoreStageUnlockStore.clear(stageId: Int, difficulty: Difficulty) {
        recordClearAsync(stageId, difficulty)
        writes.children.toList().joinAll()
    }

    @AfterTest
    fun close() {
        writes.cancel()
        dataStoreScope.cancel()
    }

    @Test
    fun `a stage keeps the hardest difficulty it was cleared on`() = runBlocking {
        val store = store()
        store.clear(1, Difficulty.NORMAL)
        store.clear(1, Difficulty.EASY)
        store.clear(2, Difficulty.HARD)

        assertEquals(mapOf(1 to Difficulty.NORMAL, 2 to Difficulty.HARD), store.clears.first())
    }

    @Test
    fun `only clear keys are read back as clears`() = runBlocking {
        dataStore.edit {
            it[PREFS_KEY_HIGHEST_STAGE] = 3
            it[prefsKeyStageHighScore(1)] = 2
            it[prefsKeyStageCleared(2)] = Difficulty.EASY.stars
        }
        assertEquals(mapOf(2 to Difficulty.EASY), store().clears.first())
    }

    @Test
    fun `the stages opened before there were difficulties count as cleared on Hard`() =
        runBlocking {
            val old = preferencesOf(PREFS_KEY_HIGHEST_STAGE to 3)
            assertTrue(ClearsBeforeDifficultiesMigration.shouldMigrate(old))

            val migrated = ClearsBeforeDifficultiesMigration.migrate(old)

            assertEquals(Difficulty.HARD.stars, migrated[prefsKeyStageCleared(1)])
            assertEquals(Difficulty.HARD.stars, migrated[prefsKeyStageCleared(2)])
            assertEquals(null, migrated[prefsKeyStageCleared(3)], "the stage opened last")
            assertFalse(
                ClearsBeforeDifficultiesMigration.shouldMigrate(migrated),
                "a second run would take a later Easy clear's unlock for a Hard clear",
            )
        }

    @Test
    fun `a store with nothing in it is marked done all the same`() = runBlocking {
        val migrated = ClearsBeforeDifficultiesMigration.migrate(preferencesOf())

        assertEquals(true, migrated[PREFS_KEY_CLEARS_KEPT])
        assertFalse(migrated.asMap().keys.any { it.name.startsWith("cleared_stage_") })
    }
}
