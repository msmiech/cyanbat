package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.resource.SoundEffect
import at.smiech.engine.Sound
import kotlin.test.Test
import kotlin.test.assertEquals

/** When [SoundBoard] plays an effect, and how loud. */
class SoundBoardTest {

    /** Every effect through a sound that only writes down what it was asked to play. */
    private class Recorder : Sound {
        val volumes = mutableListOf<Float>()
        override fun play(volume: Float) {
            volumes += volume
        }

        override fun dispose() = Unit
    }

    private val recorders = SoundEffect.entries.associateWith { Recorder() }
    private var enabled = true
    private val board = SoundBoard(recorders) { enabled }

    private fun played(effect: SoundEffect) = recorders.getValue(effect).volumes.size

    @Test
    fun `an effect plays at its own volume`() {
        board.play(SoundEffect.ENEMY_DEATH)
        assertEquals(
            listOf(SoundEffect.ENEMY_DEATH.volume),
            recorders.getValue(SoundEffect.ENEMY_DEATH).volumes
        )
    }

    @Test
    fun `nothing plays while sounds are off`() {
        enabled = false
        board.play(SoundEffect.SHOT)
        enabled = true
        board.play(SoundEffect.SHOT)
        assertEquals(1, played(SoundEffect.SHOT), "only the play made with sounds on")
    }

    /** Five hits landed by one fan of shots on one tick are one hit heard, not five stacked. */
    @Test
    fun `an effect is held to its gap`() {
        repeat(5) { board.play(SoundEffect.HIT) }
        assertEquals(1, played(SoundEffect.HIT))

        board.advance(SoundEffect.HIT.gapSeconds / 2)
        board.play(SoundEffect.HIT)
        assertEquals(1, played(SoundEffect.HIT), "half a gap later")

        board.advance(SoundEffect.HIT.gapSeconds / 2)
        board.play(SoundEffect.HIT)
        assertEquals(2, played(SoundEffect.HIT), "a whole gap after the first")
    }

    @Test
    fun `one effect does not hold up another`() {
        board.play(SoundEffect.HIT)
        board.play(SoundEffect.ENEMY_DEATH)
        assertEquals(1, played(SoundEffect.HIT))
        assertEquals(1, played(SoundEffect.ENEMY_DEATH))
    }

    @Test
    fun `an effect with no gap plays every time`() {
        assertEquals(0f, SoundEffect.SHOT.gapSeconds)
        repeat(3) { board.play(SoundEffect.SHOT) }
        assertEquals(3, played(SoundEffect.SHOT))
    }
}
