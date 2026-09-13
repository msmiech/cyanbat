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
 * Draws the charge an [AuraComponent] is carrying: a halo that swells with [AuraComponent.intensity],
 * and sparks and lightning that are added a tier at a time.
 *
 * Add it **twice**, once per [Layer], with the entity's sprites drawn in between. The halo belongs
 * behind the entity, because a glow drawn over it would wash the artwork out at exactly the point
 * in a run where the player most needs to read their own position; the arcs belong in front,
 * because electricity that never crosses the silhouette reads as a decal stuck on the background.
 *
 * Nothing here is a particle system. Every spark and every bolt is a pure function of
 * [AuraComponent.phase] and its own index, hashed for the jitter, so an aura at full power costs
 * one float of state and allocates nothing per frame - which is what lets the two passes draw the
 * same effect from the same clock without either of them owning a pool.
 */
class AuraSystem(private val layer: Layer) : GameSystem() {

    /**
     * Which half of the effect this instance draws.
     *
     * [HALO] also owns the clock. An aura is meant to be added as both, and advancing the phase in
     * each pass would run it at double speed; advancing it in the pass that is always added first
     * is the cheapest way to say so.
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
            // Wrapped rather than left to run: the phase drives sines and modulo cycles, and a
            // float that has grown large enough loses the precision they are read at.
            aura.phase = (aura.phase + deltaTime) % PHASE_WRAP_SECONDS
            if (aura.surge > 0f) aura.surge = (aura.surge - deltaTime * SURGE_DECAY).coerceAtLeast(0f)
        }
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(transforms, auras) { id ->
            val aura = auras.require(id)
            if (aura.intensity <= 0f && aura.tier <= 0) return@forEach
            // A dead entity stops glowing, the way a dead one stops shooting and stops trailing.
            if (healths[id]?.alive == false) return@forEach

            val rect = transforms.require(id).rect
            val radiusX = rect.width * (HALO_BASE_X + HALO_GROWTH_X * aura.intensity) * swellOf(aura)
            val radiusY = rect.height * (HALO_BASE_Y + HALO_GROWTH_Y * aura.intensity) * swellOf(aura)

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
     * Outermost first, so each ring paints over the one before it and the alphas stack toward the
     * center instead of leaving a flat wash. The innermost ring is deliberately smaller than the
     * entity: it is the part that never shows on its own, and only exists to make the light appear
     * to come from inside the sprite rather than from a ring drawn around it.
     *
     * The alpha curve is what decides whether this reads as light or as a coin. A ring is a filled
     * oval with one alpha and therefore a hard edge, so the outermost ring's alpha is the edge of
     * the whole halo - at a flat ramp it stayed visible as a rim, and the effect looked like a
     * disc sitting behind the bat. Falling off as a power of the depth instead puts the outer
     * rings at an alpha that rounds to nothing, and the hard edge goes with them.
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
                EngineColors.lerp(AURA_AMBER, AURA_GOLD, inwards),
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
     * Embers rising through the halo, [SPARKS_PER_TIER] more of them for every tier reached.
     *
     * Each one climbs its own column on its own staggered cycle and is simply not drawn while it
     * is outside the halo's ellipse, which is what shapes the swarm to the glow without any of
     * them having to be steered around it.
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

            // Full brightness in the middle of the climb, nothing at either end, so sparks appear
            // and go out rather than blinking into and out of existence.
            val alpha = sin(life * PI.toFloat())
            val size = if (index % 3 == 0) 1 else 2
            graphics.drawRect(
                (centerX + x).roundToInt(),
                (centerY + y).roundToInt(),
                size,
                size,
                EngineColors.withAlpha(SPARK_COLOR, alpha),
            )
        }
    }

    /**
     * Lightning crawling over the halo: one arc per tier, each struck on its own cycle and gone
     * inside [BOLT_FLASH_SECONDS].
     *
     * A bolt is not stored anywhere. Its endpoints and every kink in it come out of a hash of
     * which slot struck it and which strike it is, so the shape holds still for the length of the
     * flash and is a different shape the next time round - without a single allocation, and
     * without the effect having any state to get out of step with the halo it is drawn over.
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

        // Struck well inside the halo rather than on its rim. An arc drawn at the full radius
        // floats clear of the sprite on a glow this wide, and electricity that never touches what
        // it is coming off reads as weather rather than as power.
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

            // Squared, so an arc holds near full brightness and then drops out rather than
            // dimming evenly across its flash - which is what a spark gap actually does, and what
            // stops the effect reading as a ribbon being faded in and out.
            val life = elapsed / (BOLT_FLASH_SECONDS * rate)
            val fade = 1f - life * life
            val color = EngineColors.withAlpha(BOLT_COLOR, fade)

            var fromX = centerX + cos(startAngle) * shellX
            var fromY = centerY + sin(startAngle) * shellY
            for (segment in 1..BOLT_SEGMENTS) {
                val t = segment / BOLT_SEGMENTS.toFloat()
                // Kinked off the arc rather than off a straight chord, so a bolt crawls over the
                // shell of the aura instead of cutting across the middle of it. Both the radius
                // and the angle are thrown off: radius alone gives a wave, and it takes the two
                // together to make a line look broken rather than bent.
                val last = segment == BOLT_SEGMENTS
                val skew = if (last) 0f
                else (hash01(seed + segment * 13) - 0.5f) * BOLT_ANGLE_JITTER
                val jitter = if (last) 1f
                else 1f + (hash01(seed + segment * 29) - 0.5f) * BOLT_JITTER
                val angle = startAngle + sweep * t + skew
                val toX = centerX + cos(angle) * shellX * jitter
                val toY = centerY + sin(angle) * shellY * jitter

                graphics.drawLine(
                    fromX.roundToInt(), fromY.roundToInt(), toX.roundToInt(), toY.roundToInt(), color
                )
                // A second line a pixel over, once the aura is fierce enough to deserve one. It is
                // the only thickness available: the Graphics API draws hairlines and nothing else.
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
            // Where it earths, which is the brightest part of a real arc and the part the eye
            // actually catches at this size.
            graphics.drawRect(
                (centerX + cos(endAngle) * shellX).roundToInt() - 1,
                (centerY + sin(endAngle) * shellY).roundToInt() - 1,
                3,
                3,
                EngineColors.withAlpha(SPARK_COLOR, fade),
            )
        }
    }

    private companion object {
        const val TAU = 6.2831855f

        /**
         * A deterministic 0..1 from an integer.
         *
         * Deterministic rather than random because that is the whole trick: a bolt has to look the
         * same for every frame of its flash, and re-deriving its shape from its index each frame is
         * what saves storing one. Integer overflow is part of the mix and is well defined in Kotlin.
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
         * Halo size against the entity's own box. The base is already wider than the sprite at
         * the faintest setting, because a glow that starts inside the silhouette is a glow nobody
         * ever sees begin.
         */
        const val HALO_BASE_X = 0.62f
        const val HALO_GROWTH_X = 0.34f
        const val HALO_BASE_Y = 0.70f
        const val HALO_GROWTH_Y = 0.38f

