package at.smiech.cyanbat.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import at.smiech.cyanbat.Difficulty
import at.smiech.cyanbat.PREFS_KEY_CLEARS_KEPT
import at.smiech.cyanbat.PREFS_KEY_HIGHEST_STAGE
import at.smiech.cyanbat.PREFS_STAGE_CLEARED_PREFIX
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.clearsBeforeDifficulties
import at.smiech.cyanbat.harderThanBest
import at.smiech.cyanbat.prefsKeyStageCleared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [StageUnlockStore] backed by Jetpack DataStore, in the same file as the highscore and settings.
 *
 * Owns a process-lifetime scope, like [DataStoreHighscoreStore], so an unlock issued as the boss
 * dies still lands if the player taps straight through to the next screen.
 */
class DataStoreStageUnlockStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : StageUnlockStore {

    override val highestUnlocked: Flow<Int> =
        dataStore.data.map { (it[PREFS_KEY_HIGHEST_STAGE] ?: 1).coerceAtLeast(1) }

    // Read back off the keys, as the highscores are, so a stage added later needs nothing here.
    override val clears: Flow<Map<Int, Difficulty>> = dataStore.data.map { preferences ->
        preferences.asMap().entries.mapNotNull { (key, value) ->
            val stageId = key.name.takeIf { it.startsWith(PREFS_STAGE_CLEARED_PREFIX) }
                ?.removePrefix(PREFS_STAGE_CLEARED_PREFIX)
                ?.toIntOrNull()
            val difficulty = (value as? Int)?.let(Difficulty::ofStars)
            if (stageId != null && difficulty != null) stageId to difficulty else null
        }.toMap()
    }

    override fun unlockAsync(stageId: Int) {
        scope.launch {
            dataStore.edit {
                it[PREFS_KEY_HIGHEST_STAGE] = maxOf(it[PREFS_KEY_HIGHEST_STAGE] ?: 1, stageId)
            }
        }
    }

    override fun recordClearAsync(stageId: Int, difficulty: Difficulty) {
        val key = prefsKeyStageCleared(stageId)
        scope.launch {
            dataStore.edit {
                if (harderThanBest(it[key]?.let(Difficulty::ofStars), difficulty)) {
                    it[key] = difficulty.stars
                }
            }
        }
    }
}

/**
 * Gives a store kept before there were difficulties the clears its unlocks stand for; see
 * [clearsBeforeDifficulties].
 *
 * Done once, and marked done in [PREFS_KEY_CLEARS_KEPT]: done again, it would read a stage opened by
 * a clear on Easy as one cleared on Hard. A store with nothing in it is marked all the same.
 */
internal object ClearsBeforeDifficultiesMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[PREFS_KEY_CLEARS_KEPT] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val highest = currentData[PREFS_KEY_HIGHEST_STAGE] ?: 1
        return currentData.toMutablePreferences().apply {
            for ((stageId, difficulty) in clearsBeforeDifficulties(highest)) {
                val key = prefsKeyStageCleared(stageId)
                if (harderThanBest(this[key]?.let(Difficulty::ofStars), difficulty)) {
                    this[key] = difficulty.stars
                }
            }
            this[PREFS_KEY_CLEARS_KEPT] = true
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
