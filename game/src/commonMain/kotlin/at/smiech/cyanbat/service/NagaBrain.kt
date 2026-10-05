package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.util.NAGA_COIL_SECONDS
import at.smiech.cyanbat.util.NAGA_ENRAGED_COIL_SECONDS
import at.smiech.cyanbat.util.NAGA_ENRAGED_SPIT_SECONDS
import at.smiech.cyanbat.util.NAGA_ENRAGED_SUBMERGED_SECONDS
import at.smiech.cyanbat.util.NAGA_ENRAGED_SWIM_SPEED
import at.smiech.cyanbat.util.NAGA_FRAME
import at.smiech.cyanbat.util.NAGA_PHASE_2_AT
import at.smiech.cyanbat.util.NAGA_PHASE_3_AT
import at.smiech.cyanbat.util.NAGA_PHASE_4_AT
import at.smiech.cyanbat.util.NAGA_PHASE_5_AT
import at.smiech.cyanbat.util.NAGA_REAR_SECONDS
import at.smiech.cyanbat.util.NAGA_RECOIL_SECONDS
import at.smiech.cyanbat.util.NAGA_RING_DAMAGE
import at.smiech.cyanbat.util.NAGA_RISE_SECONDS
import at.smiech.cyanbat.util.NAGA_SHIELD_FRACTION
import at.smiech.cyanbat.util.NAGA_SINK_SECONDS
import at.smiech.cyanbat.util.NAGA_SPACING
import at.smiech.cyanbat.util.NAGA_SPACING_LEAST
import at.smiech.cyanbat.util.NAGA_SPACING_MOST
import at.smiech.cyanbat.util.NAGA_SPIT_DAMAGE
import at.smiech.cyanbat.util.NAGA_SPIT_SECONDS
import at.smiech.cyanbat.util.NAGA_STRIKE_REACH
import at.smiech.cyanbat.util.NAGA_STRIKE_SPEED
import at.smiech.cyanbat.util.NAGA_SUBMERGED_SECONDS
import at.smiech.cyanbat.util.NAGA_SWIM_SPEED
import at.smiech.cyanbat.util.NAGA_TELL_SECONDS
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Naga's fight: a hooded serpent that rears up out of the water, sways and spits, strikes, and
 * dives to come up somewhere else - over five phases marked by its health, a fifth of it apiece, the
 * longest fight in the game.
 *
 * 1. **Rearing.** The water boils where it is about to come up - the tell - and it rises there, the
 *    body running from the water up to its swaying head, spits fans of three at the bat, and goes
 *    back under. Its first rise is a warning, well ahead of the bat.
 * 2. **The strike.** From four fifths it also strikes: it draws its head back - the warning - then
 *    lunges straight at where the bat was, as far as its body reaches, draws back, and throws a ring
 *    of spit from where it struck.
 * 3. **The brood.** From three fifths it calls its kraits up out of the sea every time it rises.
 * 4. **The tide.** From two fifths its hood raises a shield, it strikes twice a rise, and between rises
 *    it swims across the frame low over the water, its body following its head in humps that break
 *    the surface and dip back under, spitting as it goes.
 * 5. **Enraged.** From one fifth its hood raises the shield again; it stays under for less time,
 *    strikes three times a rise and spits faster, swims faster, and calls up a school of piranhas
 *    with its brood.
 *
 * Nothing about it is a movement of the engine's: this places the head every tick and lays the body
 * from it, as the Sand Wyrm's brain lays its plates. Reared, the body runs from where it came up out
 * of the water to the head, along a curve that rises out of the sea leaning away from the bat and
 * comes forward under the hood - the cobra's S - its parts spaced evenly along it, stretching and
 * gathering a little as the head sways and strikes. Swimming, the body follows the path the head
 * has swum, a fixed distance apart. It changes from one to the other only under the water, where the
 * change cannot be seen. Every part is given the velocity it moved by, so anything forecasting by
 * velocities - the recorder's autopilot - sees it going the way it is going.
 *
 * Phases are read off its health, and a hit that crosses more than one plays each entrance in order.
 *
 * @param parts the body, from the neck back to the tail. They are the brain's to move: nothing else
 *   moves, culls or kills them, which is what makes it safe to hold their ids for the whole fight.
 * @param random where it comes up, within the room it has.
 * @param onSummon called with how many kraits to call up, and whether a school of piranhas comes with
 *   them; the generator is what knows how.
 * @param onPhaseChanged called with the phase it has just entered, 2 to 5, for the screen to announce.
 */
