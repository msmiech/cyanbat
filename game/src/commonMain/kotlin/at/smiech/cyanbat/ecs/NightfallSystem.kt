package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.scenery.Daylight
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.CrossfadeComponent
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.World
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws a [Backdrop.Nightfall]: a sky that goes from noon to night across the stage, and the ground
 * scrolling past underneath it at three depths - and keeps everything else in the stage that is
 * drawn by daylight, its obstacles, in the same light.
 *
 * Add it first. Its [draw] is the back of the picture, and everything the world draws after it -
 * the sprites, the effects, the bars - has to land on top.
 *
 * How far through the day the stage is comes from [dayPosition], read once a tick, so the sun goes
 * down on the stage clock and holds still whenever the stage does. Everything else it draws - the
 * scroll, the twinkle, the meteors - is advanced by the same fixed tick for the same reason.
 *
 * Nothing it draws is an entity. A sky is a handful of colored bands, a hundred-odd stars and a few
 * circles, and as entities they would have been a hundred-odd things to sort into the draw order
 * behind everything else every frame, only to draw them first anyway.
 */
class NightfallSystem(
    private val backdrop: Backdrop.Nightfall,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val dayPosition: () -> Float,
    random: Random = Random(SKY_SEED),
) : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var crossfades: ComponentMapper<CrossfadeComponent>

    /** Where through its day the stage is, as of the last tick; see [Daylight.position]. */
    private var position = 0f

    /** Seconds of play, which is what the stars twinkle and the meteors fall by. */
    private var clock = 0f

    /** How far each layer has scrolled, wrapped to its width. */
    private val scrolled = FloatArray(backdrop.layers.size)

    /**
     * The sky, one color per band, repainted only when the day has moved on by [REPAINT_STEP].
     * The sunset takes minutes; working out seventy-odd colors again every frame of it would be work
     * nobody could see the result of.
     */
    private val bands = IntArray((HORIZON_Y + BAND_HEIGHT - 1) / BAND_HEIGHT)
    private var bandsPaintedAt = Float.NaN

    // The stars, one entry per star across these arrays. Scattered once, from a fixed seed, so the
    // sky is the same sky on every run - the constellations are part of the stage.
    private val starX = FloatArray(STAR_COUNT)
    private val starY = IntArray(STAR_COUNT)
    private val starColor = IntArray(STAR_COUNT)
    private val starBrightness = FloatArray(STAR_COUNT)

    /** How much of the night has to be up before this star is: the brightest come out first. */
    private val starThreshold = FloatArray(STAR_COUNT)
    private val starTwinkleRate = FloatArray(STAR_COUNT)
    private val starTwinklePhase = FloatArray(STAR_COUNT)

    private val meteors = Random(random.nextInt())
    private var untilMeteor = METEOR_FIRST_SECONDS
    private var meteorAge = -1f
    private var meteorLife = 0f
    private var meteorX = 0f
    private var meteorY = 0f
    private var meteorDx = 0f
    private var meteorDy = 0f
    private var meteorLength = 0f

    init {
        for (i in 0 until STAR_COUNT) {
            starX[i] = random.nextFloat() * frameWidth
            // Thinner toward the horizon, where the glow of the sunset lingers longest anyway.
            val height = random.nextFloat()
            starY[i] = (STAR_TOP + height * height * (STAR_BOTTOM - STAR_TOP)).toInt()
            val roll = random.nextFloat()
            starBrightness[i] = when {
                roll < BRIGHT_SHARE -> 1f
                roll < BRIGHT_SHARE + MEDIUM_SHARE -> 0.6f + random.nextFloat() * 0.3f
                else -> 0.28f + random.nextFloat() * 0.3f
            }
            starThreshold[i] = (1f - starBrightness[i]) * 0.8f + random.nextFloat() * 0.2f
            starColor[i] = STAR_COLORS[random.nextInt(STAR_COLORS.size)]
            starTwinkleRate[i] = 1.2f + random.nextFloat() * 2.8f
            starTwinklePhase[i] = random.nextFloat() * TWO_PI
        }
    }

    override fun onAttach(world: World) {
        sprites = world.mapper(SpriteComponent::class)
        crossfades = world.mapper(CrossfadeComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        clock += deltaTime
        position = dayPosition()

        for (i in scrolled.indices) {
            val layer = backdrop.layers[i]
            scrolled[i] = (scrolled[i] + layer.speed) % layer.sheet.width
        }

        if (bandsPaintedAt.isNaN() || abs(position - bandsPaintedAt) >= REPAINT_STEP) paintBands()

        // The scenery standing in the stage is drawn in the same keyframes as the ground, so it is
        // put in the same light on the same tick. Its sheet's rows are its own height, which the
        // sprite already knows.
        val below = Daylight.keyframeBelow(position)
        val above = Daylight.keyframeAbove(position)
        val blend = Daylight.keyframeBlend(position)
        world.forEach(sprites, crossfades) { id ->
            val sprite = sprites.require(id)
            sprite.srcY = below * sprite.srcHeight
            val crossfade = crossfades.require(id)
            crossfade.srcY = above * sprite.srcHeight
            crossfade.alpha = blend
        }

        advanceMeteor(deltaTime)
    }

    override fun draw(world: World, graphics: Graphics) {
        drawSky(graphics)
        drawStars(graphics)
        drawMeteor(graphics)
        drawMoon(graphics)
        drawSun(graphics)
        drawGround(graphics)
    }

    private fun paintBands() {
        bandsPaintedAt = position
        for (band in bands.indices) {
            val middle = band * BAND_HEIGHT + BAND_HEIGHT / 2f
            bands[band] = Daylight.skyColor(position, middle / HORIZON_Y)
        }
    }

    /**
     * Banded rather than smooth, like the shading on every sprite in the game: a gradient drawn a
     * few pixels at a time is what a sky looks like in pixel art. Below the horizon the last band
     * carries on down to the bottom of the frame, behind the ground.
     */
    private fun drawSky(graphics: Graphics) {
        for (band in bands.indices) {
            graphics.drawRect(0, band * BAND_HEIGHT, frameWidth, BAND_HEIGHT, bands[band])
        }
        val below = bands.size * BAND_HEIGHT
        graphics.drawRect(0, below, frameWidth, frameHeight - below, bands.last())
    }

    private fun drawStars(graphics: Graphics) {
        val night = Daylight.starlight(position)
        if (night <= 0f) return
        // The sky turns slowly overhead: slow enough to be felt rather than seen.
        val drift = clock * STAR_DRIFT_PER_SECOND
        for (i in 0 until STAR_COUNT) {
            // Each star fades in over its own slice of the dusk, from the moment the night passes
            // its threshold, so they come out one by one rather than all at once.
            val up = ((night - starThreshold[i]) / STAR_FADE_IN).coerceIn(0f, 1f)
            if (up <= 0f) continue
            val twinkle = 1f - TWINKLE_DEPTH * (0.5f + 0.5f * sin(clock * starTwinkleRate[i] + starTwinklePhase[i]))
            val alpha = up * starBrightness[i] * twinkle
            val x = wrap(starX[i] - drift)
            val y = starY[i]
            val color = starColor[i]
            graphics.drawRect(x, y, 1, 1, EngineColors.withAlpha(color, alpha))
            if (starBrightness[i] >= 1f) {
                // The brightest few get a cross of light, dimmer than the point, so they read as
                // bigger stars rather than as bigger pixels.
                val arm = EngineColors.withAlpha(color, alpha * 0.45f)
                graphics.drawRect(x - 1, y, 1, 1, arm)
                graphics.drawRect(x + 1, y, 1, 1, arm)
                graphics.drawRect(x, y - 1, 1, 1, arm)
                graphics.drawRect(x, y + 1, 1, 1, arm)
            }
        }
    }

    private fun drawMoon(graphics: Graphics) {
        val light = Daylight.moonlight(position)
        if (light <= 0f) return
        val moon = backdrop.moon
        // It climbs a little as it comes out, rather than fading in on the spot.
        val y = (MOON_Y + MOON_RISE * (1f - light)).roundToInt()
        graphics.drawPixmapFaded(moon, MOON_X - moon.width / 2, y, 0, 0, moon.width, moon.height, light)
    }

    /**
     * A disc with a brighter core, inside three rings of halo, faintest outermost. The halo is how a
     * low sun sets the sky around it alight; the rings are hard-edged, so it is the same banded
     * light as the sky rather than a blur.
     */
    private fun drawSun(graphics: Graphics) {
        if (position >= Daylight.SUNSET) return
        val x = Daylight.sunX(position)
        val y = Daylight.sunY(position)
        val radius = Daylight.sunRadius(position)
        val color = Daylight.sunColor(position)
        val glow = Daylight.sunGlow(position)

        for (ring in HALO_RINGS.indices) {
            circle(graphics, x, y, radius * HALO_RINGS[ring], EngineColors.withAlpha(color, HALO_ALPHA[ring] * glow))
        }
        circle(graphics, x, y, radius, color)
        circle(graphics, x, y, radius * 0.72f, EngineColors.lerp(color, EngineColors.WHITE, 0.55f))
    }

    private fun circle(graphics: Graphics, x: Float, y: Float, radius: Float, color: Int) {
        val size = (radius * 2f).roundToInt()
        graphics.drawOval((x - radius).roundToInt(), (y - radius).roundToInt(), size, size, color)
    }

    private fun advanceMeteor(deltaTime: Float) {
        if (meteorAge >= 0f) {
            meteorAge += deltaTime
            if (meteorAge >= meteorLife) meteorAge = -1f
            return
        }
        // Only once the night is well up: a meteor in daylight is a meteor nobody would see.
        if (Daylight.starlight(position) < METEOR_NIGHT) return
        untilMeteor -= deltaTime
        if (untilMeteor > 0f) return
        untilMeteor = METEOR_MIN_GAP + meteors.nextFloat() * METEOR_GAP_SPREAD

        meteorAge = 0f
        meteorLife = 0.55f + meteors.nextFloat() * 0.35f
        meteorX = frameWidth * (0.3f + meteors.nextFloat() * 0.65f)
        meteorY = STAR_TOP + meteors.nextFloat() * 70f
        // Down and to the left, the way the scenery goes, at a shallow and varying slant.
        val angle = (PI * (1.08 + meteors.nextFloat() * 0.14)).toFloat()
        meteorDx = cos(angle)
        meteorDy = -sin(angle)
        meteorLength = 18f + meteors.nextFloat() * 16f
    }

    /** A streak in three fading lengths, head brightest, in and out over its short life. */
    private fun drawMeteor(graphics: Graphics) {
        if (meteorAge < 0f) return
        val life = meteorAge / meteorLife
        val fade = Daylight.smoothstep(0f, 0.2f, life) * (1f - Daylight.smoothstep(0.6f, 1f, life))
        val travelled = meteorAge * METEOR_SPEED
        val headX = meteorX + meteorDx * travelled
        val headY = meteorY + meteorDy * travelled
        for (piece in 0 until 3) {
            val near = meteorLength * piece / 3f
            val far = meteorLength * (piece + 1) / 3f
            graphics.drawLine(
                (headX - meteorDx * near).roundToInt(), (headY - meteorDy * near).roundToInt(),
                (headX - meteorDx * far).roundToInt(), (headY - meteorDy * far).roundToInt(),
                EngineColors.withAlpha(EngineColors.WHITE, fade * METEOR_ALPHA[piece]),
            )
        }
    }

    /**
     * Each layer, back to front: the keyframe the day is leaving, then the one it is going to faded
     * in over it. The strip is blitted in as many spans as it takes to cross the frame, wrapping
     * round to its own first column.
     *
     * A blit paints one column and one row short of its source - the deliberate `- 1` in both
     * backends - so each span asks for a column more than it has to cover, and the next one starts
     * on the last column the previous one actually painted. At the wrap that skips the strip's own
     * last column, which on a strip built to be periodic is invisible.
     */
    private fun drawGround(graphics: Graphics) {
        val below = Daylight.keyframeBelow(position)
        val above = Daylight.keyframeAbove(position)
        val blend = Daylight.keyframeBlend(position)
        for (i in backdrop.layers.indices) {
            val layer = backdrop.layers[i]
            val sheet = layer.sheet
            val rowHeight = layer.rowHeight
            var srcX = scrolled[i].toInt()
            var dstX = 0
            while (dstX < frameWidth) {
                val span = minOf(sheet.width - srcX, frameWidth - dstX + 1)
                graphics.drawPixmap(sheet, dstX, layer.top, srcX, below * rowHeight, span, rowHeight)
                if (blend > 0f) {
                    graphics.drawPixmapFaded(sheet, dstX, layer.top, srcX, above * rowHeight, span, rowHeight, blend)
                }
                dstX += span - 1
                srcX = 0
            }
        }
    }

    private fun wrap(x: Float): Int {
        val wrapped = x % frameWidth
        return (if (wrapped < 0f) wrapped + frameWidth else wrapped).toInt()
    }

    private companion object {
        const val SKY_SEED = 20260926

        /** Where the sky's gradient ends: level with the far dunes, where the ground takes over. */
        const val HORIZON_Y = 236
        const val BAND_HEIGHT = 3

        /** How far the day moves before the sky's colors are worked out again. */
        const val REPAINT_STEP = 0.0015f

        const val STAR_COUNT = 130
        const val STAR_TOP = 4
        const val STAR_BOTTOM = 212
        const val BRIGHT_SHARE = 0.06f
        const val MEDIUM_SHARE = 0.24f

        /** The share of the night's arrival each star takes to come all the way out. */
        const val STAR_FADE_IN = 0.2f

        /** How far a twinkle dims a star, at its dimmest. */
        const val TWINKLE_DEPTH = 0.35f
        const val STAR_DRIFT_PER_SECOND = 1.1f

        /** Mostly white, with a few blue, gold and rose among them, as a clear desert sky has. */
        val STAR_COLORS = intArrayOf(
            0xFFF4F4FF.toInt(), 0xFFF4F4FF.toInt(), 0xFFF4F4FF.toInt(),
            0xFFC8D8FF.toInt(), 0xFFFFECC8.toInt(), 0xFFFFD8E6.toInt(),
        )

        const val MOON_X = 150
        const val MOON_Y = 40
        const val MOON_RISE = 26f

        val HALO_RINGS = floatArrayOf(3.4f, 2.4f, 1.6f)
        val HALO_ALPHA = floatArrayOf(0.1f, 0.16f, 0.26f)

        const val METEOR_FIRST_SECONDS = 2.5f
        const val METEOR_MIN_GAP = 4f
        const val METEOR_GAP_SPREAD = 6f
        const val METEOR_NIGHT = 0.5f
        const val METEOR_SPEED = 170f
        val METEOR_ALPHA = floatArrayOf(0.95f, 0.5f, 0.2f)

        const val TWO_PI = (2.0 * PI).toFloat()
    }
}
