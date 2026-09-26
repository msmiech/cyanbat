package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.progress.PowerUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MontageTest {

    private val frameSeconds = 0.057f

    /** A run of the stage clock, frame by frame, as the recorder would have taped it. */
    private class Run(private val frameSeconds: Float) {
        val moments = mutableListOf<Moment>()
        private var clock = 0f
        private var pick: PowerUp? = null

        /** Flies on to [until] seconds, with [enemies] on screen at each moment. */
        fun play(until: Float, boss: Boolean = false, enemies: (Float) -> Int = { 2 }) {
            while (clock < until) {
                moments += moment(boss = boss, enemies = enemies(clock))
                clock += frameSeconds
            }
        }

        /** A level up dialog for [seconds], which stops the clock, closing on [taken]. */
        fun levelUp(taken: PowerUp, seconds: Float = 1.3f, boss: Boolean = false) {
            repeat((seconds / frameSeconds).toInt()) { moments += moment(boss = boss, offer = true) }
            pick = taken
        }

        /** The boss down, and the overlay held over a clock that has stopped. */
        fun complete(seconds: Float = 2.6f) {
            repeat((seconds / frameSeconds).toInt()) { moments += moment(boss = true, complete = true) }
        }

        private fun moment(boss: Boolean, enemies: Int = 2, offer: Boolean = false, complete: Boolean = false) =
            Moment(
                seconds = clock,
                wave = (clock / 60f).toInt(),
                bossSpawned = boss,
                complete = complete,
                offer = offer,
                banner = null,
                enemies = enemies,
                blasts = 0,
                health = 1f,
                hit = false,
                level = 1,
                score = 0,
                pick = if (offer) null else pick.also { pick = null },
            )
    }

    private val run = Run(frameSeconds).apply {
        play(until = 100f)
        levelUp(PowerUp.SPREAD_SHOT)
        play(until = 150f)
        levelUp(PowerUp.VITALITY)
        // The fourth wave is the most varied, and this stretch of it the busiest.
        play(until = 300f) { if (it in 200f..203f) 12 else 2 }
        play(until = 300.5f, boss = true)
        // Mopping up the escort as the boss arrives buys a level.
        levelUp(PowerUp.RAPID_FIRE, boss = true)
        play(until = 310f, boss = true)
        complete()
    }.moments

    private val frames = Montage.cut(run, frameSeconds, actionWave = 3).flatten()

    @Test
    fun `the footage runs in play order from the opening to the end of the overlay`() {
        assertEquals(frames.sorted().distinct(), frames)
        assertEquals(0, frames.first())
        assertEquals(run.lastIndex, frames.last())
    }

    @Test
    fun `the level up shown is the one that took Spread Shot`() {
        val shown = frames.filter { run[it].offer }.map { run[it].seconds }.distinct()
        assertTrue(shown.isNotEmpty() && shown.all { it in 99.9f..100.1f }, "dialogs shown at $shown")
    }

    @Test
    fun `a dialog anywhere else is cut out, leaving the boss's arrival whole`() {
        val arrival = frames.filter { run[it].seconds in 300f..302f }
        assertTrue(arrival.none { run[it].offer })
        assertTrue(arrival.size >= (2f / frameSeconds).toInt())
    }

    @Test
    fun `the action is the busiest stretch of the most varied wave`() {
        val busy = run.indices.filter { run[it].enemies == 12 }
        assertTrue(busy.all { it in frames })
    }
}
