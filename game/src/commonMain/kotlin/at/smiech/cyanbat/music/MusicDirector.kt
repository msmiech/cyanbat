package at.smiech.cyanbat.music

import at.smiech.cyanbat.ui.game.ComboHeat
import at.smiech.engine.LayeredMusic
import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum
import kotlin.math.max

/**
 * Decides, a frame at a time, how much of a stage's music is playing and how it sounds.
 *
 * Two dials set how many layers are up. How far into the stage the run is raises a floor: the
 * opening minute of the cave is its bed alone, and by the last wave the drums are in whatever the
 * player does. The combo builds on that floor, and a hit takes it away again - the streak is the
 * thing the player is actually doing well or badly at, and they should be able to hear it. That is
 * the Doom 2016 and Eternal idea: the music is only as intense as the fighting.
 *
 * The combo is heard on the same ladder the HUD's readout burns up ([ComboHeat]): each rung it
 * names, from HOT to SUPERNOVA, is a step of the music, and the steps between rungs are not, so a
 * new title flaring on screen and a new layer arriving are one event. The steps come closer
 * together as the fire climbs: under the tune a layer takes two rungs, over it one, so the hottest
 * streaks get a trap beat's 808s, rolling hats and chopped voices a rung apart. SUPERNOVA calls in
 * the boss's own heavy layer on top. Past it the multiplier climbs on, but the music has nothing
 * left to add.
 *
 * Then there is what the music does at the edges of the action, which is SSX 3's idea: a hit is a
 * thud, the music ducking under a low-pass for a moment the way it does when a rider wipes out;
 * the level up dialog, which holds the run still, carries the music on under a muffle rather than
 * stopping it, and lifting the muffle as the pick is made is the run landing again.
 *
 * And the boss gets an entrance. The music drops to its bed for a bar and slams back in on a
 * downbeat with everything, the heavy [MusicLayer.FURY] stem included - and does the same each
 * time the boss changes phase.
 *
 * Every change of layer lands on the beat, or for a slam on the bar, because [LayeredMusic] puts
 * it there. This class only says what should be playing; it never has to know where the beat is.
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

    private var cleared = false

    init {
        order(MusicLayer.BED, 1f, Quantum.IMMEDIATE, fadeBeats = 0f)
    }

    /**
     * Brings the music into line with the run. Called every frame, whatever the run is doing, so a
     * muffle can open while the world stands still.
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
                cleared || dropping -> false
                bossFight -> true
                layer == MusicLayer.FURY -> comboMultiplier >= ComboHeat.SUPERNOVA
                else -> intensity >= threshold(layer) - EPSILON
            }
            val fade = when {
                wanted && slamming -> SLAM_FADE_BEATS
                wanted -> LayeredMusic.DEFAULT_FADE_BEATS
                cleared -> CLEARED_FADE_BEATS
                dropping -> DROP_FADE_BEATS
                else -> FALL_FADE_BEATS
            }
            // The heavy layer always comes in on a downbeat: whether it is the boss's slam or a
            // streak going supernova, it is a moment the music should be seen to make.
            val quantum = if (slamming || (wanted && layer == MusicLayer.FURY)) Quantum.BAR else Quantum.BEAT
            order(layer, if (wanted) 1f else 0f, quantum, fade)
        }
        slamming = false

        val muffle = maxOf(
            if (suspended) SUSPENDED_MUFFLE else 0f,
            if (cleared) CLEARED_MUFFLE else 0f,
            thud * THUD_MUFFLE,
        )
        music.setMuffle(muffle)
        music.setVolume(
            when {
                suspended -> SUSPENDED_VOLUME
                cleared -> CLEARED_VOLUME
                else -> 1f - thud * THUD_DIP
            }
        )
    }

    /** The bat took a hit. The combo it cost is read on the next [update]; this is the thud. */
    fun onPlayerHit() {
        thud = 1f
    }

    /** The boss is here: drop to the bed, then slam everything in on a downbeat. */
    fun onBossArrived() = drop()

    /** The boss has changed phase, which gets the same entrance as the boss itself. */
    fun onBossPhaseChanged() = drop()

    /** The boss is down. The fight's layers wind down and the bed plays on under the overlay. */
    fun onStageCleared() {
        cleared = true
        dropSeconds = 0f
    }

    private fun drop() {
        if (cleared) return
        dropSeconds = dropLengthSeconds
    }

    private fun order(layer: MusicLayer, level: Float, quantum: Quantum, fadeBeats: Float) {
        if (ordered[layer.ordinal] == level) return
        ordered[layer.ordinal] = level
        music.setLayerLevel(layer.ordinal, level, quantum, fadeBeats)
    }

    companion object {
        /** Every layer the director turns up and down; the bed is always up. */
        private val LAYERED = MusicLayer.entries - MusicLayer.BED

        /**
         * How much is going on, from about 0.1 for the opening seconds of stage 1 upward. Compared
         * against each layer's [threshold]; nothing reads it as a volume.
         *
         * The floor rises evenly across the waves before the boss - [WAVE_RISE] over the whole
         * stage - and sits higher on a harder stage, so stage 3 opens with its bass already moving.
         * Each rung of the combo's heat ladder adds [COMBO_STEP] on top, all [COMBO_RUNGS] of them:
         * HOT at a streak of 3, BLAZING at 9, SCORCHING at 15, INFERNO at 24, HELLFIRE at 36,
         * BLUE FLAME at 51, WHITE HOT at 72 and SUPERNOVA at 102. Past that a longer streak is more
         * points, and a faster fire on the HUD, but not more music.
         */
        fun intensity(waveIndex: Int, bossWave: Int, comboMultiplier: Int, difficulty: Float): Float {
            val progress = if (bossWave <= 1) 1f else (waveIndex.toFloat() / (bossWave - 1)).coerceIn(0f, 1f)
            val floor = OPENING + WAVE_RISE * progress + (difficulty - 1f) * PER_DIFFICULTY
            val combo = COMBO_STEP * ComboHeat.rung(comboMultiplier).coerceAtMost(COMBO_RUNGS)
            return floor + combo
        }

        /**
         * Where each layer comes in. Tuned against stage 1, whose first wave is the bed alone: HOT
         * brings the pulse in, and SCORCHING the drums, which by the last wave are in without any
         * streak at all. The melody is the reward for a fire that keeps burning, and gets easier
         * to earn as the stage goes on - HELLFIRE in the first wave, INFERNO in the second,
         * SCORCHING in the third, BLAZING in the fourth, and any fire at all in the last.
         *
         * Over the melody the layers are a rung apart. In the first wave BLUE FLAME brings the
         * 808s, WHITE HOT the rolling hats, and SUPERNOVA the chopped voices; in the last, BLAZING,
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

        /** A layer leaving because the streak broke goes over a quarter of a beat: a stumble. */
        private const val FALL_FADE_BEATS = 0.25f

        /** The drop is a cut, not a fade: the silence is what makes the slam land. */
        private const val DROP_FADE_BEATS = 0.0625f

        /** The slam arrives all at once, on the downbeat. */
        private const val SLAM_FADE_BEATS = 0.03125f

        /** A won stage winds down over two beats rather than stopping dead. */
        private const val CLEARED_FADE_BEATS = 2f

        /**
         * The level up dialog: muffled to the bass and the thump of the drums, and a little quieter,
         * so the music is still there but not asking for attention while the player reads.
         */
        private const val SUSPENDED_MUFFLE = 0.8f
        private const val SUSPENDED_VOLUME = 0.75f

        /** After the boss: warm, and a step back, under the victory overlay. */
        private const val CLEARED_MUFFLE = 0.35f
        private const val CLEARED_VOLUME = 0.8f

        /** A hit's thud: a muffle and a dip that clear over about half a second. */
        private const val THUD_SECONDS = 0.45f
        private const val THUD_MUFFLE = 0.6f
        private const val THUD_DIP = 0.25f
    }
}
