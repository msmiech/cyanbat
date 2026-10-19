package at.smiech.cyanbat.scenery

import at.smiech.cyanbat.scenery.ScenePalettes.BODY
import at.smiech.cyanbat.scenery.ScenePalettes.DEEP
import at.smiech.cyanbat.scenery.ScenePalettes.LEFT
import at.smiech.cyanbat.scenery.ScenePalettes.RIGHT
import at.smiech.cyanbat.scenery.ScenePalettes.TOP
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A stretch of one band given over to [biome] from band-x [from] on; see [EndlessLand.paint]. */
class Stretch(val from: Int, val biome: Biome)

/**
 * How the sky over a chunk of clouds looks as it is made: how much of it is cloud, and how much of
 * that is a storm's or a volcano's ash, each 0..1.
 */
class CloudCover(val cloudiness: Float, val storm: Float, val ash: Float)

/**
 * The endless stage's land, made up a chunk at a time as it scrolls into view: every biome's shape
 * and dressing for every band, as palette codes (see [ScenePalettes]).
 *
 * Everything is a pure function of where along its band a column is, the run's seed and which
 * biome holds that stretch of the band, so a chunk can be made in any order and still meet its
 * neighbors without a seam: a mountain's slope runs on from one chunk into the next, and a tree on
 * the join is painted into both.
 *
 * Where a band passes from one biome into the next, the two blend across a zone [transition] wide:
 * the ground's height slides from one profile to the other, and each column is one biome's or the
 * other's along a ragged edge, so a snowfield gives way to ash rather than being cut off.
 */
internal object EndlessLand {

    /** How wide a chunk is, in band pixels. */
    const val CHUNK = 128

    /** How wide the zone is that a band blends from one biome into the next across. */
    fun transition(band: Band): Int = when (band) {
        Band.CLOUDS -> 1
        Band.FAR -> 220
        Band.MID -> 260
        Band.NEAR -> 320
    }

    /** How far apart clouds are strung, one to a cell at most. */
    const val CLOUD_CELL = 96

    /**
     * The chunk of [band] from band-x [left], for a band whose land is [stretches] (in order of
     * [Stretch.from], at least one), from the run's [seed]. The clouds' band reads [clouds] instead:
     * how the sky looks over each cloud's cell, which must answer the same for a cell every time, so
     * a cloud across two chunks is the same cloud in both.
     *
     * A chunk is a column wider and a row taller than it covers: a blit paints one column and one
     * row short of what it is given (the deliberate `- 1` in `ComposeGraphics.drawPixmap`), so the
     * extra column is the next chunk's first, painted twice, and the extra row goes undrawn.
     */
    fun paint(
        band: Band,
        left: Int,
        stretches: List<Stretch>,
        seed: Int,
        clouds: (cell: Int) -> CloudCover = { CloudCover(0.4f, 0f, 0f) },
    ): LandPainter {
        val painter = LandPainter(band, left, CHUNK + 1, band.height + 1)
        if (band == Band.CLOUDS) {
            paintClouds(painter, seed, clouds)
            return painter
        }
        val mixes = Mixes(band, stretches, seed)
        for (x in left..left + CHUNK) {
            val h = mixes.surface(x)
            val slope = mixes.surface(x + 1) - mixes.surface(x - 1)
            val biome = mixes.biomeAt(x)
            landOf(biome).column(painter, x, h, slope, seed)
        }
        // Dressed after the ground, so what stands on it stands in front of it. Each biome dresses
        // only the columns that are its own along the ragged edge.
        for (biome in mixes.biomesIn(left - DRESSING_REACH, left + CHUNK + DRESSING_REACH)) {
            landOf(biome).dress(painter, seed, mixes::surface) { mixes.biomeAt(it) == biome }
        }
        return painter
    }

    /** The farthest anything dressing a band reaches from where it stands, sideways. */
    private const val DRESSING_REACH = 220

    /** Which biome a band's columns belong to, and how high its blended ground stands. */
    private class Mixes(val band: Band, val stretches: List<Stretch>, val seed: Int) {
        private val width = transition(band)

        /** The stretch [x] is in: the last to begin at or before it. */
        private fun stretchAt(x: Int): Int {
            var index = 0
            for (i in stretches.indices) if (stretches[i].from <= x) index = i
            return index
        }

        /** How far into its stretch's blend [x] is, 0 at its start, 1 once the blend is done. */
        private fun amount(x: Int, index: Int): Float =
            if (index == 0) 1f else smoothstep(
                0f,
                width.toFloat(),
                (x - stretches[index].from).toFloat()
            )

        fun biomeAt(x: Int): Biome {
            val index = stretchAt(x)
            val amount = amount(x, index)
            if (amount >= 1f || index == 0) return stretches[index].biome
            // A ragged edge, so the new land creeps in in clumps of columns rather than along a line.
            val edge = 0.1f + 0.8f * (0.55f * Noise.value(x / 6f, seed + 77) + 0.45f * Noise.unit(
                x / 2,
                seed + 78
            ))
            return if (amount > edge) stretches[index].biome else stretches[index - 1].biome
        }

        fun surface(x: Int): Float {
            val index = stretchAt(x)
            val own = landOf(stretches[index].biome).surface(band, x, seed)
            val amount = amount(x, index)
            if (amount >= 1f || index == 0) return own + band.floor
            val before = landOf(stretches[index - 1].biome).surface(band, x, seed)
            return before + (own - before) * amount + band.floor
        }

        /**
         * Every biome with a column between [from] and [to]: a stretch's biome holds its columns
         * from its start until the next stretch has finished blending in.
         */
        fun biomesIn(from: Int, to: Int): Set<Biome> {
            val set = LinkedHashSet<Biome>()
            for (i in stretches.indices) {
                val start = if (i == 0) Int.MIN_VALUE else stretches[i].from
                val end =
                    if (i + 1 < stretches.size) stretches[i + 1].from + width else Int.MAX_VALUE
                if (end >= from && start <= to) set += stretches[i].biome
            }
            return set
        }
    }

    private val lands = HashMap<Biome, Land>()

    private fun landOf(biome: Biome): Land = lands.getOrPut(biome) {
        when (biome) {
            Biome.JUNGLE -> JungleLand
            Biome.DESERT -> DesertLand
            Biome.COAST -> CoastLand
            Biome.PEAKS -> PeaksLand
            Biome.VOLCANO -> VolcanoLand
            Biome.SWAMP -> SwampLand
            Biome.FOGGY_FOREST -> ForestLand
        }
    }

    // --- the obstacles ---------------------------------------------------------------------------

    /** How many kinds of obstacle each biome stands on its ground, and hangs from the top. */
    const val GROUND_KINDS = 3
    const val HANGING_KINDS = 2

    /**
     * Obstacle [kind] of [biome], standing on the ground or [hanging] from the top of the frame, as
     * codes on a canvas its own size; null for a hanging one where the sky is open. Shaped afresh
     * from [seed], so a biome met again brings obstacles of its kind but not the same ones.
     */
    fun obstacle(biome: Biome, hanging: Boolean, kind: Int, seed: Int): LandPainter? =
        obstacleArt(landOf(biome), hanging, kind, seed)

    // --- the clouds ------------------------------------------------------------------------------

    /**
     * Clouds strung along the top of the sky, as many as [coverOf] says over each cell: puffs of a
     * few overlapping ovals with flat bottoms, lit on top, gray and heavy in a storm, brown with ash
     * over a volcano.
     */
    private fun paintClouds(p: LandPainter, seed: Int, coverOf: (Int) -> CloudCover) {
        val cell = CLOUD_CELL
        for (c in floor((p.left - 120) / cell.toFloat()).toInt()..((p.left + p.width + 120) / cell)) {
            val cover = coverOf(c)
            val chance = 0.15f + 0.8f * cover.cloudiness
            val roll = Noise.unit(c, seed + 501)
            if (roll > chance) continue
            val cx = c * cell + Noise.unit(c, seed + 502) * cell
            val bottom = 46f + Noise.unit(c, seed + 503) * 64f + cover.storm * 10f
            val span = 36f + Noise.unit(c, seed + 504) * (60f + 50f * cover.cloudiness)
            val tall = 12f + Noise.unit(c, seed + 505) * (12f + 14f * cover.storm)
            val (light, body, shade) = when {
                cover.ash > Noise.unit(c, seed + 506) -> Triple(
                    ScenePalettes.ASH_CLOUD,
                    ScenePalettes.ASH_CLOUD,
                    ScenePalettes.ASH_DARK
                )

                cover.storm > Noise.unit(c, seed + 507) -> Triple(
                    ScenePalettes.STORM,
                    ScenePalettes.STORM,
                    ScenePalettes.STORM_DARK
                )

                else -> Triple(
                    ScenePalettes.CLOUD_LIGHT,
                    ScenePalettes.CLOUD,
                    ScenePalettes.CLOUD_SHADE
                )
            }
            val puffs = 3 + (Noise.unit(c, seed + 508) * 3).toInt()
            for (i in 0 until puffs) {
                val along = if (puffs == 1) 0.5f else i / (puffs - 1f)
                val px = cx - span / 2f + span * along
                // The middle puffs stand tallest, so the cloud has a crown.
                val rise = tall * (0.55f + 0.45f * sin(along * PI_F)) * (0.75f + 0.5f * Noise.unit(
                    c * 7 + i,
                    seed + 509
                ))
                val rx = span / puffs * 0.9f + rise * 0.4f
                p.ellipse(px, bottom - rise * 0.5f, rx, rise) { dx, dy ->
                    // Flat across the bottom, as a cloud is.
                    if (dy > 0.45f) return@ellipse 0
                    val material = when {
                        dy < -0.5f -> light
                        dy > 0.2f -> shade
                        else -> body
                    }
                    ScenePalettes.cloud(material, LandPainter.roundShade(dx, dy))
                }
            }
        }
    }

