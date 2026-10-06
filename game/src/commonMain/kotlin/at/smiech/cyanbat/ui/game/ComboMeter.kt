package at.smiech.cyanbat.ui.game

import at.smiech.cyanbat.resource.GameText
import at.smiech.cyanbat.util.COMBO_FLAME_MAX_HEAT
import at.smiech.cyanbat.util.COMBO_FLAME_MIN_HEAT
import at.smiech.cyanbat.util.COMBO_FLARE_HEAT
import at.smiech.cyanbat.util.COMBO_FLARE_SECONDS
import at.smiech.cyanbat.util.COMBO_FONT_SIZE
import at.smiech.cyanbat.util.COMBO_KILL_FLARE
import at.smiech.cyanbat.util.COMBO_POP_GROWTH
import at.smiech.cyanbat.util.COMBO_POP_SECONDS
import at.smiech.cyanbat.util.COMBO_TITLE_POP_GROWTH
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The combo readout: the streak's title and multiplier, set alight once there is a
 * streak and burning hotter the longer it runs. [ComboHeat] says how hot; this
 * animates it, so a number worth something looks it.
 *
 * Each step the count pops (swelling, shaking and flaring its fire) and settles a size bigger, and
 * a new rung pops its title too. Every kill in between fans the flames a little. While the letters
 * burn they bob and flicker in a wave. A hit douses it all: the readout drops back to a cold
 * "Combo: x1" and the fire gutters out over a few ticks.
 *
 * Everything moves on the game's fixed tick, fed to [update], so pausing freezes the flames.
 */
internal class ComboMeter(private val random: Random = Random.Default) {

    private val fire = ComboFire(FIRE_COLUMNS, FIRE_ROWS, random)

    /** The meter's own clock, which the bobbing, the flicker, the flames and the colors run on. */
    private var seconds = 0f

    /** The multiplier and streak as of the last tick, to tell a kill, a step and a hit apart. */
    var multiplier = 1
        private set
    private var streak = 0

    /** The multiplier the fire was last fed at, so a doused fire gutters out in its own colors. */
    private var fireMultiplier = 1

    /** What is left of the count's pop, and of the title's, in seconds. */
    var popTime = 0f
        private set
    private var titlePopTime = 0f

    /** How hard the fire is flaring, from 1 on a step down to 0 once it has settled. */
    var flare = 0f
        private set

    /** The pop's shake, rerolled every tick of it. */
    private var shakeX = 0
    private var shakeY = 0

    /** The text as it stands, a glyph to a string so each can bob on its own; rebuilt on a step. */
    private var title = ""
    private var titleGlyphs = emptyArray<String>()
    private var countGlyphs = emptyArray<String>()
    private var glyphsFor = 0

    /**
     * Where the letters were last drawn, in the fire's cells: the columns under the title and the
     * count, and the row each is stoked along. Kept from the draw, since only the draw can measure
     * text while the fire is stoked on the tick.
     */
    private var titleFrom = 0
    private var titleUntil = 0
    private var titleRow = 0
    private var countFrom = 0
    private var countUntil = 0
    private var countRow = 0

    /** The fire's colors, rebuilt only when the colors they come from move. */
    private val palette = IntArray(PALETTE_LEVELS + 1)
    private var paletteEmber = 0
    private var paletteColor = 0
    private var paletteCore = 0

    /** The text the readout's words were last read from; a new one means a new language. */
    private var readIn: GameText? = null

    /** The whole readout while it is cold, which it only ever is at x1. */
    private var coldText = ""

    /** Whether the readout is burning at all, which it does from the first step up. */
    val burning: Boolean get() = ComboHeat.burning(multiplier)

    /** Whether any flame is still standing, doused or not. */
    val fireLit: Boolean get() = fire.lit

    /** Advances the readout by one tick, given the run's current [streak] and [multiplier]. */
    fun update(deltaTime: Float, streak: Int, multiplier: Int) {
        seconds += deltaTime

        when {
            multiplier > this.multiplier -> {
                popTime = COMBO_POP_SECONDS
                flare = 1f
                if (ComboHeat.rung(multiplier) != ComboHeat.rung(this.multiplier)) {
                    titlePopTime = COMBO_POP_SECONDS
                }
            }
            // Doused: no pop, and no flare to keep the fire going.
            multiplier < this.multiplier -> {
                popTime = 0f
                titlePopTime = 0f
                flare = 0f
            }

            streak > this.streak -> flare = maxOf(flare, COMBO_KILL_FLARE)
        }
        this.multiplier = multiplier
        this.streak = streak

        popTime = (popTime - deltaTime).coerceAtLeast(0f)
        titlePopTime = (titlePopTime - deltaTime).coerceAtLeast(0f)
        flare = (flare - deltaTime / COMBO_FLARE_SECONDS).coerceAtLeast(0f)

        // A pixel or two of shake at the start of a pop, more the hotter it is, and none after.
        val strength = ComboHeat.flameStrength(multiplier)
        val shake = (popFraction() * (1f + 2f * strength)).roundToInt()
        shakeX = if (shake > 0) random.nextInt(-shake, shake + 1) else 0
        shakeY = if (shake > 0) random.nextInt(-shake, shake + 1) else 0

        fire.rise()
        if (burning) {
            fireMultiplier = multiplier
            val heat = fullHeat() + COMBO_FLARE_HEAT * flare
            stoke(titleFrom, titleUntil, titleRow, heat)
            stoke(countFrom, countUntil, countRow, heat)
        }
    }

