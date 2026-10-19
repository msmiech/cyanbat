package at.smiech.cyanbat.music

import at.smiech.cyanbat.music.MusicDirector.Companion.COMBO_RUNGS
import at.smiech.cyanbat.music.MusicDirector.Companion.COMBO_STEP
import at.smiech.cyanbat.music.MusicDirector.Companion.WAVE_RISE
import at.smiech.cyanbat.ui.game.ComboHeat
import at.smiech.engine.LayeredMusic
import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum
import kotlin.math.max

/**
 * Decides, frame by frame, how much of a stage's music is playing and how it sounds.
 *
 * Two inputs set how many layers are up, so the music is only as intense as the fighting. Progress
 * through the stage raises a floor: the cave's opening minute is its bed alone, and by the last
 * wave the drums are in regardless. The combo builds on that floor, and a hit takes it away again,
 * so the player hears how well they are doing. A lost streak winds the music down rather than
 * cutting it: the layers it had built go a beat apart, the top one first, each fading over its beat.
 *
 * The combo is heard on the HUD's heat ladder ([ComboHeat]): each rung it names,
 * HOT to SUPERNOVA, is a step of the music, so a new title on screen and a new
 * layer arrive together. Under the tune a layer takes two rungs, over it one, so
 * the hottest streaks get the 808s, rolling hats and chopped voices a rung apart.
 * SUPERNOVA adds the boss's heavy layer; past it the music has nothing left to add.
 *
 * At the edges of the action, a hit is a thud, the music briefly ducking under a
 * low-pass; the level-up dialog, which holds the run still, keeps the music going
 * under a muffle that lifts as the pick is made.
 *
 * The boss gets an entrance: a break, where the weight of the drums - the kit, the 808s and the
 * heavy layer - drops out while the bass, the rolling hats, the tune and the voices drive on, and
 * then a slam on a downbeat with everything, [MusicLayer.FURY] included. Each of its phase changes
 * gets a shorter break of its own. The music never falls back to its bed in the fight: a boss is
 * the most intense the stage gets, and a fight gone quiet for a bar at every phase sounded as if it
 * had ended. Its exit is the screen's: the music stops dead as the boss goes down, handing over to
 * the victory fanfare.
 *
 * Every layer change lands on the beat, or for a slam on the bar, because [LayeredMusic] schedules
 * it; this class only says what should be playing.
 *
 * On the endless stage there is no last wave for the floor to rise toward: it rises with every wave
 * of the run instead ([endlessIntensity]), with the combo building on it as ever, so the layers come
 * in one by one as the run goes on until the whole piece is up whatever the streak, and from
 * [ENDLESS_FURY_WAVE] the heavy layer stays in too.
 *
 * @param bossWave the wave the boss arrives on; the floor rises across the waves before it.
 * @param difficulty the stage's own, where stage 1 is 1: a harder stage opens on a higher floor.
 * @param endless whether this is the endless stage's music, whose floor rises with the run's waves.
 * @param bossArriving the music is opened for the boss's arrival, a piece of the boss's own: it
 *   starts in the break, from its first note, rather than on its bed.
 */