    private const val PI_F = 3.1415927f
}

/** One biome's land: how high its ground stands, what it is made of, and how it is dressed. */
private abstract class Land(val biome: Biome) {

    /** The ground's height above the band's bottom at band-x [x], in pixels. */
    abstract fun surface(band: Band, x: Int, seed: Int): Float

    /**
     * The level of water (or lava) over the low ground, above the band's bottom and its floor; 0
     * for none.
     */
    open fun waterline(band: Band): Float = 0f

    /** [waterline] over the band's floor, as the ground's height is; 0 for none. */
    fun liquid(band: Band): Float = waterline(band).let { if (it > 0f) it + band.floor else 0f }

    /**
     * Whether the liquid runs all the way down far off, as open water does, or pools on the ground
     * under it, as lava does in a valley.
     */
    open val deepLiquid: Boolean get() = true

    /** What the liquid is: its surface, its body and its depths. */
    open val liquidTop: Int get() = 0
    open val liquidBody: Int get() = 0
    open val liquidDeep: Int get() = 0

    /** How many rows of a surface face the way it slopes, and how many below that are lit at all. */
    open val rim: Int get() = 2
    open val body: Int get() = 14

    /** The material of the ground at [x], [y], [depth] rows under its surface of [height]. */
    abstract fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int

    /** Dresses the band: trees, rocks, ruins and the rest, on the columns [own] says are this biome's. */
    abstract fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean)

    fun code(material: Int, shade: Int): Int = ScenePalettes.code(biome, material, shade)

    /**
     * Paints one column of ground of [height] sloping by [slope]. Where the ground lies under this
     * land's water, far off the water runs all the way down, deepening; close up, in the near band,
     * it is shallows, with the bottom showing under it. Lava pools on the ground everywhere; see
     * [deepLiquid].
     */
    fun column(p: LandPainter, x: Int, height: Float, slope: Float, seed: Int) {
        val band = p.band
        val h = p.height
        val level = liquid(band)
        val surface = (h - level).roundToInt().coerceAtLeast(0)
        if (level > height && band != Band.NEAR && deepLiquid) {
            pool(p, x, surface, h, seed)
            return
        }
        val top = (h - height).roundToInt().coerceIn(0, h)
        if (level > height) pool(p, x, surface, top, seed)
        val facing = LandPainter.facing(slope)
        for (y in top until h) {
            val depth = y - top
            val shade = when {
                depth < rim -> facing
                depth < rim + body -> BODY
                depth < rim + body + 2 -> if ((x + y) and 1 == 0) BODY else DEEP
                else -> DEEP
            }
            val material = material(band, x, y, depth, height, seed)
            if (material != 0) p.set(x, y, code(material, shade))
        }
    }

    /** A column of water (or lava) from row [surface] down to [bottom], with ripples across it. */
    private fun pool(p: LandPainter, x: Int, surface: Int, bottom: Int, seed: Int) {
        for (y in surface until bottom) {
            val depth = y - surface
            // Ripples: short bright dashes across the water, sparser the deeper they lie.
            val ripple = depth > 1 && depth % 4 == 1 && Noise.unit(
                x / 5,
                y,
                seed + 31
            ) < 0.22f - depth * 0.004f
            val (material, shade) = when {
                depth == 0 -> liquidTop to TOP
                ripple -> liquidTop to BODY
                depth < 6 -> liquidBody to BODY
                else -> liquidBody to DEEP
            }
            p.set(
                x,
                y,
                code(if (depth > 14 && !ripple && liquidDeep != 0) liquidDeep else material, shade)
            )
        }
    }

    /** Every cell of [size] whose anchor could reach into [p], with its index and anchor x. */
    inline fun cells(
        p: LandPainter,
        size: Int,
        reach: Int,
        salt: Int,
        seed: Int,
        chance: Float,
        each: (cell: Int, x: Int) -> Unit
    ) {
        val first = floor((p.left - reach).toFloat() / size).toInt()
        val last = floor((p.left + p.width + reach).toFloat() / size).toInt()
        for (cell in first..last) {
            if (Noise.unit(cell, seed + salt) >= chance) continue
            val x = cell * size + (Noise.unit(cell, seed + salt + 1) * size).toInt()
            each(cell, x)
        }
    }

    /** A dice roll for [cell], 0..1, of its own [salt]. */
    fun roll(cell: Int, salt: Int, seed: Int): Float = Noise.unit(cell, seed + salt)

    /** Where the ground stands at [x], as a row of the band. */
    fun groundRow(p: LandPainter, surface: (Int) -> Float, x: Int): Float = p.height - surface(x)

    // --- what stands on the ground -----------------------------------------------------------------

    /** A conifer [tall] pixels high on [baseY], in tiers, its edges catching the light. */
    fun pine(
        p: LandPainter,
        cx: Int,
        baseY: Float,
        tall: Float,
        dark: Int,
        mid: Int,
        trunk: Int,
        snow: Int = 0
    ) {
        val trunkRows = (tall * 0.12f).roundToInt().coerceAtLeast(1)
        for (y in 0 until trunkRows) p.set(cx, (baseY - y).roundToInt(), code(trunk, DEEP))
        val crownBottom = baseY - trunkRows
        val crown = tall - trunkRows
        val tiers = (crown / 9f).roundToInt().coerceIn(2, 6)
        val tierHeight = crown / tiers
        for (tier in 0 until tiers) {
            // Lower tiers are wider; each overlaps the one below it.
            val tierBottom = crownBottom - tier * tierHeight * 0.85f
            val tierTop = tierBottom - tierHeight * 1.25f
            val halfWidth = tall * 0.32f * (1f - tier / (tiers + 0.6f))
            var y = tierTop
            while (y <= tierBottom) {
                val along = ((y - tierTop) / (tierBottom - tierTop)).coerceIn(0f, 1f)
                val half = halfWidth * along
                val row = y.roundToInt()
                for (x in (cx - half).roundToInt()..(cx + half).roundToInt()) {
                    val dx = if (half < 0.5f) 0f else (x - cx) / half
                    val edge = row == tierBottom.roundToInt()
                    val material = when {
                        snow != 0 && along < 0.55f && abs(dx) > 0.2f -> snow
                        snow != 0 && along < 0.25f -> snow
                        dx > 0.3f || edge -> dark
                        else -> mid
                    }
                    val shade = if (edge) DEEP else LandPainter.sideShade(dx)
                    p.set(x, row, code(material, if (material == snow) TOP else shade))
                }
                y += 1f
            }
        }
    }

    /** A round mass of leaves at [cx], [cy], speckled darker, lit on the side it turns to. */
    fun crown(
        p: LandPainter,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        leaf: Int,
        dark: Int,
        seed: Int
    ) {
        p.ellipse(cx, cy, rx, ry) { dx, dy ->
            val speckle = Noise.unit(
                ((cx + dx * rx) / 2f).toInt(),
                ((cy + dy * ry) / 2f).toInt(),
                seed + 91
            ) < 0.22f
            code(if (speckle && dy > -0.6f) dark else leaf, LandPainter.roundShade(dx, dy))
        }
    }

    /** A trunk [width] wide from [y0] down to [y1]. */
    fun trunk(p: LandPainter, cx: Int, y0: Float, y1: Float, width: Int, material: Int) {
        val half = width / 2f
        for (x in (cx - half).roundToInt() until (cx - half).roundToInt() + width) {
            val dx = (x + 0.5f - cx) / half.coerceAtLeast(0.5f)
            for (y in y0.roundToInt()..y1.roundToInt()) p.set(
                x,
                y,
                code(material, LandPainter.sideShade(dx))
            )
        }
    }

    /** A rock half sunk in the ground at [cx], its top catching the light. */
    fun rock(
        p: LandPainter,
        cx: Float,
        baseY: Float,
        rx: Float,
        ry: Float,
        stone: Int,
        cap: Int = 0
    ) {
        p.ellipse(cx, baseY, rx, ry) { dx, dy ->
            if (dy > 0.4f) return@ellipse 0
            val material = if (cap != 0 && dy < -0.55f) cap else stone
            code(material, LandPainter.roundShade(dx, dy))
        }
    }

    /** Fronds fanning up from [cx], [baseY]. */
    fun fern(
        p: LandPainter,
        cx: Int,
        baseY: Float,
        tall: Float,
        light: Int,
        dark: Int,
        cell: Int,
        seed: Int
    ) {
        val fronds = 3 + (roll(cell, 61, seed) * 3).toInt()
        for (f in 0 until fronds) {
            val spread = (f - (fronds - 1) / 2f) / fronds
            val length = tall * (0.6f + 0.4f * roll(cell * 5 + f, 62, seed))
            val tipX = cx + spread * length * 1.6f
            val tipY = baseY - length * (1f - abs(spread) * 0.8f)
            p.line(
                cx.toFloat(),
                baseY,
                tipX,
                tipY,
                code(if (f % 2 == 0) light else dark, if (spread < 0) LEFT else RIGHT)
            )
        }
    }
}

