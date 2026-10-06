package at.smiech.cyanbat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Persistence for each stage's highscore. One record per stage, because a run flies one stage and
 * the stages do not score alike.
 *
 * The read is scoped to the caller, but the write happens as the bat dies, moments before the game
 * screen is torn down, so it must survive that teardown: implementations own a process-lifetime
 * scope for [saveAsync].
 *
 * [saveAsync] only ever raises the stored value. A run that ends before its read returns believes
 * the highscore is zero, and must not overwrite the real record.
 */
interface HighscoreStore {
    /** Every stage's highscore, by stage id. A stage nothing has been scored on is absent. */
    val byStage: Flow<Map<Int, Int>>

    /** [stageId]'s highscore, or 0 before anything has been scored on it. */
    suspend fun read(stageId: Int): Int = byStage.first()[stageId] ?: 0

    /** Raises [stageId]'s highscore to [value], if that beats it. Never lowers one. */
    fun saveAsync(stageId: Int, value: Int)
}
