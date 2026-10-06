package at.smiech.cyanbat.music

import at.smiech.engine.LayeredMusic
import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records what the director asks of the music, and plays nothing. */
private class RecordingMusic : LayeredMusic {
    data class Order(
        val layer: MusicLayer,
        val level: Float,
        val quantum: Quantum,
        val fadeBeats: Float
    )

    val orders = mutableListOf<Order>()
    var muffled = 0f
    var loudness = 1f

    fun level(layer: MusicLayer): Float = orders.lastOrNull { it.layer == layer }?.level ?: 0f

    fun playing(): Set<MusicLayer> = MusicLayer.entries.filter { level(it) > 0f }.toSet()

    override val layerCount = MusicLayer.entries.size
    override val isPlaying = true
    override fun play() = Unit
    override fun pause() = Unit
    override fun setLayerLevel(layer: Int, level: Float, quantum: Quantum, fadeBeats: Float) {
        orders += Order(MusicLayer.entries[layer], level, quantum, fadeBeats)
    }

    override fun setMuffle(amount: Float) {
        muffled = amount
    }

    override fun setVolume(volume: Float) {
        loudness = volume
    }

    override fun dispose() = Unit
}

/** Which layers [MusicDirector] brings in and takes out as a run goes on. */
class MusicDirectorTest {

    /** 120 beats a minute in four: a bar is two seconds. */
    private val grid = MusicGrid(beatsPerMinute = 120.0, beatsPerBar = 4)
    private val music = RecordingMusic()

    private fun director(difficulty: Float = 1f) =
        MusicDirector(music, grid, bossWave = 5, difficulty = difficulty)

    private fun MusicDirector.fly(
        waveIndex: Int = 0,
        combo: Int = 1,
        boss: Boolean = false,
        suspended: Boolean = false,
        seconds: Float = 0.016f,
    ) = update(seconds, waveIndex, combo, boss, suspended)

    @Test
    fun `the cave opens on its bed alone`() {
        director().fly()
        assertEquals(setOf(MusicLayer.BED), music.playing())
    }

    /** The combo is the player's own doing, so they should hear it: and hear losing it. */
    @Test
    fun `a streak brings the pulse in and a hit takes it away`() {
        val director = director()
        director.fly(combo = 2)
        assertEquals(setOf(MusicLayer.BED, MusicLayer.PULSE), music.playing())
        assertEquals(Quantum.BEAT, music.orders.last { it.layer == MusicLayer.PULSE }.quantum)

        director.onPlayerHit()
        director.fly(combo = 1)
        assertEquals(setOf(MusicLayer.BED), music.playing())
    }

    @Test
    fun `the drums come in with the last wave whatever the streak`() {
        director().fly(waveIndex = 4, combo = 1)
        assertEquals(setOf(MusicLayer.BED, MusicLayer.PULSE, MusicLayer.DRIVE), music.playing())
    }

    @Test
    fun `the melody is earned`() {
        val director = director()
        director.fly(waveIndex = 2, combo = 5)
        assertEquals(0f, music.level(MusicLayer.LEAD), "BLAZING in the third wave is not enough")
        director.fly(waveIndex = 2, combo = 6)
        assertEquals(1f, music.level(MusicLayer.LEAD), "SCORCHING is")
    }

    @Test
    fun `the last wave gives the melody to any fire at all`() {
        val director = director()
        director.fly(waveIndex = 4, combo = 1)
        assertEquals(0f, music.level(MusicLayer.LEAD))
        director.fly(waveIndex = 4, combo = 2)
        assertEquals(1f, music.level(MusicLayer.LEAD))
    }

