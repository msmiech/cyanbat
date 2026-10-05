package at.smiech.cyanbat.ecs

import at.smiech.cyanbat.util.FROST_BEAM_CORE_COLOR
import at.smiech.cyanbat.util.FROST_BEAM_EDGE_COLOR
import at.smiech.cyanbat.util.FROST_BEAM_GLOW_COLOR
import at.smiech.cyanbat.util.FROST_BEAM_HALF_WIDTH
import at.smiech.cyanbat.util.FROST_BEAM_SHOW_SECONDS
import at.smiech.engine.EngineColors
import at.smiech.engine.Graphics
import at.smiech.engine.Input
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.Component
import at.smiech.engine.ecs.ComponentMapper
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.GameSystem
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A frost beam as it shows for a moment after it has fired: from where it left the bat to where it
 * left the frame. Drawn and reaped by [FrostBeamSystem]. It is light and nothing else - nothing to
 * collide with, and it has done its work by the time it is seen.
 */
class FrostBeamComponent(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) :
    Component {
    /** How long it has been showing, advanced by [FrostBeamSystem]. */
    var elapsed = 0f
}

/**
 * The bat's frost beam. Every [intervalSeconds] it picks an enemy on screen at random, fires from the
 * bat's middle through that enemy's on to the edge of the frame, and freezes every enemy the beam
 * passes through for [freezeSeconds]; see [FrostSystem].
 *
 * What it may freeze is [canFreeze]'s call - the run's, which knows a boss, a part of one and an elite
 * when it sees one. It never aims at anything else, and goes through anything else as if it were not
 * there. It prefers an enemy not frozen yet, since one that is gains little; only with nothing else to
 * aim at does it pick one again.
 *
 * Its charge holds while there is nothing to aim at, so a beam come round on an empty screen goes off
 * at the first enemy to arrive rather than waiting out another interval. It is paced on the tick, as
 * everything that paces the fight is: a paused run is a paused beam.
 *
 * Its update can go anywhere in the order; its draw belongs over the sprites, and in a stage flown in
 * the dark over the dark too, because a beam is light.
 *
 * @param origin the box the beam leaves from - the bat's - or null while it may not fire.
 * @param onFire called with every enemy a beam froze, the one it was aimed at first, and the beam.
 */
