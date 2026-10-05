package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.resource.Backdrop
import at.smiech.cyanbat.scenery.Day
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Draws a [Backdrop.Sky]: a sky that changes with the time of day across the stage - the desert's
 * from noon into night, the lagoon's from night to noon - and the ground scrolling past underneath it
 * at its depths; and keeps everything else in the stage that is drawn by daylight, its obstacles, in
 * the same light.
 *
 * Add it first. Its [draw] is the back of the picture, and everything the world draws after it -
 * the sprites, the effects, the bars - has to land on top.
 *
 * How far through the day the stage is comes from [dayPosition], read once a tick, so the sun moves
 * on the stage clock and holds still whenever the stage does. Everything else it draws - the scroll,
 * the twinkle, the meteors, the glints on the water - is advanced by the same fixed tick for the same
 * reason.
 *
 * Nothing it draws is an entity. A sky is a handful of colored bands, a hundred-odd stars and a few
 * circles, and as entities they would have been a hundred-odd things to sort into the draw order
 * behind everything else every frame, only to draw them first anyway.
 */
class SkySystem(
    private val backdrop: Backdrop.Sky,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val dayPosition: () -> Float,
    random: Random = Random(SKY_SEED),
) : GameSystem() {
    private lateinit var sprites: ComponentMapper<SpriteComponent>
    private lateinit var crossfades: ComponentMapper<CrossfadeComponent>

    private val day: Day = backdrop.day
    private val horizonY = backdrop.horizonY
    private val starBottom = horizonY - STAR_HORIZON_GAP

    /** Where through its day the stage is, as of the last tick; see [Day.position]. */
    private var position = 0f

    /** Seconds of play, which is what the stars twinkle, the meteors fall and the water glints by. */
    private var clock = 0f

    /** How far into its current stretch each layer has scrolled, wrapped to its width. */
    private val scrolled = FloatArray(backdrop.layers.size)

    /** Which stretch of its strip each layer is into, counted from the stage's start. */
    private val stretch = IntArray(backdrop.layers.size)

    /**
     * The first stretch of each layer drawn from its [at.smiech.cyanbat.resource.ParallaxLayer.ahead]
     * sheet, once the day has got that far; none until then. Settled on the first stretch still out
     * of sight when it does, so the new scenery comes in from the right rather than appearing in view.
     */
    private val aheadFrom = IntArray(backdrop.layers.size) { Int.MAX_VALUE }

    /**
     * The sky, one color per band, repainted only when the day has moved on by [REPAINT_STEP].
     * A sunset or a sunrise takes minutes; working out seventy-odd colors again every frame of it would
     * be work nobody could see the result of.
     */
    private val bands = IntArray((horizonY + BAND_HEIGHT - 1) / BAND_HEIGHT)
    private var bandsPaintedAt = Float.NaN

    // The stars, one entry per star across these arrays. Scattered once, from a fixed seed, so the
    // sky is the same sky on every run - the constellations are part of the stage.
    private val starX = FloatArray(STAR_COUNT)
    private val starY = IntArray(STAR_COUNT)
    private val starColor = IntArray(STAR_COUNT)
    private val starBrightness = FloatArray(STAR_COUNT)

    /** How much of the night has to be up before this star is: the brightest come out first, and go last. */
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
        for (layer in backdrop.layers) {
            val ahead = layer.ahead ?: continue
            require(ahead.width == layer.sheet.width && ahead.height == layer.sheet.height) {
                "A band's scenery ahead has to be the size of what it follows on from"
            }
        }
        for (i in 0 until STAR_COUNT) {
            starX[i] = random.nextFloat() * frameWidth
            // Thinner toward the horizon, where the glow of the sunset lingers longest anyway.
            val height = random.nextFloat()
            starY[i] = (STAR_TOP + height * height * (starBottom - STAR_TOP)).toInt()
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
            val width = layer.sheet.width
            if (aheadFrom[i] == Int.MAX_VALUE && layer.ahead != null && position >= layer.aheadFrom) {
                // The next stretch is already coming into view when there is less of this one left
                // than the frame is wide; the new scenery waits for the one after it.
                val nextInView = width - scrolled[i] <= frameWidth
                aheadFrom[i] = stretch[i] + if (nextInView) 2 else 1
            }
            scrolled[i] += layer.speed
            while (scrolled[i] >= width) {
                scrolled[i] -= width
                stretch[i]++
            }
        }

        if (bandsPaintedAt.isNaN() || abs(position - bandsPaintedAt) >= REPAINT_STEP) paintBands()

        // The scenery standing in the stage is drawn in the same keyframes as the ground, so it is
        // put in the same light on the same tick. Its sheet's rows are its own height, which the
        // sprite already knows.
        val below = day.keyframeBelow(position)
        val above = day.keyframeAbove(position)
        val blend = day.keyframeBlend(position)
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
            bands[band] = day.skyColor(position, middle / horizonY)
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
        val night = day.starlight(position)
        if (night <= 0f) return
        // The sky turns slowly overhead: slow enough to be felt rather than seen.
        val drift = clock * STAR_DRIFT_PER_SECOND
        for (i in 0 until STAR_COUNT) {
            // Each star fades in over its own slice of the night's arrival, from the moment the night
            // passes its threshold, so they come out one by one rather than all at once - and go out
            // the same way as it leaves.
            val up = ((night - starThreshold[i]) / STAR_FADE_IN).coerceIn(0f, 1f)
            if (up <= 0f) continue
            val twinkle =
                1f - TWINKLE_DEPTH * (0.5f + 0.5f * sin(clock * starTwinkleRate[i] + starTwinklePhase[i]))
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
        val light = day.moonlight(position)
        if (light <= 0f) return
        val moon = backdrop.moon
        val x = day.moonX(position).roundToInt()
        val y = day.moonY(position).roundToInt()
        graphics.drawPixmapFaded(moon, x, y, 0, 0, moon.width, moon.height, light)
    }

    /**
     * A disc with a brighter core, inside three rings of halo, faintest outermost. The halo is how a
     * low sun sets the sky around it alight; the rings are hard-edged, so it is the same banded light
     * as the sky rather than a blur.
     *
     * A sun seen through the haze over a sea is cut into bands across its lower half, and shades from
     * its own color at the top to a deeper one at the bottom: it is drawn a row at a time then.
     */
    private fun drawSun(graphics: Graphics) {
        if (!day.sunUp(position)) return
        val x = day.sunX(position)
        val y = day.sunY(position)
        val radius = day.sunRadius(position)
        val color = day.sunColor(position)
        val glow = day.sunGlow(position)

        for (ring in HALO_RINGS.indices) {
            circle(
                graphics,
                x,
                y,
                radius * HALO_RINGS[ring],
                EngineColors.withAlpha(color, HALO_ALPHA[ring] * glow)
            )
        }
        val low = day.sunLowColor(position)
        val bands = day.sunBands(position)
        if (bands <= 0f && low == color) {
            circle(graphics, x, y, radius, color)
            circle(
                graphics,
                x,
                y,
                radius * 0.72f,
                EngineColors.lerp(color, EngineColors.WHITE, 0.55f)
            )
        } else {
            drawBandedSun(graphics, x, y, radius, color, low, bands)
        }
    }

    /**
     * The sun a row at a time, from [top] at its crown to [bottom] at its foot, with the rows that fall
     * in the haze's bands left out so the sky behind shows through. The bands start at its middle and
     * thicken toward its foot, more of them the more [bands] there is.
     */
    private fun drawBandedSun(
        graphics: Graphics,
        x: Float,
        y: Float,
        radius: Float,
        top: Int,
        bottom: Int,
        bands: Float,
    ) {
        val rows = radius.roundToInt()
        val core = radius * 0.72f
        for (row in -rows until rows) {
            val dy = row + 0.5f
            // How far down the disc this row is, 0 at the middle and 1 at the foot.
            val down = dy / radius
            if (down > 0f && bands > 0f && inBand(down, bands)) continue
            val half = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
            if (half < 0.5f) continue
            val along = ((dy + radius) / (2f * radius)).coerceIn(0f, 1f)
            var color = EngineColors.lerp(top, bottom, along)
            val left = (x - half).roundToInt()
            val width = (x + half).roundToInt() - left
            graphics.drawRect(left, (y + row).roundToInt(), width, 1, color)
            val coreHalf = sqrt((core * core - dy * dy).coerceAtLeast(0f))
            if (coreHalf >= 0.5f) {
                color = EngineColors.lerp(color, EngineColors.WHITE, 0.55f)
                val coreLeft = (x - coreHalf).roundToInt()
                graphics.drawRect(
                    coreLeft,
                    (y + row).roundToInt(),
                    (x + coreHalf).roundToInt() - coreLeft,
                    1,
                    color
                )
            }
        }
    }

    /**
     * Whether a row [down] of the way from the sun's middle to its foot falls in one of the haze's
     * bands: [SUN_BAND_COUNT] of them across the lower half, each a slice of its own period that grows
     * toward the foot - from nothing at the top to most of the period at the bottom, at full [bands].
     */
    private fun inBand(down: Float, bands: Float): Boolean {
        val period = (down * SUN_BAND_COUNT) % 1f
        val thickness = (SUN_BAND_LEAST + (SUN_BAND_MOST - SUN_BAND_LEAST) * down) * bands
        return period < thickness && down > SUN_BAND_START
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
        // Only while the night is well up: a meteor in daylight is a meteor nobody would see.
        if (day.starlight(position) < METEOR_NIGHT) return
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
        val fade = smooth(0f, 0.2f, life) * (1f - smooth(0.6f, 1f, life))
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
     * round to its own first column - and, on the way to the boss, on to the scenery ahead.
     *
     * A blit paints one column and one row short of its source - the deliberate `- 1` in both
     * backends - so each span asks for a column more than it has to cover, and the next one starts
     * on the last column the previous one actually painted. At the wrap that skips the strip's own
     * last column, which on a strip built to be periodic is invisible.
     */
    private fun drawGround(graphics: Graphics) {
        val below = day.keyframeBelow(position)
        val above = day.keyframeAbove(position)
        val blend = day.keyframeBlend(position)
        val rows = day.keyframes.size
        for (i in backdrop.layers.indices) {
            val layer = backdrop.layers[i]
            val rowHeight = layer.sheet.height / rows
            var srcX = scrolled[i].toInt()
            var dstX = 0
            var stretchHere = stretch[i]
            while (dstX < frameWidth) {
                val sheet = sheetFor(i, stretchHere)
                val span = minOf(sheet.width - srcX, frameWidth - dstX + 1)
                graphics.drawPixmap(
                    sheet,
                    dstX,
                    layer.top,
                    srcX,
                    below * rowHeight,
                    span,
                    rowHeight
                )
                if (blend > 0f) {
                    graphics.drawPixmapFaded(
                        sheet,
                        dstX,
                        layer.top,
                        srcX,
                        above * rowHeight,
                        span,
                        rowHeight,
                        blend
                    )
                }
                dstX += span - 1
                srcX = 0
                stretchHere++
            }
            layer.water?.let { drawGlitter(graphics, it) }
        }
    }

    /** The sheet a layer's [stretch] is drawn from: its own, or the scenery ahead once that has come. */
    private fun sheetFor(layer: Int, stretch: Int): Pixmap {
        val ahead = backdrop.layers[layer].ahead
        return if (ahead != null && stretch >= aheadFrom[layer]) ahead else backdrop.layers[layer].sheet
    }

    /**
     * The low sun's path across the water: short dashes of its color under it, row after row from the
     * horizon down, widening and spreading out as they come nearer, each flickering on its own beat.
     * Drawn over the band whose water it is, and so under every nearer band - an island in front of
     * the path cuts it, as it would.
     */
    private fun drawGlitter(graphics: Graphics, water: IntRange) {
        val strength = day.sunGlitter(position)
        if (strength <= 0f || !day.sunUp(position)) return
        val x = day.sunX(position)
        val color = EngineColors.lerp(day.sunColor(position), EngineColors.WHITE, 0.35f)
        var row = water.first
        var index = 0
        while (row <= water.last) {
            val near =
                (row - water.first).toFloat() / (water.last - water.first + 1).coerceAtLeast(1)
            val shimmer = 0.5f + 0.5f * sin(clock * GLITTER_RATE + index * GLITTER_STAGGER)
            val alpha =
                strength * (GLITTER_FAINTEST + (1f - GLITTER_FAINTEST) * shimmer) * (1f - 0.4f * near)
            if (alpha > 0.04f) {
                val reach = GLITTER_REACH * (0.35f + near) * strength
                val sway = sin(clock * GLITTER_SWAY_RATE + index * 1.7f) * reach * 0.5f
                val length = (GLITTER_DASH * (0.6f + near) * (0.4f + 0.6f * shimmer)).roundToInt()
                    .coerceAtLeast(1)
                graphics.drawRect(
                    (x + sway - length / 2f).roundToInt(),
                    row,
                    length,
                    1,
                    EngineColors.withAlpha(color, alpha)
                )
            }
            row += GLITTER_ROW_STEP
            index++
        }
    }

    private fun wrap(x: Float): Int {
        val wrapped = x % frameWidth
        return (if (wrapped < 0f) wrapped + frameWidth else wrapped).toInt()
    }

    /** Eased 0..1 between [from] and [to], for the meteor's fade. */
    private fun smooth(from: Float, to: Float, value: Float): Float {
        val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        const val SKY_SEED = 20260926

        const val BAND_HEIGHT = 3

        /** How far the day moves before the sky's colors are worked out again. */
        const val REPAINT_STEP = 0.0015f

        const val STAR_COUNT = 130
        const val STAR_TOP = 4

        /** How far above the horizon the lowest stars sit, where the glow along it hides them anyway. */
        const val STAR_HORIZON_GAP = 24
        const val BRIGHT_SHARE = 0.06f
        const val MEDIUM_SHARE = 0.24f

        /** The share of the night's arrival each star takes to come all the way out. */
        const val STAR_FADE_IN = 0.2f

        /** How far a twinkle dims a star, at its dimmest. */
        const val TWINKLE_DEPTH = 0.35f
        const val STAR_DRIFT_PER_SECOND = 1.1f

        /** Mostly white, with a few blue, gold and rose among them, as a clear sky has. */
        val STAR_COLORS = intArrayOf(
            0xFFF4F4FF.toInt(), 0xFFF4F4FF.toInt(), 0xFFF4F4FF.toInt(),
            0xFFC8D8FF.toInt(), 0xFFFFECC8.toInt(), 0xFFFFD8E6.toInt(),
        )

        val HALO_RINGS = floatArrayOf(3.4f, 2.4f, 1.6f)
        val HALO_ALPHA = floatArrayOf(0.1f, 0.16f, 0.26f)

        /**
         * The banded sun: how many bands across its lower half, how far down they start, and how
         * much of each band's period is cut out at its top and at its foot.
         */
        const val SUN_BAND_COUNT = 5f
        const val SUN_BAND_START = 0.12f
        const val SUN_BAND_LEAST = 0.12f
        const val SUN_BAND_MOST = 0.55f

        /**
         * The sun's path on the water: a dash every other row, flickering at [GLITTER_RATE] a
         * second and out of step with the rows either side, swaying up to [GLITTER_REACH] pixels
         * from under the sun, and up to [GLITTER_DASH] pixels long near the shore.
         */
        const val GLITTER_ROW_STEP = 2
        const val GLITTER_RATE = 5.5f
        const val GLITTER_STAGGER = 2.3f
        const val GLITTER_SWAY_RATE = 1.3f
        const val GLITTER_REACH = 26f
        const val GLITTER_DASH = 22f
        const val GLITTER_FAINTEST = 0.25f

        const val METEOR_FIRST_SECONDS = 2.5f
        const val METEOR_MIN_GAP = 4f
        const val METEOR_GAP_SPREAD = 6f
        const val METEOR_NIGHT = 0.5f
        const val METEOR_SPEED = 170f
        val METEOR_ALPHA = floatArrayOf(0.95f, 0.5f, 0.2f)

        const val TWO_PI = (2.0 * PI).toFloat()
    }
}
