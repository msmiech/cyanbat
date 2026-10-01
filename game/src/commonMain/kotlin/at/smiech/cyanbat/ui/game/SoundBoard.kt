package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.resource.SoundEffect
import at.smiech.engine.Sound

/**
 * Plays a run's sound effects: only while sounds are on, and never one too soon after itself.
 *
 * A volley, a hit or a kill can come several to a tick - a fan of shots into a swarm lands five
 * hits at once - and the two platforms disagree about a sound played over itself. Android's
 * SoundPool stacks the copies, so five hits play five times as loud and clip; the desktop's clip
 * starts over. Holding each effect to its [SoundEffect.gapSeconds] makes the two agree, and keeps a
 * busy moment a run of hits rather than a wall of noise.
 *
 * The gaps are measured on a clock of its own, which only moves as the run [advance]s it, so a
 * paused run holds it still along with everything else.
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

    fun advance(deltaTime: Float) {
        clock += deltaTime
    }

    fun play(effect: SoundEffect) {
        if (!enabled()) return
        if (clock - lastPlayed[effect.ordinal] < effect.gapSeconds) return
        lastPlayed[effect.ordinal] = clock
        sounds[effect]?.play(effect.volume)
    }
}