// --- the biomes --------------------------------------------------------------------------------------

private object JungleLand : Land(Biome.JUNGLE) {
    const val SOIL = 1
    const val LEAF_DARK = 2
    const val LEAF = 3
    const val LEAF_LIGHT = 4
    const val STONE = 5
    const val STONE_MOSS = 6
    const val TRUNK = 7
    const val PALM = 8
    const val KARST = 9
    const val KARST_DARK = 10

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> {
            // Wooded karst domes, round-topped and steep.
            var h = 34f + 18f * Noise.fbm(x / 70f, seed + 1)
            val cell = 84
            val c = floor(x / cell.toFloat()).toInt()
            for (k in c - 1..c + 1) {
                val center = k * cell + Noise.unit(k, seed + 2) * cell
                val radius = 32f + Noise.unit(k, seed + 3) * 30f
                val tall = 60f + Noise.unit(k, seed + 4) * 95f
                val dx = (x - center) / radius
                if (abs(dx) < 1f) h = maxOf(h, 30f + tall * (1f - dx * dx).pow(0.6f))
            }
            h
        }

        Band.MID -> 58f + 22f * Noise.fbm(x / 90f, seed + 5)
        else -> 22f + 6f * Noise.fbm(x / 26f, seed + 6)
    }

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when (band) {
            Band.FAR -> if (Noise.unit(x / 3, y / 3, seed + 7) < 0.25f) KARST_DARK else KARST
            Band.MID -> if (depth < 3) LEAF_DARK else if (Noise.unit(
                    x / 2,
                    y / 2,
                    seed + 8
                ) < 0.2f
            ) LEAF else LEAF_DARK

            else -> if (depth < 3) LEAF_DARK else SOIL
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        when (p.band) {
            Band.FAR -> Unit
            Band.MID -> {
                // A ruin now and then, its top stepping up out of the canopy, behind the trees.
                cells(p, 620, 80, 110, seed, 0.55f) { cell, x ->
                    if (!own(x)) return@cells
                    val ground = groundRow(p, surface, x)
                    val tiers = 4 + (roll(cell, 111, seed) * 3).toInt()
                    for (t in 0 until tiers) {
                        val half = 46 - t * 8
                        val bottom = ground - 6 - t * 12
                        p.rect(
                            x - half,
                            (bottom - 12).roundToInt(),
                            x + half,
                            bottom.roundToInt()
                        ) { px, py ->
                            val dx = (px - x) / half.toFloat()
                            val moss = Noise.unit(px / 2, py / 2, seed + 112) < 0.3f
                            val shade =
                                if (py.toFloat() >= bottom - 1) DEEP else if (py.toFloat() <= bottom - 11) TOP else LandPainter.sideShade(
                                    dx
                                )
                            code(if (moss) STONE_MOSS else STONE, shade)
                        }
                    }
                }
                // The canopy: crowns crowding over one another, a palm standing out of it here and there.
                cells(p, 20, 30, 120, seed, 0.92f) { cell, x ->
                    if (!own(x)) return@cells
                    val ground = groundRow(p, surface, x)
                    val r = 11f + roll(cell, 121, seed) * 9f
                    val cy = ground - r * 0.3f - roll(cell, 122, seed) * 16f
                    crown(
                        p,
                        x.toFloat(),
                        cy,
                        r * 1.15f,
                        r,
                        if (roll(cell, 123, seed) < 0.3f) LEAF_LIGHT else LEAF,
                        LEAF_DARK,
                        seed
                    )
                }
                cells(p, 140, 40, 130, seed, 0.45f) { cell, x ->
                    if (!own(x)) return@cells
                    val ground = groundRow(p, surface, x)
                    val tall = 46f + roll(cell, 131, seed) * 30f
                    palm(p, x, ground - 10f, tall, cell, seed)
                }
            }

            else -> {
                cells(p, 11, 20, 140, seed, 0.6f) { cell, x ->
                    if (!own(x)) return@cells
                    fern(
                        p,
                        x,
                        groundRow(p, surface, x) + 1f,
                        7f + roll(cell, 141, seed) * 10f,
                        LEAF_LIGHT,
                        LEAF,
                        cell,
                        seed
                    )
                }
                cells(p, 90, 30, 150, seed, 0.35f) { cell, x ->
                    if (!own(x)) return@cells
                    palm(
                        p,
                        x,
                        groundRow(p, surface, x) + 1f,
                        30f + roll(cell, 151, seed) * 12f,
                        cell,
                        seed
                    )
                }
                cells(p, 60, 20, 160, seed, 0.35f) { cell, x ->
                    if (!own(x)) return@cells
                    rock(
                        p,
                        x.toFloat(),
                        groundRow(p, surface, x) + 2f,
                        6f + roll(cell, 161, seed) * 6f,
                        5f,
                        STONE,
                        STONE_MOSS
                    )
                }
            }
        }
    }

    /** A palm: a trunk bending as it rises, a burst of fronds at its top. */
    fun palm(p: LandPainter, x: Int, baseY: Float, tall: Float, cell: Int, seed: Int) {
        val lean = (roll(cell, 171, seed) - 0.5f) * 10f
        var topX = x.toFloat()
        val rows = tall.roundToInt()
        for (i in 0 until rows) {
            val along = i / tall
            topX = x + lean * along * along
            val y = (baseY - i).roundToInt()
            p.set(topX.roundToInt(), y, code(TRUNK, LEFT))
            p.set(topX.roundToInt() + 1, y, code(TRUNK, if (i % 3 == 0) DEEP else RIGHT))
        }
        val topY = baseY - tall
        for (f in 0 until 7) {
            val angle = -PI_HALF * 0.15f + f * 0.45f + roll(cell * 7 + f, 172, seed) * 0.2f
            val length = 9f + roll(cell * 7 + f, 173, seed) * 7f
            val dx = kotlin.math.cos(angle + PI_HALF * 1.2f) * length * if (f % 2 == 0) 1f else -1f
            val dy = -sin(angle) * length * 0.4f + length * 0.25f
            p.line(topX, topY, topX + dx, topY + dy, code(PALM, if (dx < 0) LEFT else RIGHT))
            p.line(topX, topY + 1, topX + dx * 0.8f, topY + dy + 1, code(LEAF, DEEP))
        }
    }

    private const val PI_HALF = 1.5707964f
}

private object DesertLand : Land(Biome.DESERT) {
    const val SAND = 1
    const val SAND_SHADE = 2
    const val ROCK = 3
    const val ROCK_DARK = 4
    const val MESA_TOP = 5
    const val CACTUS = 6
    const val CACTUS_LIGHT = 7
    const val DUNE_FAR = 8
    const val ROCK_RED = 9

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> {
            // Mesas: the ground's noise cut into flat-topped steps with sheer sides.
            val q = Noise.fbm(x / 260f, seed + 11, 3)
            28f + 10f * Noise.fbm(x / 40f, seed + 12) + 72f * smoothstep(
                0.48f,
                0.51f,
                q
            ) + 38f * smoothstep(0.6f, 0.62f, q)
        }