        /**
         * Rings from the outside in, and how far the innermost is drawn in from the outermost.
         *
         * Sixteen rather than the seven it started as. The count is what the gradient is made of -
         * at seven the steps between rings were plainly visible as bands - and rings are cheap:
         * the whole halo is sixteen filled ovals a frame, which is nothing next to the sprites.
         */
        const val HALO_RINGS = 16
        const val HALO_TAPER = 0.50f

        /**
         * The alpha of the innermost ring, and the power the rest fall off by; see [drawHalo].
         *
         * They stack, so the core ends up around half opaque while the rim is under a twentieth -
         * a glow the cave still reads through, with no edge on it. The peak is the dial to turn
         * if the bat ever starts disappearing inside its own light.
         */
        const val HALO_PEAK_ALPHA = 0.24f
        const val HALO_FALLOFF = 1.8f

        // Bright on purpose, and the reason is the blend rather than the hue. Half-opacity amber
        // over the dark blue of the cave averages to khaki - the first two passes both came out
        // brown - and the fix is not a warmer color but a lighter one, carried at enough alpha
        // that the middle of the halo settles near its own gold instead of halfway to the cave.
        val AURA_AMBER = 0xFFFFC83C.toInt()
        val AURA_GOLD = 0xFFFFFCE4.toInt()
        val SPARK_COLOR = 0xFFFFFBDC.toInt()
        val BOLT_COLOR = 0xFFFFFFF0.toInt()

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
         * How far round the halo an arc travels, as a fraction of a full turn - so between about
         * a twelfth and a fifth of the way round. Short: a bolt that crossed half the aura read as
         * a wire laid over it rather than as a discharge.
         */
        const val BOLT_MIN_SWEEP = 0.07f
        const val BOLT_SWEEP_RANGE = 0.13f

        /** The radius arcs are struck at, as a fraction of the halo's. */
        const val BOLT_SHELL = 0.74f

        /** How far a kink is thrown off the shell: outwards as a fraction of the radius, and
         *  sideways in radians along it. */
        const val BOLT_JITTER = 0.34f
        const val BOLT_ANGLE_JITTER = 0.30f

        /** From this tier on, arcs are drawn two pixels thick. */
        const val BOLT_THICKENS_AT_TIER = 3
    }
}
