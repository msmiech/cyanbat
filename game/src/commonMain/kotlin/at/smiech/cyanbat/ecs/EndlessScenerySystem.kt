package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.scenery.Band
import at.smiech.cyanbat.scenery.EndlessDay
import at.smiech.cyanbat.scenery.EndlessLand
import at.smiech.cyanbat.scenery.EndlessScenery
import at.smiech.cyanbat.scenery.Noise
import at.smiech.cyanbat.scenery.Weather
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.CrossfadeComponent
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.World
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the endless stage's country (see [EndlessScenery]) and moves it on: a sky that goes round
 * the clock over the land in view, the stars, the moon and the sun, the clouds and the land's three
 * bands as they are made, the smoke off a volcano, the fog in the valleys, the glints a low sun
 * lays on water, and the lightning of a storm. It puts the obstacles in the light of the hour too,
 * as `SkySystem` does on the stages flown under a sky.
 *
 * Add it first: its [draw] is the back of the picture, and its [update] advances the land, the day
 * and the weather by the tick the run is advanced by, so a paused run is a paused land.
 *
 * Nothing it draws is an entity, for the same reason as the stages' sky: a few hundred stars and
 * bands would only be entities to sort to the back every frame.
 */
class EndlessScenerySystem(
    private val scenery: EndlessScenery,
    private val moon: Pixmap,
    private val frameWidth: Int,
    private val frameHeight: Int,
    random: Random = Random(STAR_SEED),
) : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var crossfades: ComponentMapper<CrossfadeComponent>

    /** Seconds of play, for the twinkle, the smoke and the glints. */
    private var clock = 0f

    /** The sky, a color per band of rows, worked out once a tick. */
    private val bands = IntArray((HORIZON_Y + BAND_HEIGHT - 1) / BAND_HEIGHT)

    private val starX = FloatArray(STAR_COUNT)
    private val starY = IntArray(STAR_COUNT)
    private val starBrightness = FloatArray(STAR_COUNT)
    private val starThreshold = FloatArray(STAR_COUNT)
    private val starRate = FloatArray(STAR_COUNT)
    private val starPhase = FloatArray(STAR_COUNT)

    init {
        for (i in 0 until STAR_COUNT) {
            starX[i] = random.nextFloat() * frameWidth
            val height = random.nextFloat()
            starY[i] = (4 + height * height * (HORIZON_Y - 40)).toInt()
            starBrightness[i] = if (random.nextFloat() < 0.08f) 1f else 0.3f + random.nextFloat() * 0.55f
            starThreshold[i] = (1f - starBrightness[i]) * 0.8f + random.nextFloat() * 0.2f
            starRate[i] = 1.2f + random.nextFloat() * 2.8f
            starPhase[i] = random.nextFloat() * TWO_PI
        }
    }

    override fun onAttach(world: World) {
        sprites = world.mapper(SpriteComponent::class)
        crossfades = world.mapper(CrossfadeComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        clock += deltaTime
        scenery.update(deltaTime)
        for (band in bands.indices) {
            val middle = band * BAND_HEIGHT + BAND_HEIGHT / 2f
            bands[band] = scenery.skyColor(middle / HORIZON_Y)
        }

        // The obstacles are drawn in the same palettes as the land, a row of their sheet apiece,
        // so they are put in the same light on the same tick.
        val position = scenery.position
        val below = EndlessDay.rowBelow(position)
        val above = EndlessDay.rowAbove(position)
        val blend = EndlessDay.rowBlend(position)
        world.forEach(sprites, crossfades) { id ->
            val sprite = sprites.require(id)
            sprite.srcY = below * sprite.srcHeight
            val crossfade = crossfades.require(id)
            crossfade.srcY = above * sprite.srcHeight
            crossfade.alpha = blend
        }
    }

    override fun draw(world: World, graphics: Graphics) {
        val position = scenery.position
        drawSky(graphics)
        drawStars(graphics, position)
        drawMoon(graphics, position)
        drawSun(graphics, position)
        // The flash lights the sky behind everything standing against it.
        if (scenery.flash > 0f) {
            graphics.drawRect(0, 0, frameWidth, HORIZON_Y, EngineColors.withAlpha(FLASH_COLOR, 0.45f * scenery.flash))
            drawBolt(graphics)
        }
        drawBand(graphics, Band.CLOUDS, position)
        drawBand(graphics, Band.FAR, position)
        drawSmoke(graphics)
        drawFog(graphics, FAR_FOG_TOP, FAR_FOG_BOTTOM, FAR_FOG_STRENGTH, Band.FAR)
        drawBand(graphics, Band.MID, position)
        drawGlitter(graphics, position)
        drawFog(graphics, MID_FOG_TOP, MID_FOG_BOTTOM, MID_FOG_STRENGTH, Band.MID)
        drawBand(graphics, Band.NEAR, position)
    }

    /** Banded rather than smooth, like the sprites' shading; the last band runs on under the land. */
    private fun drawSky(graphics: Graphics) {
        for (band in bands.indices) {
            graphics.drawRect(0, band * BAND_HEIGHT, frameWidth, BAND_HEIGHT, bands[band])
        }
        val below = bands.size * BAND_HEIGHT
        graphics.drawRect(0, below, frameWidth, frameHeight - below, bands.last())
    }

    /** How much of the sky the clouds hide, 0..1, for the stars and the sun behind them. */
    private fun overcast(): Float = when (scenery.weather) {
        Weather.RAIN, Weather.SNOW -> 0.6f * scenery.intensity
        Weather.ASH -> 0.4f * scenery.intensity
        else -> 0f
    }

    private fun drawStars(graphics: Graphics, position: Float) {
        val night = EndlessDay.starlight(position) * (1f - overcast())
        if (night <= 0f) return
        val drift = clock * STAR_DRIFT_PER_SECOND
        for (i in 0 until STAR_COUNT) {
            val up = ((night - starThreshold[i]) / STAR_FADE_IN).coerceIn(0f, 1f)
            if (up <= 0f) continue
            val twinkle = 1f - 0.35f * (0.5f + 0.5f * sin(clock * starRate[i] + starPhase[i]))
            val alpha = up * starBrightness[i] * twinkle
            val x = wrap(starX[i] - drift)
            graphics.drawRect(x, starY[i], 1, 1, EngineColors.withAlpha(STAR_COLOR, alpha))
            if (starBrightness[i] >= 1f) {
                val arm = EngineColors.withAlpha(STAR_COLOR, alpha * 0.45f)
                graphics.drawRect(x - 1, starY[i], 1, 1, arm)
                graphics.drawRect(x + 1, starY[i], 1, 1, arm)
                graphics.drawRect(x, starY[i] - 1, 1, 1, arm)
                graphics.drawRect(x, starY[i] + 1, 1, 1, arm)
            }
        }
    }

    private fun drawMoon(graphics: Graphics, position: Float) {
        val light = EndlessDay.moonlight(position) * (1f - 0.7f * overcast())
        if (light <= 0f) return
        val x = EndlessDay.moonX(position).roundToInt()
        val y = EndlessDay.moonY(position, HORIZON_Y).roundToInt()
        graphics.drawPixmapFaded(moon, x, y, 0, 0, moon.width, moon.height, light)
    }

    /** A disc with a brighter core inside hard-edged halo rings, as the stages' sun is. */
    private fun drawSun(graphics: Graphics, position: Float) {
        if (!EndlessDay.sunUp(position)) return
        val clear = 1f - 0.8f * overcast()
        val x = EndlessDay.sunX(position)
        val y = EndlessDay.sunY(position, HORIZON_Y)
        val radius = EndlessDay.sunRadius(position)
        val color = EndlessDay.sunColor(position)
        val glow = EndlessDay.sunGlow(position) * clear
        for (ring in HALO_RINGS.indices) {
            circle(graphics, x, y, radius * HALO_RINGS[ring], EngineColors.withAlpha(color, HALO_ALPHA[ring] * glow))
        }
        circle(graphics, x, y, radius, EngineColors.withAlpha(color, clear))
        circle(graphics, x, y, radius * 0.72f, EngineColors.withAlpha(EngineColors.lerp(color, EngineColors.WHITE, 0.55f), clear))
    }

    /** A bolt of lightning from the top of the sky down into the far hills, forking once. */
    private fun drawBolt(graphics: Graphics) {
        val color = EngineColors.withAlpha(EngineColors.WHITE, scenery.flash)
        var x = scenery.boltX
        var y = 0f
        var step = 0
        while (y < BOLT_BOTTOM) {
            val nx = x + (Noise.unit(step, scenery.boltSeed) - 0.5f) * 18f
            val ny = y + 12f + Noise.unit(step, scenery.boltSeed + 1) * 10f
            graphics.drawLine(x.roundToInt(), y.roundToInt(), nx.roundToInt(), ny.roundToInt(), color)
            graphics.drawLine(x.roundToInt() + 1, y.roundToInt(), nx.roundToInt() + 1, ny.roundToInt(), color)
            if (step == 3) {
                val fx = nx + (Noise.unit(9, scenery.boltSeed) - 0.5f) * 40f
                graphics.drawLine(nx.roundToInt(), ny.roundToInt(), fx.roundToInt(), (ny + 30f).roundToInt(), color)
            }
            x = nx
            y = ny
            step++
        }
    }

    /**
     * One band, chunk by chunk: the palette the day is leaving, with the next faded in over it. A
     * chunk is a column wider and a row taller than it covers, which the blit's `- 1` leaves out.
     */
    private fun drawBand(graphics: Graphics, band: Band, position: Float) {
        val below = EndlessDay.rowBelow(position)
        val above = EndlessDay.rowAbove(position)
        val blend = EndlessDay.rowBlend(position)
        val rowHeight = band.height + 1
        val scrolled = floor(scenery.scroll[band.ordinal]).toInt()
        val width = EndlessLand.CHUNK + 1
        for (chunk in scenery.chunks[band.ordinal]) {
            val x = chunk.left - scrolled
            if (x >= frameWidth || x + width <= 0) continue
            graphics.drawPixmap(chunk.sheet, x, band.top, 0, below * rowHeight, width, rowHeight)
            if (blend > 0f) {
                graphics.drawPixmapFaded(chunk.sheet, x, band.top, 0, above * rowHeight, width, rowHeight, blend)
            }
        }
    }

    /**
     * Smoke rising from every volcano's crater in view, drifting off on the wind, and the crater's
     * glow under it: puffs swelling and thinning as they climb, each on its own clock.
     */
    private fun drawSmoke(graphics: Graphics) {
        val scrolled = floor(scenery.scroll[Band.FAR.ordinal])
        val night = scenery.darkness
        for (chunk in scenery.chunks[Band.FAR.ordinal]) {
            for (feature in chunk.features) {
                val x = feature.x - scrolled
                if (x < -120f || x > frameWidth + 40f) continue
                val y = Band.FAR.top + feature.y
                val pulse = 0.5f + 0.5f * sin(clock * 2.3f + feature.x)
                circle(graphics, x, y, 9f + 3f * pulse, EngineColors.withAlpha(CRATER_GLOW, 0.25f + 0.35f * night))
                for (puff in 0 until SMOKE_PUFFS) {
                    val age = ((clock * SMOKE_RISE + puff / SMOKE_PUFFS.toFloat() + feature.x * 0.01f) % 1f)
                    val px = x - age * age * (40f + 30f * scenery.wind) + sin(clock + puff) * 2f
                    val py = y - 4f - age * 110f
                    val radius = 3f + age * 15f
                    val fadeIn = (age / 0.1f).coerceAtMost(1f)
                    val color = EngineColors.lerp(SMOKE_LIT, SMOKE, (age * 3f).coerceAtMost(1f) * (1f - 0.5f * night))
                    circle(graphics, px, py, radius, EngineColors.withAlpha(color, 0.5f * (1f - age) * fadeIn))
                }
            }
        }
    }

    /**
     * A fog lying between [top] and [bottom], thickening toward the bottom, in rows, with a few
     * banks of it drifting along at [band]'s pace.
     */
    private fun drawFog(graphics: Graphics, top: Int, bottom: Int, strength: Float, band: Band) {
        val fog = scenery.fog * strength
        if (fog <= 0.01f) return
        val color = scenery.fogColor()
        var y = top
        while (y < bottom) {
            val depth = (y - top).toFloat() / (bottom - top)
            val breath = 0.85f + 0.15f * sin(clock * 0.3f + y * 0.21f)
            val alpha = fog * depth * depth * breath
            if (alpha > 0.02f) graphics.drawRect(0, y, frameWidth, FOG_ROW, EngineColors.withAlpha(color, alpha))
            y += FOG_ROW
        }
        val drift = scenery.scroll[band.ordinal] * 1.3f
        for (bank in 0 until FOG_BANKS) {
            val span = frameWidth + 240f
            val bx = ((bank * 197f - drift) % span + span) % span - 120f
            val by = top + (bottom - top) * (0.35f + 0.5f * Noise.unit(bank, band.ordinal))
            val alpha = fog * 0.35f
            graphics.drawOval((bx - 90f).roundToInt(), (by - 7f).roundToInt(), 180, 14, EngineColors.withAlpha(color, alpha))
        }
    }

    /**
     * A low sun's path across the water in view: short dashes of its color beneath it, row by row,
     * spreading as they come nearer, each flickering on its own beat; see `SkySystem`'s.
     */
    private fun drawGlitter(graphics: Graphics, position: Float) {
        val strength = EndlessDay.sunGlitter(position) * scenery.water * (1f - overcast())
        if (strength <= 0.02f) return
        val x = EndlessDay.sunX(position)
        val color = EngineColors.lerp(EndlessDay.sunColor(position), EngineColors.WHITE, 0.35f)
        var row = GLITTER_TOP
        var index = 0
        while (row <= GLITTER_BOTTOM) {
            val near = (row - GLITTER_TOP).toFloat() / (GLITTER_BOTTOM - GLITTER_TOP)
            val shimmer = 0.5f + 0.5f * sin(clock * 5.5f + index * 2.3f)
            val alpha = strength * (0.25f + 0.75f * shimmer) * (1f - 0.4f * near)
            if (alpha > 0.04f) {
                val reach = 26f * (0.35f + near) * strength
                val sway = sin(clock * 1.3f + index * 1.7f) * reach * 0.5f
                val length = (22f * (0.6f + near) * (0.4f + 0.6f * shimmer)).roundToInt().coerceAtLeast(1)
                graphics.drawRect((x + sway - length / 2f).roundToInt(), row, length, 1, EngineColors.withAlpha(color, alpha))
            }
            row += 2
            index++
        }
    }

    private fun circle(graphics: Graphics, x: Float, y: Float, radius: Float, color: Int) {
        val size = (radius * 2f).roundToInt()
        if (size <= 0) return
        graphics.drawOval((x - radius).roundToInt(), (y - radius).roundToInt(), size, size, color)
    }

    private fun wrap(x: Float): Int {
        val wrapped = x % frameWidth
        return (if (wrapped < 0f) wrapped + frameWidth else wrapped).toInt()
    }

    private companion object {
        const val STAR_SEED = 20261013

        /** Where the sky meets the far land, and the sun and moon come up out of it. */
        const val HORIZON_Y = 252

        const val BAND_HEIGHT = 3

        const val STAR_COUNT = 130
        const val STAR_FADE_IN = 0.2f
        const val STAR_DRIFT_PER_SECOND = 1.1f
        val STAR_COLOR = 0xFFF4F4FF.toInt()

        val HALO_RINGS = floatArrayOf(3.4f, 2.4f, 1.6f)
        val HALO_ALPHA = floatArrayOf(0.1f, 0.16f, 0.26f)

        val FLASH_COLOR = 0xFFE8F0FF.toInt()

        /** How far down the sky a bolt reaches, into the far hills. */
        const val BOLT_BOTTOM = 200f

        /** The volcanoes' smoke: how many puffs a plume is, and how fast they climb, a turn a second. */
        const val SMOKE_PUFFS = 9
        const val SMOKE_RISE = 0.07f
        val SMOKE = 0xFF3C3638.toInt()
        val SMOKE_LIT = 0xFFC8603A.toInt()
        val CRATER_GLOW = 0xFFFF7A2A.toInt()

        /** The fog: the rows it lies in behind the middle band and behind the near one, and how thick. */
        const val FAR_FOG_TOP = 190
        const val FAR_FOG_BOTTOM = 266
        const val FAR_FOG_STRENGTH = 0.7f
        const val MID_FOG_TOP = 270
        const val MID_FOG_BOTTOM = 330
        const val MID_FOG_STRENGTH = 0.5f
        const val FOG_ROW = 2
        const val FOG_BANKS = 4

        /** The open water a low sun lays its glints on: the middle band's sea or swamp. */
        const val GLITTER_TOP = 264
        const val GLITTER_BOTTOM = 300

        const val TWO_PI = (2.0 * PI).toFloat()
    }
}
