package at.smiech.cyanbat.music

/**
 * The eight stems every stage's music is cut into, bottom up. Each is a part of one piece rather
 * than a piece of its own, and each adds to the ones under it; see [MusicDirector] for when each
 * one plays.
 *
 * Past the tune, the ladder climbs by a trap beat growing under the stage's own instruments, the
 * way the hottest streaks deserve: its 808s, then its rolling hats, then voices chopped into a hook.
 *
 * The order is the order the stems are handed to the engine, and so each layer's index there.
 */
enum class MusicLayer {
    /** The harmony and the color of the place. Always playing, and a whole piece on its own. */
    BED,

    /** Bass and light percussion: the music starting to move. */
    PULSE,

    /** The full kit. The fight is on. */
    DRIVE,

    /** The tune, which a player earns by keeping a streak going. */
    LEAD,

    /** 808s sliding between the chords, and the kick that goes with them. */
    BOOM,

    /** Hi-hats rolling faster than any hand could, and a snare roll into the top of the loop. */
    ROLL,

    /** A voice chopped into a hook, under the tune. */
    CHOP,

    /** Heavy drums and brass, held back for the boss. */
    FURY;

    /** How the layer's stem is named on disk; see [at.smiech.cyanbat.resource.StageMusic]. */
    val fileSuffix: String get() = name.lowercase()
}
