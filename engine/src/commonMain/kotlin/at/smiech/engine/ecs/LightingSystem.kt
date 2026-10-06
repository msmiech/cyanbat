package at.smiech.engine.ecs

import at.smiech.engine.Gloss
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.Lighting
import at.smiech.engine.Pixmap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Makes the stage dark: the frame lit only by [ambient], brightened around every [LightComponent],
 * with every [OccluderComponent] in a light's way casting a shadow and, if it shines, catching a
 * glint on the lit side.
 *
 * Add it after the passes that draw what light falls on and before those that draw lights
 * themselves: what is drawn after it keeps its own brightness, what is drawn before is as bright as
 * the light it stands in. See [Graphics.drawLighting].
 *
 * Every light casts shadows, so a shot flying past a creature throws its shadow on the scenery
 * behind. That is affordable because only occluders within a light's reach cast from it, and an
 * outline's shadow is a few dozen points. It also ages fading lights and removes those that go with
 * their light.
 *
 * The light is computed per tick and drawn per frame. A frame with no tick since the last (paused,
 * or the second of two frames on a fast screen) reuses it, and [Graphics.drawLighting] recognizes
 * it by its version and does not render it again.
 *
 * @param glow see [Lighting.glow].
 */
class LightingSystem(
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val ambient: Int,
    private val glow: Float = 0f,
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var lights: ComponentMapper<LightComponent>
    private lateinit var occluders: ComponentMapper<OccluderComponent>
    private lateinit var sprites: ComponentMapper<SpriteComponent>

    private val lighting = Lighting()
    private val silhouettes = Silhouettes()

    /** Set by every tick and cleared once the light has been recomputed. */
    private var stale = true

    /** This tick's occluders, the first [occluderCount] of them; reused across ticks. */
    private val standing = ArrayList<Occluder>()
    private var occluderCount = 0

    /** Every occluder's outline in frame pixels, stored consecutively; see [Occluder.from]. */
    private var outlines = FloatArray(256)

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        lights = world.mapper(LightComponent::class)
        occluders = world.mapper(OccluderComponent::class)
        sprites = world.mapper(SpriteComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(lights) { id ->
            val light = lights.require(id)
            if (light.fadeSeconds <= 0f) return@forEach
            light.elapsed += deltaTime
            if (light.removeWhenFaded && light.faded) world.removeEntity(id)
        }
        stale = true
    }

    override fun draw(world: World, graphics: Graphics) {
        if (stale) {
            light(world)
            stale = false
        }
        graphics.drawLighting(lighting)
    }

    /**
     * Computes the light as the world stands: every light in the frame, with its
     * shadows and glints.
     */
    private fun light(world: World) {
        lighting.begin(ambient, glow)
        gatherOccluders(world)
        world.forEach(transforms, lights) { id ->
            val light = lights.require(id)
            val strength = light.strength
            val radius = light.radius
            if (strength <= 0f || radius <= 0) return@forEach
            val rect = transforms.require(id).rect
            val x = floor(rect.centerX).toInt()
            val y = floor(rect.centerY).toInt()
            if (x + radius < 0 || x - radius >= frameWidth || y + radius < 0 || y - radius >= frameHeight) {
                return@forEach
            }
            val added = lighting.add(x, y, radius, light.color, strength)
            castShadows(added)
            glint(added)
        }
    }

    /**
     * Every occluder's outline in frame pixels: its sprite's current silhouette, placed as
     * [RenderSystem] places the sprite (at its box's corner, truncated to the pixel, magnified and
     * turned with it).
     */
    private fun gatherOccluders(world: World) {
        occluderCount = 0
        var used = 0
        world.forEach(transforms, sprites, occluders) { id ->
            val sprite = sprites.require(id)
            val hull = silhouettes.of(
                sprite.pixmap,
                sprite.srcX,
                sprite.srcY,
                sprite.srcWidth,
                sprite.srcHeight
            )
                ?: return@forEach
            val rect = transforms.require(id).rect
            val occluder =
                if (occluderCount < standing.size) standing[occluderCount] else Occluder().also { standing += it }
            occluderCount++
            occluder.pixmap = sprite.pixmap
            occluder.srcX = sprite.srcX
            occluder.srcY = sprite.srcY
            occluder.srcWidth = sprite.srcWidth
            occluder.srcHeight = sprite.srcHeight
            occluder.left = rect.left.toInt()
            occluder.top = rect.top.toInt()
            occluder.dstWidth = (sprite.srcWidth * sprite.scale).roundToInt()
            occluder.dstHeight = (sprite.srcHeight * sprite.scale).roundToInt()
            occluder.turned = sprite.rotationDegrees != 0f
            occluder.shine = occluders.require(id).shine

            // A blit paints a column and a row short of its box and stretches the picture over the
            // rest, so a magnified sprite's pixels sit that much further apart.
            val scaleX =
                if (sprite.srcWidth > 1) (occluder.dstWidth - 1f) / (sprite.srcWidth - 1) else sprite.scale
            val scaleY =
                if (sprite.srcHeight > 1) (occluder.dstHeight - 1f) / (sprite.srcHeight - 1) else sprite.scale
            val radians = sprite.rotationDegrees * RADIANS_PER_DEGREE
            val cos = cos(radians)
            val sin = sin(radians)
            val pivotX = occluder.left + occluder.dstWidth / 2f
            val pivotY = occluder.top + occluder.dstHeight / 2f

            if (used + hull.size > outlines.size) outlines =
                outlines.copyOf(maxOf(outlines.size * 2, used + hull.size))
            occluder.from = used
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (i in hull.indices step 2) {
                var x = occluder.left + hull[i] * scaleX
                var y = occluder.top + hull[i + 1] * scaleY
                if (occluder.turned) {
                    // Clockwise about the middle of its box, as the sprite is turned to draw it.
                    val dx = x - pivotX
                    val dy = y - pivotY
                    x = pivotX + dx * cos - dy * sin
                    y = pivotY + dx * sin + dy * cos
                }
                outlines[used++] = x
                outlines[used++] = y
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
            occluder.until = used
            occluder.minX = minX
            occluder.minY = minY
            occluder.maxX = maxX
            occluder.maxY = maxY
        }
    }

    /**
     * Every shadow cast across [light]'s disc from its center, the middle of its pixel. An occluder
     * wholly out of reach casts nothing.
     */
    private fun castShadows(light: Lighting.Light) {
        val centerX = light.x + 0.5f
        val centerY = light.y + 0.5f
        val reach = reachOf(light)
        for (i in 0 until occluderCount) {
            val occluder = standing[i]
            occluder.shadow = OUT_OF_REACH
            if (occluder.distanceTo(centerX, centerY) > reach) continue
            val before = light.shadowCount
            val thrown =
                Shadow.cast(outlines, occluder.from, occluder.until, centerX, centerY, reach, light)
            occluder.shadow = when {
                !thrown -> LIGHT_INSIDE
                light.shadowCount > before -> light.shadowCount - 1
                else -> NO_SHADOW
            }
        }
    }

    /**
     * A glint of [light] off every shining occluder it reaches, on the light's
     * side; none for one in another's shadow or with the light inside it, since a
     * creature's own glow does not glint off it.
     *
     * It fades with distance more slowly than the light does: a dim creature at the edge of a light
     * is the one whose outline most needs showing.
     */
    private fun glint(light: Lighting.Light) {
        val centerX = light.x + 0.5f
        val centerY = light.y + 0.5f
        for (i in 0 until occluderCount) {
            val occluder = standing[i]
            if (occluder.shine <= 0f || occluder.turned) continue
            if (occluder.shadow == OUT_OF_REACH || occluder.shadow == LIGHT_INSIDE) continue
            val middleX = occluder.left + occluder.dstWidth / 2f
            val middleY = occluder.top + occluder.dstHeight / 2f
            if (light.inShadow(middleX, middleY, except = occluder.shadow)) continue
            val reached =
                sqrt(Lighting.falloff(occluder.distanceTo(centerX, centerY) / light.radius))
            val strength = light.intensity * reached * occluder.shine
            if (strength < FAINTEST_GLINT) continue
            val angle = atan2(centerY - middleY, centerX - middleX)
            val direction =
                ((angle / DIRECTION_STEP).roundToInt() % Gloss.DIRECTIONS + Gloss.DIRECTIONS) % Gloss.DIRECTIONS
            lighting.addGlint(
                occluder.pixmap!!,
                occluder.srcX,
                occluder.srcY,
                occluder.srcWidth,
                occluder.srcHeight,
                occluder.left,
                occluder.top,
                occluder.dstWidth,
                occluder.dstHeight,
                direction,
                light.color,
                strength,
            )
        }
    }

    /** The disc covers the pixels whose centers lie within its radius plus a half. */
    private fun reachOf(light: Lighting.Light): Float = light.radius + 1f

    /**
     * One occluder as this tick found it: its sprite's frame and where it is drawn, its outline in
     * [outlines] from [from] until [until] with its bounding box, and, for the light being
     * computed, which of the light's shadows it cast.
     */
    private class Occluder {
        var pixmap: Pixmap? = null
        var srcX = 0
        var srcY = 0
        var srcWidth = 0
        var srcHeight = 0
        var left = 0
        var top = 0
        var dstWidth = 0
        var dstHeight = 0
        var turned = false
        var shine = 0f
        var from = 0
        var until = 0
        var minX = 0f
        var minY = 0f
        var maxX = 0f
        var maxY = 0f
        var shadow = OUT_OF_REACH

        /** How far ([x], [y]) is from the nearest point of the box around the outline. */
        fun distanceTo(x: Float, y: Float): Float {
            val dx = x.coerceIn(minX, maxX) - x
            val dy = y.coerceIn(minY, maxY) - y
            return sqrt(dx * dx + dy * dy)
        }
    }

    private companion object {
        const val RADIANS_PER_DEGREE = 0.017453292f
        const val DIRECTION_STEP = (2.0 * PI / Gloss.DIRECTIONS).toFloat()

        /** A glint fainter than this would not be seen, and is not drawn. */
        const val FAINTEST_GLINT = 0.03f

        /** What [Occluder.shadow] holds when the light is out of its reach, or inside its outline. */
        const val OUT_OF_REACH = -3
        const val LIGHT_INSIDE = -2

        /**
         * What it holds when the light reaches it but it cast no shadow, which
         * should not happen for a convex outline.
         */
        const val NO_SHADOW = -1
    }
}
