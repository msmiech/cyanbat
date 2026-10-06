package at.smiech.engine.ecs

import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the charge an [AuraComponent] carries, in its [AuraComponent.colors]: a halo that swells
 * with [AuraComponent.intensity], and sparks and lightning added a tier at a time.
 *
 * Add it twice, once per [Layer], with the entity's sprites drawn in between. The halo goes behind
 * the entity, since a glow over it would wash out the artwork when the player most needs to see it;
 * the arcs go in front, since electricity that never crosses the silhouette looks like a decal.
 *
 * Not a particle system: every spark and bolt is a pure function of [AuraComponent.phase] and its
 * own index, hashed for jitter, so an aura costs one float of state and allocates nothing per
 * frame, and both passes draw the same effect from the same clock.
 */
class AuraSystem(private val layer: Layer) : GameSystem() {

    /**
     * Which half of the effect this instance draws.
     *
     * [HALO] also advances the clock, since an aura is added as both and advancing it in each pass
     * would run it at double speed.
     */
    enum class Layer { HALO, ARCS }

    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var auras: ComponentMapper<AuraComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        auras = world.mapper(AuraComponent::class)
        healths = world.mapper(HealthComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        if (layer != Layer.HALO) return
        world.forEach(auras) { id ->
            val aura = auras.require(id)
            // Wrapped, because the phase drives sines and modulo cycles, and a large float loses
            // the precision they need.
            aura.phase = (aura.phase + deltaTime) % PHASE_WRAP_SECONDS
            if (aura.surge > 0f) aura.surge =
                (aura.surge - deltaTime * SURGE_DECAY).coerceAtLeast(0f)
        }
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, auras) { id ->
            val aura = auras.require(id)
            if (aura.intensity <= 0f && aura.tier <= 0) return@forEach
            // A dead entity stops glowing, as it stops shooting and trailing.
            if (healths[id]?.alive == false) return@forEach

            val rect = transforms.require(id).rect
            val radiusX =
                rect.width * (HALO_BASE_X + HALO_GROWTH_X * aura.intensity) * swellOf(aura)
            val radiusY =
                rect.height * (HALO_BASE_Y + HALO_GROWTH_Y * aura.intensity) * swellOf(aura)

            when (layer) {
                Layer.HALO -> drawHalo(graphics, aura, rect.centerX, rect.centerY, radiusX, radiusY)
                Layer.ARCS -> {
                    drawSparks(graphics, aura, rect.centerX, rect.centerY, radiusX, radiusY)
                    drawBolts(graphics, aura, rect.centerX, rect.centerY, radiusX, radiusY)
                }
            }
        }
    }

    /** The aura breathing, plus whatever is left of the flare from the last tier crossed. */
    private fun swellOf(aura: AuraComponent): Float =
        1f + PULSE_DEPTH * sin(aura.phase * PULSE_SPEED) + aura.surge * SURGE_SWELL

    /**
     * Concentric ovals from the outside in, faint and wide to bright and tight.
     *
     * Outermost first, so the alphas stack toward the center instead of leaving a flat wash. The
     * innermost ring is smaller than the entity, so the light seems to come from inside the sprite.
     *
     * Each ring is a filled oval with one alpha and so a hard edge; with a linear ramp the
     * outermost ring showed as a rim, and the halo looked like a disc. Falling off as a power of
     * the depth puts the outer rings at an alpha that rounds to nothing, and the edge disappears.
     */
    private fun drawHalo(
        graphics: Graphics,
        aura: AuraComponent,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ) {
        for (ring in 0 until HALO_RINGS) {
            val inwards = ring / (HALO_RINGS - 1f)
            val scale = 1f - inwards * HALO_TAPER
            val alpha = HALO_PEAK_ALPHA * inwards.pow(HALO_FALLOFF) *
                    aura.intensity * (1f + aura.surge)
            val color = EngineColors.withAlpha(
                EngineColors.lerp(aura.colors.rim, aura.colors.core, inwards),
                alpha,
            )

            val width = (radiusX * scale * 2f).roundToInt()
            val height = (radiusY * scale * 2f).roundToInt()
            if (width <= 0 || height <= 0) continue
            graphics.drawOval(
                (centerX - width / 2f).roundToInt(),
                (centerY - height / 2f).roundToInt(),
                width,
                height,
                color,
            )
        }
    }

    /**
     * Embers rising through the halo, [SPARKS_PER_TIER] more for every tier reached.
     *
     * Each climbs its own column on a staggered cycle and is skipped while outside the halo's
     * ellipse, which shapes the swarm to the glow without steering any of them.
     */
    private fun drawSparks(
        graphics: Graphics,
        aura: AuraComponent,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ) {
        val count = (aura.tier * SPARKS_PER_TIER).coerceAtMost(MAX_SPARKS)
        for (index in 0 until count) {
            val life = (aura.phase * SPARK_SPEED + hash01(index * 7)) % 1f
            val offsetX = (hash01(index * 31 + 5) * 2f - 1f) * radiusX
            val offsetY = radiusY - life * 2f * radiusY
            val drift = sin(aura.phase * SPARK_WOBBLE_SPEED + index) * SPARK_WOBBLE

            val x = offsetX + drift
            val y = offsetY
            if ((x / radiusX) * (x / radiusX) + (y / radiusY) * (y / radiusY) > 1f) continue

            // Brightest mid-climb and dark at either end, so sparks fade in and out rather than
            // blinking.
            val alpha = sin(life * PI.toFloat())
            val size = if (index % 3 == 0) 1 else 2
            graphics.drawRect(
                (centerX + x).roundToInt(),
                (centerY + y).roundToInt(),
                size,
                size,
                EngineColors.withAlpha(aura.colors.spark, alpha),
            )
        }
    }

    /**
     * Lightning crawling over the halo: one arc per tier, each struck on its own cycle and gone
     * within [BOLT_FLASH_SECONDS].
     *
     * A bolt is not stored: its endpoints and kinks come from a hash of its slot and strike number,
     * so the shape holds for the flash and differs on the next strike, with no allocation and no
     * state to drift out of step with the halo.
     */
    private fun drawBolts(
        graphics: Graphics,
        aura: AuraComponent,
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ) {
        val count = aura.tier.coerceAtMost(MAX_BOLTS)
        if (count <= 0) return

        // Struck well inside the halo rather than on its rim: at the full radius an arc floats
        // clear of the sprite and no longer looks like it comes from it.
        val shellX = radiusX * BOLT_SHELL
        val shellY = radiusY * BOLT_SHELL

        val rate = BOLT_RATE * (1f + aura.tier * BOLT_RATE_PER_TIER)
        for (slot in 0 until count) {
            val clock = aura.phase * rate + slot * BOLT_STAGGER
            val strike = clock.toInt()
            val elapsed = clock - strike
            if (elapsed > BOLT_FLASH_SECONDS * rate) continue

            val seed = slot * 1009 + strike * 7919
            val startAngle = hash01(seed) * TAU
            val sweep = (BOLT_MIN_SWEEP + hash01(seed + 1) * BOLT_SWEEP_RANGE) * TAU
            val endAngle = startAngle + sweep

            // Squared, so an arc holds near full brightness and then drops out, as a real spark
            // does, rather than dimming evenly like a ribbon being faded.
            val life = elapsed / (BOLT_FLASH_SECONDS * rate)
            val fade = 1f - life * life
            val color = EngineColors.withAlpha(aura.colors.bolt, fade)

            var fromX = centerX + cos(startAngle) * shellX
            var fromY = centerY + sin(startAngle) * shellY
            for (segment in 1..BOLT_SEGMENTS) {
                val t = segment / BOLT_SEGMENTS.toFloat()
                // Kinked off the arc rather than a straight chord, so a bolt crawls over the aura's
                // shell instead of cutting across it. Both radius and angle are jittered: radius
                // alone gives a wave, and it takes both to make a line look broken.
                val last = segment == BOLT_SEGMENTS
                val skew = if (last) 0f
                else (hash01(seed + segment * 13) - 0.5f) * BOLT_ANGLE_JITTER
                val jitter = if (last) 1f
                else 1f + (hash01(seed + segment * 29) - 0.5f) * BOLT_JITTER
                val angle = startAngle + sweep * t + skew
                val toX = centerX + cos(angle) * shellX * jitter
                val toY = centerY + sin(angle) * shellY * jitter

                graphics.drawLine(
                    fromX.roundToInt(),
                    fromY.roundToInt(),
                    toX.roundToInt(),
                    toY.roundToInt(),
                    color
                )
                // A second line a pixel over at high tiers, since Graphics only draws hairlines.
                if (aura.tier >= BOLT_THICKENS_AT_TIER) {
                    graphics.drawLine(
                        fromX.roundToInt(), fromY.roundToInt() + 1,
                        toX.roundToInt(), toY.roundToInt() + 1,
                        color,
                    )
                }
                fromX = toX
                fromY = toY
            }
            // Where it earths: the brightest part of a real arc, and what the eye catches at this
            // size.
            graphics.drawRect(
                (centerX + cos(endAngle) * shellX).roundToInt() - 1,
                (centerY + sin(endAngle) * shellY).roundToInt() - 1,
                3,
                3,
                EngineColors.withAlpha(aura.colors.spark, fade),
            )
        }
    }

    private companion object {
        const val TAU = 6.2831855f

        /**
         * A deterministic 0..1 from an integer, so a bolt looks the same on every
         * frame of its flash without being stored. Integer overflow is part of the
         * mix and well defined in Kotlin.
         */
        fun hash01(n: Int): Float {
            var h = n * 0x27d4eb2d
            h = h xor (h ushr 15)
            h *= 0x165667b1
            h = h xor (h ushr 13)
            return (h ushr 8) * (1f / (1 shl 24))
        }

        /** How long the phase runs before wrapping. Long enough that the wrap is never seen. */
        const val PHASE_WRAP_SECONDS = 3600f

        /** The halo breathing, as a fraction of its radius and in radians per second. */
        const val PULSE_DEPTH = 0.06f
        const val PULSE_SPEED = 3.4f

        /** The flare on crossing a tier: how far it pushes the halo out, and how fast it dies. */
        const val SURGE_SWELL = 0.35f
        const val SURGE_DECAY = 1.4f

        /**
         * Halo size relative to the entity's box. Even the base is wider than the sprite, so the
         * faintest glow is visible.
         */
        const val HALO_BASE_X = 0.62f
        const val HALO_GROWTH_X = 0.34f
        const val HALO_BASE_Y = 0.70f
        const val HALO_GROWTH_Y = 0.38f

        /**
         * Rings from the outside in, and how far the innermost is drawn in from the outermost.
         * Enough rings that no banding shows; sixteen filled ovals a frame are cheap.
         */
        const val HALO_RINGS = 16
        const val HALO_TAPER = 0.50f

        /**
         * The alpha of the innermost ring, and the power the rest fall off by; see [drawHalo].
         *
         * They stack, so the core is about half opaque and the rim under a twentieth. Lower the
         * peak if the bat starts disappearing inside its own light.
         */
        const val HALO_PEAK_ALPHA = 0.24f
        const val HALO_FALLOFF = 1.8f

        /** Sparks added per tier, and the ceiling on them. */
        const val SPARKS_PER_TIER = 7
        const val MAX_SPARKS = 48
        const val SPARK_SPEED = 0.75f
        const val SPARK_WOBBLE = 2.2f
        const val SPARK_WOBBLE_SPEED = 5.0f

        /** Bolts: one per tier, up to a ceiling that keeps the bat visible inside its own aura. */
        const val MAX_BOLTS = 6
        const val BOLT_SEGMENTS = 5
        const val BOLT_FLASH_SECONDS = 0.11f
        const val BOLT_RATE = 1.6f
        const val BOLT_RATE_PER_TIER = 0.35f

        /** Spread across the cycle so the arcs do not all strike on the same beat. */
        const val BOLT_STAGGER = 0.3719f

        /**
         * How far round the halo an arc travels, as a fraction of a turn: a twelfth to a fifth.
         * Longer bolts looked like wires laid over the aura.
         */
        const val BOLT_MIN_SWEEP = 0.07f
        const val BOLT_SWEEP_RANGE = 0.13f

        /** The radius arcs are struck at, as a fraction of the halo's. */
        const val BOLT_SHELL = 0.74f

        /**
         * How far a kink is thrown off the shell: outward as a fraction of the radius, and sideways
         * in radians along it.
         */
        const val BOLT_JITTER = 0.34f
        const val BOLT_ANGLE_JITTER = 0.30f

        /** From this tier on, arcs are drawn two pixels thick. */
        const val BOLT_THICKENS_AT_TIER = 3
    }
}