        Band.MID -> 46f + 38f * (0.5f + 0.5f * sin(x / 70f + 3f * Noise.fbm(x / 240f, seed + 13)))
        else -> 26f + 5f * Noise.fbm(x / 50f, seed + 14)
    }

    override val rim: Int get() = 2

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when (band) {
            Band.FAR -> when {
                height < 44f -> DUNE_FAR
                depth < 3 -> MESA_TOP
                (y + (Noise.value(x / 30f, seed + 15) * 4).toInt()) % 9 < 3 -> ROCK_DARK
                else -> ROCK_RED
            }

            else -> {
                // Ripples in the sand, wavering across it.
                val wave = (y + (3f * sin(x / 11f + y * 0.7f)).roundToInt()) % 6
                if (depth > 2 && wave == 0) SAND_SHADE else SAND
            }
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        if (p.band != Band.NEAR && p.band != Band.MID) return
        val near = p.band == Band.NEAR
        cells(p, if (near) 64 else 110, 20, 210, seed, if (near) 0.4f else 0.3f) { cell, x ->
            if (!own(x)) return@cells
            val ground = groundRow(p, surface, x) + 1f
            cactus(
                p,
                x,
                ground,
                (if (near) 22f else 14f) + roll(cell, 211, seed) * (if (near) 18f else 10f),
                cell,
                seed
            )
        }
        cells(p, 48, 20, 220, seed, 0.3f) { cell, x ->
            if (!own(x)) return@cells
            rock(
                p,
                x.toFloat(),
                groundRow(p, surface, x) + 2f,
                4f + roll(cell, 221, seed) * 7f,
                3f + roll(cell, 222, seed) * 3f,
                ROCK,
                0
            )
        }
    }

    /** A saguaro: a ribbed column, with an arm or two turning up. */
    fun cactus(p: LandPainter, x: Int, baseY: Float, tall: Float, cell: Int, seed: Int) {
        val width = if (tall > 26f) 5 else 3
        for (y in 0 until tall.roundToInt()) {
            val row = (baseY - y).roundToInt()
            for (i in 0 until width) {
                val shade = when (i) {
                    0 -> LEFT; width - 1 -> RIGHT; else -> BODY
                }
                p.set(
                    x - width / 2 + i,
                    row,
                    code(if (i == 1 && y % 3 != 0) CACTUS_LIGHT else CACTUS, shade)
                )
            }
        }
        // The top, rounded.
        p.set(x, (baseY - tall).roundToInt(), code(CACTUS, TOP))
        for (side in listOf(-1, 1)) {
            if (roll(cell, 231 + side, seed) < 0.35f) continue
            val armAt = baseY - tall * (0.35f + 0.25f * roll(cell, 233 + side, seed))
            val reach = width / 2 + 3
            for (i in 1..reach) p.set(x + side * i, armAt.roundToInt(), code(CACTUS, BODY))
            val up = tall * 0.3f
            for (y in 0..up.roundToInt()) p.set(
                x + side * reach,
                (armAt - y).roundToInt(),
                code(CACTUS, if (side < 0) LEFT else RIGHT)
            )
        }
    }
}

private object CoastLand : Land(Biome.COAST) {
    const val SEA_DEEP = 1
    const val SEA = 2
    const val SEA_LIGHT = 3
    const val FOAM = 4
    const val SAND = 5
    const val LIMESTONE = 6
    const val LIMESTONE_DARK = 7
    const val GREENERY = 8
    const val PALM = 9
    const val TRUNK = 10

    override fun waterline(band: Band): Float = when (band) {
        Band.FAR -> 14f
        Band.MID -> 58f
        else -> 30f
    }

    override val liquidTop: Int get() = SEA_LIGHT
    override val liquidBody: Int get() = SEA
    override val liquidDeep: Int get() = SEA_DEEP

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> islands(x, seed + 21, 150, 0.7f, 22f, 36f, 40f, 75f, base = 4f)
        Band.MID -> islands(x, seed + 22, 270, 0.5f, 28f, 50f, 72f, 70f, base = 30f)
        else -> 18f + 20f * Noise.fbm(x / 90f, seed + 23)
    }

    /** Karst islands standing steep out of the sea, one to a cell when the dice say so. */
    private fun islands(
        x: Int,
        seed: Int,
        cell: Int,
        chance: Float,
        width: Float,
        widthSpread: Float,
        tall: Float,
        tallSpread: Float,
        base: Float
    ): Float {
        var h = base
        val c = floor(x / cell.toFloat()).toInt()
        for (k in c - 1..c + 1) {
            if (Noise.unit(k, seed) >= chance) continue
            val center = k * cell + Noise.unit(k, seed + 1) * cell
            val half = width + Noise.unit(k, seed + 2) * widthSpread
            val top = tall + Noise.unit(k, seed + 3) * tallSpread
            val dx = abs(x - center) / half
            if (dx < 1f) h = maxOf(h, top * (1f - dx.pow(4)).pow(0.5f))
        }
        return h
    }

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when {
            band == Band.NEAR -> if (height < liquid(band) + 3f && depth < 2) SEA_LIGHT else SAND
            depth < 4 + (Noise.value(x / 6f, seed + 24) * 4).toInt() -> GREENERY
            Noise.unit(x / 2, 0, seed + 25) < 0.3f -> LIMESTONE_DARK
            else -> LIMESTONE
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        val level = liquid(p.band)
        // Foam where the sea meets the islands and the beach.
        for (x in p.left until p.left + p.width) {
            if (!own(x)) continue
            val h = surface(x)
            if (h > level - 1f && h < level + 2f || (surface(x - 1) < level) != (h < level)) {
                p.set(x, (p.height - level).roundToInt(), code(FOAM, TOP))
            }
        }
        if (p.band == Band.NEAR) {
            cells(p, 80, 30, 310, seed, 0.4f) { cell, x ->
                if (!own(x) || surface(x) < level + 2f) return@cells
                JungleLandPalm.draw(
                    this,
                    p,
                    x,
                    groundRow(p, surface, x) + 1f,
                    30f + roll(cell, 311, seed) * 10f,
                    cell,
                    seed
                )
            }
            cells(p, 50, 20, 320, seed, 0.35f) { cell, x ->
                if (!own(x)) return@cells
                rock(
                    p,
                    x.toFloat(),
                    maxOf(groundRow(p, surface, x), p.height - level) + 2f,
                    5f + roll(cell, 321, seed) * 7f,
                    5f,
                    LIMESTONE_DARK,
                    LIMESTONE
                )
            }
        }
    }

    /** A palm in the coast's own colors. */
    private object JungleLandPalm {
        fun draw(
            land: Land,
            p: LandPainter,
            x: Int,
            baseY: Float,
            tall: Float,
            cell: Int,
            seed: Int
        ) {
            for (i in 0 until tall.roundToInt()) {
                val along = i / tall
                val px = (x + 4f * along * along).roundToInt()
                p.set(
                    px,
                    (baseY - i).roundToInt(),
                    land.code(TRUNK, if (i % 3 == 0) DEEP else LEFT)
                )
            }
            val topX = x + 4f
            val topY = baseY - tall
            for (f in 0 until 6) {
                val dir = if (f % 2 == 0) 1f else -1f
                val length = 8f + land.roll(cell * 6 + f, 330, seed) * 6f
                p.line(
                    topX,
                    topY,
                    topX + dir * length,
                    topY + 2f + f,
                    land.code(PALM, if (dir < 0) LEFT else RIGHT)
                )
            }
        }
    }
}

private object PeaksLand : Land(Biome.PEAKS) {
    const val ROCK_DARK = 1
    const val ROCK = 2
    const val SNOW = 3
    const val SNOW_SHADE = 4
    const val PINE_DARK = 5
    const val PINE = 6
    const val ICE = 7
    const val TRUNK = 8
    const val ROCK_LIGHT = 9

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> 36f + 146f * Noise.ridged(x / 240f, seed + 41, 5).pow(1.25f)
        Band.MID -> 44f + 46f * Noise.fbm(x / 130f, seed + 42)
        else -> 24f + 6f * Noise.fbm(x / 45f, seed + 43)
    }

    override val body: Int get() = 10

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when (band) {
            Band.FAR -> {
                val above = height - depth
                val snowline = 92f + 22f * Noise.value(x / 26f, seed + 44)
                when {
                    above > snowline -> if (depth > 8 && Noise.unit(
                            x / 2,
                            y / 3,
                            seed + 45
                        ) < 0.25f
                    ) SNOW_SHADE else SNOW

                    depth < 2 && above > snowline - 30f -> SNOW
                    Noise.unit(x / 3, y / 5, seed + 46) < 0.35f -> ROCK_DARK
                    else -> ROCK
                }
            }

            else -> if (depth < 6 || Noise.unit(
                    x / 3,
                    y / 2,
                    seed + 47
                ) < 0.7f
            ) SNOW else SNOW_SHADE
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        when (p.band) {
            Band.FAR -> Unit
            Band.MID -> cells(p, 12, 20, 410, seed, 0.62f) { cell, x ->
                if (!own(x)) return@cells
                pine(
                    p,
                    x,
                    groundRow(p, surface, x) + 2f,
                    18f + roll(cell, 411, seed) * 24f,
                    PINE_DARK,
                    PINE,
                    TRUNK,
                    SNOW
                )
            }

            else -> {
                cells(p, 46, 20, 420, seed, 0.4f) { cell, x ->
                    if (!own(x)) return@cells
                    pine(
                        p,
                        x,
                        groundRow(p, surface, x) + 2f,
                        22f + roll(cell, 421, seed) * 14f,
                        PINE_DARK,
                        PINE,
                        TRUNK,
                        SNOW
                    )
                }
                cells(p, 40, 20, 430, seed, 0.35f) { cell, x ->
                    if (!own(x)) return@cells
                    rock(
                        p,
                        x.toFloat(),
                        groundRow(p, surface, x) + 2f,
                        5f + roll(cell, 431, seed) * 8f,
                        4f + roll(cell, 432, seed) * 3f,
                        ROCK,
                        SNOW
                    )
                }
            }
        }
    }
}

