package at.smiech.engine

import kotlin.math.roundToInt

/**
 * A frame's light, as [Graphics.drawLighting] takes it: [ambient] everywhere, plus
 * point lights that each brighten a disc around themselves, minus the shadows cast
 * across that disc. The lights are laid over the dark in the order added, each
 * moving the light toward its own color by as much as it shines there.
 *
 * Refilled once a tick by `LightingSystem` and reused across frames, lights and all, so filling it
 * stops allocating once a run has seen its busiest moment. [version] advances on every refill, so a
 * [Graphics] can tell when the light is unchanged (a paused frame, or a second frame between ticks)
 * and keep what it drew last time.
 */
class Lighting {
    /**
     * The light where no point light reaches: the color the frame is multiplied by there. Opaque
     * black would be no light at all.
     */
    var ambient: Int = EngineColors.BLACK
        private set

    /**
     * How much of the light is added on top of the multiplied frame, 0..1: light seen in the air,
     * not only on surfaces. Multiplying alone shows a colored light only on things of its own
     * color; a cyan light on the cave's navy walls barely showed at all.
     */
    var glow: Float = 0f
        private set

    /** Advances every time the light is refilled; see the class. */
    var version: Int = 0
        private set

    private val lights = ArrayList<Light>()

    /** How many lights this frame has; [get] reads them. */
    var count: Int = 0
        private set

    /** Light [index] of this frame's [count]. */
    operator fun get(index: Int): Light {
        require(index in 0 until count) { "Light $index of $count" }
        return lights[index]
    }

    private val glints = ArrayList<Glint>()

    /** How many glints this frame has; [glint] reads them. */
    var glintCount: Int = 0
        private set

    /** Glint [index] of this frame's [glintCount]. */
    fun glint(index: Int): Glint {
        require(index in 0 until glintCount) { "Glint $index of $glintCount" }
        return glints[index]
    }

    /** Starts the frame's light over, dark at [ambient] and with no lights or glints in it. */
    fun begin(ambient: Int, glow: Float) {
        this.ambient = ambient
        this.glow = glow.coerceIn(0f, 1f)
        count = 0
        glintCount = 0
        version++
    }

    /**
     * Adds a glint off the sprite drawn from the [srcWidth] by [srcHeight] frame of [pixmap] at [srcX],
     * [srcY] into the [dstWidth] by [dstHeight] box at [x], [y]: light from [direction], one of
     * [Gloss.DIRECTIONS], in [color] at [strength].
     */
    fun addGlint(
        pixmap: Pixmap,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        x: Int,
        y: Int,
        dstWidth: Int,
        dstHeight: Int,
        direction: Int,
        color: Int,
        strength: Float,
    ) {
        val glint =
            if (glintCount < glints.size) glints[glintCount] else Glint().also { glints += it }
        glintCount++
        glint.pixmap = pixmap
        glint.srcX = srcX
        glint.srcY = srcY
        glint.srcWidth = srcWidth
        glint.srcHeight = srcHeight
        glint.x = x
        glint.y = y
        glint.dstWidth = dstWidth
        glint.dstHeight = dstHeight
        glint.direction = direction
        glint.color = color
        glint.strength = strength.coerceIn(0f, 1f)
    }

    /**
     * A light catching the rounded edge of a sprite, as [Gloss] computes it: the sprite's frame and
     * where it is drawn, the light's direction, color and strength. Added over the frame after the
     * light, since a glint is light reflected off a surface rather than light it stands in.
     */
    class Glint internal constructor() {
        lateinit var pixmap: Pixmap
            internal set
        var srcX: Int = 0
            internal set
        var srcY: Int = 0
            internal set
        var srcWidth: Int = 0
            internal set
        var srcHeight: Int = 0
            internal set
        var x: Int = 0
            internal set
        var y: Int = 0
            internal set
        var dstWidth: Int = 0
            internal set
        var dstHeight: Int = 0
            internal set
        var direction: Int = 0
            internal set
        var color: Int = 0
            internal set
        var strength: Float = 0f
            internal set
    }

    /**
     * Adds a point light centered on the pixel at [x], [y], reaching [radius] pixels out, in [color] at
     * [intensity] of its strength; its shadows are added to it afterwards.
     */
    fun add(x: Int, y: Int, radius: Int, color: Int, intensity: Float): Light {
        val light = if (count < lights.size) lights[count] else Light().also { lights += it }
        count++
        light.reset(x, y, radius, color, intensity.coerceIn(0f, 1f))
        return light
    }

    /**
     * One point light of a [Lighting], and the shadows cast across its disc.
     *
     * A shadow is a polygon in frame pixels, its points stored consecutively in [shadowPoints] as x
     * and y; shadow `i` runs from [shadowEnd] `(i - 1)` to [shadowEnd] `(i)`. A pixel whose center
     * lies inside any of them gets none of this light. Polygons may overlap: a pixel in two shadows
     * is no darker than in one.
     */
    class Light internal constructor() {
        var x: Int = 0
            private set
        var y: Int = 0
            private set
        var radius: Int = 0
            private set
        var color: Int = 0
            private set
        var intensity: Float = 0f
            private set