    /**
     * Feeds the fire from column [from] up to [until] along [row], unevenly: two slow waves of heat
     * run along the text in opposite directions, so tongues of flame rise and fall rather than a
     * wall of fire, and the ends burn lower so the fire tapers off with the text.
     */
    private fun stoke(from: Int, until: Int, row: Int, heat: Float) {
        // The taper is measured from the ends of the whole readout, title and count together.
        val first = titleFrom
        val last = maxOf(titleUntil, countUntil) - 1
        for (column in from until until) {
            val wave = 0.5f + 0.25f * sin(column * 0.9f + seconds * 6f) +
                    0.25f * sin(column * 0.37f - seconds * 3.7f)
            val fromEnd = minOf(column - first, last - column)
            val taper = (0.45f + 0.55f * fromEnd / TAPER_COLUMNS).coerceAtMost(1f)
            val stoked = (heat * (1f - TONGUE_DEPTH * wave) * taper).roundToInt()
            for (depth in 0 until BASE_ROWS) fire.stoke(column, row + depth, stoked)
        }
    }

    /**
     * Draws the readout with its left edge at [x] on [baseline], in [text]'s language. The flames
     * rise from the tops of the letters, under whatever HUD is drawn above.
     */
    fun draw(g: Graphics, x: Int, baseline: Int, text: GameText) {
        if (text !== readIn) {
            readIn = text
            coldText = "${text[ComboHeat.title(1)]} x1"
            title = ""
            glyphsFor = 0
        }
        val fireLeft = x - FIRE_LEFT_OF_TEXT
        val fireTop = baseline - FIRE_ROWS * CELL
        if (burning || fire.lit) {
            updatePalette(
                ComboHeat.emberColor(fireMultiplier, seconds),
                ComboHeat.color(fireMultiplier, seconds),
                ComboHeat.coreColor(fireMultiplier, seconds),
            )
            fire.draw(g, fireLeft, fireTop, CELL, palette, fullHeat())
        }

        if (!burning) {
            // Cold, it is drawn as a plain HUD line.
            g.drawOutlinedString(coldText, x, baseline, COMBO_FONT_SIZE, EngineColors.CYAN)
            titleUntil = titleFrom
            countUntil = countFrom
            return
        }

        rebuildGlyphs(text)
        val color = ComboHeat.color(multiplier, seconds)
        val bob = BOB_GROWTH * ComboHeat.flameStrength(multiplier)
        val titlePop = titlePopTime / COMBO_POP_SECONDS
        val titleSize =
            (COMBO_FONT_SIZE * (1f + COMBO_TITLE_POP_GROWTH * titlePop * titlePop)).roundToInt()
        val baseCount = ComboHeat.countSize(multiplier)
        val pop = popFraction()
        val countSize = (baseCount * (1f + COMBO_POP_GROWTH * pop * pop)).roundToInt()
        // The pop swells about the count's middle rather than up from its baseline,
        // so it lands like a punch.
        val countBaseline = baseline + ((countSize - baseCount) * POP_DROP).roundToInt() + shakeY
        val titleBaseline = baseline + shakeY

        val left = x + shakeX
        val titleEnd = drawGlyphs(g, titleGlyphs, left, titleBaseline, titleSize, color, bob, 0)
        val countStart = titleEnd + GAP
        val countEnd = drawGlyphs(
            g,
            countGlyphs,
            countStart,
            countBaseline,
            countSize,
            color,
            bob,
            titleGlyphs.size
        )

        titleFrom = columnOf(left, fireLeft)
        titleUntil = columnOf(titleEnd, fireLeft)
        titleRow = rowOf(titleBaseline - capHeight(titleSize), fireTop)
        countFrom = columnOf(countStart, fireLeft)
        countUntil = columnOf(countEnd, fireLeft)
        countRow = rowOf(countBaseline - capHeight(countSize), fireTop)
    }