private object VolcanoLand : Land(Biome.VOLCANO) {
    const val BASALT_DARK = 1
    const val BASALT = 2
    const val ASH = 3
    const val ASH_LIGHT = 4
    const val LAVA = 5
    const val LAVA_HOT = 6
    const val EMBER = 7
    const val OBSIDIAN = 8
    const val SCORCHED = 9

    /** How far apart volcanoes stand along the far band, at most one to a stretch this wide. */
    const val CONE_CELL = 560

    override fun waterline(band: Band): Float = if (band == Band.MID) 48f else 0f

    override val deepLiquid: Boolean get() = false
    override val liquidTop: Int get() = LAVA_HOT
    override val liquidBody: Int get() = LAVA
    override val liquidDeep: Int get() = EMBER

    /** The cone standing in [cell], if any: its middle, half its foot's width, and its height. */
    private fun cone(cell: Int, seed: Int): FloatArray? {
        if (Noise.unit(cell, seed + 51) > 0.85f) return null
        return floatArrayOf(
            cell * CONE_CELL + 100f + Noise.unit(cell, seed + 52) * 360f,
            140f + Noise.unit(cell, seed + 53) * 70f,
            138f + Noise.unit(cell, seed + 54) * 34f,
        )
    }

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> {
            var h = 30f + 40f * Noise.ridged(x / 120f, seed + 55, 3)
            val c = floor(x / CONE_CELL.toFloat()).toInt()
            for (k in c - 1..c + 1) {
                val cone = cone(k, seed) ?: continue
                val dx = abs(x - cone[0]) / cone[1]
                if (dx >= 1f) continue
                var top = cone[2] * (1f - dx).pow(1.25f)
                // The crater, a notch in the summit.
                val crater = abs(x - cone[0])
                if (crater < CRATER_HALF) top -= 7f * (1f - crater / CRATER_HALF)
                h = maxOf(h, top)
            }
            h
        }

        Band.MID -> 38f + 76f * Noise.ridged(x / 110f, seed + 56, 4)
        else -> 24f + 7f * Noise.fbm(x / 40f, seed + 57)
    }

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when (band) {
            Band.FAR -> {
                // Lava running down from the craters in streaks, on the surface of the slopes.
                val streak = Noise.value(x / 9f + y / 23f, seed + 58)
                when {
                    height > 100f && depth < 3 && streak > 0.72f -> if (depth == 0) LAVA_HOT else LAVA
                    height > 100f && depth < 4 && streak > 0.66f -> EMBER
                    depth < 3 -> ASH_LIGHT
                    Noise.unit(x / 3, y / 4, seed + 59) < 0.3f -> BASALT_DARK
                    else -> BASALT
                }
            }

            Band.MID -> when {
                depth < 2 -> ASH
                Noise.unit(x / 2, y / 3, seed + 60) < 0.3f -> BASALT_DARK
                else -> BASALT
            }

            else -> when {
                depth < 2 -> ASH_LIGHT
                depth < 5 -> ASH
                else -> SCORCHED
            }
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        when (p.band) {
            Band.FAR -> {
                // Each crater glows, and the scenery sends its smoke up from where it stands.
                val c = floor(p.left / CONE_CELL.toFloat()).toInt()
                for (k in c - 1..c + 1) {
                    val cone = cone(k, seed) ?: continue
                    val x = cone[0].roundToInt()
                    if (!own(x)) continue
                    val top = groundRow(p, surface, x)
                    for (dx in -CRATER_HALF.toInt()..CRATER_HALF.toInt()) {
                        val row = groundRow(p, surface, x + dx).roundToInt()
                        p.set(x + dx, row, code(LAVA_HOT, TOP))
                        p.set(x + dx, row + 1, code(LAVA, BODY))
                    }
                    if (x in p) p.features += Feature(x.toFloat(), top)
                }
            }

            Band.MID -> cells(p, 70, 20, 510, seed, 0.4f) { cell, x ->
                if (!own(x) || surface(x) < liquid(p.band)) return@cells
                spire(p, x, groundRow(p, surface, x) + 2f, 12f + roll(cell, 511, seed) * 20f)
            }

            else -> {
                // Cracks in the crust glowing from underneath.
                cells(p, 34, 30, 520, seed, 0.55f) { cell, x ->
                    if (!own(x)) return@cells
                    var cx = x.toFloat()
                    var cy = groundRow(p, surface, x) + 3f
                    val steps = 4 + (roll(cell, 521, seed) * 5).toInt()
                    for (s in 0 until steps) {
                        val nx = cx + 2f + roll(cell * 9 + s, 522, seed) * 4f
                        val ny = cy + (roll(cell * 9 + s, 523, seed) - 0.3f) * 5f
                        p.line(
                            cx,
                            cy,
                            nx,
                            ny.coerceAtMost(p.height - 2f),
                            code(if (s % 3 == 1) LAVA_HOT else LAVA, BODY)
                        )
                        cx = nx
                        cy = ny.coerceAtMost(p.height - 2f)
                    }
                }
                cells(p, 52, 20, 530, seed, 0.35f) { cell, x ->
                    if (!own(x)) return@cells
                    rock(
                        p,
                        x.toFloat(),
                        groundRow(p, surface, x) + 2f,
                        5f + roll(cell, 531, seed) * 6f,
                        5f + roll(cell, 532, seed) * 4f,
                        OBSIDIAN,
                        0
                    )
                }
            }
        }
    }

    /** A jagged spike of basalt, glowing at its foot. */
    fun spire(p: LandPainter, x: Int, baseY: Float, tall: Float) {
        for (y in 0 until tall.roundToInt()) {
            val half = (tall - y) / tall * 4f
            for (dx in -half.roundToInt()..half.roundToInt()) {
                val shade = LandPainter.sideShade(if (half < 0.5f) 0f else dx / half)
                p.set(x + dx, (baseY - y).roundToInt(), code(if (y < 2) EMBER else OBSIDIAN, shade))
            }
        }
    }

    private const val CRATER_HALF = 11f
}

private object SwampLand : Land(Biome.SWAMP) {
    const val MUD = 1
    const val MOSS = 2
    const val MOSS_LIGHT = 3
    const val WATER = 4
    const val WATER_LIGHT = 5
    const val BARK = 6
    const val BARK_LIGHT = 7
    const val REED = 8
    const val LILY = 9
    const val HANGING_MOSS = 10
    const val CYPRESS = 11

    override fun waterline(band: Band): Float = when (band) {
        Band.FAR -> 14f
        Band.MID -> 58f
        else -> 34f
    }

    override val liquidTop: Int get() = WATER_LIGHT
    override val liquidBody: Int get() = WATER
    override val liquidDeep: Int get() = WATER

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> {
            // A low treeline of cypresses, each a narrow spire.
            var h = 26f + 14f * Noise.fbm(x / 60f, seed + 61)
            val cell = 15
            val c = floor(x / cell.toFloat()).toInt()
            for (k in c - 1..c + 1) {
                if (Noise.unit(k, seed + 62) > 0.8f) continue
                val center = k * cell + Noise.unit(k, seed + 63) * cell
                val tall = 18f + Noise.unit(k, seed + 64) * 36f
                val half = 4f + Noise.unit(k, seed + 65) * 5f
                val dx = abs(x - center) / half
                if (dx < 1f) h = maxOf(h, 24f + tall * (1f - dx * dx).pow(0.7f) * (1f - 0.15f * dx))
            }
            h
        }

