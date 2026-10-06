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
 * so the player hears how well they are doing.
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
 * The boss gets an entrance: the music drops to its bed for a bar and slams back in on a downbeat
 * with everything, [MusicLayer.FURY] included, and again at each of its phase changes. Its exit is
 * the screen's: the music stops dead as the boss goes down, handing over to the victory fanfare.
 *
 * Every layer change lands on the beat, or for a slam on the bar, because [LayeredMusic] schedules
 * it; this class only says what should be playing.
 *
 * @param bossWave the wave the boss arrives on; the floor rises across the waves before it.
 * @param difficulty the stage's own, where stage 1 is 1: a harder stage opens on a higher floor.
 */
class MusicDirector(
    private val music: LayeredMusic,
    grid: MusicGrid,
    private val bossWave: Int,
    private val difficulty: Float,
) {
    /** What each layer was last set to, so a level is only ordered when it changes. */
    private val ordered = FloatArray(MusicLayer.entries.size) { -1f }

    /** A drop takes this long before the slam: a bar, give or take where in the bar it began. */
    private val dropLengthSeconds = (grid.beatsPerBar * 60.0 / grid.beatsPerMinute).toFloat()

    /** Time left in a drop; above zero, everything but the bed is out. */
    private var dropSeconds = 0f

    /** Set as a drop runs out: the next orders are the slam, and land on a downbeat. */
    private var slamming = false

    /** How much of a hit's thud is left, from 1 as it lands to 0 once it has passed. */
    private var thud = 0f

    init {
        order(MusicLayer.BED, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
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
        if (dropSeconds > 0f) {
            dropSeconds -= deltaTime
            if (dropSeconds <= 0f) slamming = true
        }
        val dropping = dropSeconds > 0f
        val intensity = intensity(waveIndex, bossWave, comboMultiplier, difficulty)

        for (layer in LAYERED) {
            val wanted = when {
                dropping -> false
                bossFight -> true
                layer == MusicLayer.FURY -> comboMultiplier >= ComboHeat.SUPERNOVA
                else -> intensity >= threshold(layer) - EPSILON
            }
            val fade = when {
                wanted && slamming -> SLAM_FADE_BEATS
                wanted -> LayeredMusic.DEFAULT_FADE_BEATS
                dropping -> DROP_FADE_BEATS
                else -> FALL_FADE_BEATS
            }
            // The heavy layer always comes in on a downbeat, for the boss's slam or
            // a supernova streak alike.
            val quantum =
                if (slamming || (wanted && layer == MusicLayer.FURY)) Quantum.BAR else Quantum.BEAT
            order(layer, if (wanted) 1f else 0f, quantum, fade)
        }
        slamming = false

        music.setMuffle(maxOf(if (suspended) SUSPENDED_MUFFLE else 0f, thud * THUD_MUFFLE))
        music.setVolume(if (suspended) SUSPENDED_VOLUME else 1f - thud * THUD_DIP)
    }

    /** The bat took a hit. The combo it cost is read on the next [update]; this is the thud. */
    fun onPlayerHit() {
        thud = 1f
    }

    /** The boss is here: drop to the bed, then slam everything in on a downbeat. */
    fun onBossArrived() = drop()

    /** The boss has changed phase, which gets the same entrance as the boss itself. */
    fun onBossPhaseChanged() = drop()

    /** Starts a one-bar drop to the bed, which ends in a slam. */
    private fun drop() {
        dropSeconds = dropLengthSeconds
    }

    /** Sets [layer] to [level], unless that is what it was last set to. */
    private fun order(layer: MusicLayer, level: Float, quantum: Quantum, fadeBeats: Float) {
        if (ordered[layer.ordinal] == level) return
        ordered[layer.ordinal] = level
        music.setLayerLevel(layer.ordinal, level, quantum, fadeBeats)
    }

    companion object {
        /** Every layer the director turns up and down; the bed is always up. */
        private val LAYERED = MusicLayer.entries - MusicLayer.BED

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

        /** A layer leaving because the streak broke fades over a quarter of a beat. */
        private const val FALL_FADE_BEATS = 0.25f

        /** The drop is a cut, not a fade: the silence is what makes the slam land. */
        private const val DROP_FADE_BEATS = 0.0625f

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
