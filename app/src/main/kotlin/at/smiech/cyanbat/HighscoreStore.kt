package at.smiech.cyanbat

/**
 * Persistence for the single highscore value.
 *
 * The asymmetry is deliberate. The read is scoped to whoever asks for it, but the write happens
 * as the bat dies - moments before the game screen is torn down - so it must not be cancellable
 * by that teardown. Implementations own a process-lifetime scope for [saveAsync].
 */
interface HighscoreStore {
    suspend fun read(): Int
    fun saveAsync(value: Int)
}
