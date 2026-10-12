package at.smiech.cyanbat.scenery

/**
 * A kind of country the endless stage is flown over, one after another: three after the stages'
 * own (the jungle, the desert, the lagoon's coast), and four of its own.
 *
 * A biome is what its land is made of and what the air over it is like: its [materials] at noon,
 * its sky and haze, its fog and its weather. How its land is shaped is `EndlessLand`'s, which reads
 * the biome; the palettes every hour of the day draws it in are [ScenePalettes]'s.
 *
 * @param materials what the land is made of, by index; index 0 is air and is never drawn. Each is
 *   a color at noon, which the palettes light for every other hour.
 * @param glowing the indices of [materials] that give off their own light, as lava does: drawn in
 *   their own colors at any hour.
 * @param skyTint a color the sky leans toward over this land, and [skyTintStrength] how far: the red
 *   over a volcano, the gray over a fog.
 * @param hazeTint likewise for the distance, which fades into this as well as into the horizon.
 * @param fog how thick a fog lies in the valleys, 0..1.
 * @param weather what falls from the sky here, and how hard on average ([weatherBase]) and at most
 *   ([weatherPeak]); see [Weather].
 * @param water whether the land is flown over water, which a low sun lays glints across.
 */
enum class Biome(
    val materials: IntArray,
    val glowing: Set<Int> = emptySet(),
    val skyTint: Int,
    val skyTintStrength: Float,
    val hazeTint: Int,
    val hazeTintStrength: Float,
    val fog: Float,
    val weather: Weather,
    val weatherBase: Float,
    val weatherPeak: Float,
    val water: Boolean = false,
) {
    /** Wooded karst domes, a canopy with ruins in it, ferns over dark soil; warm rain. */
    JUNGLE(
        materials = intArrayOf(
            0, 0x3A2A1E, 0x1E4A2A, 0x2E6E36, 0x58A04A, 0x6E7060, 0x5A7A4A, 0x5A4430, 0x3E8A3E, 0x4A7A54,
            0x2A5A34,
        ).opaque(),
        skyTint = 0xFF7AB8A0.toInt(), skyTintStrength = 0.12f,
        hazeTint = 0xFF9AC8B0.toInt(), hazeTintStrength = 0.25f,
        fog = 0.25f,
        weather = Weather.RAIN, weatherBase = 0.35f, weatherPeak = 0.9f,
    ),

    /** Mesas, dunes and saguaros, under a bleached sky; dust on the wind. */
    DESERT(
        materials = intArrayOf(
            0, 0xE0C080, 0xC8A060, 0xB06A40, 0x8A4A30, 0xC88050, 0x4E7A3A, 0x6E9A4A, 0xD8A870, 0x9A5838,
        ).opaque(),
        skyTint = 0xFFF0D8A0.toInt(), skyTintStrength = 0.18f,
        hazeTint = 0xFFF0D4A0.toInt(), hazeTintStrength = 0.3f,
        fog = 0f,
        weather = Weather.DUST, weatherBase = 0.15f, weatherPeak = 0.75f,
    ),

    /** A sea with limestone islands, a beach and palms; now and then a shower. */
    COAST(
        materials = intArrayOf(
            0, 0x1E5A8A, 0x2E7AAA, 0x5AA8D0, 0xE8F4F8, 0xE8D8A8, 0xA8B0A0, 0x7A8478, 0x3E7A44, 0x4A8A3E,
            0x6A5038,
        ).opaque(),
        skyTint = 0xFF60D0F0.toInt(), skyTintStrength = 0.1f,
        hazeTint = 0xFFB8E8F4.toInt(), hazeTintStrength = 0.2f,
        fog = 0f,
        weather = Weather.RAIN, weatherBase = 0f, weatherPeak = 0.5f,
        water = true,
    ),

    /** Snowy peaks and pine woods; snowing. */
    PEAKS(
        materials = intArrayOf(
            0, 0x4A5060, 0x6A7284, 0xE8F0F8, 0xB8C8E0, 0x1E3A34, 0x2E5244, 0xA8D8F0, 0x4A3428, 0x8A94A8,
        ).opaque(),
        skyTint = 0xFFC8D8F0.toInt(), skyTintStrength = 0.15f,
        hazeTint = 0xFFD8E4F4.toInt(), hazeTintStrength = 0.35f,
        fog = 0.2f,
        weather = Weather.SNOW, weatherBase = 0.45f, weatherPeak = 1f,
    ),

    /** Volcanoes alight, black basalt and rivers of lava; ash falling and embers rising. */
    VOLCANO(
        materials = intArrayOf(
            0, 0x241E22, 0x3A3036, 0x5A5254, 0x7A706C, 0xFF6A1E, 0xFFC040, 0xB8341C, 0x1A1420, 0x4A3A2E,
        ).opaque(),
        glowing = setOf(5, 6, 7),
        skyTint = 0xFF8A3A2A.toInt(), skyTintStrength = 0.38f,
        hazeTint = 0xFF7A4A3A.toInt(), hazeTintStrength = 0.45f,
        fog = 0.1f,
        weather = Weather.ASH, weatherBase = 0.55f, weatherPeak = 1f,
    ),

    /** Cypress and dead trees standing in murky water, reeds and lilies; rain and mist. */
    SWAMP(
        materials = intArrayOf(
            0, 0x3E3A26, 0x4E6A32, 0x6E8A3E, 0x2E4A3E, 0x4E6E58, 0x3A3028, 0x5A4A38, 0x7A8A44, 0x3E7A3A,
            0x8A9A6A, 0x2A4030,
        ).opaque(),
        skyTint = 0xFF8A9A70.toInt(), skyTintStrength = 0.22f,
        hazeTint = 0xFF9AA888.toInt(), hazeTintStrength = 0.45f,
        fog = 0.6f,
        weather = Weather.RAIN, weatherBase = 0.45f, weatherPeak = 1f,
        water = true,
    ),

    /** Ridge after ridge of pines lost in fog, ferns and mossy stones; a drizzle. */
    FOGGY_FOREST(
        materials = intArrayOf(
            0, 0x3A3024, 0x3E5A3A, 0x4E7A44, 0x1E3430, 0x2A463E, 0x3A5E4E, 0x4A3A30, 0x5A6060, 0xC0503A,
        ).opaque(),
        skyTint = 0xFFA8B4B8.toInt(), skyTintStrength = 0.4f,
        hazeTint = 0xFFC0CACC.toInt(), hazeTintStrength = 0.6f,
        fog = 1f,
        weather = Weather.RAIN, weatherBase = 0.2f, weatherPeak = 0.55f,
    ),
    ;

    /** Whether [material] gives off its own light. */
    fun glows(material: Int): Boolean = material in glowing
}

/** What falls from the sky over a [Biome]. */
enum class Weather {
    /** Streaks of rain, beading on what they land on and running off it; a storm brings lightning. */
    RAIN,

    /** Flakes drifting down and settling on top of whatever they land on. */
    SNOW,

    /** Flakes of ash, sooting what they land on, and embers rising from below. */
    ASH,

    /** Dust blowing along on the wind, low over the ground. */
    DUST,
}

private fun IntArray.opaque(): IntArray = IntArray(size) { if (it == 0) 0 else this[it] or 0xFF000000.toInt() }