    /**
     * The music climbs the HUD's heat ladder, not the multiplier: a new title on the combo readout
     * is a step, and a step of the multiplier that does not reach one is not.
     */
    @Test
    fun `the combo is heard a rung of its fire at a time`() {
        fun at(combo: Int) =
            MusicDirector.intensity(0, bossWave = 5, comboMultiplier = combo, difficulty = 1f)
        assertEquals(at(2), at(3), "x3 is still HOT")
        assertTrue(at(4) > at(3), "x4 is BLAZING")
        assertEquals(at(4), at(5), "x5 is still BLAZING")
        assertTrue(at(6) > at(5), "x6 is SCORCHING")
        assertTrue(at(9) > at(8), "x9 is INFERNO")
    }

    @Test
    fun `a harder stage opens with its pulse already moving`() {
        director(difficulty = 1.7f).fly()
        assertEquals(setOf(MusicLayer.BED, MusicLayer.PULSE), music.playing())
    }

    @Test
    fun `the heavy layer is kept for the boss`() {
        director(difficulty = 1.7f).fly(waveIndex = 4, combo = 34)
        assertEquals(0f, music.level(MusicLayer.FURY), "WHITE HOT is not enough")
        assertEquals(1f, music.level(MusicLayer.LEAD))
    }

    /** The one rung past INFERNO the music still answers, since the multiplier has no ceiling. */
    @Test
    fun `a streak gone supernova calls the heavy layer in on a downbeat`() {
        val director = director()
        director.fly(waveIndex = 1, combo = 35)
        val fury = music.orders.last { it.layer == MusicLayer.FURY }
        assertEquals(1f, fury.level)
        assertEquals(Quantum.BAR, fury.quantum)

        director.onPlayerHit()
        director.fly(waveIndex = 1, combo = 1)
        assertEquals(0f, music.level(MusicLayer.FURY), "and a hit puts it out")
    }

    /** The boss's entrance: the music drops out under the banner and comes back all at once. */
    @Test
    fun `the boss drops the music to its bed and slams back in on a downbeat`() {
        val director = director()
        director.fly(waveIndex = 4, combo = 3)
        director.onBossArrived()
        director.fly(waveIndex = 5, boss = true)
        assertEquals(setOf(MusicLayer.BED), music.playing(), "the drop")

        val dropped = music.orders.size
        director.fly(waveIndex = 5, boss = true, seconds = 2.1f)
        assertEquals(MusicLayer.entries.toSet(), music.playing(), "the slam")
        val slam = music.orders.drop(dropped)
        assertTrue(slam.all { it.quantum == Quantum.BAR }, "on the bar: $slam")
    }

    @Test
    fun `a boss phase gets the same entrance`() {
        val director = director()
        director.fly(waveIndex = 5, boss = true)
        assertEquals(MusicLayer.entries.toSet(), music.playing())

        director.onBossPhaseChanged()
        director.fly(waveIndex = 5, boss = true)
        assertEquals(setOf(MusicLayer.BED), music.playing())
        director.fly(waveIndex = 5, boss = true, seconds = 2.1f)
        assertEquals(MusicLayer.entries.toSet(), music.playing())
    }

    /** The run holds still for a pick, and the music carries on under a muffle. */
    @Test
    fun `the level up dialog muffles the music without dropping a layer`() {
        val director = director()
        director.fly(waveIndex = 4)
        val before = music.playing()

        director.fly(waveIndex = 4, suspended = true)
        assertEquals(before, music.playing())
        assertTrue(
            music.muffled > 0.5f && music.loudness < 1f,
            "muffle ${music.muffled}, volume ${music.loudness}"
        )

        director.fly(waveIndex = 4)
        assertEquals(0f, music.muffled)
        assertEquals(1f, music.loudness)
    }

    @Test
    fun `a hit is a thud that clears in about half a second`() {
        val director = director()
        director.onPlayerHit()
        director.fly(seconds = 0f)
        assertTrue(music.muffled > 0.5f, "muffle on the hit: ${music.muffled}")
        director.fly(seconds = 0.25f)
        assertTrue(music.muffled in 0.01f..0.5f, "muffle a moment later: ${music.muffled}")
        director.fly(seconds = 0.25f)
        assertEquals(0f, music.muffled)
    }

