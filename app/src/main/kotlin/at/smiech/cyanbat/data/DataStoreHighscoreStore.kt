package at.smiech.cyanbat.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.PREFS_KEY_LEGACY_HIGH_SCORE
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
 * Owns a process-lifetime scope so a save issued as the game screen is being disposed still
 * completes - exactly the runs that earn a highscore are the ones that would otherwise lose it.
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
