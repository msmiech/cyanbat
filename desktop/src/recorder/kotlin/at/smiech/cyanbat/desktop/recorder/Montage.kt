package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.progress.PowerUp
import kotlin.math.roundToInt

/**
 * Cuts a won run down to its footage: which frames go into the GIF, in play order.
 *
 * Five clips, each picked for what it shows rather than for where it falls:
 * - the opening, with the stage's name over it;
 * - a level up, from just before the dialog opens to the bat flying on with its pick - Spread Shot
 *   where there is one, since that is the pick whose effect shows at once;
 * - the busiest stretch from the stage's most varied wave on, by enemies on screen and blasts,
 *   preferring one the bat comes through without a hit;
 * - the boss arriving under its banner;
 * - the boss going down, and the stage complete overlay after it.
 *
 * Only the level up clip shows a dialog. Anywhere else one is cut out, which leaves no seam: the run
 * stands still while a dialog is up, so the frames either side of it follow on from each other. It
 * matters most around the boss, whose arrival is when the bat mops up the last of the waves and
 * levels.
 */
object Montage {
    private const val OPENING_SECONDS = 1.6f
    private const val ACTION_SECONDS = 4.6f
    private const val OFFER_LEAD_SECONDS = 0.3f
    private const val OFFER_TAIL_SECONDS = 1.0f
    private const val ARRIVAL_LEAD_SECONDS = 0.2f
    private const val ARRIVAL_SECONDS = 2.2f
    private const val FINALE_LEAD_SECONDS = 2.8f

    /** Clips closer than this are not worth a cut; the two run into one. */
    private const val MERGE_GAP_SECONDS = 1.5f

    private const val BLAST_WEIGHT = 3f
    private const val HIT_WEIGHT = 40f
    private const val BANNER_WEIGHT = 2f

    /**
     * The frames to show, clip by clip.
     *
     * @param actionWave the first wave to take the action from: the stage's most varied, where
     *   every kind of enemy it has is in the air at once. Later waves count too, since they only
     *   get busier on the way to the boss.
     */
    fun cut(moments: List<Moment>, frameSeconds: Float, actionWave: Int): List<List<Int>> {
        fun frames(seconds: Float) = (seconds / frameSeconds).roundToInt()
        val last = moments.lastIndex
        val bossAt = moments.indexOfFirst { it.bossSpawned }
        val completeAt = moments.indexOfFirst { it.complete }
        require(bossAt > 0 && completeAt > bossAt) { "Only a run that beat its boss can be cut" }

        val arrival = earlier(moments, bossAt, frames(ARRIVAL_LEAD_SECONDS))..later(moments, bossAt, frames(ARRIVAL_SECONDS))
        val finale = earlier(moments, completeAt, frames(FINALE_LEAD_SECONDS)).coerceAtLeast(arrival.first)..last
        val opening = 0..<frames(OPENING_SECONDS)
        val waves = (opening.last + 1)..<arrival.first

        val levelUp = levelUp(moments, waves, frames(OFFER_LEAD_SECONDS), frames(OFFER_TAIL_SECONDS))
        val wanted = waves.filter { moments[it].wave >= actionWave }
        val action = listOfNotNull(wanted.firstOrNull()?.let { it..wanted.last() }, waves)
            .firstNotNullOfOrNull { busiest(moments, outside(it, levelUp), frames(ACTION_SECONDS)) }

        val clips = merge(
            listOfNotNull(opening, levelUp, action, arrival, finale).sortedBy { it.first },
            frames(MERGE_GAP_SECONDS),
        )
        return clips.map { clip -> clip.filter { !moments[it].offer || (levelUp != null && it in levelUp) } }
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
