package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.service.SkyViperBrain.Companion.AWAY_EXIT_HIGHEST
import at.smiech.cyanbat.service.SkyViperBrain.Companion.AWAY_SECONDS
import at.smiech.cyanbat.service.SkyViperBrain.Companion.HOVER_EDGE
import at.smiech.cyanbat.service.SkyViperBrain.Companion.HOVER_HIGHEST
import at.smiech.cyanbat.service.SkyViperBrain.Companion.HOVER_LEFTMOST
import at.smiech.cyanbat.service.SkyViperBrain.Companion.HOVER_LOWEST
import at.smiech.cyanbat.service.SkyViperBrain.Companion.STEEPEST
import at.smiech.cyanbat.service.SkyViperBrain.Companion.SWOOP_GATHER_SECONDS
import at.smiech.cyanbat.service.SkyViperBrain.Companion.SWOOP_LAUNCH
import at.smiech.cyanbat.service.SkyViperBrain.Companion.TURN_PER_TICK
import at.smiech.cyanbat.util.SKY_VIPER_AWAY_SECONDS
import at.smiech.cyanbat.util.SKY_VIPER_DROP_DAMAGE
import at.smiech.cyanbat.util.SKY_VIPER_DROP_SECONDS
import at.smiech.cyanbat.util.SKY_VIPER_ENRAGED_AWAY_SECONDS
import at.smiech.cyanbat.util.SKY_VIPER_ENRAGED_SWOOP_SPEED
import at.smiech.cyanbat.util.SKY_VIPER_ENRAGED_TELL_SECONDS
import at.smiech.cyanbat.util.SKY_VIPER_ENRAGED_WEAVE_SPEED
import at.smiech.cyanbat.util.SKY_VIPER_FRAME
import at.smiech.cyanbat.util.SKY_VIPER_NECK_BACK
import at.smiech.cyanbat.util.SKY_VIPER_PHASE_2_AT
import at.smiech.cyanbat.util.SKY_VIPER_PHASE_3_AT
import at.smiech.cyanbat.util.SKY_VIPER_RAIN_SPEED
import at.smiech.cyanbat.util.SKY_VIPER_RING_DAMAGE
import at.smiech.cyanbat.util.SKY_VIPER_SPACING
import at.smiech.cyanbat.util.SKY_VIPER_SPIT_DAMAGE
import at.smiech.cyanbat.util.SKY_VIPER_SWOOP_SPEED
import at.smiech.cyanbat.util.SKY_VIPER_TELL_SECONDS
import at.smiech.cyanbat.util.SKY_VIPER_WEAVE_SPEED
import at.smiech.cyanbat.util.SKY_VIPER_WING_BACK
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.PlayerControlComponent
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Sky Viper: its entrance, which is the lagoon's interlude, and its fight, three phases marked by
 * its health, made of passes across the frame with the viper out of sight between them.
 *
 * Its entrance: it lurks above the frame, unseen, while its screech is heard; dives down out of the
 * sky at the bat and bites it ([onBite]); flies on, under the bat and up out of the frame to the
 * left; and sweeps back in from above, and with that its fight begins ([onArrived]). Holding the bat
 * still for the bite is the screen's part.
 *
 * Its passes:
 * 1. **The swoop.** In from above to hover ahead of the bat, a fan spat at it as it settles; it
 *    rears, turned to face the bat, shivers as the warning, and dives at where the bat was, aimed
 *    once and never steered, on out of the frame. **The weave.** In from the right and across the
 *    frame round the bat's height, spitting fans as it goes.
 * 2. **The rain.** From its first wound it also crosses along the top of the frame, a drop of venom
 *    falling from it every so often, and spits wider fans.
 * 3. **Enraged.** From its second it rears and swoops quicker, is away for less time, swoops twice
 *    for every weave and rain, and throws a ring of venom where each swoop was aimed.
 *
 * It uses no engine movement: this places the head every tick and lays the wings and the body back
 * along the path the head has flown, by distance, as the Sand Wyrm's brain lays its plates. It only
 * ever flies leftward, or as near straight up or down as a dive needs, since its art faces left and a
 * head turned past straight up would fly upside down. It goes out of sight after every pass and
 * starts the next from out of sight, so the body is never seen to jump. Every part is given the
 * velocity it moved by, so anything forecasting by velocities sees where it is going.
 *
 * A hit that crosses both thresholds plays both entrances in order.
 *
 * @param wingsId its wings, set on its neck.
 * @param parts its body, from the neck back to the tail. Only this brain moves the wings and the
 *   body, and nothing culls or kills them, so their ids are safe to hold for the whole fight.
 * @param random where its weaves cross, within the room they have.
 * @param onBite called the moment its entrance's dive reaches the bat.
 * @param onArrived called as it sweeps back in after the bite, which is where its fight begins.
 * @param onPhaseChanged called with the phase it has just entered, 2 or 3, for the screen to announce.
 */
