package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.scenery.Biome
import at.smiech.cyanbat.scenery.EndlessScenery
import at.smiech.cyanbat.scenery.Weather
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The endless stage's weather: rain, snow, ash and embers, or dust on the wind, falling through the
 * frame as hard as [EndlessScenery] says, and landing on the bat and everything hostile.
 *
 * Particles fall in two layers. The far one is small, slow and faint, and is drawn by this system,
 * which goes straight after the scenery, behind every sprite. The near one is bigger and quicker and
 * drawn by [front], which goes after every sprite; and only it lands on things, since only it is in
 * front of them. What lands leaves a mark (see [WeatherMarksComponent]):
 * - **snow** settles on top of the creature, piling up a pixel at a time, and melts away slowly;
 * - **rain** beads where it lands and runs down the creature's side, dripping off its underside;
 * - **ash** sticks as soot, and an **ember** glows where it lands before it goes out to soot.
 * A mark sits against the outline of whatever frame the creature is showing, read off its art, so
 * snow on a wing rides the wingbeat. A blow knocks the marks off ([shake]), and they fall away.
 *
 * Fireflies come out over the swamp, the jungle and the forest at night, and nothing else lands.
 *
 * At most [MAX_PARTICLES] are in the air, in arrays, so a blizzard allocates nothing.
 */
