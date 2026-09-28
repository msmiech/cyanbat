package at.smiech.cyanbat.ui.game

import at.smiech.engine.Graphics
import kotlin.random.Random

/**
 * The fire the combo readout burns in: a small grid of heat that rises a row a tick, cooling and
 * drifting as it goes, fed from whichever cells are stoked.
 *
 * The old demoscene fire, the one Doom's console ports opened on. Every tick each cell takes its
 * heat from a cell in the row below - straight under it, or a step to one side, more often the
 * right than the left, so the flames stream back the way a bat flying right would leave them - and
 * loses some of it on the way. Stoke hotter and the flames stand taller; stop stoking and the fire goes out, bottom to
 * top, in as many ticks as it is tall.
 *
 * Ticked on the game's fixed tick like everything else, so it freezes with the run on pause. Drawn
 * a row at a time as runs of one color, which draws the sixteen hundred or so cells of a roaring
 * fire in some six hundred rectangles.
 *
 * @param width cells across.
 * @param height cells up, which is the tallest the flames can stand.
 */
internal class ComboFire(
    val width: Int,
    val height: Int,
    private val random: Random = Random.Default,
) {
    /** Each cell's heat, row by row from the top. */
    private val cells = ByteArray(width * height)

    /** Whether anything is burning; an unlit fire costs nothing to tick or draw. */
    var lit = false
        private set

    /**
     * Lets every row rise one. Stoke after this rather than before: the rise fills each row from
     * the one below it, which under a stoked cell is usually nothing, and would put the fire out.
     */
    fun rise() {
        if (!lit) return
        var burning = false
        // Four bits of randomness a cell - two for where it takes from, two for the cooling - drawn
        // eight cells to a number.
        var bits = 0
        var bitsLeft = 0
        // Every cell takes its heat from one below it, where the classic fire has every cell hand
        // its heat to one above. Handed up, a cell nobody hands anything to keeps the heat it had a
        // tick ago, and down the edge of a fire this narrow that leaves a column of stale heat
        // standing; taken, every cell is fresh every tick. In place, top row first, because each row
        // is read into the one above before the one below overwrites it.
        for (y in 0 until height - 1) {
            val row = y * width
            val below = row + width
            for (x in 0 until width) {
                if (bitsLeft == 0) {
                    bits = random.nextInt()
                    bitsLeft = 8
                }
                // Clamped at the edges rather than wrapped, or a flame leaving one side would come
                // back in at the other a row higher.
                val from = below + (x + SOURCE[bits and 3]).coerceIn(0, width - 1)
                val heat = (cells[from] - COOLING[(bits ushr 2) and 3]).coerceAtLeast(0)
                cells[row + x] = heat.toByte()
                if (heat > 0) burning = true
                bits = bits ushr 4
                bitsLeft--
            }
        }
        // The bottom row has nothing under it to rise from.
        cells.fill(0, (height - 1) * width, height * width)
        lit = burning
    }

    /** Sets the cell at [x] in [row] to [heat], where the fire is being fed; off the grid is ignored. */
    fun stoke(x: Int, row: Int, heat: Int) {
        if (heat <= 0 || x !in 0 until width || row !in 0 until height) return
        cells[row * width + x] = heat.coerceAtMost(Byte.MAX_VALUE.toInt()).toByte()
        lit = true
    }

    /**
     * Puts the fire on screen with its top left corner at [left], [top], each cell a [cell] pixel
     * square.
     *
     * [palette] runs from the coolest color at index 1 to the hottest at its end; index 0 is never
     * drawn, since it is where there is no fire. A cell at [fullHeat] or hotter takes the hottest
     * color and cooler cells take their share of the rest, so a small fire still has a bright heart:
     * how tall it stands says how hot it is, not how dim.
     */
    fun draw(g: Graphics, left: Int, top: Int, cell: Int, palette: IntArray, fullHeat: Int) {
        if (!lit || fullHeat <= 0) return
        val levels = palette.size - 1
        for (y in 0 until height) {
            val row = y * width
            var x = 0
            while (x < width) {
                val level = levelOf(cells[row + x].toInt(), levels, fullHeat)
                if (level == 0) {
                    x++
                    continue
                }
                var end = x + 1
                while (end < width && levelOf(cells[row + end].toInt(), levels, fullHeat) == level) end++
                g.drawRect(left + x * cell, top + y * cell, (end - x) * cell, cell, palette[level])
                x = end
            }
        }
    }

    /** How many rows up from the bottom the highest burning cell is; 0 when nothing burns. */
    fun flameHeight(): Int {
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (cells[row + x] > 0) return height - y
            }
        }
        return 0
    }

    /** Rounded up, so the faintest cell still shows as the coolest color rather than as nothing. */
    private fun levelOf(cell: Int, levels: Int, fullHeat: Int): Int =
        if (cell <= 0) 0 else ((cell * levels + fullHeat - 1) / fullHeat).coerceAtMost(levels)

    private companion object {
        /**
         * Where under it a cell takes its heat from, by a two-bit roll: the left, straight under, or
         * the right twice as often - so the flames lean left by a quarter of a cell a row, which is
         * the streaming back. The classic fire's half a cell blows it clean off the letters it is
         * meant to be burning on.
         */
        val SOURCE = intArrayOf(-1, 0, 1, 1)

        /** What it loses on the way, by another: a degree a row on average, and never quite evenly. */
        val COOLING = intArrayOf(0, 1, 1, 2)
    }
}
