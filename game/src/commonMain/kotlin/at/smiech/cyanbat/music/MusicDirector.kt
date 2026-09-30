package at.smiech.cyanbat.music

import at.smiech.engine.LayeredMusic
import at.smiech.engine.MusicGrid
import at.smiech.engine.Quantum
import kotlin.math.max

/**
 * Decides, a frame at a time, how much of a stage's music is playing and how it sounds.
 *
 * Two dials set how many layers are up. How far into the stage the run is raises a floor: the
 * opening minute of the cave is its bed alone, and by the last wave the drums are in whatever the
 * player does. The combo builds on that floor, a layer at a time, and a hit takes it away again -
 * the streak is the thing the player is actually doing well or badly at, and they should be able
 * to hear it. That is the Doom 2016 and Eternal idea: the music is only as intense as the fighting.
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
                layer == MusicLayer.FURY -> false
                else -> intensity >= threshold(layer) - EPSILON
            }
            val fade = when {
                wanted && slamming -> SLAM_FADE_BEATS
                wanted -> LayeredMusic.DEFAULT_FADE_BEATS
                cleared -> CLEARED_FADE_BEATS
                dropping -> DROP_FADE_BEATS
                else -> FALL_FADE_BEATS
            }
            order(layer, if (wanted) 1f else 0f, if (slamming) Quantum.BAR else Quantum.BEAT, fade)
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
         * Each step of the combo multiplier adds [COMBO_STEP] on top, up to [COMBO_STEPS] steps. At
         * the default three kills a step, a streak of 3 brings a layer in and one of 12 is as far as
         * it goes; past that, a longer streak is more points but not more music.
         */
        fun intensity(waveIndex: Int, bossWave: Int, comboMultiplier: Int, difficulty: Float): Float {
            val progress = if (bossWave <= 1) 1f else (waveIndex.toFloat() / (bossWave - 1)).coerceIn(0f, 1f)
            val floor = OPENING + WAVE_RISE * progress + (difficulty - 1f) * PER_DIFFICULTY
            val combo = COMBO_STEP * (comboMultiplier - 1).coerceIn(0, COMBO_STEPS)
            return floor + combo
        }

        /**
         * Where each layer comes in. Tuned against stage 1: its first wave is the bed alone, a
         * streak of 3 brings the pulse in and one of 9 the drums. The melody needs a streak of 12
         * from the third wave on, or of 6 in the last, where the drums are in without any streak
         * at all.
         */
        fun threshold(layer: MusicLayer): Float = when (layer) {
            MusicLayer.BED -> 0f
            MusicLayer.PULSE -> 0.2f
            MusicLayer.DRIVE -> 0.4f
            MusicLayer.LEAD -> 0.7f
            // Boss only, whatever the intensity.
            MusicLayer.FURY -> Float.MAX_VALUE
        }

        private const val OPENING = 0.1f
        private const val WAVE_RISE = 0.4f
        private const val PER_DIFFICULTY = 0.3f
        private const val COMBO_STEP = 0.1f
        private const val COMBO_STEPS = 4

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
