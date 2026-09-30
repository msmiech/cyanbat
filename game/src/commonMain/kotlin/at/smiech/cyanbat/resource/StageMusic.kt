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
 */
data class StageMusic(val name: String, val grid: MusicGrid) {
    /** Every stem's asset path, in [MusicLayer] order. */
    val stems: List<String> get() = MusicLayer.entries.map { "music/${name}_${it.fileSuffix}.wav" }
}