        /** The points of every shadow on this light, as x, y pairs; only [shadowEnd] of them count. */
        var shadowPoints = FloatArray(64)
            private set
        private var pointCount = 0
        private var ends = IntArray(8)

        /** How many shadows this light throws. */
        var shadowCount: Int = 0
            private set

        /** Where shadow [index]'s points end in [shadowPoints], one past its last y. */
        fun shadowEnd(index: Int): Int {
            require(index in 0 until shadowCount) { "Shadow $index of $shadowCount" }
            return ends[index]
        }

        /** Adds a point to the shadow being laid down; [closeShadow] ends it. */
        fun addShadowPoint(x: Float, y: Float) {
            if (pointCount + 2 > shadowPoints.size) shadowPoints =
                shadowPoints.copyOf(shadowPoints.size * 2)
            shadowPoints[pointCount++] = x
            shadowPoints[pointCount++] = y
        }

        /**
         * Whether the point ([x], [y]) lies in any of this light's shadows other than [except], the
         * one cast by whatever the point belongs to. Tested even-odd, which for a shadow that never
         * crosses itself is the same as non-zero.
         */
        fun inShadow(x: Float, y: Float, except: Int = -1): Boolean {
            var start = 0
            for (shadow in 0 until shadowCount) {
                val end = ends[shadow]
                if (shadow != except && encloses(start, end, x, y)) return true
                start = end
            }
            return false
        }

        private fun encloses(start: Int, end: Int, x: Float, y: Float): Boolean {
            var inside = false
            var previous = end - 2
            var point = start
            while (point < end) {
                val ax = shadowPoints[previous]
                val ay = shadowPoints[previous + 1]
                val bx = shadowPoints[point]
                val by = shadowPoints[point + 1]
                if ((ay > y) != (by > y) && x < ax + (y - ay) / (by - ay) * (bx - ax)) inside =
                    !inside
                previous = point
                point += 2
            }
            return inside
        }

        /** Ends the shadow whose points were added since the last one ended. */
        fun closeShadow() {
            val start = if (shadowCount == 0) 0 else ends[shadowCount - 1]
            // Fewer than three points enclose nothing, and are dropped rather than handed on.
            if (pointCount - start < 6) {
                pointCount = start
                return
            }
            if (shadowCount == ends.size) ends = ends.copyOf(ends.size * 2)
            ends[shadowCount++] = pointCount
        }

        internal fun reset(x: Int, y: Int, radius: Int, color: Int, intensity: Float) {
            this.x = x
            this.y = y
            this.radius = radius
            this.color = color
            this.intensity = intensity
            pointCount = 0
            shadowCount = 0
        }
    }

    companion object {
        /**
         * How many steps a light falls off in, from full to nothing. Banded rather
         * than smooth, like the sprites' shading and an aura's halo, because that
         * is how light looks in pixel art.
         */
        const val BANDS = 16

        /**
         * The rings a light of [radius] is drawn in, outermost and dimmest first, as pairs of a
         * ring's radius and its brightness, 0 to 255.
         *
         * A ring of radius r covers the pixels [Raster.oval] gives for the circle
         * in the 2r + 1 box centered on the light's pixel. Each ring is brighter
         * than the one outside it, so a pixel's brightness is its innermost ring's:
         * [falloff] at its distance, rounded to one of [BANDS] steps.
         */
        fun rings(radius: Int): IntArray {
            val rings = IntArray(2 * BANDS)
            var count = 0
            for (band in 1..BANDS) {
                // Up to here the light is nearer this band's brightness than the band below's.
                val reach = radius * reachOf((band - 0.5f) / BANDS)
                // The circle r + 1/2 pixels out from the light's center is the
                // nearest to that reach.
                val ringRadius = (reach - 0.5f).roundToInt()
                if (ringRadius < 0) continue
                rings[count++] = ringRadius
                rings[count++] = (255 * band + BANDS / 2) / BANDS
            }
            return rings.copyOf(count)
        }

        /**
         * How far a light shines at full strength, as a fraction of its radius, before it falls
         * off. A light fading from its very center leaves too little brightness for shadows to show
         * against; one that holds and then falls casts shadows that read across most of its reach.
         */
        const val FULL_REACH = 0.45f

        /**
         * A light's brightness [t] of the way out to its radius: 1 up to [FULL_REACH], 0 at the
         * edge, with a smoothstep between, so it neither ends in a rim nor dims in a visible step.
         */
        fun falloff(t: Float): Float {
            val s = ((t - FULL_REACH) / (1f - FULL_REACH)).coerceIn(0f, 1f)
            return 1f - s * s * (3f - 2f * s)
        }

        /** Where [falloff] reaches [brightness], found by bisection since it only decreases. */
        private fun reachOf(brightness: Float): Float {
            var near = 0f
            var far = 1f
            repeat(24) {
                val middle = (near + far) / 2f
                if (falloff(middle) >= brightness) near = middle else far = middle
            }
            return near
        }
    }
}
