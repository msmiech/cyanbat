package at.smiech.cyanbat.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.PREFS_KEY_LEGACY_HIGH_SCORE
import at.smiech.cyanbat.PREFS_KEY_STAGE_ORDER
import at.smiech.cyanbat.PREFS_STAGE_HIGH_SCORE_PREFIX
import at.smiech.cyanbat.prefsKeyStageHighScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [HighscoreStore] backed by Jetpack DataStore, one key per stage.
 *
 * Owns a process-lifetime scope, so a save issued as the game screen is disposed still completes:
 * the runs that earn a highscore are exactly the ones that would otherwise lose it.
 */
class DataStoreHighscoreStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : HighscoreStore {

    // Read back off the keys rather than asked for by a list of stage ids, so a stage added later
    // needs nothing here.
    override val byStage: Flow<Map<Int, Int>> = dataStore.data.map { preferences ->
        preferences.asMap().entries.mapNotNull { (key, value) ->
            val stageId = key.name.takeIf { it.startsWith(PREFS_STAGE_HIGH_SCORE_PREFIX) }
                ?.removePrefix(PREFS_STAGE_HIGH_SCORE_PREFIX)
                ?.toIntOrNull()
            if (stageId != null && value is Int) stageId to value else null
        }.toMap()
    }

    override fun saveAsync(stageId: Int, value: Int) {
        val key = prefsKeyStageHighScore(stageId)
        scope.launch {
            dataStore.edit { it[key] = maxOf(it[key] ?: 0, value) }
        }
    }
}

/**
 * Moves the single highscore kept before they were per stage onto stage 1.
 *
 * Every release that kept one had the cave as its only stage, so that is where it was earned.
 * Merged rather than copied, in case stage 1 already has a better score of its own.
 */
internal object LegacyHighscoreMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        PREFS_KEY_LEGACY_HIGH_SCORE in currentData

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = currentData[PREFS_KEY_LEGACY_HIGH_SCORE] ?: return currentData
        val stageOne = prefsKeyStageHighScore(1)
        return currentData.toMutablePreferences().apply {
            this[stageOne] = maxOf(this[stageOne] ?: 0, legacy)
            remove(PREFS_KEY_LEGACY_HIGH_SCORE)
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

/**
 * Moves the highscores of the first two stages to where those stages went when they swapped places:
 * the jungle, which was stage 2 as the forest, flies first now, and the cave second. A record is a
 * record of a stage, not of a slot in the stage select.
 *
 * Done once, and marked done in [PREFS_KEY_STAGE_ORDER], since a swap run twice would undo itself.
 * A store with nothing in it is marked all the same, so the scores of the stages as they are now
 * are never moved.
 */
internal object StageOrderMigration : DataMigration<Preferences> {
    /** The order with the jungle first. Each later reordering would get its own number. */
    const val JUNGLE_FIRST = 1

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[PREFS_KEY_STAGE_ORDER] ?: 0) < JUNGLE_FIRST

    override suspend fun migrate(currentData: Preferences): Preferences {
        val first = prefsKeyStageHighScore(1)
        val second = prefsKeyStageHighScore(2)
        return currentData.toMutablePreferences().apply {
            val cave = this[first]
            val forest = this[second]
            remove(first)
            remove(second)
            forest?.let { this[first] = it }
            cave?.let { this[second] = it }
            this[PREFS_KEY_STAGE_ORDER] = JUNGLE_FIRST
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
