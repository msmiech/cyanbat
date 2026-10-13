package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.util.ENDLESS_BIOME_SECONDS
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.LightingSystem
import kotlin.math.floor
import kotlin.random.Random

/**
 * The endless stage's country as the run goes on: the clock its sky turns on, which biome it is
 * flown over, the land of every band made up a chunk at a time as it scrolls into view, the
 * weather, and the obstacles that stand in it.
 *
 * Everything moves on [update], which the stage's scenery system calls once a tick, so a paused run
 * is a paused land. The biome changes every [ENDLESS_BIOME_SECONDS]: each band's next chunk, not
 * yet in view, starts blending into the new land (see [EndlessLand]), so it scrolls in from the
 * right, the near band first and the far one last, and the air - the sky's tint, the fog, the
 * weather - turns over [AIR_TURN_SECONDS].
 *
 * @param graphics makes the chunks into pictures; see [Graphics.newPixmap].
 * @param random picks the biomes and rolls the weather's lightning.
 * @param seed the land's, so the same seed makes the same country.
 * @param first the land a run starts over, or null for one out of the bag; and [startSeconds] how
 *   far into its day. A run starts on the bag's pick at sunrise; the stage's preview, and the tests,
 *   start where they need to look.
 */
class EndlessScenery(
    private val graphics: Graphics,
    private val frameWidth: Int,
    private val random: Random = Random.Default,
    val seed: Int = random.nextInt(),
    first: Biome? = null,
    startSeconds: Float = 0f,
) {
    /** Seconds of the run, which the day and the weather go by. */
    var seconds = startSeconds
        private set

    /** Where round the clock the day is; see [EndlessDay.position]. */
    val position: Float
        get() = EndlessDay.position(seconds)

    /** The land being flown over now, and the one it is turning from. */
    var biome: Biome
        private set
    var previous: Biome
        private set

    /** When [biome] began, in run seconds, and when the next will. */
    private var biomeSince = 0f
    private var nextBiomeAt = startSeconds + ENDLESS_BIOME_SECONDS

    /** How many biomes have come, the first included. */
    var biomesSeen = 1
        private set

    /** The biomes still to come before every one has come again. */
    private val bag = ArrayList<Biome>()

    /** How far the air has turned from [previous] to [biome], 0..1. */
    val turned: Float
        get() = smoothstep(0f, AIR_TURN_SECONDS, seconds - biomeSince)

    /** How far each band has scrolled, in its own pixels. */
    val scroll = FloatArray(Band.entries.size)

    /** Which biome holds each band from where on. */
    private val stretches = Array(Band.entries.size) { ArrayList<Stretch>() }

    /** The chunks of each band from the leftmost still in view, in order. */
    val chunks = Array(Band.entries.size) { ArrayDeque<Chunk>() }

    /** Where each band's next chunk begins. */
    private val nextLeft = IntArray(Band.entries.size)

    /** One band's land from band-x [left], drawn in every palette, a row of its sheet apiece. */
    class Chunk(val left: Int, val sheet: Pixmap, val features: List<Feature>)

    /**
     * How the sky looked over each cloud's cell when a chunk first needed it: kept, so the chunk on
     * either side of a cloud paints the same cloud, however the weather has turned in between.
     */
    private val cloudCells = HashMap<Int, CloudCover>()

    /** Reused between chunks of the same band, since [Graphics.newPixmap] copies them. */
    private val pixels = arrayOfNulls<IntArray>(Band.entries.size)

    // --- the weather -------------------------------------------------------------------------------

    /** What is falling, and how hard, 0..1. */
    var weather: Weather = Weather.RAIN
        private set
    var intensity = 0f
        private set

    /** How much of the weather is a storm, 0..1: lightning, and the day dimmed under it. */
    var storm = 0f
        private set

    /** How hard the wind blows the weather to the left, in frame pixels a tick. */
    var wind = 0f
        private set

    /** A lightning flash, from 1 as it strikes to 0, and where its bolt comes down. */
    var flash = 0f
        private set
    var boltX = 0f
        private set
    var boltSeed = 0
        private set
    private var untilBolt = BOLT_FIRST_SECONDS

    /** Called when a strike's thunder arrives, a moment after its flash, as sound lags light. */
    var onThunder: (() -> Unit)? = null

    /** Seconds until the last strike's thunder arrives; below zero once it has. */
    private var thunderIn = -1f

    // --- the obstacles -----------------------------------------------------------------------------

    /** What stands on the ground and hangs from above in the land now, and in the land before. */
    var ground: Array<Pixmap?> = emptyArray()
        private set
    var hanging: Array<Pixmap?> = emptyArray()
        private set
    private var previousGround: Array<Pixmap?> = emptyArray()
    private var previousHanging: Array<Pixmap?> = emptyArray()

    init {
        biome = first ?: drawBiome(null)
        previous = biome
        // The first land's air is its own from the start, not turning into it.
        biomeSince = seconds - AIR_TURN_SECONDS
        for (band in Band.entries) stretches[band.ordinal] += Stretch(0, biome)
        makeObstacles()
        previousGround = ground
        previousHanging = hanging
        updateWeather()
        for (band in Band.entries) fill(band, all = true)
    }

    /** Moves the land on by one tick: the clock, the scroll, the biomes, the weather. */
    fun update(deltaTime: Float) {
        seconds += deltaTime
        if (seconds >= nextBiomeAt) changeBiome()
        for (band in Band.entries) {
            scroll[band.ordinal] += band.speed
            fill(band, all = false)
        }
        updateWeather()
        advanceLightning(deltaTime)
    }

    /** Moves on to the next land, which the bands start blending into from their next chunk. */
    private fun changeBiome() {
        previous = biome
        biome = drawBiome(biome)
        biomeSince = seconds
        nextBiomeAt = seconds + ENDLESS_BIOME_SECONDS
        biomesSeen++
        for (band in Band.entries) {
            val list = stretches[band.ordinal]
            list += Stretch(nextLeft[band.ordinal], biome)
            // Only the last few can still be in view or blending.
            while (list.size > 3) list.removeAt(0)
        }
        previousGround = ground
        previousHanging = hanging
        makeObstacles()
    }

    /** The next biome out of the bag, never the one it follows. */
    private fun drawBiome(after: Biome?): Biome {
        if (bag.isEmpty()) {
            bag += Biome.entries
            bag.shuffle(random)
            if (bag.first() == after) bag.add(bag.removeAt(0))
        }
        return bag.removeAt(0)
    }

    /**
     * Makes [band]'s chunks until the next one starts past the frame's right edge by a chunk, and
     * drops those scrolled out of view on the left. One new chunk a tick once the run is going, so
     * the cost is spread; the band is a chunk ahead, so one is always enough.
     */
    private fun fill(band: Band, all: Boolean) {
        val queue = chunks[band.ordinal]
        val left = scroll[band.ordinal]
        while (queue.isNotEmpty() && queue.first().left + EndlessLand.CHUNK <= floor(left).toInt()) {
            queue.removeFirst().sheet.dispose()
        }
        while (nextLeft[band.ordinal] < left + frameWidth + EndlessLand.CHUNK) {
            queue.addLast(make(band, nextLeft[band.ordinal]))
            nextLeft[band.ordinal] += EndlessLand.CHUNK
            if (!all) break
        }
    }

    /** One chunk of [band] from band-x [left], in every palette. */
    private fun make(band: Band, left: Int): Chunk {
        val painter = EndlessLand.paint(band, left, stretches[band.ordinal], seed, ::coverOf)
        val codes = painter.codes
        val size = codes.size
        val rows = EndlessDay.ROWS
        val out = pixels[band.ordinal]?.takeIf { it.size == size * rows }
            ?: IntArray(size * rows).also { pixels[band.ordinal] = it }
        for (time in EndlessDay.TimeOfDay.entries) {
            val table = ScenePalettes.table(band.depth, time)
            val offset = time.ordinal * size
            for (i in 0 until size) out[offset + i] = table[codes[i]]
        }
        val sheet = graphics.newPixmap(painter.width, painter.height * rows, out)
        return Chunk(left, sheet, painter.features)
    }

    /** How the sky looks over cloud [cell]: as the weather had it when the cell was first asked about. */
    private fun coverOf(cell: Int): CloudCover {
        // The cells long scrolled away are forgotten.
        val gone = floor(scroll[Band.CLOUDS.ordinal] / EndlessLand.CLOUD_CELL).toInt() - 4
        if (cloudCells.size > 64) cloudCells.keys.removeAll { it < gone }
        return cloudCells.getOrPut(cell) { cloudCover() }
    }

    /** The clouds as the weather has them now. */
    private fun cloudCover(): CloudCover {
        val ash = if (weather == Weather.ASH) 0.5f + 0.5f * intensity else 0f
        val cloudy = when (weather) {
            Weather.RAIN -> 0.35f + 0.65f * intensity
            Weather.SNOW -> 0.4f + 0.5f * intensity
            Weather.ASH -> 0.5f + 0.4f * intensity
            Weather.DUST -> 0.05f
        }
        val heavy = if (weather == Weather.SNOW) intensity * 0.5f else storm
        return CloudCover(cloudy, heavy, ash)
    }

    /**
     * Each band's obstacles, made afresh for the new biome: the same kinds the land always has,
     * shaped from a seed of their own each time it comes round.
     */
    private fun makeObstacles() {
        val visit = seed + biomesSeen * 7919
        ground = Array(EndlessLand.GROUND_KINDS) { kind ->
            obstacle(EndlessLand.obstacle(biome, hanging = false, kind, visit + kind * 31))
        }
        hanging = Array(EndlessLand.HANGING_KINDS) { kind ->
            obstacle(EndlessLand.obstacle(biome, hanging = true, kind, visit + 500 + kind * 31))
        }.filterNotNull().toTypedArray()
    }

    private fun obstacle(art: LandPainter?): Pixmap? {
        art ?: return null
        val rows = EndlessDay.ROWS
        val size = art.codes.size
        val out = IntArray(size * rows)
        for (time in EndlessDay.TimeOfDay.entries) {
            val table = ScenePalettes.table(ScenePalettes.Depth.NEAR, time)
            for (i in 0 until size) out[time.ordinal * size + i] = table[art.codes[i]]
        }
        return graphics.newPixmap(art.width, art.height * rows, out)
    }

    /**
     * Something to stand on the ground: from the new land's set, or while the land is still
     * turning, as often from the old one's as the old land still holds.
     */
    fun groundObstacles(random: Random): Array<Pixmap?> =
        if (previousGround.isNotEmpty() && random.nextFloat() >= turned) previousGround else ground

    /** Something to hang from the top: the set of whichever land holds most of the air. */
    fun hangingObstacles(): Array<Pixmap?> = if (turned < 0.5f) previousHanging else hanging

    // --- the air -----------------------------------------------------------------------------------

    /**
     * How hard [land]'s weather falls now: its average, rising toward its peak and falling back as
     * a slow noise wanders, so a drizzle builds to a downpour and clears again.
     */
    private fun level(land: Biome): Float {
        val wander = smoothstep(0.3f, 0.8f, Noise.fbm(seconds / WEATHER_WANDER_SECONDS, seed + 900, 2))
        return land.weatherBase + (land.weatherPeak - land.weatherBase) * wander
    }

    private fun updateWeather() {
        val t = turned
        if (previous.weather == biome.weather) {
            weather = biome.weather
            intensity = level(previous) + (level(biome) - level(previous)) * t
        } else if (t < 0.5f) {
            // The old weather dies away before the new one comes in.
            weather = previous.weather
            intensity = level(previous) * (1f - 2f * t)
        } else {
            weather = biome.weather
            intensity = level(biome) * (2f * t - 1f)
        }
        storm = if (weather == Weather.RAIN) smoothstep(0.65f, 0.95f, intensity) else 0f
        wind = when (weather) {
            Weather.RAIN -> 0.8f + 1.6f * intensity
            Weather.SNOW -> 0.4f + 1.2f * intensity
            Weather.ASH -> 0.3f + 0.5f * intensity
            Weather.DUST -> 2.5f + 3f * intensity
        }
    }

    private fun advanceLightning(deltaTime: Float) {
        flash = (flash - deltaTime / FLASH_SECONDS).coerceAtLeast(0f)
        if (thunderIn >= 0f) {
            thunderIn -= deltaTime
            if (thunderIn < 0f) onThunder?.invoke()
        }
        if (storm <= 0f) return
        untilBolt -= deltaTime * storm
        if (untilBolt > 0f) return
        untilBolt = BOLT_MIN_SECONDS + random.nextFloat() * BOLT_SPREAD_SECONDS
        flash = 1f
        boltX = frameWidth * (0.15f + 0.7f * random.nextFloat())
        boltSeed = random.nextInt()
        thunderIn = THUNDER_MIN_SECONDS + random.nextFloat() * THUNDER_SPREAD_SECONDS
    }

    /** How dark it is, 0 by day to 1 at the dead of night. */
    val darkness: Float
        get() = EndlessDay.darkness(position)

    /**
     * The light where nothing else reaches, for the stage's dark: the night's, lit up for a moment
     * by a storm's lightning. Daylight by day, storm or none, so the dark costs nothing then: a storm
     * darkens the sky and its clouds, and colored light round every shot in a bright land would read
     * as balloons rather than light.
     */
    fun ambient(): Int {
        var ambient = EndlessDay.ambient(position)
        if (flash > 0f) ambient = EngineColors.lerp(ambient, LightingSystem.DAYLIGHT, flash)
        return if (ambient or OPAQUE == LightingSystem.DAYLIGHT) LightingSystem.DAYLIGHT else ambient
    }

    /** How thick the fog lies, 0..1, as the air turns from one land's to the next. */
    val fog: Float
        get() = previous.fog + (biome.fog - previous.fog) * turned

    /** The sky's color at [height] of the way from the zenith to the horizon, over this land now. */
    fun skyColor(height: Float): Int {
        val p = position
        var color = EndlessDay.skyColor(p, height)
        val t = turned
        val tint = EngineColors.lerp(previous.skyTint, biome.skyTint, t)
        val strength = previous.skyTintStrength + (biome.skyTintStrength - previous.skyTintStrength) * t
        // A land's own tint shows most by day; the night is the night everywhere.
        color = EngineColors.lerp(color, tint, strength * (1f - 0.6f * EndlessDay.darkness(p)) * (0.4f + 0.6f * height))
        val overcast = (if (weather == Weather.RAIN || weather == Weather.SNOW) intensity * 0.35f else 0f) + storm * 0.3f
        if (overcast > 0f) {
            val gray = EngineColors.lerp(OVERCAST_DAY, OVERCAST_NIGHT, EndlessDay.darkness(p))
            color = EngineColors.lerp(color, gray, overcast)
        }
        return color
    }

    /** The fog's color: the haze of the hour, a little paler. */
    fun fogColor(): Int = EngineColors.lerp(skyColor(1f), EngineColors.WHITE, 0.25f * (1f - darkness))

    /** Whether the land in view is over water, for a low sun's glints; 0..1. */
    val water: Float
        get() = (if (previous.water) 1f - turned else 0f) + (if (biome.water) turned else 0f)

    private companion object {
        /** How long the sky's tint, the fog and the weather take to turn from one land to the next. */
        const val AIR_TURN_SECONDS = 24f

        /** How slowly the weather wanders between its calm and its peak. */
        const val WEATHER_WANDER_SECONDS = 38f

        const val FLASH_SECONDS = 0.35f
        const val BOLT_FIRST_SECONDS = 3f
        const val BOLT_MIN_SECONDS = 3.5f
        const val BOLT_SPREAD_SECONDS = 7f

        /** How long a strike's thunder takes to arrive: as far off as the bolt is, a mile or so. */
        const val THUNDER_MIN_SECONDS = 0.3f
        const val THUNDER_SPREAD_SECONDS = 1.2f

        val OVERCAST_DAY = 0xFF8C929C.toInt()
        val OVERCAST_NIGHT = 0xFF1C2028.toInt()
        const val OPAQUE = 0xFF000000.toInt()
    }
}
