package at.smiech.cyanbat.ui.game

import at.smiech.engine.Graphics
import kotlin.random.Random

/**
 * The fire the combo readout burns in: a small grid of heat that rises a row a tick, cooling and
 * drifting as it goes, fed from whichever cells are stoked.
 *
 * The classic demoscene fire effect. Every tick each cell takes its heat from a cell in the row
 * below (straight under it, or a step to one side, more often the right, so the flames stream back
 * as behind a bat flying right) and loses some on the way. Stoke hotter and the flames stand
 * taller; stop, and the fire goes out bottom to top in as many ticks as it is tall.
 *
 * Ticked on the game's fixed tick, so it freezes on pause. Drawn row by row as runs of one color,
 * which draws a roaring fire's 1600 or so cells in about 600 rectangles.
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
        // Four random bits per cell (two for the source, two for the cooling), eight cells per Int.
        var bits = 0
        var bitsLeft = 0
        // Every cell takes its heat from below, where the classic fire hands it up:
        // handed up, a cell nobody hands anything to keeps stale heat, which left a
        // column standing at the edge of a fire this narrow. In place, top row
        // first, so each row is read before it is overwritten.
        for (y in 0 until height - 1) {
            val row = y * width
            val below = row + width
            for (x in 0 until width) {
                if (bitsLeft == 0) {
                    bits = random.nextInt()
                    bitsLeft = 8
                }
                // Clamped at the edges rather than wrapped, or a flame leaving one side would
                // reappear at the other.
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
     * Draws the fire with its top left corner at [left], [top], each cell a [cell]-pixel square.
     *
     * [palette] runs from the coolest color at index 1 to the hottest at its end;
     * index 0, no fire, is never drawn. A cell at [fullHeat] or hotter takes the
     * hottest color and cooler cells their share of the rest, so a small fire still
     * has a bright heart: its height shows its heat, not its color.
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
                while (end < width && levelOf(
                        cells[row + end].toInt(),
                        levels,
                        fullHeat
                    ) == level
                ) end++
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

    /** A cell's palette index, rounded up so the faintest cell still shows in the coolest color. */
    private fun levelOf(cell: Int, levels: Int, fullHeat: Int): Int =
        if (cell <= 0) 0 else ((cell * levels + fullHeat - 1) / fullHeat).coerceAtMost(levels)

    private companion object {
        /**
         * Where below a cell takes its heat from, by a two-bit roll: left, straight under, or right
         * twice as often, so the flames lean left a quarter of a cell per row. The classic fire's
         * half a cell blew the flames clean off the letters.
         */
        val SOURCE = intArrayOf(-1, 0, 1, 1)

        /** The heat lost per row, by another roll: one on average, unevenly. */
        val COOLING = intArrayOf(0, 1, 1, 2)
    }
}