class SkyViperBrain(
    private val world: World,
    private val headId: EntityId,
    private val wingsId: EntityId,
    private val parts: List<EntityId>,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val random: Random = Random.Default,
    private val onBite: () -> Unit = {},
    private val onArrived: () -> Unit = {},
    private val onPhaseChanged: (Int) -> Unit = {},
) : BossBrain {
    /** The phase it is in, 1 to 3. */
    var phase = 1
        private set

    /** What it is doing; see [Act]. */
    var act = Act.LURK
        private set

    /** Whether its fight has begun: it has bitten the bat and come back into sight. */
    var arrived = false
        private set

    /** The head's center, which everything else is laid back from. */
    var headX = frameWidth * LURK_X
        private set
    var headY = -FRAME
        private set

    /** Seconds into the current [act], and of the whole fight, which its hover bobs on. */
    private var actTime = 0f
    private var clock = 0f

    /** How many passes it has made, which picks the next; see [nextPass]. */
    private var passes = 0

    // A curve the head follows over the act: rising, descending, flying on from the bite. Its end and
    // second-to-last points move with the bat on the dive, so the bite lands wherever the bat is.
    private val curveX = FloatArray(4)
    private val curveY = FloatArray(4)

    /** Where it hovers before a swoop. */
    private var hoverX = 0f
    private var hoverY = 0f

    /** Which way it moves on a swoop, and once out of the frame, and how far along the swoop it is. */
    private var dirX = -1f
    private var dirY = 0f
    private var speed = 0f
    private var along = 0f

    /** How far the swoop's aim was, and whether its ring has been thrown there. */
    private var aimDistance = 0f
    private var ringThrown = false

    /** The height a weave crosses round, and where along the frame it started. */
    private var laneY = 0f
    private var startX = 0f

    /** Seconds until it next spits or drops venom. */
    private var untilSpit = 0f

    /** Volleys waiting to be fired, and the seconds until the next; see [fireQueued]. */
    private var untilVolley = 0f
    private val queued = ArrayDeque<Volley>()

    // The path the head has flown, as a ring buffer of points at least a pixel apart, as long as the
    // body needs.
    private val pathX = FloatArray(PATH_POINTS)
    private val pathY = FloatArray(PATH_POINTS)
    private var newest = -1
    private var points = 0
    private val walker = Walker()

    init {
        resetPath(headX, headY, trailing = Trailing.UP)
        place(headId, headX, headY, rotation = HEAD_DOWN)
        layBody()
    }

    override fun update(deltaTime: Float) {
        val health = world.getComponent(headId, HealthComponent::class) ?: return
        if (!health.alive) return

        val due = phaseFor(health.fraction)
        while (phase < due) enter(phase + 1)

        clock += deltaTime
        actTime += deltaTime
        val lastX = headX
        val lastY = headY
        when (act) {
            Act.LURK -> lurk()
            Act.DIVE -> dive()
            Act.AWAY -> flyAway()
            Act.LEAVING -> leave()
            Act.GONE -> waitAway()
            Act.DESCEND -> descend()
            Act.REAR -> rear(deltaTime)
            Act.SWOOP -> swoop()
            Act.WEAVE -> weave(deltaTime)
            Act.RAIN -> rain(deltaTime)
        }
        record(headX, headY)
        place(headId, headX, headY, headRotation(lastX, lastY))
        layBody()
        fireQueued(deltaTime)
    }

    // --- its entrance ------------------------------------------------------------------------------

    /** Above the frame, unseen, while its screech is heard; then it dives. */
    private fun lurk() {
        if (actTime < LURK_SECONDS) return
        curveX[0] = headX
        curveY[0] = headY
        curveX[1] = headX
        curveY[1] = frameHeight * DIVE_DROP
        step(Act.DIVE)
    }

    /**
     * Down out of the sky, gathering speed, curving in from the right onto the bat, which the curve's
     * end follows; and at its end, the bite.
     */
    private fun dive() {
        val (batX, batY) = batCenter() ?: (frameWidth / 3f to frameHeight / 2f)
        curveX[3] = batX + BITE_X
        curveY[3] = batY + BITE_Y
        curveX[2] = batX + DIVE_SWING_X
        curveY[2] = batY + DIVE_SWING_Y
        val t = (actTime / DIVE_SECONDS).coerceAtMost(1f)
        follow(t * t)
        if (t < 1f) return
        onBite()
        // On from the bite: under the bat, and up and out of the frame on the left.
        curveX[0] = headX
        curveY[0] = headY
        curveX[1] = batX + AWAY_DIP_X
        curveY[1] = batY + AWAY_DIP_Y
        curveX[2] = batX + AWAY_RISE_X
        curveY[2] = batY + AWAY_RISE_Y
        curveX[3] = -FRAME
        curveY[3] = (batY + AWAY_EXIT_Y).coerceAtLeast(AWAY_EXIT_HIGHEST)
        step(Act.AWAY)
    }

    /** On from the bite and out of the frame. */
    private fun flyAway() {
        val t = (actTime / AWAY_SECONDS).coerceAtMost(1f)
        val lastX = headX
        val lastY = headY
        follow(t)
        if (t >= 1f) beginLeaving(headX - lastX, headY - lastY)
    }

    // --- between passes ------------------------------------------------------------------------------

    /**
     * Flies on the way it was going, at the speed it was going, once its head is out of the frame,
     * until all of it is: the tail follows the head out.
     */
    private fun beginLeaving(dx: Float, dy: Float) {
        val length = hypot(dx, dy)
        if (length > EPSILON) {
            dirX = dx / length
            dirY = dy / length
            speed = length.coerceAtLeast(LEAVING_LEAST_SPEED)
        }
        step(Act.LEAVING)
    }

    private fun leave() {
        headX += dirX * speed
        headY += dirY * speed
        if (allOutOfSight() || actTime > LEAVING_LONGEST_SECONDS) step(Act.GONE)
    }

    /** Out of sight between passes, then the next one. */
    private fun waitAway() {
        val away = when {
            !arrived -> RETURN_SECONDS
            phase >= 3 -> SKY_VIPER_ENRAGED_AWAY_SECONDS
            else -> SKY_VIPER_AWAY_SECONDS
        }
        if (actTime < away) return
        if (!arrived) {
            // Back in from above after the bite: its fight begins.
            arrived = true
            startDescent()
            onArrived()
            return
        }
        when (nextPass()) {
            Pass.SWOOP -> startDescent()
            Pass.WEAVE -> startWeave()
            Pass.RAIN -> startRain()
        }
    }

    /** The pass after the last, round its phase's cycle. */
    private fun nextPass(): Pass {
        val cycle = when {
            phase >= 3 -> ENRAGED_CYCLE
            phase == 2 -> RAIN_CYCLE
            else -> OPENING_CYCLE
        }
        return cycle[passes++ % cycle.size]
    }

    // --- the swoop -----------------------------------------------------------------------------------

    /** In from above the frame to hover ahead of the bat, high enough to dive at it. */
    private fun startDescent() {
        val (batX, batY) = batCenter() ?: (frameWidth / 3f to frameHeight / 2f)
        hoverX = (batX + HOVER_LEAD).coerceIn(HOVER_LEFTMOST, frameWidth - HOVER_EDGE)
        hoverY = (batY - HOVER_ABOVE).coerceIn(HOVER_HIGHEST, HOVER_LOWEST)
        headX = hoverX + DESCENT_FROM_X
        headY = -FRAME
        resetPath(headX, headY, trailing = Trailing.UP)
        curveX[0] = headX
        curveY[0] = headY
        curveX[1] = headX
        curveY[1] = hoverY * DESCENT_DROP
        curveX[2] = hoverX + DESCENT_SWING_X
        curveY[2] = hoverY + DESCENT_SWING_Y
        curveX[3] = hoverX
        curveY[3] = hoverY
        step(Act.DESCEND)
    }

    private fun descend() {
        val t = (actTime / DESCENT_SECONDS).coerceAtMost(1f)
        follow(eased(t))
        if (t < 1f) return
        untilSpit = REAR_SPIT_SECONDS
        step(Act.REAR)
    }

    /**
     * Hovering, bobbing on its wings, turned toward the bat; it spits as it settles, shivers as the
     * warning, and dives at where the bat is when the warning ends.
     */
    private fun rear(deltaTime: Float) {
        val tell = if (phase >= 3) SKY_VIPER_ENRAGED_TELL_SECONDS else SKY_VIPER_TELL_SECONDS
        headX = hoverX + sin(clock * BOB_RATE) * BOB_X
        headY = hoverY + sin(clock * BOB_RATE * 2f) * BOB_Y
        if (actTime > tell - SHIVER_SECONDS) headX += sin(clock * SHIVER_RATE) * SHIVER
        if (untilSpit > 0f) {
            untilSpit -= deltaTime
            if (untilSpit <= 0f) queue(spitFor(phase))
        }
        if (actTime < tell) return

        val (batX, batY) = batCenter() ?: (headX - FRAME to headY)
        val heading = leftward(atan2(batY - headY, batX - headX) * DEGREES_PER_RADIAN)
        dirX = cos(heading / DEGREES_PER_RADIAN)
        dirY = sin(heading / DEGREES_PER_RADIAN)
        aimDistance = hypot(batX - headX, batY - headY)
        along = 0f
        ringThrown = false
        step(Act.SWOOP)
    }

    /** The dive: straight down the line it was aimed along, gathering speed, and on out of sight. */
    private fun swoop() {
        val full = if (phase >= 3) SKY_VIPER_ENRAGED_SWOOP_SPEED else SKY_VIPER_SWOOP_SPEED
        speed = full * (SWOOP_LAUNCH + actTime / SWOOP_GATHER_SECONDS).coerceAtMost(1f)
        headX += dirX * speed
        headY += dirY * speed
        along += speed
        // Enraged, a ring from where the bat was when it was aimed.
        if (phase >= 3 && !ringThrown && along >= aimDistance) {
            ringThrown = true
            queue(RING)
        }
        if (headOutOfSight()) step(Act.LEAVING)
    }

    // --- the weave and the rain ------------------------------------------------------------------------

    /** In from the right, to cross the frame round the bat's height. */
    private fun startWeave() {
        val batY = batCenter()?.second ?: (frameHeight / 2f)
        laneY = (batY + (random.nextFloat() * 2f - 1f) * WEAVE_JITTER).coerceIn(
            WEAVE_HIGHEST,
            frameHeight - WEAVE_LOWEST
        )
        startX = frameWidth + FRAME
        headX = startX
        headY = laneY
        resetPath(headX, headY, trailing = Trailing.RIGHT)
        untilSpit = WEAVE_FIRST_SPIT_SECONDS
        step(Act.WEAVE)
    }

    private fun weave(deltaTime: Float) {
        val pace = if (phase >= 3) SKY_VIPER_ENRAGED_WEAVE_SPEED else SKY_VIPER_WEAVE_SPEED
        headX -= pace
        headY = laneY + sin((startX - headX) / WEAVE_WAVELENGTH * TWO_PI) * WEAVE_AMPLITUDE
        if (headInFrame()) {
            untilSpit -= deltaTime
            if (untilSpit <= 0f) {
                untilSpit += WEAVE_SPIT_SECONDS
                queue(spitFor(phase))
            }
        }
        if (headX < -FRAME / 2f) beginLeaving(-pace, 0f)
    }

    /** In from the right, along the top of the frame. */
    private fun startRain() {
        startX = frameWidth + FRAME
        headX = startX
        headY = RAIN_Y
        resetPath(headX, headY, trailing = Trailing.RIGHT)
        untilSpit = 0f
        step(Act.RAIN)
    }

    /** Along the top, bobbing, a drop of venom falling from it every so often while it is in sight. */
    private fun rain(deltaTime: Float) {
        headX -= SKY_VIPER_RAIN_SPEED
        headY = RAIN_Y + sin((startX - headX) / RAIN_WAVELENGTH * TWO_PI) * RAIN_BOB
        if (headInFrame()) {
            untilSpit -= deltaTime
            if (untilSpit <= 0f) {
                untilSpit += SKY_VIPER_DROP_SECONDS
                queue(DROP)
            }
        }
        if (headX < -FRAME / 2f) beginLeaving(-SKY_VIPER_RAIN_SPEED, 0f)
    }

    // --- phases --------------------------------------------------------------------------------------

    private fun enter(next: Int) {
        phase = next
        onPhaseChanged(next)
    }

    // --- placing it ----------------------------------------------------------------------------------

    /** Puts the head at time [t] along the act's curve. */
    private fun follow(t: Float) {
        val u = 1f - t
        headX =
            u * u * u * curveX[0] + 3f * u * u * t * curveX[1] + 3f * u * t * t * curveX[2] + t * t * t * curveX[3]
        headY =
            u * u * u * curveY[0] + 3f * u * u * t * curveY[1] + 3f * u * t * t * curveY[2] + t * t * t * curveY[3]
    }

    /**
     * Along its heading as it flies; turned toward the bat as it rears; never further round than a
     * dive needs; and turning at most [TURN_PER_TICK] a tick, so a change of course is seen to turn.
     */
    private fun headRotation(lastX: Float, lastY: Float): Float {
        val current = world.getComponent(headId, SpriteComponent::class)?.rotationDegrees ?: 0f
        val target = when {
            act == Act.REAR -> batCenter()?.let { (batX, batY) ->
                leftward(atan2(batY - headY, batX - headX) * DEGREES_PER_RADIAN) - 180f
            } ?: 0f

            hypot(headX - lastX, headY - lastY) > MOVING ->
                normalized(
                    leftward(
                        atan2(
                            headY - lastY,
                            headX - lastX
                        ) * DEGREES_PER_RADIAN
                    ) - 180f
                )

            else -> current
        }
        return current + (normalized(target - current)).coerceIn(-TURN_PER_TICK, TURN_PER_TICK)
    }

    /**
     * Starts the path afresh at ([x], [y]), out of sight, the body trailing back from it straight up
     * above the frame or off to its right, whichever it comes in from.
     */
    private fun resetPath(x: Float, y: Float, trailing: Trailing) {
        points = 0
        newest = -1
        var back = BODY_LENGTH + PATH_SPARE
        while (back >= 0f) {
            when (trailing) {
                Trailing.UP -> record(x, y - back, force = true)
                Trailing.RIGHT -> record(x + back, y, force = true)
            }
            back -= PATH_STEP
        }
    }

    /** Adds a point to the head's path, unless it is within a pixel of the last one. */
    private fun record(x: Float, y: Float, force: Boolean = false) {
        if (!force && points > 0 && hypot(x - pathX[newest], y - pathY[newest]) < 1f) return
        newest = (newest + 1) % PATH_POINTS
        pathX[newest] = x
        pathY[newest] = y
        if (points < PATH_POINTS) points++
    }

    /**
     * Lays the wings and every part of the body back along the head's path, each at its distance
     * behind the head, turned to lie along it, and drawn from the head's wound row.
     */
    private fun layBody() {
        val woundRow = world.getComponent(headId, SpriteComponent::class)?.srcY ?: 0
        // The wings first, on a walk of their own, since they sit among the parts; turned along the
        // path just ahead of them.
        walker.start()
        walker.walkTo(SKY_VIPER_WING_BACK - WING_LINE)
        walker.walkTo(SKY_VIPER_WING_BACK)
        placePart(wingsId, walker.x, walker.y, walker.aheadX, walker.aheadY, woundRow)
        walker.start()
        var aheadX = walker.x
        var aheadY = walker.y
        for ((index, part) in parts.withIndex()) {
            walker.walkTo(SKY_VIPER_NECK_BACK + index * SKY_VIPER_SPACING)
            placePart(part, walker.x, walker.y, aheadX, aheadY, woundRow)
            aheadX = walker.x
            aheadY = walker.y
        }
    }

    /** Walks back along the path from the head, by distance; see [layBody]. */
    private inner class Walker {
        /** Where it has walked to, and the point it walked on from. */
        var x = 0f
        var y = 0f
        var aheadX = 0f
        var aheadY = 0f
        private var cursor = 0
        private var remaining = 0
        private var walked = 0f

        /** Back at the head, with the whole path behind it. */
        fun start() {
            x = headX
            y = headY
            cursor = newest
            remaining = points
            walked = 0f
        }

        /** On to [distance] behind the head, noting the point a little ahead of where it stops. */
        fun walkTo(distance: Float) {
            aheadX = x
            aheadY = y
            var left = distance - walked
            while (left > 0f) {
                if (remaining == 0) {
                    // Past the end of the path, the body runs on the way the path last went.
                    val dx = x - aheadX
                    val dy = y - aheadY
                    val length = hypot(dx, dy)
                    if (length > EPSILON) {
                        x += dx / length * left
                        y += dy / length * left
                    } else {
                        y -= left
                    }
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
            walked = distance
        }
    }

    /** A part at ([x], [y]), turned to lie along the body toward the point ahead of it. */
    private fun placePart(
        id: EntityId,
        x: Float,
        y: Float,
        aheadX: Float,
        aheadY: Float,
        woundRow: Int
    ) {
        val rotation = if (aheadX != x || aheadY != y) {
            normalized(atan2(aheadY - y, aheadX - x) * DEGREES_PER_RADIAN - 180f)
        } else {
            world.getComponent(id, SpriteComponent::class)?.rotationDegrees ?: 0f
        }
        place(id, x, y, rotation)
        world.getComponent(id, SpriteComponent::class)?.srcY = woundRow
    }

    /**
     * Centers [id] on ([x], [y]) and sets its velocity to what it moved by, except for a jump out of
     * sight, which is not movement.
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

    /** Whether the head is wholly in the frame across, where what it fires will be fired. */
    private fun headInFrame(): Boolean = headX >= HALF && headX <= frameWidth - HALF

    /** Whether the head's frame is wholly out of the frame. */
    private fun headOutOfSight(): Boolean = outOfSight(headId)

    /** Whether every part of it is out of the frame. */
    private fun allOutOfSight(): Boolean =
        outOfSight(headId) && outOfSight(wingsId) && parts.all { outOfSight(it) }

    private fun outOfSight(id: EntityId): Boolean {
        val rect = world.getComponent(id, TransformComponent::class)?.rect ?: return true
        return rect.right <= 0f || rect.left >= frameWidth || rect.bottom <= 0f || rect.top >= frameHeight
    }

    // --- firing --------------------------------------------------------------------------------------

    private fun queue(volley: Volley) {
        queued.addLast(volley)
    }

    /**
     * Fires the queue one volley at a time, a little apart, through the head's gun and ordinary
     * weapon, whose cadence never comes round on its own; see [TRIGGERED_INTERVAL].
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
        world.getComponent(headId, WeaponComponent::class)
            ?.let { it.timeSinceLastShot = it.interval }
    }

    // --- helpers -------------------------------------------------------------------------------------

    private fun step(next: Act) {
        act = next
        actTime = 0f
    }

    /** The living bat's center, or null. */
    private fun batCenter(): Pair<Float, Float>? {
        for (id in world.query(PlayerControlComponent::class, TransformComponent::class)) {
            if (world.getComponent(id, HealthComponent::class)?.alive == false) continue
            val rect = world.getComponent(id, TransformComponent::class)?.rect ?: continue
            return rect.centerX to rect.centerY
        }
        return null
    }

    /** What it does, pass after pass. */
    enum class Act {
        /** Above the frame, unseen, at the start of its entrance. */
        LURK,

        /** Down out of the sky at the bat, to bite it. */
        DIVE,

        /** On from the bite, under the bat and up out of the frame. */
        AWAY,

        /** Its head out of the frame, the rest of it following. */
        LEAVING,

        /** Out of sight between passes. */
        GONE,

        /** In from above to hover ahead of the bat. */
        DESCEND,

        /** Hovering, rearing to swoop: the warning. */
        REAR,

        /** Diving at where the bat was, and on out of the frame. */
        SWOOP,

        /** Across the frame from the right, round the bat's height, spitting. */
        WEAVE,

        /** Along the top of the frame, dropping venom. */
        RAIN,
    }

    /** A pass across the frame, from out of sight and back out of it. */
    private enum class Pass { SWOOP, WEAVE, RAIN }

    /** Which way the body hangs back from a head starting a pass out of sight. */
    private enum class Trailing { UP, RIGHT }

    companion object {
        private const val FRAME = SKY_VIPER_FRAME.toFloat()
        private const val HALF = FRAME / 2f

        /** How far along the path the wings are turned by: the stretch just ahead of them. */
        private const val WING_LINE = 6f

        /** How long the body is, wings to tail, which a fresh path has to hold. */
        private val BODY_LENGTH = SKY_VIPER_NECK_BACK + 10 * SKY_VIPER_SPACING
        private const val PATH_SPARE = 60f
        private const val PATH_STEP = 4f
        private const val PATH_POINTS = 640

        /** Where across the frame it lurks above, and how long, while its screech is heard. */
        private const val LURK_X = 0.62f
        private const val LURK_SECONDS = 0.6f

        /**
         * Its dive: how long it takes, how far down the frame it falls straight before it curves,
         * and how far right of and above the bat the curve swings in from.
         */
        private const val DIVE_SECONDS = 1.25f
        private const val DIVE_DROP = 0.45f
        private const val DIVE_SWING_X = 190f
        private const val DIVE_SWING_Y = -60f

        /** Where the head is against the bat's middle as it bites: its jaws on the bat. */
        private const val BITE_X = 30f
        private const val BITE_Y = -6f

        /**
         * On from the bite, over [AWAY_SECONDS]: dipping under the bat, rising past it, and out of the
         * frame on the left, well up, but no higher than [AWAY_EXIT_HIGHEST].
         */
        private const val AWAY_SECONDS = 1.1f
        private const val AWAY_DIP_X = -70f
        private const val AWAY_DIP_Y = 80f
        private const val AWAY_RISE_X = -170f
        private const val AWAY_RISE_Y = -40f
        private const val AWAY_EXIT_Y = -170f
        private const val AWAY_EXIT_HIGHEST = 24f

        /** How long it is out of sight after the bite before it comes back for its fight. */
        private const val RETURN_SECONDS = 0.4f

        /** The least speed it leaves at, and the longest it takes to, in case anything holds it. */
        private const val LEAVING_LEAST_SPEED = 2f
        private const val LEAVING_LONGEST_SECONDS = 4f

        /**
         * Where it hovers to swoop: this far right of and above the bat, but no further left than
         * [HOVER_LEFTMOST], no nearer the right edge than [HOVER_EDGE], and between [HOVER_HIGHEST]
         * and [HOVER_LOWEST] down the frame.
         */
        private const val HOVER_LEAD = 230f
        private const val HOVER_ABOVE = 80f
        private const val HOVER_LEFTMOST = 330f
        private const val HOVER_EDGE = 70f
        private const val HOVER_HIGHEST = 70f
        private const val HOVER_LOWEST = 170f

        /** Its descent to the hover: from how far right of it, how far down it falls straight, and how it swings in. */
        private const val DESCENT_SECONDS = 1f
        private const val DESCENT_FROM_X = 70f
        private const val DESCENT_DROP = 0.55f
        private const val DESCENT_SWING_X = 60f
        private const val DESCENT_SWING_Y = 34f

        /** Its bob while it hovers, and the shiver it ends on: the warning. */
        private const val BOB_X = 7f
        private const val BOB_Y = 5f
        private const val BOB_RATE = 4.2f
        private const val SHIVER = 2.5f
        private const val SHIVER_RATE = 60f
        private const val SHIVER_SECONDS = 0.35f

        /** When it spits after settling into its hover. */
        private const val REAR_SPIT_SECONDS = 0.2f

        /**
         * Its swoop sets off at [SWOOP_LAUNCH] of its speed and reaches the whole of it over
         * [SWOOP_GATHER_SECONDS], so the dive is seen to start.
         */
        private const val SWOOP_LAUNCH = 0.3f
        private const val SWOOP_GATHER_SECONDS = 0.25f

        /**
         * The most its heading may turn from straight left, either way: as near straight down or up
         * as a dive needs, never so far that its head would turn upside down.
         */
        private const val STEEPEST = 80f

        /** Its weave: how far either side of the bat's height it crosses, and how it undulates. */
        private const val WEAVE_JITTER = 40f
        private const val WEAVE_HIGHEST = 90f
        private const val WEAVE_LOWEST = 90f
        private const val WEAVE_AMPLITUDE = 64f
        private const val WEAVE_WAVELENGTH = 300f
        private const val WEAVE_FIRST_SPIT_SECONDS = 0.5f
        private const val WEAVE_SPIT_SECONDS = 1.15f

        /** Its rain: how high it crosses, under the HUD's top lines, and how it bobs as it goes. */
        private const val RAIN_Y = 60f
        private const val RAIN_BOB = 8f
        private const val RAIN_WAVELENGTH = 160f

        /** How fast its head turns, in degrees a tick, and the least it moves to count as moving. */
        private const val TURN_PER_TICK = 9f
        private const val MOVING = 0.6f

        /** Its head as it lurks above the frame, pointing as near straight down as it ever does. */
        private const val HEAD_DOWN = -STEEPEST

        /** Seconds between the volleys of one burst. */
        private const val VOLLEY_GAP_SECONDS = 0.22f

        private const val DEGREES_PER_RADIAN = 57.29578f
        private const val TWO_PI = (2.0 * PI).toFloat()
        private const val EPSILON = 1e-3f

        /** Longer than any fight: the brain fires the gun, never its cadence. */
        const val TRIGGERED_INTERVAL = 1_000_000f

        /** What it spits: a fan at the bat, a wider one from its second phase. */
        private val FAN = Volley(
            ShotPattern.AIMED_FAN,
            count = 3,
            spreadDegrees = 13f,
            speed = 2.7f,
            damageFactor = SKY_VIPER_SPIT_DAMAGE,
        )
        private val WIDE_FAN = Volley(
            ShotPattern.AIMED_FAN,
            count = 5,
            spreadDegrees = 11f,
            speed = 2.9f,
            damageFactor = SKY_VIPER_SPIT_DAMAGE,
        )

        /** A drop of its rain, falling straight. */
        private val DROP =
            Volley(ShotPattern.DOWN, speed = 2.3f, damageFactor = SKY_VIPER_DROP_DAMAGE)

        /** The ring it throws where an enraged swoop was aimed. */
        private val RING =
            Volley(
                ShotPattern.RADIAL,
                count = 12,
                speed = 2.1f,
                damageFactor = SKY_VIPER_RING_DAMAGE
            )

        /** The gun it is built with, which the brain loads with one chosen volley at a time. */
        val GUN = EnemyGun(TRIGGERED_INTERVAL, listOf(FAN))

        /** Its passes, round and round, in each phase. */
        private val OPENING_CYCLE = listOf(Pass.SWOOP, Pass.WEAVE)
        private val RAIN_CYCLE = listOf(Pass.SWOOP, Pass.WEAVE, Pass.RAIN)
        private val ENRAGED_CYCLE = listOf(Pass.SWOOP, Pass.RAIN, Pass.SWOOP, Pass.WEAVE)

        /** What it spits in [phase]. */
        fun spitFor(phase: Int): Volley = if (phase >= 2) WIDE_FAN else FAN

        /** The phase its health puts it in. */
        fun phaseFor(healthFraction: Float): Int = when {
            healthFraction <= SKY_VIPER_PHASE_3_AT -> 3
            healthFraction <= SKY_VIPER_PHASE_2_AT -> 2
            else -> 1
        }

        /** [degrees] into -180..180. */
        private fun normalized(degrees: Float): Float {
            var turned = degrees % 360f
            if (turned > 180f) turned -= 360f
            if (turned <= -180f) turned += 360f
            return turned
        }

        /** The heading [degrees] turned back to within [STEEPEST] of straight left. */
        private fun leftward(degrees: Float): Float {
            val off = normalized(degrees - 180f).coerceIn(-STEEPEST, STEEPEST)
            return 180f + off
        }

        /** Smoothstep over 0..1. */
        private fun eased(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return c * c * (3f - 2f * c)
        }
    }
}
