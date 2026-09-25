package at.smiech.cyanbat

/**
 * Persistence for the single highscore value.
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
    suspend fun read(): Int
    fun saveAsync(value: Int)
}