    /**
     * Draws [glyphs] one at a time from [x], each bobbing on its own beat and flickering toward
     * white, and returns where the pen ends. [first] is the first glyph's index in the whole
     * readout, so the wave runs on from the title into the count.
     */
    private fun drawGlyphs(
        g: Graphics,
        glyphs: Array<String>,
        x: Int,
        baseline: Int,
        size: Int,
        color: Int,
        bob: Float,
        first: Int,
    ): Int {
        var pen = x
        for (i in glyphs.indices) {
            val beat = seconds * BOB_SPEED - (first + i) * BOB_STAGGER
            val dy = (sin(beat) * bob).roundToInt()
            val flicker = 0.5f + 0.5f * sin(seconds * FLICKER_SPEED + (first + i) * FLICKER_STAGGER)
            val glyphColor = mix(color, EngineColors.WHITE, FLICKER_DEPTH * flicker)
            g.drawOutlinedString(glyphs[i], pen, baseline + dy, size, glyphColor)
            pen += g.measureString(glyphs[i], size)
        }
        return pen
    }

    /** Splits the title and count into glyphs, when the multiplier has changed since last time. */
    private fun rebuildGlyphs(text: GameText) {
        if (glyphsFor == multiplier) return
        glyphsFor = multiplier
        val newTitle = text[ComboHeat.title(multiplier)]
        if (newTitle != title) {
            title = newTitle
            titleGlyphs = Array(title.length) { title[it].toString() }
        }
        val count = "x$multiplier"
        countGlyphs = Array(count.length) { count[it].toString() }
    }

    /**
     * The fire's palette, from the coolest tip at index 1 to the heart at the end: embers (the
     * faintest translucent), then the color itself, then the core.
     */
    private fun updatePalette(ember: Int, color: Int, core: Int) {
        if (ember == paletteEmber && color == paletteColor && core == paletteCore) return
        paletteEmber = ember
        paletteColor = color
        paletteCore = core
        palette[1] = EngineColors.withAlpha(ember, 0.55f)
        palette[2] = ember
        palette[3] = mix(ember, color, 0.5f)
        palette[4] = color
        palette[5] = mix(color, core, 0.5f)
        palette[6] = core
    }

    /** How hot the fire is kept at the multiplier it was last fed at, before any flare. */
    private fun fullHeat(): Int {
        val strength = ComboHeat.flameStrength(fireMultiplier)
        return (COMBO_FLAME_MIN_HEAT + (COMBO_FLAME_MAX_HEAT - COMBO_FLAME_MIN_HEAT) * strength).roundToInt()
    }

    /** How much of the pop is left, from 1 the tick it starts down to 0. */
    private fun popFraction(): Float = popTime / COMBO_POP_SECONDS

    /** The fire column frame x [x] falls in. */
    private fun columnOf(x: Int, fireLeft: Int): Int =
        floor((x - fireLeft).toFloat() / CELL).toInt()

    /** The fire row frame y [y] falls in. */
    private fun rowOf(y: Int, fireTop: Int): Int = floor((y - fireTop).toFloat() / CELL).toInt()

    /**
     * How far above the baseline the fire is fed at [size]: just under the capitals, which in
     * Roboto, Arial and DejaVu Sans stand at about seven tenths of the size.
     */
    private fun capHeight(size: Int): Int = (size * CAP_HEIGHT).roundToInt()

    private companion object {
        /**
         * One cell of fire, in framebuffer pixels. Two, because a pixel-fine fire this small looks
         * like speckle; at two its tongues read as flames, as chunky as the sprites.
         */
        const val CELL = 2

        /**
         * The fire's grid, in cells: wide enough for the widest title (German and Polish run about
         * ten cells past the widest English) and a four-digit count at the peak of a pop, with room
         * to stream left; tall enough for the hottest flare on top of a popped count.
         */
        const val FIRE_COLUMNS = 120
        const val FIRE_ROWS = 30

        /**
         * How many rows deep the fire is fed below the tops of the letters, so the flames rise out
         * of the letters rather than off a line over them.
         */
        const val BASE_ROWS = 2

        /** How far the grid starts left of the text, for the flames streaming back off its start. */
        const val FIRE_LEFT_OF_TEXT = 16

        /** One more than the palette's colors: index 0 is where there is no fire. */
        const val PALETTE_LEVELS = 6

        /** How deep the tongues cut, as the share of the heat the trough between two leaves. */
        const val TONGUE_DEPTH = 0.3f

        /** How many cells in from each end the fire takes to reach its full height. */
        const val TAPER_COLUMNS = 4f

        /** Where the fire is fed, as a share of the font size above the baseline: just under the caps. */
        const val CAP_HEIGHT = 0.62f

        /** Between the title and the count, in framebuffer pixels. */
        const val GAP = 4

        /** How much of a pop's growth goes below the baseline. */
        const val POP_DROP = 0.3f

        /** The letters' bob: how far at full heat, in pixels, how fast, and how out of step. */
        const val BOB_GROWTH = 1.8f
        const val BOB_SPEED = 7f
        const val BOB_STAGGER = 0.8f

        /** The letters' flicker toward white: how far at most, how fast, and how out of step. */
        const val FLICKER_DEPTH = 0.45f
        const val FLICKER_SPEED = 17f
        const val FLICKER_STAGGER = 2.3f
    }
}