class NagaBrain(
    private val world: World,
    private val factory: EntityFactory,
    private val sheet: Pixmap,
    private val headId: EntityId,
    private val parts: List<EntityId>,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val random: Random = Random.Default,
    private val onSummon: (kraits: Int, school: Boolean) -> Unit = { _, _ -> },
    private val onPhaseChanged: (Int) -> Unit = {},
) : BossBrain {
    var phase = 1
        private set

    /** What it is doing; see [Act]. */
    var act = Act.SUBMERGED
        private set

    /** Where it will come up next, as the x where its body leaves the water, once the tell has begun. */
    var riseX = Float.NaN
        private set

    /** Whether its body is following its head's path, swimming, rather than rearing up out of the water. */
    var swimming = false
        private set

    /** The head's center, which everything else is laid from. */
    var headX = frameWidth * 0.7f
        private set
    var headY = frameHeight + BURIED
        private set

    private var actTime = 0f
    private var clock = 0f
    private var untilSplash = 0f
    private var untilRise = ENTRANCE_SECONDS
    private var rises = 0
    private var nextIsSwim = false

    // Where the body leaves the water while it rears, and the station its head sways about.
    private var baseX = headX
    private var stationX = headX
    private var stationY = REAR_HIGHEST

    // A move from one point to another over the act's time: rising, sinking, coiling, recoiling.
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f

    private var strikesLeft = 0
    private var struck = 0f
    private var untilSpit = 0f
    private var rearFor = 0f

    private var swimStartX = 0f
    private var untilVolley = 0f
    private val queued = ArrayDeque<Volley>()

    // The path the head has swum, newest last, as a ring of points at least a pixel apart; only
    // what the body needs is kept.
    private val pathX = FloatArray(PATH_POINTS)
    private val pathY = FloatArray(PATH_POINTS)
    private var newest = -1
    private var points = 0

    // The rearing body's curve, sampled, and how far along it each sample is.
    private val curveX = FloatArray(CURVE_SAMPLES + 1)
    private val curveY = FloatArray(CURVE_SAMPLES + 1)
    private val curveAt = FloatArray(CURVE_SAMPLES + 1)

    init {
        submerge(headX)
        place(headId, headX, headY, rotation = 0f)
        layBody()
    }

    override fun update(deltaTime: Float) {
        val health = world.getComponent(headId, HealthComponent::class) ?: return
        if (!health.alive) return

        val due = phaseFor(health.fraction)
        while (phase < due) enter(phase + 1, health)

        clock += deltaTime
        actTime += deltaTime
        val lastX = headX
        val lastY = headY
        when (act) {
            Act.SUBMERGED -> waitUnder(deltaTime)
            Act.RISING -> rise()
            Act.REARED -> rear(deltaTime)
            Act.COIL -> coil()
            Act.STRIKE -> strike()
            Act.RECOIL -> recoil()
            Act.SINKING -> sink()
            Act.SWIM -> swim(deltaTime)
        }
        place(headId, headX, headY, headRotation(lastX, lastY))
        if (swimming) record(headX, headY)
        layBody()
        fireQueued(deltaTime)
    }

    // --- the round -----------------------------------------------------------------------------------

    private fun waitUnder(deltaTime: Float) {
        untilRise -= deltaTime
        // The tell starts here, which is also when it picks its spot: as late as it can, so a rise is
        // placed against where the bat is rather than where it was a second ago.
        if (riseX.isNaN() && untilRise <= NAGA_TELL_SECONDS) {
            riseX = if (nextIsSwim) frameWidth - SWIM_TELL_INSET else chooseRise()
            untilSplash = 0f
        }
        if (!riseX.isNaN()) {
            untilSplash -= deltaTime
            if (untilSplash <= 0f) {
                untilSplash += SPLASH_GAP_SECONDS
                factory.createSplash(riseX, frameHeight.toFloat(), sheet)
            }
        }
        if (untilRise > 0f) return
        if (nextIsSwim) startSwim() else startRise()
    }

    private fun startRise() {
        rises++
        swimming = false
        baseX = riseX
        riseX = Float.NaN
        stationX = baseX - STATION_LEAD
        stationY = (batCenter()?.second ?: (frameHeight * 0.4f)).coerceIn(REAR_HIGHEST, REAR_LOWEST)
        moveFrom(stationX, frameHeight + BURIED, stationX, stationY)
        headX = fromX
        headY = fromY
        step(Act.RISING)
        factory.createSplash(baseX, frameHeight.toFloat(), sheet, scale = RISE_SPLASH_SCALE)
        strikesLeft = strikesFor(phase)
        rearFor = if (phase == 1) NAGA_REAR_SECONDS else FIRST_STRIKE_AFTER_SECONDS
        untilSpit = FIRST_SPIT_SECONDS
        if (phase >= 3) onSummon(if (phase >= 5) 3 else 2, phase >= 5 && rises % 2 == 0)
    }

    private fun rise() {
        val t = eased(actTime / NAGA_RISE_SECONDS)
        headX = fromX + (toX - fromX) * t
        headY = fromY + (toY - fromY) * t
        if (actTime >= NAGA_RISE_SECONDS) step(Act.REARED)
    }

    private fun rear(deltaTime: Float) {
        sway()
        untilSpit -= deltaTime
        if (untilSpit <= 0f) {
            untilSpit += spitSeconds()
            queue(spitFor(phase))
        }
        if (actTime < rearFor) return
        val bat = batCenter()
        // It strikes only at a bat in front of it: its hood faces the way it rose to face.
        if (strikesLeft > 0 && bat != null && bat.first < headX - STRIKE_LEAST_LEAD) {
            strikesLeft--
            val back = COIL_BACK
            moveFrom(headX, headY, headX + back, headY - back * 0.4f)
            step(Act.COIL)
        } else {
            moveFrom(headX, headY, headX, frameHeight + BURIED)
            step(Act.SINKING)
        }
    }

    private fun coil() {
        val time = coilSeconds()
        val t = eased(actTime / time)
        // A shiver on the way back, so the warning reads as tension and not a drift.
        val shiver = sin(clock * SHIVER_RATE) * SHIVER * t
        headX = fromX + (toX - fromX) * t + shiver
        headY = fromY + (toY - fromY) * t
        if (actTime < time) return
        // Aimed once, as it lets go, and not steered after: a strike can always be flown out of.
        val bat = batCenter() ?: (headX - STRIKE_REACH_FALLBACK to headY)
        var targetX = bat.first
        var targetY = bat.second
        val dx = targetX - baseX
        val dy = targetY - frameHeight
        val reach = hypot(dx, dy)
        if (reach > NAGA_STRIKE_REACH) {
            targetX = baseX + dx / reach * NAGA_STRIKE_REACH
            targetY = frameHeight + dy / reach * NAGA_STRIKE_REACH
        }
        moveFrom(headX, headY, targetX, targetY.coerceAtLeast(STRIKE_HIGHEST))
        struck = 0f
        step(Act.STRIKE)
    }

    private fun strike() {
        val dx = toX - fromX
        val dy = toY - fromY
        val length = hypot(dx, dy).coerceAtLeast(1f)
        struck = (struck + NAGA_STRIKE_SPEED).coerceAtMost(length)
        headX = fromX + dx / length * struck
        headY = fromY + dy / length * struck
        if (struck < length) return
        // A ring of spit from where it struck, as it draws back.
        if (phase >= 2) queue(ringFor(phase))
        moveFrom(headX, headY, stationX, stationY)
        step(Act.RECOIL)
    }

    private fun recoil() {
        val t = eased(actTime / NAGA_RECOIL_SECONDS)
        headX = fromX + (toX - fromX) * t
        headY = fromY + (toY - fromY) * t
        if (actTime < NAGA_RECOIL_SECONDS) return
        rearFor = if (strikesLeft > 0) BETWEEN_STRIKES_SECONDS else LAST_REAR_SECONDS
        untilSpit = untilSpit.coerceAtLeast(SPIT_AFTER_STRIKE_SECONDS)
        step(Act.REARED)
    }

    private fun sink() {
        val t = eased(actTime / NAGA_SINK_SECONDS)
        headX = fromX + (toX - fromX) * t
        headY = fromY + (toY - fromY) * t
        if (actTime >= NAGA_SINK_SECONDS && allUnder()) goUnder()
    }

    private fun startSwim() {
        rises++
        riseX = Float.NaN
        swimStartX = frameWidth + SWIM_ENTRY
        headX = swimStartX
        headY = SWIM_Y
        submergedPath(headX, headY, trailingRight = true)
        swimming = true
        untilSpit = FIRST_SPIT_SECONDS
        step(Act.SWIM)
        factory.createSplash(frameWidth - SWIM_TELL_INSET, frameHeight.toFloat(), sheet, scale = RISE_SPLASH_SCALE)
        if (phase >= 5) onSummon(2, false)
    }

    private fun swim(deltaTime: Float) {
        headX -= swimSpeed()
        val along = (swimStartX - headX) / SWIM_WAVELENGTH
        headY = SWIM_Y + sin(along * TWO_PI) * SWIM_AMPLITUDE
        if (headX < frameWidth - NAGA_FRAME && headX > NAGA_FRAME) {
            untilSpit -= deltaTime
            if (untilSpit <= 0f) {
                untilSpit += SWIM_SPIT_SECONDS
                queue(spitFor(phase))
            }
        }
        // Gone once the tail has followed it out past the left edge.
        if (headX < -NAGA_FRAME - NAGA_SPACING * (parts.size + 1)) goUnder()
    }

    private fun goUnder() {
        submerge(headX.coerceIn(0f, frameWidth.toFloat()))
        step(Act.SUBMERGED)
        untilRise = if (phase >= 5) NAGA_ENRAGED_SUBMERGED_SECONDS else NAGA_SUBMERGED_SECONDS
        // From the fourth phase it swims across between rises.
        nextIsSwim = phase >= 4 && !nextIsSwim
    }

    // --- phases ------------------------------------------------------------------------------------

    private fun enter(next: Int, health: HealthComponent) {
        phase = next
        if (next >= 4) {
            world.getComponent(headId, ShieldComponent::class)
                ?.raise((health.maxHitPoints * NAGA_SHIELD_FRACTION).roundToInt().coerceAtLeast(1))
        }
        onPhaseChanged(next)
    }

    private fun spitSeconds(): Float = if (phase >= 5) NAGA_ENRAGED_SPIT_SECONDS else NAGA_SPIT_SECONDS
    private fun coilSeconds(): Float = if (phase >= 5) NAGA_ENRAGED_COIL_SECONDS else NAGA_COIL_SECONDS
    private fun swimSpeed(): Float = if (phase >= 5) NAGA_ENRAGED_SWIM_SPEED else NAGA_SWIM_SPEED

    // --- placing it ----------------------------------------------------------------------------------

    /**
     * Where to come up: the far side of the frame from the bat, ahead of it by at least
     * [RISE_LEAST_LEAD] - and its first time, at the frame's right, so the whole of it is seen before
     * anything is aimed at the bat.
     */
    private fun chooseRise(): Float {
        val least = RISE_LEFTMOST
        val most = frameWidth - RISE_EDGE_MARGIN
        if (rises == 0) return most
        val bat = batCenter()?.first ?: (frameWidth / 3f)
        val from = (bat + RISE_LEAST_LEAD).coerceIn(least, most)
        return from + random.nextFloat() * (most - from)
    }

    /** Its head swaying about its station: a lazy figure eight, the hood bobbing as it goes. */
    private fun sway() {
        val t = clock * SWAY_RATE
        headX = stationX + sin(t) * SWAY_X
        headY = stationY + sin(t * 2f) * SWAY_Y
    }

    /**
     * Upright while it rears, the hood rocking a little as it sways; pointed down its line while it
     * strikes, as far as a hood can lean; and along its heading while it swims.
     */
    private fun headRotation(lastX: Float, lastY: Float): Float = when (act) {
        Act.STRIKE -> {
            val heading = atan2(toY - fromY, toX - fromX) * DEGREES_PER_RADIAN
            normalized(heading - 180f).coerceIn(-STRIKE_LEAN, STRIKE_LEAN)
        }

        Act.SWIM -> {
            if (lastX == headX && lastY == headY) {
                world.getComponent(headId, SpriteComponent::class)?.rotationDegrees ?: 0f
            } else {
                normalized(atan2(headY - lastY, headX - lastX) * DEGREES_PER_RADIAN - 180f)
            }
        }

        else -> sin(clock * SWAY_RATE) * SWAY_LEAN
    }

    /** Down under the water at [x], still, with its body hanging straight down beneath it. */
    private fun submerge(x: Float) {
        headX = x
        headY = frameHeight + BURIED
        baseX = x
        swimming = false
        submergedPath(x, headY, trailingRight = false)
    }

    /**
     * Starts the path the body follows at ([x], [y]), with the rest of it trailing off to the right -
     * off the frame, for a swim coming in from that side - or hanging straight down under the water.
     */
    private fun submergedPath(x: Float, y: Float, trailingRight: Boolean) {
        points = 0
        newest = -1
        val length = NAGA_SPACING * (parts.size + 2)
        var along = length
        while (along >= 0f) {
            if (trailingRight) record(x + along, y, force = true) else record(x, y + along, force = true)
            along -= PATH_STEP
        }
    }

    private fun record(x: Float, y: Float, force: Boolean = false) {
        if (!force && points > 0 && hypot(x - pathX[newest], y - pathY[newest]) < 1f) return
        newest = (newest + 1) % PATH_POINTS
        pathX[newest] = x
        pathY[newest] = y
        if (points < PATH_POINTS) points++
    }

    private fun layBody() {
        val woundRow = world.getComponent(headId, SpriteComponent::class)?.srcY ?: 0
        if (swimming) layAlongPath() else layAlongCurve()
        for (part in parts) world.getComponent(part, SpriteComponent::class)?.srcY = woundRow
    }

    /**
     * Swimming: each part [NAGA_SPACING] behind the one in front along the path the head has swum, by
     * distance rather than by time, so the body neither bunches where the head slows nor strings out
     * where it speeds up.
     */
    private fun layAlongPath() {
        var x = headX
        var y = headY
        var aheadX = x
        var aheadY = y
        var cursor = newest
        var remaining = points
        for (part in parts) {
            var left = NAGA_SPACING
            while (left > 0f) {
                if (remaining == 0) {
                    x += left
                    break
                }
                val toX = pathX[cursor]
                val toY = pathY[cursor]
                val step = hypot(toX - x, toY - y)
                if (step >= left) {
                    x += (toX - x) * left / step
                    y += (toY - y) * left / step
                    break
                }
                left -= step
                x = toX
                y = toY
                cursor = (cursor - 1 + PATH_POINTS) % PATH_POINTS
                remaining--
            }
            placePart(part, x, y, aheadX, aheadY)
            aheadX = x
            aheadY = y
        }
    }

    /**
     * Rearing: along a curve from under the hood down to where the body leaves the water - straight
     * down from the hood at first, then leaning back away from the bat, and in again to come up out of
     * the sea - its parts spaced evenly along however long that is, within what the body can stretch
     * or gather to. Whatever is left over hangs straight down under the water.
     */
    private fun layAlongCurve() {
        val neckX = headX + NECK_X
        val neckY = headY + NECK_Y
        val baseY = frameHeight + BASE_DEPTH
        // The cobra's S, as a cubic: down from the neck, back over the body, and up out of the sea -
        // in proportion to how far the head is above the water, so a head still under it has the
        // whole body under it too.
        val height = (baseY - neckY).coerceAtLeast(0f)
        val c1x = neckX + NECK_LEAN_X * (height / REARED_HEIGHT).coerceAtMost(1f)
        val c1y = neckY + NECK_DROP * height
        val c2x = baseX + BODY_LEAN_X * (height / REARED_HEIGHT).coerceAtMost(1f)
        val c2y = baseY - BODY_RISE * height
        var length = 0f
        for (i in 0..CURVE_SAMPLES) {
            val t = i / CURVE_SAMPLES.toFloat()
            val u = 1f - t
            val x = u * u * u * neckX + 3f * u * u * t * c1x + 3f * u * t * t * c2x + t * t * t * baseX
            val y = u * u * u * neckY + 3f * u * u * t * c1y + 3f * u * t * t * c2y + t * t * t * baseY
            if (i > 0) length += hypot(x - curveX[i - 1], y - curveY[i - 1])
            curveX[i] = x
            curveY[i] = y
            curveAt[i] = length
        }
        val spacing = (length / (parts.size + 0.5f)).coerceIn(NAGA_SPACING_LEAST, NAGA_SPACING_MOST)
        var aheadX = headX
        var aheadY = headY
        var sample = 0
        for ((index, part) in parts.withIndex()) {
            val along = (index + NECK_START) * spacing
            val x: Float
            val y: Float
            if (along >= length) {
                x = baseX
                y = baseY + (along - length)
            } else {
                while (sample < CURVE_SAMPLES - 1 && curveAt[sample + 1] < along) sample++
                val span = (curveAt[sample + 1] - curveAt[sample]).coerceAtLeast(EPSILON)
                val f = ((along - curveAt[sample]) / span).coerceIn(0f, 1f)
                x = curveX[sample] + (curveX[sample + 1] - curveX[sample]) * f
                y = curveY[sample] + (curveY[sample + 1] - curveY[sample]) * f
            }
            placePart(part, x, y, aheadX, aheadY)
            aheadX = x
            aheadY = y
        }
    }

    /** A part at ([x], [y]), turned to lie along the body toward the part in front of it. */
    private fun placePart(id: EntityId, x: Float, y: Float, aheadX: Float, aheadY: Float) {
        val rotation = if (aheadX != x || aheadY != y) {
            normalized(atan2(aheadY - y, aheadX - x) * DEGREES_PER_RADIAN - 180f)
        } else {
            world.getComponent(id, SpriteComponent::class)?.rotationDegrees ?: 0f
        }
        place(id, x, y, rotation)
    }

    /**
     * Puts [id] with its center at ([x], [y]), and gives it what it moved by as its velocity - leaving
     * out a jump under the water, which is not movement.
     */
    private fun place(id: EntityId, x: Float, y: Float, rotation: Float) {
        val transform = world.getComponent(id, TransformComponent::class) ?: return
        val old = transform.rect
        val rect = Rect.fromLTWH(x - HALF, y - HALF, FRAME, FRAME)
        transform.rect = rect
        val dx = rect.left - old.left
        val dy = rect.top - old.top
        world.getComponent(id, VelocityComponent::class)?.velocity =
            if (abs(dx) + abs(dy) > FRAME) Vector2.Zero else Vector2(dx, dy)
        world.getComponent(id, SpriteComponent::class)?.rotationDegrees = rotation
    }

    private fun allUnder(): Boolean = isUnder(headId) && parts.all { isUnder(it) }

    private fun isUnder(id: EntityId): Boolean =
        (world.getComponent(id, TransformComponent::class)?.rect?.top ?: Float.MAX_VALUE) > frameHeight + UNDER_MARGIN

    // --- firing ------------------------------------------------------------------------------------

    private fun queue(volley: Volley) {
        queued.addLast(volley)
    }

    /**
     * Fires what has been queued, one volley at a time and a little apart, through the head's gun and
     * the same weapon every enemy fires by. Its cadence is set so long that it never comes round on
     * its own: this is the only thing that pulls the trigger. Nothing is fired from under the water,
     * where the screen would not let it anyway.
     */
    private fun fireQueued(deltaTime: Float) {
        untilVolley -= deltaTime
        if (untilVolley > 0f || queued.isEmpty()) return
        val volley = queued.removeFirst()
        untilVolley = VOLLEY_GAP_SECONDS
        world.getComponent(headId, GunComponent::class)?.apply {
            volleys = listOf(volley)
            next = 0
        }
        world.getComponent(headId, WeaponComponent::class)?.let { it.timeSinceLastShot = it.interval }
    }

    // --- helpers -----------------------------------------------------------------------------------

    private fun step(next: Act) {
        act = next
        actTime = 0f
    }

    private fun moveFrom(x0: Float, y0: Float, x1: Float, y1: Float) {
        fromX = x0
        fromY = y0
        toX = x1
        toY = y1
    }

    private fun batCenter(): Pair<Float, Float>? {
        for (id in world.query(PlayerControlComponent::class, TransformComponent::class)) {
            if (world.getComponent(id, HealthComponent::class)?.alive == false) continue
            val rect = world.getComponent(id, TransformComponent::class)?.rect ?: continue
            return rect.centerX to rect.centerY
        }
        return null
    }

    private fun normalized(degrees: Float): Float {
        var turned = degrees % 360f
        if (turned > 180f) turned -= 360f
        if (turned <= -180f) turned += 360f
        return turned
    }

    /** What it is doing, round after round. */
    enum class Act {
        /** Under the water between rises, the water boiling where it will come up next. */
        SUBMERGED,

        /** Coming up out of the water to its station. */
        RISING,

        /** Swaying on its station, spitting. */
        REARED,

        /** Drawing its head back before a strike: the warning. */
        COIL,

        /** Lunging straight at where the bat was. */
        STRIKE,

        /** Drawing its head back to its station after a strike. */
        RECOIL,

        /** Going back down into the water. */
        SINKING,

        /** Swimming across the frame low over the water, from its fourth phase. */
        SWIM,
    }

    companion object {
        private const val FRAME = NAGA_FRAME.toFloat()
        private const val HALF = FRAME / 2f

        /** How long it stays under when it first arrives, so the banner announcing it can be read. */
        private const val ENTRANCE_SECONDS = 2.4f

        /** How far below the bottom edge the head waits, so not a pixel of it shows. */
        private const val BURIED = 60f
        private const val UNDER_MARGIN = 2f

        /**
         * How deep under the bottom edge the body's curve ends: deep enough that the part there is out
         * of sight, so the body comes up out of the sea rather than starting on it.
         */
        private const val BASE_DEPTH = 36f

        /** Seconds between the bursts of the tell, and how much bigger the rise's own is. */
        private const val SPLASH_GAP_SECONDS = 0.16f
        private const val RISE_SPLASH_SCALE = 1.5f

        /** How far in from the right edge a swim's tell boils, and how far past it the swim begins. */
        private const val SWIM_TELL_INSET = 40f
        private const val SWIM_ENTRY = 50f

        /**
         * Where it comes up: never left of [RISE_LEFTMOST] or nearer the right edge than
         * [RISE_EDGE_MARGIN], and at least [RISE_LEAST_LEAD] ahead of the bat where there is room.
         */
        private const val RISE_LEFTMOST = 300f
        private const val RISE_EDGE_MARGIN = 36f
        private const val RISE_LEAST_LEAD = 190f

        /** How far left of where its body leaves the water its head sways: the hood leans toward the bat. */
        private const val STATION_LEAD = 34f

        /** The highest and lowest its head rears to: level with the bat, within these. */
        private const val REAR_HIGHEST = 110f
        private const val REAR_LOWEST = 210f

        /** Its sway: a figure eight this wide and tall, at this many radians a second, the hood rocking. */
        private const val SWAY_X = 16f
        private const val SWAY_Y = 9f
        private const val SWAY_RATE = 2.3f
        private const val SWAY_LEAN = 5f

        /** Its first spit after rising, and after a strike, so neither comes on top of the other. */
        private const val FIRST_SPIT_SECONDS = 0.6f
        private const val SPIT_AFTER_STRIKE_SECONDS = 0.7f

        /** How long it rears before its first strike, between strikes, and after its last before it sinks. */
        private const val FIRST_STRIKE_AFTER_SECONDS = 1.5f
        private const val BETWEEN_STRIKES_SECONDS = 0.5f
        private const val LAST_REAR_SECONDS = 0.9f

        /** How far it draws its head back to coil, and how hard it shivers there. */
        private const val COIL_BACK = 26f
        private const val SHIVER = 2f
        private const val SHIVER_RATE = 60f

        /** It strikes only at a bat at least this far in front of its head, and never above this height. */
        private const val STRIKE_LEAST_LEAD = 40f
        private const val STRIKE_HIGHEST = 24f
        private const val STRIKE_REACH_FALLBACK = 160f

        /** How far its hood may lean down the line of a strike. */
        private const val STRIKE_LEAN = 50f

        /**
         * Its swim: low over the water, rising and dipping this far either side of [SWIM_Y] once every
         * [SWIM_WAVELENGTH] pixels across, so its humps break the surface and go back under, and it spits
         * every [SWIM_SPIT_SECONDS] while it is in the frame.
         */
        private const val SWIM_Y = 318f
        private const val SWIM_AMPLITUDE = 62f
        private const val SWIM_WAVELENGTH = 260f
        private const val SWIM_SPIT_SECONDS = 1.3f

        /**
         * The rearing body's curve, against the head's center: where the neck leaves the hood; how far
         * it drops from there and how far the body rises out of the water, as shares of the head's
         * height above it; and how far each leans, at a full rear of [REARED_HEIGHT] - the neck toward
         * the bat a little, the body back away from it.
         */
        private const val NECK_X = 4f
        private const val NECK_Y = 18f
        private const val NECK_DROP = 0.34f
        private const val BODY_RISE = 0.52f
        private const val NECK_LEAN_X = 8f
        private const val BODY_LEAN_X = 50f
        private const val REARED_HEIGHT = 200f

        /** How many part-spacings below the neck the first part sits, so it tucks under the hood. */
        private const val NECK_START = 0.6f

        private const val CURVE_SAMPLES = 48
        private const val PATH_POINTS = 640
        private const val PATH_STEP = 4f
        private const val EPSILON = 1e-3f

        /** Seconds between the volleys of one burst - a strike's ring after a spit, say. */
        private const val VOLLEY_GAP_SECONDS = 0.22f

        private const val DEGREES_PER_RADIAN = 57.29578f
        private const val TWO_PI = (2.0 * PI).toFloat()

        /** Far longer than any fight: the brain pulls the trigger, never the cadence. */
        const val TRIGGERED_INTERVAL = 1_000_000f

        /** What it spits while it rears and swims: fans at the bat, wider once it is enraged. */
        private val FAN = Volley(
            ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 13f, speed = 2.7f, damageFactor = NAGA_SPIT_DAMAGE,
        )
        private val WIDE_FAN = Volley(
            ShotPattern.AIMED_FAN, count = 5, spreadDegrees = 11f, speed = 2.9f, damageFactor = NAGA_SPIT_DAMAGE,
        )

        /** What it throws from the end of a strike: a ring to thread, denser as the fight goes on. */
        private val RING = Volley(ShotPattern.RADIAL, count = 10, speed = 2.0f, damageFactor = NAGA_RING_DAMAGE)
        private val DENSE_RING = Volley(ShotPattern.RADIAL, count = 14, speed = 2.2f, damageFactor = NAGA_RING_DAMAGE)

        /** The gun it is built with, which the brain only ever fires one chosen volley at a time from. */
        val GUN = EnemyGun(TRIGGERED_INTERVAL, listOf(FAN))

        fun spitFor(phase: Int): Volley = if (phase >= 5) WIDE_FAN else FAN

        fun ringFor(phase: Int): Volley = if (phase >= 3) DENSE_RING else RING

        /** How many times it strikes each time it rises. */
        fun strikesFor(phase: Int): Int = when {
            phase >= 5 -> 3
            phase >= 4 -> 2
            phase >= 2 -> 1
            else -> 0
        }

        /** The phase its health puts it in. */
        fun phaseFor(healthFraction: Float): Int = when {
            healthFraction <= NAGA_PHASE_5_AT -> 5
            healthFraction <= NAGA_PHASE_4_AT -> 4
            healthFraction <= NAGA_PHASE_3_AT -> 3
            healthFraction <= NAGA_PHASE_2_AT -> 2
            else -> 1
        }

        /** Eased in and out over 0..1, for every move it makes but the strike, which is all at once. */
        private fun eased(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }
}
