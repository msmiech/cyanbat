package at.smiech.cyanbat.resource

import at.smiech.cyanbat.music.MusicLayer
import at.smiech.engine.MusicGrid

/**
 * A stage's music: one piece cut into a stem per [MusicLayer], and the grid it was written on.
 *
 * A description rather than something loaded, because only the stage being flown needs its stems,
 * and a run with music turned off needs none at all. `GameScreen` opens them when it starts
 * playing.
 *
 * The stems are generated, like the art: `tools/generate_<name>_music.py` writes them, at the tempo
 * and meter [grid] repeats here. `MusicStemTest` holds the two to each other.
 *
 * @param name the stems are `music/<name>_<layer>.wav`, one for each [MusicLayer].
 * @param boss a piece of its own for the boss fight, which the run hands over to as the boss
 *   arrives; null for a stage whose boss is fought to its own music, with its heavy layer up.
 */
data class StageMusic(val name: String, val grid: MusicGrid, val boss: StageMusic? = null) {
    /** Every stem's asset path, in [MusicLayer] order. */
    val stems: List<String> get() = MusicLayer.entries.map { "music/${name}_${it.fileSuffix}.wav" }

    /** This piece and the boss's, if it has one of its own: every set of stems the stage plays. */
    val pieces: List<StageMusic> get() = listOfNotNull(this, boss)
}
