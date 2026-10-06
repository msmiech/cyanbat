package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.progress.PowerUp
import kotlin.math.roundToInt

/**
 * How much of a stage the footage gives away: which of the [Montage]'s clips it gets, and how long.
 * Every stage opens on its title; the rest is up to this.
 *
 * @param levelUp whether a level up dialog is shown.
 * @param actionSeconds how long the busiest stretch runs, or 0 for none.
 * @param scenerySeconds how long each clip of a stage's changing look runs, or 0 for none.
 * @param arrivalSeconds how long the boss is shown arriving, or 0 for none.
 * @param finale whether the boss is shown going down, with the stage complete overlay after it.
 */
class Coverage(
    val levelUp: Boolean = false,
    val actionSeconds: Float = 0f,
    val scenerySeconds: Float = 0f,
    val arrivalSeconds: Float = 0f,
    val finale: Boolean = false,
) {
    /**
     * Whether the boss is on screen anywhere in the footage. A run whose boss is not is only flown up
     * to its arrival, and its tape stops short of it, so no frame of the boss can reach the footage.
     */
    val showsBoss: Boolean get() = arrivalSeconds > 0f || finale

    /**
     * Whether the footage is the stage's opening and its hours and nothing else (no level up, no
     * action, no boss), so a run need only be flown as far as its last hour.
     */
    val onlyScenery: Boolean get() = !levelUp && actionSeconds <= 0f && !showsBoss

    companion object {
        /** The whole stage, from its title to the boss going down. */
        val WHOLE = Coverage(
            levelUp = true,
            actionSeconds = 4.6f,
            scenerySeconds = 2.4f,
            arrivalSeconds = 2.2f,
            finale = true,
        )
    }
}

/**
 * Cuts a run down to its footage: which frames go into the GIF, in play order.
 *
 * Up to six clips, each picked for what it shows rather than for where it falls. The opening is
 * always there; each of the rest only if the stage's [Coverage] asks for it:
 * - the opening, with the stage's name over it;
 * - a level up, from just before the dialog opens to the bat flying on with its pick: Spread Shot
 *   where there is one, since that is the pick whose effect shows at once;
 * - the busiest stretch from the stage's most varied wave on, by enemies on screen and blasts,
 *   preferring one the bat comes through without a hit;
 * - for a stage whose look changes with its clock, the hours that show it changing, such as the
 *   desert's sunset, between its noon opening and its boss's night;
 * - the boss arriving under its banner;
 * - the boss going down, and the stage complete overlay after it.
 *
 * Only the level up clip shows a dialog. Anywhere else one is cut out, which leaves no seam: the run
 * stands still while a dialog is up, so the frames either side of it follow on from each other. It
 * matters most around the boss, whose arrival is when the bat mops up the last of the waves and
 * levels.
 */
object Montage {
    /** How long the opening runs, under the stage's name. */
    private const val OPENING_SECONDS = 1.6f

    /** How much of the level up clip comes before the dialog opens. */
    private const val OFFER_LEAD_SECONDS = 0.3f

    /** How much of it comes after the dialog closes, with the bat flying on with its pick. */
    private const val OFFER_TAIL_SECONDS = 1.0f

    /** How much of the arrival clip comes before the boss appears. */
    private const val ARRIVAL_LEAD_SECONDS = 0.2f

    /** How much of the finale comes before the boss goes down. */
    private const val FINALE_LEAD_SECONDS = 2.8f

    /** Clips closer than this are not worth a cut; the two run into one. */
    private const val MERGE_GAP_SECONDS = 1.5f

    /** What a blast on screen counts for in the busiest stretch, against one enemy. */
    private const val BLAST_WEIGHT = 3f

    /** What a hit on the bat costs a stretch: enough that one without a hit nearly always wins. */
    private const val HIT_WEIGHT = 40f

    /** What a wave's banner on screen adds, so the stretch leans toward showing one. */
    private const val BANNER_WEIGHT = 2f