        Band.MID -> 34f + 34f * Noise.fbm(x / 100f, seed + 66)
        else -> 18f + 24f * Noise.fbm(x / 70f, seed + 67)
    }

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when (band) {
            Band.FAR -> if (Noise.unit(x / 2, y / 3, seed + 68) < 0.3f) MOSS else CYPRESS
            else -> when {
                depth < 2 -> if (Noise.unit(x, 0, seed + 69) < 0.5f) MOSS_LIGHT else MOSS
                else -> MUD
            }
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        val level = liquid(p.band)
        when (p.band) {
            Band.FAR -> Unit
            Band.MID -> {
                cells(p, 74, 40, 610, seed, 0.6f) { cell, x ->
                    if (!own(x)) return@cells
                    val base = p.height - maxOf(level, surface(x)) + 2f
                    deadTree(p, x, base, 52f + roll(cell, 611, seed) * 58f, cell, seed)
                }
                reeds(p, seed, surface, own, level, 9, 14f, 620)
            }

            else -> {
                reeds(p, seed, surface, own, level, 6, 18f, 630)
                cells(p, 22, 10, 640, seed, 0.5f) { cell, x ->
                    if (!own(x) || surface(x) > level - 1f) return@cells
                    val y = p.height - level
                    p.ellipse(
                        x.toFloat(),
                        y + 0.5f,
                        3f + roll(cell, 641, seed) * 4f,
                        1.4f
                    ) { dx, dy ->
                        code(LILY, if (dy < 0f) TOP else BODY)
                    }
                }
                cells(p, 120, 50, 650, seed, 0.35f) { cell, x ->
                    if (!own(x)) return@cells
                    val base = p.height - maxOf(level, surface(x)) + 2f
                    deadTree(p, x, base, 34f + roll(cell, 651, seed) * 20f, cell, seed)
                }
            }
        }
    }

    /** Reeds standing in the shallows and on the banks, a seed head on each. */
    private fun reeds(
        p: LandPainter,
        seed: Int,
        surface: (Int) -> Float,
        own: (Int) -> Boolean,
        level: Float,
        cell: Int,
        tall: Float,
        salt: Int
    ) {
        cells(p, cell, 6, salt, seed, 0.5f) { c, x ->
            if (!own(x)) return@cells
            val h = surface(x)
            if (h < level - 10f) return@cells
            val base = p.height - maxOf(h, level) + 1f
            val stalks = 2 + (roll(c, salt + 1, seed) * 3).toInt()
            for (s in 0 until stalks) {
                val sx = x + s * 2 - stalks
                val length = tall * (0.5f + 0.5f * roll(c * 5 + s, salt + 2, seed))
                for (y in 0 until length.roundToInt()) p.set(
                    sx,
                    (base - y).roundToInt(),
                    code(REED, if (s % 2 == 0) LEFT else RIGHT)
                )
                p.set(sx, (base - length).roundToInt(), code(BARK, DEEP))
                p.set(sx, (base - length).roundToInt() + 1, code(BARK, DEEP))
            }
        }
    }

    /** A dead tree: a bare trunk, a few crooked branches, moss hanging from them in strands. */
    fun deadTree(p: LandPainter, x: Int, baseY: Float, tall: Float, cell: Int, seed: Int) {
        val width = if (tall > 60f) 5 else 3
        trunk(p, x, baseY - tall, baseY, width, BARK)
        // The roots flaring into the water.
        for (side in listOf(-1, 1)) p.line(
            x.toFloat(),
            baseY - 3f,
            x + side * (width + 2f),
            baseY + 1f,
            code(BARK, DEEP)
        )
        val branches = 2 + (roll(cell, 661, seed) * 3).toInt()
        for (b in 0 until branches) {
            val side = if ((b + cell) % 2 == 0) 1f else -1f
            val y = baseY - tall * (0.45f + 0.5f * b / branches)
            val reach = tall * (0.18f + 0.15f * roll(cell * 5 + b, 662, seed))
            val endX = x + side * reach
            val endY = y - reach * 0.6f
            p.line(x.toFloat(), y, endX, endY, code(BARK_LIGHT, if (side < 0) LEFT else RIGHT))
            // Moss hanging off it.
            val strands = 2 + (roll(cell * 5 + b, 663, seed) * 3).toInt()
            for (s in 0 until strands) {
                val along = (s + 1f) / (strands + 1f)
                val sx = x + (endX - x) * along
                val sy = y + (endY - y) * along
                val length = 5f + roll(cell * 31 + b * 7 + s, 664, seed) * 11f
                p.line(sx, sy + 1f, sx, sy + length, code(HANGING_MOSS, BODY))
            }
        }
    }
}

private object ForestLand : Land(Biome.FOGGY_FOREST) {
    const val SOIL = 1
    const val MOSS = 2
    const val FERN = 3
    const val PINE_DARK = 4
    const val PINE = 5
    const val PINE_LIGHT = 6
    const val TRUNK = 7
    const val ROCK = 8
    const val MUSHROOM = 9

    override fun surface(band: Band, x: Int, seed: Int): Float = when (band) {
        Band.FAR -> {
            // A ridge furred with pines: the ridge's line, and a sawtooth of treetops along it.
            val ridge = 58f + 76f * Noise.fbm(x / 180f, seed + 71)
            val cell = 8
            val c = floor(x / cell.toFloat()).toInt()
            var trees = 0f
            for (k in c - 1..c + 1) {
                val center = k * cell + Noise.unit(k, seed + 72) * cell
                val tall = 6f + Noise.unit(k, seed + 73) * 12f
                val dx = abs(x - center) / (tall * 0.35f)
                if (dx < 1f) trees = maxOf(trees, tall * (1f - dx))
            }
            ridge + trees
        }

        Band.MID -> 30f + 16f * Noise.fbm(x / 90f, seed + 74)
        else -> 22f + 6f * Noise.fbm(x / 35f, seed + 75)
    }

    override fun material(band: Band, x: Int, y: Int, depth: Int, height: Float, seed: Int): Int =
        when (band) {
            Band.FAR -> PINE_DARK
            else -> if (depth < 3) MOSS else SOIL
        }

    override fun dress(p: LandPainter, seed: Int, surface: (Int) -> Float, own: (Int) -> Boolean) {
        when (p.band) {
            Band.FAR -> Unit
            Band.MID -> cells(p, 14, 40, 710, seed, 0.78f) { cell, x ->
                if (!own(x)) return@cells
                val tall = 52f + roll(cell, 711, seed) * 74f
                pine(
                    p,
                    x,
                    groundRow(p, surface, x) + 2f,
                    tall,
                    PINE_DARK,
                    if (roll(cell, 712, seed) < 0.3f) PINE_LIGHT else PINE,
                    TRUNK
                )
            }

            else -> {
                cells(p, 12, 20, 720, seed, 0.5f) { cell, x ->
                    if (!own(x)) return@cells
                    fern(
                        p,
                        x,
                        groundRow(p, surface, x) + 1f,
                        6f + roll(cell, 721, seed) * 9f,
                        FERN,
                        MOSS,
                        cell,
                        seed
                    )
                }
                cells(p, 40, 10, 730, seed, 0.25f) { cell, x ->
                    if (!own(x)) return@cells
                    val ground = groundRow(p, surface, x)
                    p.set(x, (ground - 1).roundToInt(), code(TRUNK, BODY))
                    p.set(x, (ground - 2).roundToInt(), code(TRUNK, BODY))
                    p.ellipse(x.toFloat(), ground - 3f, 3f, 1.6f) { dx, dy ->
                        code(
                            MUSHROOM,
                            LandPainter.roundShade(dx, dy)
                        )
                    }
                }
                cells(p, 56, 20, 740, seed, 0.3f) { cell, x ->
                    if (!own(x)) return@cells
                    rock(
                        p,
                        x.toFloat(),
                        groundRow(p, surface, x) + 2f,
                        6f + roll(cell, 741, seed) * 8f,
                        5f + roll(cell, 742, seed) * 3f,
                        ROCK,
                        MOSS
                    )
                }
            }
        }
    }
}

// --- the obstacles ------------------------------------------------------------------------------------

/** A canvas for an obstacle [width] by [height]. */
private fun canvas(width: Int, height: Int) = LandPainter(Band.NEAR, 0, width, height)

/** A size between [least] and [least] + [spread], by [seed]'s dice. */
private fun size(least: Int, spread: Int, seed: Int, salt: Int): Int =
    least + (Noise.unit(salt, seed) * spread).toInt()

/**
 * An obstacle of [land]'s biome: on the ground, its bottom row is the frame's (a blit leaves it
 * undrawn, as every obstacle's); hanging, its top row is the frame's top.
 */
