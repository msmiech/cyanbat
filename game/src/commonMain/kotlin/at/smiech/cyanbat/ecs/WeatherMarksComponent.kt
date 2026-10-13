package at.smiech.cyanbat.ecs

import at.smiech.engine.ecs.Component

/**
 * What the weather has left on a creature: snow settled on its back, rain beading and running down
 * it, soot, an ember still glowing. Each mark is pinned to a column of the creature's sprite and
 * placed against the outline of whatever frame it is showing, so snow on a bat's wing rises and
 * falls with the wingbeat; see [WeatherSystem].
 *
 * A fixed handful of marks, kept in arrays and recycled oldest first, so a long snowfall on a boss
 * allocates nothing. The marks go with the creature, since a component goes with its entity.
 */
class WeatherMarksComponent : Component {
    /** How many of the slots hold a mark. */
    var count = 0

    /** The sprite column each mark is pinned to, in the sprite's own pixels. */
    val column = IntArray(CAPACITY)

    /**
     * Where along that column it sits, in the sprite's pixels from the outline's top there:
     * negative above it, as settled snow is, positive down the creature's side.
     */
    val depth = FloatArray(CAPACITY)

    /** What it is: one of [WeatherSystem]'s mark kinds. */
    val kind = IntArray(CAPACITY)

    /** Seconds since it landed. */
    val age = FloatArray(CAPACITY)

    /** Adds a mark, pushing out the oldest once every slot is taken. */
    fun add(column: Int, depth: Float, kind: Int) {
        val slot = if (count < CAPACITY) count++ else oldest()
        this.column[slot] = column
        this.depth[slot] = depth
        this.kind[slot] = kind
        age[slot] = 0f
    }

    /** Takes mark [slot] off, moving the last mark into its place. */
    fun remove(slot: Int) {
        val last = --count
        column[slot] = column[last]
        depth[slot] = depth[last]
        kind[slot] = kind[last]
        age[slot] = age[last]
    }

    /** How many marks sit on [column] already, of [kind]: how high its snow is piled. */
    fun pile(column: Int, kind: Int): Int {
        var pile = 0
        for (i in 0 until count) if (this.column[i] == column && this.kind[i] == kind) pile++
        return pile
    }

    private fun oldest(): Int {
        var slot = 0
        for (i in 1 until count) if (age[i] > age[slot]) slot = i
        return slot
    }

    companion object {
        /** The most marks one creature carries. */
        const val CAPACITY = 40
    }
}