    /**
     * The frames to show, clip by clip.
     *
     * @param actionWave the first wave to take the action from: the stage's most varied, where
     *   every kind of enemy it has is in the air at once. Later waves count too, since they only
     *   get busier on the way to the boss.
     * @param scenery seconds of the stage clock worth a clip for how the stage looks then, each
     *   starting there. Empty for a stage that looks the same from start to finish.
     * @param coverage which of the clips to cut. Short of the whole stage, [moments] need only run as
     *   far as the last of them, and for a boss kept hidden they may stop before it arrives.
     */
    fun cut(
        moments: List<Moment>,
        frameSeconds: Float,
        actionWave: Int,
        scenery: List<Float> = emptyList(),
        coverage: Coverage = Coverage.WHOLE,
    ): List<List<Int>> {
        fun frames(seconds: Float) = (seconds / frameSeconds).roundToInt()
        val last = moments.lastIndex
        val bossAt = moments.indexOfFirst { it.bossSpawned }
        val completeAt = moments.indexOfFirst { it.complete }
        require(!coverage.showsBoss || bossAt > 0) { "Only a run flown to its boss can show it" }
        require(!coverage.finale || completeAt > bossAt) {
            "Only a run that beat its boss can show it going down"
        }

        val arrival = if (coverage.arrivalSeconds > 0f) {
            earlier(moments, bossAt, frames(ARRIVAL_LEAD_SECONDS))..
                    later(moments, bossAt, frames(coverage.arrivalSeconds))
        } else {
            null
        }
        val finale = if (coverage.finale) {
            val from = earlier(moments, completeAt, frames(FINALE_LEAD_SECONDS))
            from.coerceAtLeast(arrival?.first ?: bossAt)..last
        } else {
            null
        }
        val opening = 0..<frames(OPENING_SECONDS)
        // Everything else comes from between the title and the boss, so a boss the coverage keeps
        // hidden stays hidden even on a tape that runs on past its arrival.
        val bossFrom = arrival?.first ?: bossAt.takeIf { it >= 0 } ?: moments.size
        val waves = (opening.last + 1)..<bossFrom

        val levelUp = if (coverage.levelUp) {
            levelUp(moments, waves, frames(OFFER_LEAD_SECONDS), frames(OFFER_TAIL_SECONDS))
        } else {
            null
        }
        val action = if (coverage.actionSeconds > 0f) {
            val wanted = waves.filter { moments[it].wave >= actionWave }
            listOfNotNull(wanted.firstOrNull()?.let { it..wanted.last() }, waves)
                .firstNotNullOfOrNull {
                    busiest(moments, outside(it, levelUp), frames(coverage.actionSeconds))
                }
        } else {
            null
        }

        val views = if (coverage.scenerySeconds > 0f) {
            scenery.mapNotNull { seconds ->
                val start =
                    waves.firstOrNull { moments[it].seconds >= seconds && !moments[it].offer }
                start?.let {
                    val end = later(moments, it, frames(coverage.scenerySeconds))
                    it..end.coerceAtMost(waves.last)
                }
            }
        } else {
            emptyList()
        }

        val clips = merge(
            (listOfNotNull(opening, levelUp, action, arrival, finale) + views).sortedBy { it.first },
            frames(MERGE_GAP_SECONDS),
        )
        // Dialogs are cut out of every clip but the level up's.
        return clips.map { clip ->
            clip.filter { !moments[it].offer || (levelUp != null && it in levelUp) }
        }
    }

    /** The frame [count] dialog-free frames before [from]. */
    private fun earlier(moments: List<Moment>, from: Int, count: Int): Int {
        var i = from
        var left = count
        while (i > 0 && left > 0) {
            i--
            if (!moments[i].offer) left--
        }
        return i
    }

    /** The frame [count] dialog-free frames after [from]. */
    private fun later(moments: List<Moment>, from: Int, count: Int): Int {
        var i = from
        var left = count
        while (i < moments.lastIndex && left > 0) {
            i++
            if (!moments[i].offer) left--
        }
        return i
    }

    /**
     * A level up whose clip fits inside [span], from [lead] frames before the dialog opens to [tail]
     * frames after it closes: the last one that took Spread Shot, or failing that the last of all.
     */
    private fun levelUp(moments: List<Moment>, span: IntRange, lead: Int, tail: Int): IntRange? {
        var last: IntRange? = null
        var lastSpread: IntRange? = null
        var i = span.first
        while (i <= span.last) {
            if (!moments[i].offer) {
                i++
                continue
            }
            val opened = i
            while (i <= span.last && moments[i].offer) i++
            val clip = (opened - lead)..(i - 1 + tail)
            if (clip.first < span.first || clip.last > span.last) continue
            last = clip
            // Anywhere in the run of dialogs, for a kill that bought more than one level.
            if ((opened..i).any { moments[it].pick == PowerUp.SPREAD_SHOT }) lastSpread = clip
        }
        return lastSpread ?: last
    }

    /** The longer part of [span] either side of [taken], or all of it when the two do not meet. */
    private fun outside(span: IntRange, taken: IntRange?): IntRange {
        if (taken == null || taken.last < span.first || taken.first > span.last) return span
        val before = span.first..<taken.first
        val after = (taken.last + 1)..span.last
        return if (before.count() >= after.count()) before else after
    }

    /** The [length]-frame window of [span] with the most going on, and no dialog in it. */
    private fun busiest(moments: List<Moment>, span: IntRange, length: Int): IntRange? {
        if (span.count() < length) return null
        // A wave's banner by its English word, which is the language the recorder runs in.
        fun weight(m: Moment): Float =
            if (m.offer) Float.NEGATIVE_INFINITY
            else m.enemies + BLAST_WEIGHT * m.blasts - (if (m.hit) HIT_WEIGHT else 0f) +
                    (if (m.banner?.startsWith("WAVE") == true) BANNER_WEIGHT else 0f)

        var bestStart = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (start in span.first..(span.last - length + 1)) {
            var score = 0f
            for (i in start until start + length) score += weight(moments[i])
            if (score > bestScore) {
                bestScore = score
                bestStart = start
            }
        }
        return if (bestStart < 0) null else bestStart..<(bestStart + length)
    }

    /** [clips], in order, with any closer together than [gap] frames joined into one. */
    private fun merge(clips: List<IntRange>, gap: Int): List<IntRange> {
        val merged = mutableListOf<IntRange>()
        for (clip in clips) {
            val previous = merged.lastOrNull()
            if (previous != null && clip.first <= previous.last + gap) {
                merged[merged.lastIndex] = previous.first..maxOf(previous.last, clip.last)
            } else {
                merged += clip
            }
        }
        return merged
    }
}
