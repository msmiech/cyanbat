package at.smiech.cyanbat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Persistence for each stage's highscore.
 *
 * One record per stage rather than one for the whole game: a run flies a single stage, and the
 * stages do not score alike, so a shared number would only ever say which stage it was set on.
 *
 * The asymmetry is deliberate. The read is scoped to whoever asks for it, but the write happens
 * as the bat dies - moments before the game screen is torn down - so it must not be cancellable
 * by that teardown. Implementations own a process-lifetime scope for [saveAsync].
 *
 * [saveAsync] only ever raises the stored value. The screen saves whatever it believes the
 * highscore to be, and a run that ends before its read has come back believes it is zero - so a
 * store that took the value as given would let a short run overwrite the real record.
 */
interface HighscoreStore {
    /** Every stage's highscore, by stage id. A stage nothing has been scored on is absent. */
    val byStage: Flow<Map<Int, Int>>

    /** [stageId]'s highscore, or 0 before anything has been scored on it. */
    suspend fun read(stageId: Int): Int = byStage.first()[stageId] ?: 0

    /** Raises [stageId]'s highscore to [value], if that beats it. Never lowers one. */
    fun saveAsync(stageId: Int, value: Int)
}
