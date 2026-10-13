package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.EndlessDay.TimeOfDay
import at.smiech.engine.EngineColors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The colors of the endless stage's land at every hour, as lookup tables from a [code] to ARGB.
 *
 * The land is generated as codes rather than colors: a code says which biome's material a pixel is
 * and which way it faces (its [shade]). A table per [TimeOfDay] and per [Depth] turns the codes into
 * that hour's colors, so one generated chunk is drawn in all five palettes at once, a row of its
 * sheet apiece, and the stage crossfades between them as the stages' scenery does.
 *
 * A palette is the material's noon color lit by the hour: the direct light (sun or moon) on the
 * faces turned toward it, the sky's ambient on the rest, normalized so that at noon a plain surface
 * is exactly the material's own color. The farther away, the more of it fades into the horizon's
 * haze. What glows keeps its own color at every hour.
 */
internal object ScenePalettes {

    /** Which way a pixel of land faces, and so how the light finds it. */
    const val DEEP = 0
    const val BODY = 1
    const val TOP = 2
    const val LEFT = 3
    const val RIGHT = 4
    const val SHADES = 5

    /** Room for this many materials per biome. */
    const val MATERIALS = 16

    /** The slot the clouds' materials sit in, after every biome's. */
    val CLOUDS = Biome.entries.size

    /** Every code there is, transparent 0 included. */
    val CODES = (CLOUDS + 1) * MATERIALS * SHADES + 1

    /** The clouds' materials, by index; 0 is air. */
    const val CLOUD_LIGHT = 1
    const val CLOUD = 2
    const val CLOUD_SHADE = 3
    const val STORM = 4
    const val STORM_DARK = 5
    const val ASH_CLOUD = 6
    const val ASH_DARK = 7
    private val CLOUD_COLORS = intArrayOf(0, 0xF4F6FA, 0xDCE2EC, 0xA8B2C8, 0x707884, 0x4E5664, 0x4A403E, 0x302826)
        .map { if (it == 0) 0 else it or OPAQUE }.toIntArray()

    /** How far off a band is, and so how much of it fades into the haze. */
    enum class Depth(val haze: Float) {
        CLOUDS(0f),
        FAR(0.55f),
        MID(0.25f),
        NEAR(0f),
    }

    /** The code for [biome]'s [material] facing [shade]; 0 is transparent. */
    fun code(biome: Biome, material: Int, shade: Int): Int = code(biome.ordinal, material, shade)

    /** The code for a cloud of [material] facing [shade]. */
    fun cloud(material: Int, shade: Int): Int = code(CLOUDS, material, shade)

    private fun code(slot: Int, material: Int, shade: Int): Int =
        if (material == 0) 0 else (slot * MATERIALS + material) * SHADES + shade + 1

    /** Where round the clock each palette is taken from, for its sky's haze. */
    private fun positionOf(time: TimeOfDay): Float = when (time) {
        TimeOfDay.DAWN -> 0.01f
        TimeOfDay.NOON -> 0.25f
        TimeOfDay.GOLDEN -> 0.53f
        TimeOfDay.SUNSET -> 0.62f
        TimeOfDay.NIGHT -> 0.8f
    }

    private val tables = HashMap<Int, IntArray>()

    /** The colors of every code at [depth] in [time]'s palette, worked out once. */
    fun table(depth: Depth, time: TimeOfDay): IntArray =
        tables.getOrPut(depth.ordinal * SHADES + time.ordinal) { build(depth, time) }

    private fun build(depth: Depth, time: TimeOfDay): IntArray {
        val table = IntArray(CODES)
        val sky = EndlessDay.hazeColor(positionOf(time))
        for (slot in 0..CLOUDS) {
            val biome = Biome.entries.getOrNull(slot)
            val colors = biome?.materials ?: CLOUD_COLORS
            val haze = if (biome == null) sky else {
                // At night the land's own haze all but goes out with the light.
                val tint = biome.hazeTintStrength * if (time == TimeOfDay.NIGHT) 0.3f else 1f
                EngineColors.lerp(sky, biome.hazeTint, tint)
            }
            for (material in 1 until colors.size.coerceAtMost(MATERIALS)) {
                val glows = biome?.glows(material) == true
                for (shade in 0 until SHADES) {
                    table[code(slot, material, shade)] =
                        color(colors[material], glows, shade, time, depth.haze * time.haze, haze)
                }
            }
        }
        return table
    }

    /**
     * [base], a material's color at noon, as it looks facing [shade] in [time]'s light, faded
     * [haze] of the way into [hazeColor].
     */
    private fun color(base: Int, glows: Boolean, shade: Int, time: TimeOfDay, haze: Float, hazeColor: Int): Int {
        if (glows) return EngineColors.lerp(base, hazeColor, haze * 0.35f)
        val r = channel(base shr 16, time.light shr 16, time.ambient shr 16, NOON.light shr 16, NOON.ambient shr 16, shade, time)
        val g = channel(base shr 8, time.light shr 8, time.ambient shr 8, NOON.light shr 8, NOON.ambient shr 8, shade, time)
        val b = channel(base, time.light, time.ambient, NOON.light, NOON.ambient, shade, time)
        val lit = OPAQUE or (r shl 16) or (g shl 8) or b
        return EngineColors.lerp(lit, hazeColor, haze.coerceIn(0f, 1f))
    }

    private val NOON = TimeOfDay.NOON

    /** How much of the direct light a surface facing [shade] takes in [time]. */
    private fun direct(shade: Int, time: TimeOfDay): Float = when (shade) {
        DEEP -> 0f
        BODY -> BODY_SHARE * max(time.fromAbove, (time.fromLeft + time.fromRight) / 2f)
        TOP -> time.fromAbove
        LEFT -> time.fromLeft
        else -> time.fromRight
    }

    private fun channel(
        base: Int, light: Int, ambient: Int, noonLight: Int, noonAmbient: Int, shade: Int, time: TimeOfDay,
    ): Int {
        val shadow = if (shade == DEEP) DEEP_SHADOW else 1f
        val lit = (ambient and 0xFF) / 255f * AMBIENT_SHARE * shadow + (light and 0xFF) / 255f * direct(shade, time) * DIRECT_SHARE
        val noon = (noonAmbient and 0xFF) / 255f * AMBIENT_SHARE + (noonLight and 0xFF) / 255f * direct(BODY, NOON) * DIRECT_SHARE
        return ((base and 0xFF) * lit / noon).roundToInt().coerceIn(0, 255)
    }

    /** How much of a surface's light is the sky's, and how much the sun's or moon's. */
    private const val AMBIENT_SHARE = 0.75f
    private const val DIRECT_SHARE = 0.6f

    /** How much of the direct light reaches a surface facing neither toward it nor away. */
    private const val BODY_SHARE = 0.45f

    /** How much darker the depths of a mass are than its body. */
    private const val DEEP_SHADOW = 0.72f

    private const val OPAQUE = 0xFF000000.toInt()
}