class WeatherSystem(
    private val scenery: EndlessScenery,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val random: Random = Random.Default,
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var collisions: ComponentMapper<CollisionComponent>
    private lateinit var marks: ComponentMapper<WeatherMarksComponent>

    // The particles, one entry per particle across these arrays.
    private val x = FloatArray(MAX_PARTICLES)
    private val y = FloatArray(MAX_PARTICLES)
    private val vx = FloatArray(MAX_PARTICLES)
    private val vy = FloatArray(MAX_PARTICLES)
    private val life = FloatArray(MAX_PARTICLES)
    private val kind = IntArray(MAX_PARTICLES)
    private val near = BooleanArray(MAX_PARTICLES)

    /** For a particle shed off a creature, the kind of mark it was, which its color is. */
    private val shedKind = IntArray(MAX_PARTICLES)
    private var count = 0

    /** The creatures weather can land on this tick, gathered once rather than per particle. */
    private var creatures = IntArray(64)
    private var creatureCount = 0

    /** Particles owed but not yet spawned: fractions carry from tick to tick. */
    private var owed = 0f
    private var owedEmbers = 0f
    private var owedFlies = 0f

    /** Seconds of play, for flicker and sway. */
    private var clock = 0f

    /** Each creature frame's outline, top and bottom row of every column; see [outline]. */
    private val outlines = HashMap<Pixmap, HashMap<Int, IntArray>>()
    private var scratch = IntArray(0)

    /** The near layer and every mark, drawn over the sprites: add it after them. */
    val front: GameSystem = object : GameSystem() {
        override fun update(world: World, deltaTime: Float, input: Input?) = Unit
        override fun draw(world: World, graphics: Graphics) = drawFront(world, graphics)
    }

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        sprites = world.mapper(SpriteComponent::class)
        collisions = world.mapper(CollisionComponent::class)
        marks = world.mapper(WeatherMarksComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        clock += deltaTime
        spawn(deltaTime)
        gatherCreatures(world)
        move(world, deltaTime)
        age(world, deltaTime)
    }

    /** The bat and everything hostile: what the near layer can land on. */
    private fun gatherCreatures(world: World) {
        creatureCount = 0
        world.forEach(transforms, sprites, collisions) { id ->
            val group = collisions.require(id).group
            if (group != CollisionGroup.PLAYER && group != CollisionGroup.ENEMY) return@forEach
            if (creatureCount == creatures.size) creatures = creatures.copyOf(creatureCount * 2)
            creatures[creatureCount++] = id
        }
    }

    // --- the air -----------------------------------------------------------------------------------

    private fun spawn(deltaTime: Float) {
        val intensity = scenery.intensity
        owed += RATE[scenery.weather.ordinal] * intensity * deltaTime
        while (owed >= 1f) {
            owed -= 1f
            spawnFalling(scenery.weather)
        }
        if (scenery.weather == Weather.ASH) {
            owedEmbers += EMBER_RATE * intensity * deltaTime
            while (owedEmbers >= 1f) {
                owedEmbers -= 1f
                spawnEmber()
            }
        }
        val flies = fireflies()
        if (flies > 0f) {
            owedFlies += FIREFLY_RATE * flies * deltaTime
            while (owedFlies >= 1f) {
                owedFlies -= 1f
                spawnFirefly()
            }
        }
    }

    /** How many fireflies are about: only by night, over the lands with standing water and woods. */
    private fun fireflies(): Float {
        val biome = scenery.biome
        val kind = biome == Biome.SWAMP || biome == Biome.JUNGLE || biome == Biome.FOGGY_FOREST
        return if (kind) scenery.darkness * scenery.turned else 0f
    }

    private fun take(): Int = if (count < MAX_PARTICLES) count++ else -1

    /** One particle of [weather], in from the top, or from the right where the wind blows it from. */
    private fun spawnFalling(weather: Weather) {
        val i = take()
        if (i < 0) return
        val isNear = random.nextFloat() < NEAR_SHARE
        near[i] = isNear
        val wind = scenery.wind
        val depth = if (isNear) 1f else FAR_SPEED
        when (weather) {
            Weather.RAIN -> {
                kind[i] = RAIN
                vy[i] = (300f + random.nextFloat() * 60f) * depth
                vx[i] = -wind * 52f * depth
            }
            Weather.SNOW -> {
                kind[i] = SNOW
                vy[i] = (26f + random.nextFloat() * 26f) * depth
                vx[i] = -wind * 34f * depth
            }
            Weather.ASH -> {
                kind[i] = ASH
                vy[i] = (20f + random.nextFloat() * 22f) * depth
                vx[i] = -wind * 30f * depth
            }
            Weather.DUST -> {
                kind[i] = DUST
                vy[i] = (random.nextFloat() - 0.4f) * 8f
                vx[i] = -wind * 60f * depth * (0.8f + 0.4f * random.nextFloat())
            }
        }
        life[i] = 0f
        if (weather == Weather.DUST) {
            x[i] = frameWidth + random.nextFloat() * 40f
            y[i] = DUST_TOP + random.nextFloat() * (frameHeight - DUST_TOP)
            return
        }
        // Spread across the top and, the harder the wind, up the right edge, so the slant fills the
        // frame rather than leaving its right side bare.
        val span = frameWidth + (-vx[i] / vy[i]) * frameHeight
        val along = random.nextFloat() * span
        if (along < frameWidth) {
            x[i] = along
            y[i] = -4f
        } else {
            x[i] = frameWidth + 2f
            y[i] = (along - frameWidth) / span * frameHeight
        }
    }

    private fun spawnEmber() {
        val i = take()
        if (i < 0) return
        kind[i] = EMBER
        near[i] = random.nextFloat() < 0.5f
        x[i] = random.nextFloat() * (frameWidth + 60f)
        y[i] = frameHeight - random.nextFloat() * 50f
        vx[i] = -scenery.wind * 20f - random.nextFloat() * 10f
        vy[i] = -(28f + random.nextFloat() * 40f)
        life[i] = 0f
    }

    private fun spawnFirefly() {
        val i = take()
        if (i < 0) return
        kind[i] = FIREFLY
        near[i] = false
        x[i] = random.nextFloat() * frameWidth
        y[i] = FIREFLY_TOP + random.nextFloat() * (frameHeight - FIREFLY_TOP)
        vx[i] = (random.nextFloat() - 0.6f) * 16f
        vy[i] = (random.nextFloat() - 0.5f) * 8f
        life[i] = 0f
    }

    /** A particle shed off a creature: knocked loose by a blow, or a drop running off its underside. */
    private fun shed(px: Float, py: Float, ofKind: Int) {
        val i = take()
        if (i < 0) return
        kind[i] = FALLING
        near[i] = true
        x[i] = px
        y[i] = py
        vx[i] = (random.nextFloat() - 0.5f) * 40f - 20f
        vy[i] = -20f + random.nextFloat() * 30f
        life[i] = 0f
        shedKind[i] = ofKind
    }

    private fun move(world: World, deltaTime: Float) {
        var i = 0
        while (i < count) {
            when (kind[i]) {
                SNOW, ASH -> {
                    // Flakes sway as they fall.
                    x[i] += (vx[i] + sin(clock * 2.1f + i * 1.7f) * SWAY[kind[i]]) * deltaTime
                    y[i] += vy[i] * deltaTime
                }
                FIREFLY -> {
                    vx[i] += (random.nextFloat() - 0.5f) * 40f * deltaTime
                    vy[i] += (random.nextFloat() - 0.5f) * 40f * deltaTime
                    vx[i] = vx[i].coerceIn(-24f, 12f)
                    vy[i] = vy[i].coerceIn(-12f, 12f)
                    x[i] += vx[i] * deltaTime
                    y[i] += vy[i] * deltaTime
                }
                FALLING -> {
                    vy[i] += GRAVITY * deltaTime
                    x[i] += vx[i] * deltaTime
                    y[i] += vy[i] * deltaTime
                }
                else -> {
                    x[i] += vx[i] * deltaTime
                    y[i] += vy[i] * deltaTime
                }
            }
            life[i] += deltaTime
            if (gone(i) || (near[i] && lands(world, i))) {
                remove(i)
                continue
            }
            i++
        }
    }

    /** Whether particle [i] has left the frame, burnt out, or hit the ground. */
    private fun gone(i: Int): Boolean {
        if (x[i] < -20f || x[i] > frameWidth + 80f || y[i] > frameHeight + 4f || y[i] < -60f) return true
        return when (kind[i]) {
            EMBER -> life[i] > EMBER_SECONDS
            FIREFLY -> life[i] > FIREFLY_SECONDS
            SPLASH -> life[i] > SPLASH_SECONDS
            RAIN -> if (near[i] && y[i] > groundLine(i)) {
                splash(x[i], y[i])
                true
            } else false
            else -> false
        }
    }

    /** Where near rain hits the ground: along the near band, varying a little from drop to drop. */
    private fun groundLine(i: Int): Float = GROUND_TOP + (i * 7 % 23)

    /** A raindrop bursting on the ground: two droplets thrown up either side. */
    private fun splash(px: Float, py: Float) {
        for (side in SIDES) {
            val i = take()
            if (i < 0) return
            kind[i] = SPLASH
            near[i] = false
            x[i] = px
            y[i] = py
            vx[i] = side * (20f + random.nextFloat() * 20f)
            vy[i] = -30f - random.nextFloat() * 20f
            life[i] = 0f
        }
    }

    private fun remove(i: Int) {
        val last = --count
        x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
        life[i] = life[last]; kind[i] = kind[last]; near[i] = near[last]; shedKind[i] = shedKind[last]
    }

    // --- landing -----------------------------------------------------------------------------------

    /**
     * Whether near particle [i] has landed on a creature, leaving its mark there. It lands on the
     * outline of the frame the creature shows: snow on top of it, rain and ash where it strikes.
     */
    private fun lands(world: World, i: Int): Boolean {
        val markKind = when (kind[i]) {
            SNOW -> MARK_SNOW
            RAIN -> MARK_RAIN
            ASH -> MARK_SOOT
            EMBER -> MARK_EMBER
            else -> return false
        }
        val px = x[i]
        val py = y[i]
        for (c in 0 until creatureCount) {
            val id = creatures[c]
            val rect = transforms[id]?.rect ?: continue
            val sprite = sprites[id] ?: continue
            val left = rect.left.toInt()
            val top = rect.top.toInt()
            val scale = sprite.scale
            if (px < left) continue
            val column = ((px - left) / scale).toInt()
            if (column >= sprite.srcWidth) continue
            val outline = outline(sprite) ?: continue
            val crest = outline[column]
            if (crest < 0) continue
            val underside = outline[sprite.srcWidth + column]
            val row = (py - top) / scale
            if (row < crest - 1f || row > underside) continue
            val worn = marks[id] ?: WeatherMarksComponent().also { world.addComponent(id, it) }
            if (markKind == MARK_SNOW) {
                // Snow settles over the outline and piles up on what has settled already, two
                // pixels deep at most; a flake spreads into the column beside it, so a fall builds
                // a cap rather than a row of dots.
                settle(worn, column)
                val beside = column + if (random.nextBoolean()) 1 else -1
                if (beside in 0 until sprite.srcWidth && outline[beside] >= 0) settle(worn, beside)
            } else {
                // Just inside the outline, where it shows against the creature's own colors: on its
                // back for what falls from above, a little lower for what blows in from the side.
                worn.add(column, (row - crest).coerceIn(1f, 3f), markKind)
            }
            return true
        }
        return false
    }

    /** A flake of snow settling on [column], over the outline and on any snow there already. */
    private fun settle(worn: WeatherMarksComponent, column: Int) {
        val pile = worn.pile(column, MARK_SNOW)
        if (pile >= SNOW_PILE) return
        worn.add(column, -pile.toFloat(), MARK_SNOW)
    }

    /**
     * The outline of the frame [sprite] shows: for each of its columns, its topmost opaque row and
     * then its bottommost, or -1 for an empty column. Read off the art once a frame and kept; a
     * sheet whose pixels cannot be read counts as solid.
     */
    private fun outline(sprite: SpriteComponent): IntArray? {
        val width = sprite.srcWidth
        val height = sprite.srcHeight
        if (width <= 0 || height <= 0) return null
        val frames = outlines.getOrPut(sprite.pixmap) { HashMap() }
        val key = sprite.srcX * FRAME_KEY + sprite.srcY
        frames[key]?.let { if (it.size == 2 * width) return it }
        val outline = IntArray(2 * width)
        if (scratch.size < width * height) scratch = IntArray(width * height)
        if (sprite.pixmap.readPixels(scratch, sprite.srcX, sprite.srcY, width, height)) {
            for (column in 0 until width) {
                var crest = -1
                var underside = -1
                for (row in 0 until height) {
                    if (scratch[row * width + column] ushr 24 < SOLID) continue
                    if (crest < 0) crest = row
                    underside = row
                }
                outline[column] = crest
                outline[width + column] = underside
            }
        } else {
            for (column in 0 until width) {
                outline[column] = 0
                outline[width + column] = height - 1
            }
        }
        frames[key] = outline
        return outline
    }

    /** Ages every mark: snow melts, rain runs down and drips off, an ember goes out to soot. */
    private fun age(world: World, deltaTime: Float) {
        world.forEach(transforms, sprites, marks) { id ->
            val worn = marks.require(id)
            val sprite = sprites.require(id)
            val outline = outline(sprite)
            var m = 0
            while (m < worn.count) {
                worn.age[m] += deltaTime
                val age = worn.age[m]
                when (worn.kind[m]) {
                    MARK_RAIN -> {
                        worn.depth[m] += RUN_SPEED * deltaTime
                        val column = worn.column[m]
                        val crest = outline?.getOrNull(column) ?: 0
                        val underside = outline?.getOrNull(sprite.srcWidth + column) ?: sprite.srcHeight
                        if (crest < 0 || crest + worn.depth[m] > underside) {
                            // Run off the bottom: it drips.
                            val rect = transforms.require(id).rect
                            shedAt(rect.left + (column + 0.5f) * sprite.scale, rect.top + (underside + 1f) * sprite.scale, MARK_RAIN)
                            worn.remove(m)
                            continue
                        }
                    }
                    MARK_EMBER -> if (age > EMBER_GLOW_SECONDS) {
                        worn.kind[m] = MARK_SOOT
                        worn.age[m] = 0f
                    }
                }
                if (age > LASTS[worn.kind[m]]) {
                    worn.remove(m)
                    continue
                }
                m++
            }
        }
    }

    private fun shedAt(px: Float, py: Float, markKind: Int) = shed(px, py, markKind)

    /** A blow knocks the weather off [id]: every mark on it falls away. */
    fun shake(world: World, id: EntityId) {
        val worn = marks[id] ?: return
        val sprite = sprites[id] ?: return
        val rect = transforms[id]?.rect ?: return
        val outline = outline(sprite)
        for (m in 0 until worn.count) {
            val column = worn.column[m]
            val crest = (outline?.getOrNull(column) ?: 0).coerceAtLeast(0)
            shed(rect.left + (column + 0.5f) * sprite.scale, rect.top + (crest + worn.depth[m]) * sprite.scale, worn.kind[m])
        }
        worn.count = 0
    }

    // --- drawing -----------------------------------------------------------------------------------

    /** The far layer, behind every sprite; the darkness of the night falls on it with the rest. */
    override fun draw(world: World, graphics: Graphics) {
        for (i in 0 until count) if (!near[i]) drawParticle(graphics, i, NO_SHADE)
    }

    /**
     * The near layer and the marks, over every sprite. Drawn after the night's dark, so what does
     * not glow is darkened here by the light the dark leaves.
     */
    private fun drawFront(world: World, graphics: Graphics) {
        val shade = scenery.ambient()
        for (i in 0 until count) if (near[i]) drawParticle(graphics, i, shade)
        world.forEach(transforms, sprites, marks) { id ->
            drawMarks(graphics, marks.require(id), transforms.require(id).rect.left, transforms.require(id).rect.top, sprites.require(id), shade)
        }
    }

    private fun drawParticle(graphics: Graphics, i: Int, shade: Int) {
        val px = x[i].roundToInt()
        val py = y[i].roundToInt()
        val isNear = near[i]
        when (kind[i]) {
            RAIN -> {
                val length = if (isNear) 6f else 3f
                val speed = sqrt(vx[i] * vx[i] + vy[i] * vy[i]).coerceAtLeast(1f)
                val tx = (x[i] - vx[i] / speed * length).roundToInt()
                val ty = (y[i] - vy[i] / speed * length).roundToInt()
                graphics.drawLine(tx, ty, px, py, lit(EngineColors.withAlpha(RAIN_COLOR, if (isNear) 0.55f else 0.3f), shade))
            }
            SNOW -> {
                val size = if (isNear) 2 else 1
                graphics.drawRect(px, py, size, size, lit(EngineColors.withAlpha(SNOW_COLOR, if (isNear) 0.95f else 0.7f), shade))
            }
            ASH -> graphics.drawRect(px, py, if (isNear) 2 else 1, 1, lit(EngineColors.withAlpha(ASH_COLOR, if (isNear) 0.9f else 0.6f), shade))
            DUST -> {
                val length = if (isNear) 12 else 6
                graphics.drawRect(px, py, length, 1, lit(EngineColors.withAlpha(DUST_COLOR, if (isNear) 0.35f else 0.2f), shade))
            }
            EMBER -> {
                // Glows: never darkened, flickering, dimming as it climbs and cools.
                val cool = (life[i] / EMBER_SECONDS).coerceIn(0f, 1f)
                val flicker = 0.6f + 0.4f * sin(clock * 17f + i * 3.1f)
                graphics.drawRect(px - 1, py - 1, 3, 3, EngineColors.withAlpha(EMBER_COLOR, 0.18f * flicker * (1f - cool)))
                graphics.drawRect(px, py, 1, 1, EngineColors.withAlpha(EngineColors.lerp(EMBER_HOT, EMBER_COLOR, cool), flicker * (1f - cool * 0.7f)))
            }
            FIREFLY -> {
                val blink = (0.5f + 0.5f * sin(clock * 3f + i * 2.3f)).let { it * it }
                val fade = (life[i] / 1f).coerceAtMost(1f) * ((FIREFLY_SECONDS - life[i]) / 1f).coerceIn(0f, 1f)
                graphics.drawRect(px - 1, py - 1, 3, 3, EngineColors.withAlpha(FIREFLY_COLOR, 0.2f * blink * fade))
                graphics.drawRect(px, py, 1, 1, EngineColors.withAlpha(FIREFLY_COLOR, blink * fade))
            }
            SPLASH -> graphics.drawRect(px, py, 1, 1, lit(EngineColors.withAlpha(RAIN_COLOR, 0.7f), shade))
            FALLING -> graphics.drawRect(px, py, 1, 1, lit(MARK_COLORS[shedKind[i]], shade))
        }
    }

    /**
     * [worn]'s marks over its sprite, placed as [at.smiech.engine.ecs.RenderSystem] places the
     * sprite (at its box's corner, magnified, and turned about its middle).
     */
    private fun drawMarks(graphics: Graphics, worn: WeatherMarksComponent, left: Float, top: Float, sprite: SpriteComponent, shade: Int) {
        val outline = outline(sprite) ?: return
        val scale = sprite.scale
        val size = scale.roundToInt().coerceAtLeast(1)
        val l = left.toInt()
        val t = top.toInt()
        val turn = sprite.rotationDegrees
        val centerX = l + sprite.srcWidth * scale / 2f
        val centerY = t + sprite.srcHeight * scale / 2f
        val cos = if (turn != 0f) cos(turn * RADIANS) else 1f
        val sin = if (turn != 0f) sin(turn * RADIANS) else 0f
        for (m in 0 until worn.count) {
            val column = worn.column[m]
            if (column !in 0 until sprite.srcWidth) continue
            val crest = outline[column]
            if (crest < 0) continue
            val row = crest + worn.depth[m]
            var dx = l + column * scale
            var dy = t + row * scale
            if (turn != 0f) {
                val ox = dx + scale / 2f - centerX
                val oy = dy + scale / 2f - centerY
                dx = centerX + ox * cos - oy * sin - scale / 2f
                dy = centerY + ox * sin + oy * cos - scale / 2f
            }
            val age = worn.age[m]
            val color = when (worn.kind[m]) {
                MARK_EMBER -> EngineColors.lerp(EMBER_HOT, EMBER_COLOR, age / EMBER_GLOW_SECONDS)
                else -> {
                    // Melting and drying, a mark fades over the last of its time.
                    val left2 = LASTS[worn.kind[m]] - age
                    val fade = (left2 / MARK_FADE_SECONDS).coerceIn(0f, 1f)
                    lit(EngineColors.withAlpha(MARK_COLORS[worn.kind[m]], fade * MARK_ALPHA[worn.kind[m]]), shade)
                }
            }
            graphics.drawRect(dx.roundToInt(), dy.roundToInt(), size, size, color)
            // A bead of rain glints, and leaves the creature wet where it has run: a fainter pixel
            // above it, so it reads on any color the creature is.
            if (worn.kind[m] == MARK_RAIN) {
                graphics.drawRect(dx.roundToInt(), (dy - scale).roundToInt(), size, size, EngineColors.scaleAlpha(color, WET_TRAIL))
            }
        }
    }

    /** [color] darkened by the light [shade] the night leaves; [NO_SHADE] leaves it as it is. */
    private fun lit(color: Int, shade: Int): Int {
        if (shade == NO_SHADE) return color
        val r = ((color shr 16) and 0xFF) * ((shade shr 16) and 0xFF) / 255
        val g = ((color shr 8) and 0xFF) * ((shade shr 8) and 0xFF) / 255
        val b = (color and 0xFF) * (shade and 0xFF) / 255
        return (color and ALPHA) or (r shl 16) or (g shl 8) or b
    }

    /** How many particles are in the air, for the tests. */
    val particles: Int get() = count

    companion object {
        /** The most particles in the air at once. */
        const val MAX_PARTICLES = 340

        // The particles.
        private const val RAIN = 0
        private const val SNOW = 1
        private const val ASH = 2
        private const val DUST = 3
        private const val EMBER = 4
        private const val FIREFLY = 5
        private const val SPLASH = 6
        private const val FALLING = 7

        // The marks, and how long each lasts.
        const val MARK_SNOW = 0
        const val MARK_RAIN = 1
        const val MARK_SOOT = 2
        const val MARK_EMBER = 3
        private val LASTS = floatArrayOf(16f, 6f, 14f, 30f)
        private const val MARK_FADE_SECONDS = 3f
        private val MARK_COLORS = intArrayOf(0xFFF4F8FF.toInt(), 0xFFEAF6FF.toInt(), 0xFF3A3232.toInt(), 0xFFFFB040.toInt())

        /** How much of a raindrop's glint the wet streak above it keeps. */
        private const val WET_TRAIL = 0.45f
        private val MARK_ALPHA = floatArrayOf(1f, 0.85f, 0.9f, 1f)

        /** How much higher snow piles than the creature it lies on, in its pixels. */
        private const val SNOW_PILE = 2

        /** How fast rain runs down a creature, in its pixels a second. */
        private const val RUN_SPEED = 7f

        private const val EMBER_GLOW_SECONDS = 0.9f

        /** Particles a second at full intensity, by [Weather]. */
        private val RATE = floatArrayOf(240f, 70f, 55f, 70f)
        private const val EMBER_RATE = 14f
        private const val FIREFLY_RATE = 1.6f

        /** The share of particles in the near layer, and how much slower the far layer falls. */
        private const val NEAR_SHARE = 0.4f
        private const val FAR_SPEED = 0.6f

        /** How far a falling flake sways either way, in pixels a second, by particle. */
        private val SWAY = floatArrayOf(0f, 14f, 10f, 0f)

        private const val GRAVITY = 260f
        private const val EMBER_SECONDS = 3.5f
        private const val FIREFLY_SECONDS = 9f
        private const val SPLASH_SECONDS = 0.12f

        /** Where dust blows, and fireflies wander: low, over the land. */
        private const val DUST_TOP = 200f
        private const val FIREFLY_TOP = 180f

        /** Where near rain meets the ground, give or take. */
        private const val GROUND_TOP = 322f

        private val SIDES = floatArrayOf(-1f, 1f)

        /** How opaque a pixel of the art must be for weather to land on it. */
        private const val SOLID = 0x80

        /** Packs a frame's corner into a key: no sheet is this many pixels tall. */
        private const val FRAME_KEY = 65536

        private const val RADIANS = 0.017453292f
        private const val ALPHA = 0xFF000000.toInt()
        private const val NO_SHADE = 0

        private val RAIN_COLOR = 0xFFAEC4E4.toInt()
        private val SNOW_COLOR = 0xFFF8FBFF.toInt()
        private val ASH_COLOR = 0xFF4E4646.toInt()
        private val DUST_COLOR = 0xFFE8CC90.toInt()
        private val EMBER_COLOR = 0xFFFF5A1E.toInt()
        private val EMBER_HOT = 0xFFFFE08A.toInt()
        private val FIREFLY_COLOR = 0xFFD8FF6A.toInt()
    }
}
