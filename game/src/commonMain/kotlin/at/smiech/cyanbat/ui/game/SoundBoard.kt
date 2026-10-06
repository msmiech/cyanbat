package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.resource.SoundEffect
import at.smiech.engine.Sound

/**
 * Plays a run's sound effects: only while sounds are on, and never one too soon after itself.
 *
 * A fan of shots into a swarm can land five hits on one tick, and the platforms disagree about a
 * sound played over itself: Android's SoundPool stacks the copies, which then clip, while the
 * desktop's clip starts over. Holding each effect to its [SoundEffect.gapSeconds] makes them agree
 * and keeps a busy moment from becoming a wall of noise.
 *
 * The gaps are measured on a clock that only moves as the run [advance]s it, so pausing holds it.
 *
 * @param sounds what each effect is played through; an effect missing from it stays silent.
 * @param enabled read on every play, so turning sounds off in the settings takes at once.
 */
class SoundBoard(
    private val sounds: Map<SoundEffect, Sound>,
    private val enabled: () -> Boolean,
) {
    private var clock = 0f

    /** When each effect last played, by [clock]; never, to begin with. */
    private val lastPlayed = FloatArray(SoundEffect.entries.size) { Float.NEGATIVE_INFINITY }

    /** Advances the board's clock by one tick. */
    fun advance(deltaTime: Float) {
        clock += deltaTime
    }

    /** Plays [effect] at its volume, unless sounds are off or it played too recently. */
    fun play(effect: SoundEffect) {
        if (!enabled()) return
        if (clock - lastPlayed[effect.ordinal] < effect.gapSeconds) return
        lastPlayed[effect.ordinal] = clock
        sounds[effect]?.play(effect.volume)
    }
}