class FrostBeamSystem(
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val random: Random,
    private val origin: () -> Rect?,
    private val canFreeze: (EntityId) -> Boolean,
    private val onFire: (frozen: List<EntityId>, beam: FrostBeamComponent) -> Unit = { _, _ -> },
) : GameSystem() {
    private lateinit var transforms: ComponentMapper<TransformComponent>
    private lateinit var collisions: ComponentMapper<CollisionComponent>
    private lateinit var healths: ComponentMapper<HealthComponent>
    private lateinit var beams: ComponentMapper<FrostBeamComponent>

    /** Seconds between beams, or zero for a bat that has none. */
    var intervalSeconds = 0f

    /** How long a beam keeps what it catches frozen. */
    var freezeSeconds = 0f

    /** Time banked toward the next beam, held at [intervalSeconds] while there is nothing to aim at. */
    private var charge = 0f

    override fun onAttach(world: World) {
        transforms = world.mapper(TransformComponent::class)
        collisions = world.mapper(CollisionComponent::class)
        healths = world.mapper(HealthComponent::class)
        beams = world.mapper(FrostBeamComponent::class)
    }

    override fun update(world: World, deltaTime: Float, input: Input?) {
        world.forEach(beams) { id ->
            val beam = beams.require(id)
            beam.elapsed += deltaTime
            if (beam.elapsed >= FROST_BEAM_SHOW_SECONDS) world.removeEntity(id)
        }

        if (intervalSeconds <= 0f) return
        charge = (charge + deltaTime).coerceAtMost(intervalSeconds)
        if (charge < intervalSeconds) return
        val bat = origin() ?: return

        val reachable = freezable(world)
        val target = pickTarget(world, reachable) ?: return
        charge = 0f

        val aim = transforms.require(target).rect
        val fromX = bat.centerX
        val fromY = bat.centerY
        val (toX, toY) = rayToEdge(fromX, fromY, aim.centerX, aim.centerY, frameWidth, frameHeight)

        val frozen = mutableListOf(target)
        for (id in reachable) {
            if (id == target) continue
            val rect = transforms.require(id).rect.inflate(FROST_BEAM_HALF_WIDTH)
            if (clipSegment(fromX, fromY, toX, toY, rect) != null) frozen += id
        }
        for (id in frozen) FrostSystem.freeze(world, id, freezeSeconds)

        // Shown from where it leaves the bat, so the bat is not drawn over by its own beam.
        val leaves = clipSegment(fromX, fromY, toX, toY, bat)?.endInclusive ?: 0f
        val beam = FrostBeamComponent(
            fromX + (toX - fromX) * leaves,
            fromY + (toY - fromY) * leaves,
            toX,
            toY,
        )
        world.addComponent(world.createEntity(), beam)
        onFire(frozen, beam)
    }

    /** Every living enemy [canFreeze] allows with any of it inside the frame. */
    private fun freezable(world: World): List<EntityId> {
        val found = ArrayList<EntityId>()
        world.forEach(transforms, collisions) { id ->
            if (collisions.require(id).group != CollisionGroup.ENEMY) return@forEach
            if (healths[id]?.alive == false) return@forEach
            val rect = transforms.require(id).rect
            val inFrame =
                rect.right > 0f && rect.left < frameWidth && rect.bottom > 0f && rect.top < frameHeight
            if (inFrame && canFreeze(id)) found += id
        }
        return found
    }

    /**
     * One of [reachable] that is plainly on screen - its middle inside the frame - at random, and one
     * not frozen yet if there is one.
     */
    private fun pickTarget(world: World, reachable: List<EntityId>): EntityId? {
        val onScreen = reachable.filter { id ->
            val rect = transforms.require(id).rect
            rect.centerX in 0f..frameWidth.toFloat() && rect.centerY in 0f..frameHeight.toFloat()
        }
        val fresh = onScreen.filter { !FrostSystem.isFrozen(world, it) }
        return fresh.randomOrNull(random) ?: onScreen.randomOrNull(random)
    }

    override fun draw(world: World, graphics: Graphics) {
        world.forEach(beams) { id ->
            val beam = beams.require(id)
            val strength = 1f - (beam.elapsed / FROST_BEAM_SHOW_SECONDS).coerceIn(0f, 1f)
            if (strength <= 0f) return@forEach
            drawBeam(graphics, beam, strength)
        }
    }

    /**
     * A white core with a glow either side of it and a deep blue edge outside that: five lines a pixel
     * apart, across the beam's run, so on the grid they lie side by side and none goes over another.
     */
    private fun drawBeam(graphics: Graphics, beam: FrostBeamComponent, strength: Float) {
        val x0 = beam.fromX.roundToInt()
        val y0 = beam.fromY.roundToInt()
        val x1 = beam.toX.roundToInt()
        val y1 = beam.toY.roundToInt()
        // A steep beam is a pixel a row, so its lines are laid side by side across; a shallow one is
        // a pixel a column, and laid one over the other.
        val steep = abs(y1 - y0) > abs(x1 - x0)
        for (band in BANDS) {
            val dx = if (steep) band.offset else 0
            val dy = if (steep) 0 else band.offset
            graphics.drawLine(
                x0 + dx, y0 + dy, x1 + dx, y1 + dy,
                EngineColors.scaleAlpha(band.color, band.alpha * strength),
            )
        }
    }

    private class Band(val offset: Int, val color: Int, val alpha: Float)

    companion object {
        private val BANDS = listOf(
            Band(-2, FROST_BEAM_EDGE_COLOR, 0.7f),
            Band(-1, FROST_BEAM_GLOW_COLOR, 0.9f),
            Band(0, FROST_BEAM_CORE_COLOR, 1f),
            Band(1, FROST_BEAM_GLOW_COLOR, 0.9f),
            Band(2, FROST_BEAM_EDGE_COLOR, 0.7f),
        )

        /**
         * Where a ray from ([fromX], [fromY]) through ([throughX], [throughY]) leaves a frame [width]
         * by [height]: never short of the point it was aimed through, however that sits against the
         * frame.
         */
        fun rayToEdge(
            fromX: Float,
            fromY: Float,
            throughX: Float,
            throughY: Float,
            width: Int,
            height: Int,
        ): Pair<Float, Float> {
            val dx = throughX - fromX
            val dy = throughY - fromY
            val alongX = when {
                dx > 0f -> (width - fromX) / dx
                dx < 0f -> -fromX / dx
                else -> Float.POSITIVE_INFINITY
            }
            val alongY = when {
                dy > 0f -> (height - fromY) / dy
                dy < 0f -> -fromY / dy
                else -> Float.POSITIVE_INFINITY
            }
            val along =
                minOf(alongX, alongY).let { if (it.isFinite()) it.coerceAtLeast(1f) else 1f }
            return (fromX + dx * along) to (fromY + dy * along)
        }

        /**
         * The stretch of the segment from ([x0], [y0]) to ([x1], [y1]) inside [box], as how far along
         * the segment it goes in and comes out, 0..1 - or null when the segment misses the box.
         * Liang and Barsky's clip, one edge of the box at a time.
         */
        fun clipSegment(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            box: Rect
        ): ClosedFloatingPointRange<Float>? {
            val dx = x1 - x0
            val dy = y1 - y0
            var enter = 0f
            var leave = 1f
            // Each edge as p * t <= q: the segment is outside it where that fails.
            val edges = floatArrayOf(
                -dx, x0 - box.left,
                dx, box.right - x0,
                -dy, y0 - box.top,
                dy, box.bottom - y0,
            )
            for (i in edges.indices step 2) {
                val p = edges[i]
                val q = edges[i + 1]
                if (p == 0f) {
                    if (q < 0f) return null
                    continue
                }
                val t = q / p
                if (p < 0f) enter = maxOf(enter, t) else leave = minOf(leave, t)
                if (enter > leave) return null
            }
            return enter..leave
        }
    }
}