private fun obstacleArt(land: Land, hanging: Boolean, kind: Int, seed: Int): LandPainter? =
    when (land) {
        is JungleLand -> if (hanging) jungleHanging(kind, seed) else jungleGround(kind, seed)
        is DesertLand -> if (hanging) null else desertGround(kind, seed)
        is CoastLand -> if (hanging) null else coastGround(kind, seed)
        is PeaksLand -> if (hanging) peaksHanging(kind, seed) else peaksGround(kind, seed)
        is VolcanoLand -> if (hanging) null else volcanoGround(kind, seed)
        is SwampLand -> if (hanging) swampHanging(kind, seed) else swampGround(kind, seed)
        is ForestLand -> if (hanging) forestHanging(kind, seed) else forestGround(kind, seed)
        else -> null
    }

private fun jungleGround(kind: Int, seed: Int): LandPainter = with(JungleLand) {
    when (kind) {
        0 -> {
            // A carved stele, mossy, its glyphs cut in rows.
            val w = size(22, 8, seed, 1)
            val h = size(48, 9, seed, 2)
            canvas(w, h).also { p ->
                p.rect(1, 3, w - 2, h - 1) { x, y ->
                    val dx = (x - w / 2f) / (w / 2f)
                    val glyph = (y - 6) % 8 in 0..1 && x in 4..w - 5 && Noise.unit(
                        x / 3,
                        y / 8,
                        seed
                    ) < 0.6f
                    val moss = Noise.unit(x / 2, y / 2, seed + 1) < 0.18f + 0.3f * (y.toFloat() / h)
                    code(
                        if (moss) STONE_MOSS else STONE,
                        if (glyph) DEEP else LandPainter.sideShade(dx)
                    )
                }
                p.ellipse(w / 2f, 4f, w / 2f - 1f, 3f) { dx, dy ->
                    code(
                        STONE,
                        LandPainter.roundShade(dx, dy)
                    )
                }
            }
        }

        1 -> {
            val w = size(58, 10, seed, 3)
            val h = size(38, 8, seed, 4)
            canvas(w, h).also { p ->
                rock(p, w / 2f, h.toFloat(), w / 2f - 1f, h - 2f, STONE, STONE_MOSS)
                fern(p, 6, h - 2f, 12f, LEAF_LIGHT, LEAF, seed, seed)
                fern(p, w - 7, h - 2f, 10f, LEAF_LIGHT, LEAF, seed + 1, seed)
            }
        }

        else -> {
            val h = size(52, 8, seed, 5)
            canvas(40, h).also { p -> palm(p, 18, h - 1f, h - 8f, seed, seed) }
        }
    }
}

private fun jungleHanging(kind: Int, seed: Int): LandPainter = with(JungleLand) {
    val w = size(38, 10, seed, 6 + kind)
    val h = size(44, 12, seed, 8 + kind)
    canvas(w, h).also { p ->
        // Foliage along the top, vines trailing from it, leaves on the vines.
        p.ellipse(w / 2f, 0f, w / 2f, 7f) { dx, dy ->
            code(
                if (dy > 0.3f) LEAF_DARK else LEAF,
                LandPainter.roundShade(dx, dy)
            )
        }
        val strands = 4 + kind
        for (s in 0 until strands) {
            val x0 = 3f + (w - 6f) * s / (strands - 1)
            val length = h * (0.45f + 0.55f * Noise.unit(s, seed + 10))
            var x = x0
            for (y in 4 until length.toInt()) {
                x += (Noise.unit(s * 97 + y / 4, seed + 11) - 0.5f) * 0.6f
                p.set(
                    x.roundToInt(),
                    y,
                    code(if (kind == 0) LEAF else TRUNK, if (s % 2 == 0) LEFT else RIGHT)
                )
                if (y % 5 == 2) p.set(
                    x.roundToInt() + if (s % 2 == 0) -1 else 1,
                    y,
                    code(LEAF_LIGHT, TOP)
                )
            }
        }
    }
}

private fun desertGround(kind: Int, seed: Int): LandPainter = with(DesertLand) {
    when (kind) {
        0 -> {
            val h = size(52, 8, seed, 1)
            canvas(30, h).also { p -> cactus(p, 15, h - 1f, h - 3f, seed, seed) }
        }

        1 -> {
            // A hoodoo: a striped pillar under a wider cap of harder rock.
            val w = size(30, 10, seed, 2)
            val h = size(48, 8, seed, 3)
            canvas(w, h).also { p ->
                val waist = w * 0.32f
                for (y in 6 until h) {
                    val half = waist + (w / 2f - 2f - waist) * ((y - 6f) / (h - 6f)).pow(2f)
                    for (x in (w / 2f - half).roundToInt()..(w / 2f + half).roundToInt()) {
                        val band = (y + (Noise.value(x / 5f, seed) * 3).toInt()) % 7 < 2
                        p.set(
                            x,
                            y,
                            code(
                                if (band) ROCK_DARK else ROCK_RED,
                                LandPainter.sideShade((x - w / 2f) / half)
                            )
                        )
                    }
                }
                p.ellipse(w / 2f, 5f, w / 2f - 1f, 4.5f) { dx, dy ->
                    code(
                        MESA_TOP,
                        LandPainter.roundShade(dx, dy)
                    )
                }
            }
        }

        else -> {
            val w = size(64, 10, seed, 4)
            val h = size(38, 8, seed, 5)
            canvas(w, h).also { p ->
                rock(p, w * 0.32f, h.toFloat(), w * 0.3f, h - 3f, ROCK_RED, 0)
                rock(p, w * 0.7f, h.toFloat(), w * 0.28f, h * 0.7f, ROCK, 0)
            }
        }
    }
}

private fun coastGround(kind: Int, seed: Int): LandPainter = with(CoastLand) {
    when (kind) {
        0 -> {
            // A limestone spire, streaked, with a tuft of green on top.
            val w = size(30, 8, seed, 1)
            val h = size(52, 8, seed, 2)
            canvas(w, h).also { p ->
                for (y in 4 until h) {
                    val half = (w / 2f - 1f) * (0.55f + 0.45f * (y.toFloat() / h))
                    for (x in (w / 2f - half).roundToInt()..(w / 2f + half).roundToInt()) {
                        val streak = Noise.unit(x / 2, 0, seed) < 0.3f
                        p.set(
                            x,
                            y,
                            code(
                                if (streak) LIMESTONE_DARK else LIMESTONE,
                                LandPainter.sideShade((x - w / 2f) / half)
                            )
                        )
                    }
                }
                p.ellipse(w / 2f, 5f, w * 0.38f, 5f) { dx, dy ->
                    code(
                        GREENERY,
                        LandPainter.roundShade(dx, dy)
                    )
                }
            }
        }

        1 -> {
            val w = size(64, 10, seed, 3)
            val h = size(36, 8, seed, 4)
            canvas(w, h).also { p ->
                rock(p, w * 0.35f, h.toFloat(), w * 0.33f, h - 2f, LIMESTONE_DARK, LIMESTONE)
                rock(p, w * 0.75f, h.toFloat(), w * 0.22f, h * 0.6f, LIMESTONE, 0)
            }
        }

        else -> {
            val w = size(64, 10, seed, 5)
            val h = size(30, 6, seed, 6)
            canvas(w, h).also { p ->
                rock(p, w / 2f, h.toFloat(), w / 2f - 1f, h - 2f, LIMESTONE, GREENERY)
            }
        }
    }
}

private fun peaksGround(kind: Int, seed: Int): LandPainter = with(PeaksLand) {
    when (kind) {
        0 -> {
            val h = size(56, 8, seed, 1)
            canvas(36, h).also { p -> pine(p, 18, h - 1f, h - 2f, PINE_DARK, PINE, TRUNK, SNOW) }
        }

        1 -> {
            val w = size(62, 12, seed, 2)
            val h = size(38, 8, seed, 3)
            canvas(w, h).also { p -> rock(p, w / 2f, h.toFloat(), w / 2f - 1f, h - 2f, ROCK, SNOW) }
        }

        else -> {
            // A shard of ice, paler down its lit edge.
            val w = size(26, 8, seed, 4)
            val h = size(50, 8, seed, 5)
            canvas(w, h).also { p ->
                val lean = (Noise.unit(6, seed) - 0.5f) * 6f
                for (y in 0 until h) {
                    val along = y.toFloat() / h
                    val half = (w / 2f - 1f) * along
                    val cx = w / 2f + lean * (1f - along)
                    for (x in (cx - half).roundToInt()..(cx + half).roundToInt()) {
                        val dx = if (half < 0.5f) 0f else (x - cx) / half
                        p.set(
                            x,
                            y,
                            code(if (dx < -0.3f) SNOW_SHADE else ICE, LandPainter.sideShade(dx))
                        )
                    }
                }
            }
        }
    }
}