    @Test
    fun `a level is ordered when it changes and not every frame`() {
        val director = director()
        director.fly(combo = 2)
        val after = music.orders.size
        repeat(10) { director.fly(combo = 2) }
        assertEquals(after, music.orders.size)
    }

    @Test
    fun `the intensity floor rises evenly across the waves`() {
        val floors = (0..4).map {
            MusicDirector.intensity(
                it,
                bossWave = 5,
                comboMultiplier = 1,
                difficulty = 1f
            )
        }
        for (i in 1 until floors.size) {
            assertEquals(0.1f, floors[i] - floors[i - 1], 1e-4f)
        }
    }

    /** Every title the HUD's fire climbs through, HOT to SUPERNOVA, is a step up for the music. */
    @Test
    fun `every rung of the fire is a step up to SUPERNOVA and no further`() {
        fun at(combo: Int) =
            MusicDirector.intensity(0, bossWave = 5, comboMultiplier = combo, difficulty = 1f)

        val rungs = listOf(1, 2, 4, 6, 9, 13, 18, 25, 35)
        for ((lower, higher) in rungs.zipWithNext()) {
            assertTrue(at(higher) > at(lower), "x$higher climbs past x$lower")
        }
        assertEquals(at(35), at(300), "past SUPERNOVA the multiplier climbs on alone")
    }

    @Test
    fun `the first wave's melody waits for HELLFIRE`() {
        val director = director()
        director.fly(waveIndex = 0, combo = 12)
        assertEquals(0f, music.level(MusicLayer.LEAD), "INFERNO is not enough")
        director.fly(waveIndex = 0, combo = 13)
        assertEquals(1f, music.level(MusicLayer.LEAD), "HELLFIRE is")
    }

    /** Over the tune the layers come a rung apart: the hotter the fire, the more each rung brings. */
    @Test
    fun `the 808s and the rolls and the chopped voices come in a rung apart`() {
        val director = director()
        director.fly(waveIndex = 1, combo = 9)
        assertEquals(
            setOf(MusicLayer.BED, MusicLayer.PULSE, MusicLayer.DRIVE, MusicLayer.LEAD),
            music.playing(),
            "INFERNO in the second wave brings the melody",
        )
        director.fly(waveIndex = 1, combo = 13)
        assertEquals(1f, music.level(MusicLayer.BOOM), "HELLFIRE, the 808s")
        assertEquals(0f, music.level(MusicLayer.ROLL))
        director.fly(waveIndex = 1, combo = 18)
        assertEquals(1f, music.level(MusicLayer.ROLL), "BLUE FLAME, the rolling hats")
        assertEquals(0f, music.level(MusicLayer.CHOP))
        director.fly(waveIndex = 1, combo = 25)
        assertEquals(1f, music.level(MusicLayer.CHOP), "WHITE HOT, the chopped voices")
        assertEquals(0f, music.level(MusicLayer.FURY), "and still nothing of the boss's")
    }

    @Test
    fun `a streak gone supernova has the whole piece playing whatever the wave`() {
        director().fly(waveIndex = 0, combo = 35)
        assertEquals(MusicLayer.entries.toSet(), music.playing())
    }

    /** A hit takes the streak, and the whole trap beat it had built goes with it. */
    @Test
    fun `a hit strips the trap beat back to the floor`() {
        val director = director()
        director.fly(waveIndex = 4, combo = 25)
        assertEquals(1f, music.level(MusicLayer.CHOP))
        val beforeHit = music.orders.size

        director.onPlayerHit()
        director.fly(waveIndex = 4, combo = 1)
        assertEquals(setOf(MusicLayer.BED, MusicLayer.PULSE, MusicLayer.DRIVE), music.playing())
        val falls = music.orders.drop(beforeHit).filter { it.level == 0f }.map { it.layer }.toSet()
        assertEquals(
            setOf(MusicLayer.LEAD, MusicLayer.BOOM, MusicLayer.ROLL, MusicLayer.CHOP),
            falls
        )
    }
}