class MusicDirector(
    private val music: LayeredMusic,
    grid: MusicGrid,
    private val bossWave: Int,
    private val difficulty: Float,
    private val endless: Boolean = false,
    bossArriving: Boolean = false,
) {
    /** What each layer was last set to, so a level is only ordered when it changes. */
    private val ordered = FloatArray(MusicLayer.entries.size) { -1f }

    private val beatSeconds = (60.0 / grid.beatsPerMinute).toFloat()
    private val barSeconds = beatSeconds * grid.beatsPerBar

    /** Time left in a break; above zero, only the [BREAK] layers are up over the bed. */
    private var breakSeconds = 0f

    /** Set as a break runs out: the next orders are the slam, and land on a downbeat. */
    private var slamming = false

    /** Time until a lost streak may take another layer away; see [update]. */
    private var releaseSeconds = 0f

    /** How much of a hit's thud is left, from 1 as it lands to 0 once it has passed. */
    private var thud = 0f

    init {
        order(MusicLayer.BED, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
        if (bossArriving) {
            // Nothing has played yet, so the break is up from the top rather than a beat in.
            for (layer in BREAK) order(layer, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
            onBossArrived()
        }
    }

    /**
     * Brings the music into line with the run. Called every frame, whatever the run is doing, so a
     * muffle can lift while the world stands still.
     *
     * @param suspended the run is holding still for the player to choose something.
     */
    fun update(
        deltaTime: Float,
        waveIndex: Int,
        comboMultiplier: Int,
        bossFight: Boolean,
        suspended: Boolean,
    ) {
        thud = max(0f, thud - deltaTime / THUD_SECONDS)
        releaseSeconds = max(0f, releaseSeconds - deltaTime)
        if (breakSeconds > 0f) {
            breakSeconds -= deltaTime
            if (breakSeconds <= 0f) slamming = true
        }
        val breaking = breakSeconds > 0f
        val intensity =
            if (endless) endlessIntensity(waveIndex, comboMultiplier) else intensity(waveIndex, bossWave, comboMultiplier, difficulty)

        // The highest layer still up that the music no longer calls for, if any.
        var release: MusicLayer? = null
        for (layer in LAYERED) {
            val wanted = when {
                breaking -> layer in BREAK
                bossFight -> true
                layer == MusicLayer.FURY ->
                    comboMultiplier >= ComboHeat.SUPERNOVA || endless && waveIndex >= ENDLESS_FURY_WAVE
                else -> intensity >= threshold(layer) - EPSILON
            }
            when {
                wanted -> {
                    // The heavy layer always comes in on a downbeat, for the boss's slam or a
                    // supernova streak alike.
                    val quantum = if (slamming || layer == MusicLayer.FURY) Quantum.BAR else Quantum.BEAT
                    order(layer, 1f, quantum, if (slamming) SLAM_FADE_BEATS else LayeredMusic.DEFAULT_FADE_BEATS)
                }
                // The break is a cut, all at once on the beat: the gap is what makes the slam land.
                breaking -> order(layer, 0f, Quantum.BEAT, BREAK_FADE_BEATS)
                ordered[layer.ordinal] == 1f -> release = layer
            }
        }
        // A lost streak takes one layer a beat, from the top down, so a hit at a high streak winds
        // the music down over a few beats instead of collapsing it to the floor at once.
        if (release != null && releaseSeconds <= 0f) {
            order(release, 0f, Quantum.BEAT, RELEASE_FADE_BEATS)
            releaseSeconds = beatSeconds
        }
        slamming = false

        music.setMuffle(maxOf(if (suspended) SUSPENDED_MUFFLE else 0f, thud * THUD_MUFFLE))
        music.setVolume(if (suspended) SUSPENDED_VOLUME else 1f - thud * THUD_DIP)
    }

    /** The bat took a hit. The combo it cost is read on the next [update]; this is the thud. */
    fun onPlayerHit() {
        thud = 1f
    }

    /** The boss is here: a break of most of a bar, then everything slams in on a downbeat. */
    fun onBossArrived() {
        breakSeconds = barSeconds * ARRIVAL_BREAK_BARS
    }

    /**
     * The boss has changed phase: a shorter break than its arrival's, so the phase is marked
     * without the music letting go of the fight.
     */
    fun onBossPhaseChanged() {
        breakSeconds = barSeconds * PHASE_BREAK_BARS
    }

    /** Sets [layer] to [level], unless that is what it was last set to. */
    private fun order(layer: MusicLayer, level: Float, quantum: Quantum, fadeBeats: Float) {
        if (ordered[layer.ordinal] == level) return
        ordered[layer.ordinal] = level
        music.setLayerLevel(layer.ordinal, level, quantum, fadeBeats)
    }

    companion object {
        /** Every layer the director turns up and down, bottom up; the bed is always up. */
        private val LAYERED = MusicLayer.entries - MusicLayer.BED

        /**
         * What plays over the bed through a boss's break: the bass, the tune, the rolling hats and
         * the voices, everything but the drums' weight, so the fight drives on into the slam.
         */
        private val BREAK = setOf(MusicLayer.PULSE, MusicLayer.LEAD, MusicLayer.ROLL, MusicLayer.CHOP)

        /**
         * How much is going on, from about 0.1 in stage 1's opening seconds upward, compared
         * against each layer's [threshold].
         *
         * The floor rises evenly across the waves before the boss, by [WAVE_RISE]
         * over the stage, and starts higher on a harder stage. Each rung of the
         * combo's heat ladder adds [COMBO_STEP], up to [COMBO_RUNGS] rungs: HOT at
         * a streak of 3, BLAZING at 9, SCORCHING at 15, INFERNO at 24, HELLFIRE at
         * 36, BLUE FLAME at 51, WHITE HOT at 72 and SUPERNOVA at 102.
         */
        fun intensity(
            waveIndex: Int,
            bossWave: Int,
            comboMultiplier: Int,
            difficulty: Float
        ): Float {
            val progress =
                if (bossWave <= 1) 1f else (waveIndex.toFloat() / (bossWave - 1)).coerceIn(0f, 1f)
            val floor = OPENING + WAVE_RISE * progress + (difficulty - 1f) * PER_DIFFICULTY
            val combo = COMBO_STEP * ComboHeat.rung(comboMultiplier).coerceAtMost(COMBO_RUNGS)
            return floor + combo
        }

        /**
         * How much is going on in the endless stage, against the same thresholds as a stage's
         * [intensity]: a floor that opens with the bass already in and rises [ENDLESS_WAVE_RISE] with
         * every wave of the run, bringing in the drums by the fourth, the tune by the sixth and the
         * whole trap beat by the ninth, plus the combo's rungs as on a stage.
         *
         * @param waveIndex the wave of the whole run, from 0, across every round.
         */
        fun endlessIntensity(waveIndex: Int, comboMultiplier: Int): Float {
            val floor = ENDLESS_OPENING + ENDLESS_WAVE_RISE * waveIndex
            return floor + COMBO_STEP * ComboHeat.rung(comboMultiplier).coerceAtMost(COMBO_RUNGS)
        }

        /** The run's wave from which the endless stage keeps its heavy layer up, streak or none. */
        const val ENDLESS_FURY_WAVE = 15

        private const val ENDLESS_OPENING = 0.2f
        private const val ENDLESS_WAVE_RISE = 0.08f

        /**
         * Where each layer comes in, tuned against stage 1, whose first wave is the
         * bed alone: HOT brings in the pulse and SCORCHING the drums, which by the
         * last wave are in without a streak. The melody rewards a sustained streak
         * and gets easier to earn as the stage goes on: HELLFIRE in the first wave,
         * then INFERNO, SCORCHING, BLAZING, and any fire in the last.
         *
         * Over the melody the layers are a rung apart: in the first wave BLUE FLAME brings the
         * 808s, WHITE HOT the rolling hats and SUPERNOVA the chopped voices; in the last, BLAZING,
         * SCORCHING and INFERNO do. The boss brings everything.
         */
        fun threshold(layer: MusicLayer): Float = when (layer) {
            MusicLayer.BED -> 0f
            MusicLayer.PULSE -> 0.2f
            MusicLayer.DRIVE -> 0.4f
            MusicLayer.LEAD -> 0.6f
            MusicLayer.BOOM -> 0.7f
            MusicLayer.ROLL -> 0.8f
            MusicLayer.CHOP -> 0.9f
            // The boss's, or a supernova streak's; never the intensity's.
            MusicLayer.FURY -> Float.MAX_VALUE
        }

        private const val OPENING = 0.1f
        private const val WAVE_RISE = 0.4f
        private const val PER_DIFFICULTY = 0.3f
        private const val COMBO_STEP = 0.1f

        /** The rungs the music climbs with the HUD: every one it names, up to SUPERNOVA. */
        private val COMBO_RUNGS = ComboHeat.rung(ComboHeat.SUPERNOVA)

        /** Sums of tenths do not always land on a tenth in floating point. */
        private const val EPSILON = 1e-4f

        /** A layer a lost streak takes away plays out its beat and fades over the next. */
        private const val RELEASE_FADE_BEATS = 1f

        /** The break is a cut, not a fade: the gap is what makes the slam land. */
        private const val BREAK_FADE_BEATS = 0.0625f

        /**
         * How long a break runs before its slam waits for the downbeat. The arrival's is most of a
         * bar, so a piece opened for the boss slams in on its second bar, and one already playing
         * within two; a phase's is half a bar, so its slam lands within a bar and a half.
         */
        private const val ARRIVAL_BREAK_BARS = 0.75f
        private const val PHASE_BREAK_BARS = 0.5f

        /** The slam arrives all at once, on the downbeat. */
        private const val SLAM_FADE_BEATS = 0.03125f

        /**
         * The level-up dialog: muffled to the bass and kick and a little quieter, so the music
         * stays without competing for attention while the player reads.
         */
        private const val SUSPENDED_MUFFLE = 0.8f
        private const val SUSPENDED_VOLUME = 0.75f

        /** A hit's thud: a muffle and a dip that clear over about half a second. */
        private const val THUD_SECONDS = 0.45f
        private const val THUD_MUFFLE = 0.6f
        private const val THUD_DIP = 0.25f
    }
}