private fun peaksHanging(kind: Int, seed: Int): LandPainter = with(PeaksLand) {
    val w = size(40, 14, seed, 6 + kind)
    val h = size(36, 12, seed, 8 + kind)
    canvas(w, h).also { p ->
        // A ledge of snow, icicles hanging from it.
        p.ellipse(w / 2f, 0f, w / 2f, 7f) { dx, dy ->
            code(
                if (dy > 0.2f) SNOW_SHADE else SNOW,
                LandPainter.roundShade(dx, dy)
            )
        }
        var x = 2
        while (x < w - 2) {
            val length = 6f + Noise.unit(x, seed + 9) * (h - 8f)
            val half = 1.5f + Noise.unit(x, seed + 10) * 1.5f
            for (y in 4 until (4 + length).toInt()) {
                val taper = half * (1f - (y - 4f) / length)
                for (dx in -taper.roundToInt()..taper.roundToInt()) {
                    p.set(x + dx, y, code(ICE, if (dx < 0) LEFT else if (dx > 0) RIGHT else BODY))
                }
            }
            x += 4 + (Noise.unit(x, seed + 11) * 4).toInt()
        }
    }
}

private fun volcanoGround(kind: Int, seed: Int): LandPainter = with(VolcanoLand) {
    when (kind) {
        0 -> {
            val h = size(50, 10, seed, 1)
            canvas(34, h).also { p ->
                for (y in 0 until h) {
                    val half = 15f * (y.toFloat() / h).pow(0.7f) + 1f
                    for (x in (17 - half).roundToInt()..(17 + half).roundToInt()) {
                        val crack = abs(x - 17 - 4f * sin(y / 5f + seed)) < 0.8f && y > h / 3
                        p.set(
                            x,
                            y,
                            code(
                                if (crack) LAVA else OBSIDIAN,
                                if (crack) BODY else LandPainter.sideShade((x - 17) / half)
                            )
                        )
                    }
                }
            }
        }

        1 -> {
            val w = size(64, 12, seed, 2)
            val h = size(36, 8, seed, 3)
            canvas(w, h).also { p ->
                rock(p, w / 2f, h.toFloat(), w / 2f - 1f, h - 2f, BASALT, ASH_LIGHT)
                var cx = w * 0.2f
                var cy = h * 0.55f
                while (cx < w * 0.8f) {
                    val nx = cx + 3f + Noise.unit(cx.toInt(), seed) * 4f
                    val ny = (cy + (Noise.unit(cx.toInt(), seed + 1) - 0.5f) * 6f).coerceIn(
                        h * 0.35f,
                        h - 2f
                    )
                    p.line(cx, cy, nx, ny, code(LAVA_HOT, BODY))
                    cx = nx
                    cy = ny
                }
            }
        }

        else -> {
            val w = size(46, 8, seed, 4)
            val h = size(44, 10, seed, 5)
            canvas(w, h).also { p ->
                for (s in 0 until 3) {
                    val cx = w * (0.22f + 0.28f * s)
                    val tall = h * (0.55f + 0.45f * Noise.unit(s, seed + 6))
                    for (y in 0 until tall.toInt()) {
                        val half = 6f * (y / tall)
                        for (x in (cx - half).roundToInt()..(cx + half).roundToInt()) {
                            p.set(
                                x,
                                h - 1 - tall.toInt() + y,
                                code(
                                    OBSIDIAN,
                                    LandPainter.sideShade(if (half < 0.5f) 0f else (x - cx) / half)
                                )
                            )
                        }
                    }
                }
                for (x in 0 until w) p.set(x, h - 1, code(EMBER, BODY))
            }
        }
    }
}

private fun swampGround(kind: Int, seed: Int): LandPainter = with(SwampLand) {
    when (kind) {
        0 -> {
            val h = size(52, 8, seed, 1)
            canvas(40, h).also { p -> deadTree(p, 20, h - 1f, h - 4f, seed, seed) }
        }

        1 -> {
            // Cypress knees, knobbly stumps of root standing up out of the mud.
            val w = size(60, 12, seed, 2)
            val h = size(34, 8, seed, 3)
            canvas(w, h).also { p ->
                for (k in 0 until 5) {
                    val cx = w * (0.12f + 0.19f * k)
                    val tall = h * (0.4f + 0.6f * Noise.unit(k, seed + 4))
                    p.ellipse(
                        cx,
                        h - tall / 2f,
                        3.5f + Noise.unit(k, seed + 5) * 2f,
                        tall / 2f
                    ) { dx, dy ->
                        code(if (dy < -0.6f) MOSS else BARK, LandPainter.roundShade(dx, dy))
                    }
                }
            }
        }

        else -> {
            val w = size(36, 8, seed, 6)
            val h = size(40, 8, seed, 7)
            canvas(w, h).also { p ->
                for (s in 0 until w / 2) {
                    val x = 1 + s * 2 + (Noise.unit(s, seed + 8) * 2).toInt()
                    val tall = h * (0.5f + 0.5f * Noise.unit(s, seed + 9))
                    for (y in 0 until tall.toInt()) p.set(
                        x,
                        h - 1 - y,
                        code(REED, if (s % 2 == 0) LEFT else RIGHT)
                    )
                    p.set(x, h - 1 - tall.toInt(), code(BARK, DEEP))
                }
            }
        }
    }
}

private fun swampHanging(kind: Int, seed: Int): LandPainter = with(SwampLand) {
    val w = size(36, 12, seed, 10 + kind)
    val h = size(40, 14, seed, 12 + kind)
    canvas(w, h).also { p ->
        // A branch across the top, from the frame's edge, moss hanging from it in strands.
        p.line(0f, 0f, w - 1f, 1f + kind * 2f, code(BARK_LIGHT, TOP))
        p.line(0f, 1f, w - 1f, 2f + kind * 2f, code(BARK, DEEP))
        for (x in 1 until w - 1) {
            if (Noise.unit(x, seed + 14) < 0.35f) continue
            val length = 6f + Noise.unit(x, seed + 15) * (h - 10f)
            for (y in 4 until (4 + length).toInt()) p.set(
                x,
                y + kind,
                code(HANGING_MOSS, if (x % 2 == 0) BODY else DEEP)
            )
        }
    }
}

private fun forestGround(kind: Int, seed: Int): LandPainter = with(ForestLand) {
    when (kind) {
        0 -> {
            val h = size(56, 8, seed, 1)
            canvas(36, h).also { p -> pine(p, 18, h - 1f, h - 2f, PINE_DARK, PINE, TRUNK) }
        }

        1 -> {
            val w = size(60, 10, seed, 2)
            val h = size(38, 8, seed, 3)
            canvas(w, h).also { p ->
                rock(p, w / 2f, h.toFloat(), w / 2f - 1f, h - 2f, ROCK, MOSS)
                fern(p, 5, h - 2f, 10f, FERN, MOSS, seed, seed)
            }
        }

        else -> {
            // A fallen trunk, moss along its back, its broken end showing its rings.
            val w = size(80, 14, seed, 4)
            val h = size(26, 6, seed, 5)
            canvas(w, h).also { p ->
                val radius = h * 0.42f
                for (x in 0 until w - 3) {
                    for (y in (h - 1 - 2 * radius).roundToInt() until h) {
                        val dy = (y - (h - 1 - radius)) / radius
                        val material = if (dy < -0.6f) MOSS else TRUNK
                        p.set(
                            x,
                            y,
                            code(material, if (dy < -0.6f) TOP else if (dy > 0.5f) DEEP else BODY)
                        )
                    }
                }
                p.ellipse(w - 4f, h - 1f - radius, radius * 0.6f, radius) { dx, dy ->
                    code(
                        if ((dx * dx + dy * dy) * 4f % 1f < 0.4f) TRUNK else MUSHROOM,
                        LandPainter.roundShade(dx, dy)
                    )
                }
            }
        }
    }
}

private fun forestHanging(kind: Int, seed: Int): LandPainter = with(ForestLand) {
    val w = size(42, 14, seed, 6 + kind)
    val h = size(36, 12, seed, 8 + kind)
    canvas(w, h).also { p ->
        // A bough reaching in from above, its needles hanging in tufts.
        p.line(0f, 1f, w - 4f, h * 0.45f, code(TRUNK, BODY))
        p.line(0f, 2f, w - 4f, h * 0.45f + 1f, code(TRUNK, DEEP))
        for (t in 0 until 6) {
            val along = (t + 0.5f) / 6f
            val bx = (w - 4f) * along
            val by = 1f + (h * 0.45f - 1f) * along
            val length = 6f + Noise.unit(t, seed + 9) * (h * 0.5f)
            for (n in -3..3) {
                p.line(
                    bx, by, bx + n * 1.2f, by + length * (1f - abs(n) * 0.12f),
                    code(if (n < 0) PINE else PINE_DARK, if (n < 0) LEFT else RIGHT),
                )
            }
        }
    }
}
