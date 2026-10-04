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

    /** An hour like the desert's sunset, which none of the other clips would land on. */
    @Test
    fun `a stage whose look changes is shown at the hours asked for`() {
        val withScenery = Montage.cut(run, frameSeconds, actionWave = 3, scenery = listOf(250f)).flatten()

        val shown = withScenery.filter { run[it].seconds in 250f..252.4f }
        assertTrue(shown.size >= (2f / frameSeconds).toInt(), "the sunset got ${shown.size} frames")
        assertTrue(frames.none { run[it].seconds in 250f..252.4f }, "the sunset was shown without being asked for")
        assertEquals(withScenery.sorted().distinct(), withScenery)
    }

    @Test
    fun `the action is the busiest stretch of the most varied wave`() {
        val busy = run.indices.filter { run[it].enemies == 12 }
        assertTrue(busy.all { it in frames })
    }

    /** The cave's share of the reel: its title, its busiest stretch and its boss arriving. */
    @Test
    fun `a stage shown in glimpses shows its boss arriving and nothing after`() {
        val glimpses = Montage.cut(
            run,
            frameSeconds,
            actionWave = 3,
            coverage = Coverage(actionSeconds = 2.4f, arrivalSeconds = 1.6f),
        ).flatten()

        assertEquals(glimpses.sorted().distinct(), glimpses)
        assertTrue(glimpses.none { run[it].offer || run[it].complete }, "a dialog or the overlay was shown")
        val boss = glimpses.filter { run[it].bossSpawned }.map { run[it].seconds }
        assertTrue(boss.isNotEmpty() && boss.all { it <= 301.6f + frameSeconds }, "the boss was shown at $boss")
        assertTrue(glimpses.size < frames.size / 2, "${glimpses.size} frames of glimpses, ${frames.size} of the whole")
    }

    /** The desert's: its boss stays out of the footage, however far the tape runs. */
    @Test
    fun `a boss kept hidden never appears`() {
        val teaser = Coverage(scenerySeconds = 2f)
        val fromWhole = Montage.cut(run, frameSeconds, actionWave = 3, scenery = listOf(250f), coverage = teaser)
            .flatten()
        // The recorder stops the tape before such a boss arrives, so this is the tape it cuts.
        val fromShort = Montage.cut(
            run.takeWhile { !it.bossSpawned },
            frameSeconds,
            actionWave = 3,
            scenery = listOf(250f),
            coverage = teaser,
        ).flatten()

        assertEquals(fromWhole, fromShort)
        assertTrue(fromWhole.none { run[it].bossSpawned || run[it].offer }, "the boss or a dialog was shown")
        assertTrue(fromWhole.count { run[it].seconds in 250f..252f } >= (1.8f / frameSeconds).toInt(), "no sunset")
    }
}
