package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.util.SAND_WYRM_BREACH_SPEED
import at.smiech.cyanbat.util.SAND_WYRM_BURROW_SECONDS
import at.smiech.cyanbat.util.SAND_WYRM_ENRAGED_BREACH_SPEED
import at.smiech.cyanbat.util.SAND_WYRM_ENRAGED_BURROW_SECONDS
import at.smiech.cyanbat.util.SAND_WYRM_FRAME
import at.smiech.cyanbat.util.SAND_WYRM_PHASE_2_AT
import at.smiech.cyanbat.util.SAND_WYRM_PHASE_3_AT
import at.smiech.cyanbat.util.SAND_WYRM_RING_DAMAGE
import at.smiech.cyanbat.util.SAND_WYRM_SPACING
import at.smiech.cyanbat.util.SAND_WYRM_SPIT_DAMAGE
import at.smiech.cyanbat.util.SAND_WYRM_TELL_SECONDS
import at.smiech.engine.Pixmap
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The Sand Wyrm's fight: breaches out of the dunes aimed at the player, diving back under between
 * them, in three phases marked by its health.
 *
 * 1. **Hunting.** While it is under, the sand boils where it is about to come up - the tell - and
 *    then it breaches in an arc that tops out at the player's height, spits a fan of three from
 *    the top of it, and goes back under. Its first breach is a warning, topping out well ahead
 *    of the player: the top of an arc is the highest the whole wyrm goes, so a player who has
 *    seen one arc go by knows that above it is out of reach.
 * 2. **The brood.** From two thirds it spits a ring and an aimed bolt at the top of every arc, and
 *    calls a wyrmling up out of the sand with every breach.
 * 3. **Enraged.** From one third it stays under for less time, crosses the frame faster, rings
 *    fourteen and fans five, and calls up two at a time.
 *
 * The arc is the engine's own [EnemyMovementType.LEAP], thrown once at the player's height and
 * gravity's after that, so the head flies exactly the way its brood do - and anything that
 * forecasts a leap forecasts the wyrm. What this adds is the body: every part is laid along the path
 * the head has flown, a fixed distance behind the one in front of it, so the whole wyrm pours out of
 * one hole in the sand, over the arc and down into the next. It always crosses right to left, like
 * every hostile, because its art faces that way and would fly upside down the other.
 *
 * Phases are read off its health, as the Moth Queen's are, and a hit that crosses both thresholds
 * plays both entrances in order.
 *
 * @param parts the body, from the neck back to the tail. They are the brain's to move: nothing else
 *   moves, culls or kills them, which is what makes it safe to hold their ids for the whole fight.
 * @param onSummon called with how many wyrmlings to call up; the generator is what knows how.
 * @param onPhaseChanged called with the phase it has just entered, 2 or 3, for the screen to announce.
 */
class SandWyrmBrain(
    private val world: World,
    private val factory: EntityFactory,
    private val sheet: Pixmap,
    private val headId: EntityId,
    private val parts: List<EntityId>,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val onSummon: (count: Int) -> Unit = {},
    private val onPhaseChanged: (Int) -> Unit = {},
) : BossBrain {
    var phase = 1
        private set

    /** Whether it is under the sand between breaches, or out and in the air. */
    var burrowed = true
        private set

    /** Counts down while it is under; it breaches at zero. The first wait lets its name be read. */
    private var untilBreach = ENTRANCE_SECONDS

    /** Where it will come up next, as the x of its head's center, once the tell has begun. */
    var breachX = Float.NaN
        private set

    /** How many times it has come up, so its first can be the warning. */
    private var breaches = 0

    private var untilPlume = 0f
    private var sawRising = false
    private var spat = false
    private var volleysLeft = 0
    private var untilVolley = 0f

    // The path the head has flown, newest last, as a ring of points at least a pixel apart. It is
    // only ever as long as the body needs: the parts are laid back along it by distance.
    private val pathX = FloatArray(PATH_POINTS)
    private val pathY = FloatArray(PATH_POINTS)
    private var newest = -1
    private var points = 0

    init {
        rectOf(headId)?.let { bury(it.centerX) }
        layBody()
    }

    override fun update(deltaTime: Float) {
        val health = world.getComponent(headId, HealthComponent::class) ?: return
        if (!health.alive) return

        val due = phaseFor(health.fraction)
        while (phase < due) enter(phase + 1)

        rectOf(headId)?.let { record(it.centerX, it.centerY) }
        layBody()

        if (burrowed) waitUnder(deltaTime) else fly(deltaTime)
    }

    private fun waitUnder(deltaTime: Float) {
        untilBreach -= deltaTime
        // The tell starts here, which is also when it picks its spot: as late as it can, so the
        // breach is aimed at where the player is rather than where they were a second ago.
        if (breachX.isNaN() && untilBreach <= SAND_WYRM_TELL_SECONDS) {
            breachX = chooseBreach()
            bury(breachX)
            untilPlume = 0f
        }
        if (!breachX.isNaN()) {
            untilPlume -= deltaTime
            if (untilPlume <= 0f) {
                untilPlume += PLUME_GAP_SECONDS
                factory.createSandPlume(breachX, frameHeight.toFloat(), sheet)
            }
        }
        if (untilBreach <= 0f) breach()
    }

    private fun breach() {
        breaches++
        burrowed = false
        sawRising = false
        spat = false
        val top = rectOf(headId)?.top ?: return
        // A leap told to leap at once: its station is behind it before it starts, so on its next
        // tick the movement throws it at the player's height and hands it to gravity.
        world.addComponent(
            headId,
            EnemyBehaviorComponent(
                EnemyMovementType.LEAP,
                initialY = top,
                holdX = Float.MAX_VALUE,
                baseSpeedX = -breachSpeed(),
            ),
        )
        factory.createSandPlume(breachX, frameHeight.toFloat(), sheet, scale = BREACH_PLUME_SCALE)
        breachX = Float.NaN
        if (phase >= 2) onSummon(if (phase >= 3) 2 else 1)
    }

    private fun fly(deltaTime: Float) {
        val vy = world.getComponent(headId, VelocityComponent::class)?.velocity?.y ?: return
        if (vy < 0f) sawRising = true

        // The top of the arc: its velocity has turned from climbing to falling.
        if (sawRising && !spat && vy >= 0f) {
            spat = true
            volleysLeft = world.getComponent(headId, GunComponent::class)?.volleys?.size ?: 0
            untilVolley = 0f
        }
        if (volleysLeft > 0) {
            untilVolley -= deltaTime
            if (untilVolley <= 0f) {
                untilVolley += VOLLEY_GAP_SECONDS
                volleysLeft--
                pullTrigger()
            }
        }

        if (sawRising && vy > 0f && isUnder(headId) && parts.all { isUnder(it) }) {
            burrowed = true
            untilBreach = if (phase >= 3) SAND_WYRM_ENRAGED_BURROW_SECONDS else SAND_WYRM_BURROW_SECONDS
            rectOf(headId)?.let { rest(it.top) }
        }
    }

    /**
     * Fires the head's gun on the next tick, through the same weapon every other enemy fires by.
     * Its cadence is set so long that it never comes round on its own: this is the only thing that
     * pulls the trigger, and it does so at the top of each arc.
     */
    private fun pullTrigger() {
        world.getComponent(headId, WeaponComponent::class)?.let { it.timeSinceLastShot = it.interval }
    }

    private fun enter(next: Int) {
        phase = next
        val gun = gunFor(next)
        world.getComponent(headId, GunComponent::class)?.apply {
            volleys = gun.volleys
            this.next = 0
        }
        onPhaseChanged(next)
    }

    private fun breachSpeed(): Float = if (phase >= 3) SAND_WYRM_ENRAGED_BREACH_SPEED else SAND_WYRM_BREACH_SPEED

    /**
     * Where to come up so the top of the arc lands on the bat: ahead of it by as far as the head
     * travels on the way up. The first time, further ahead than that, so the arc goes by in front
     * of the bat rather than through it. Kept off the frame's edges, so the whole arc is seen.
     */
    private fun chooseBreach(): Float {
        val bat = batCenterX() ?: (frameWidth / 3f)
        val reach = breachSpeed() * LEAP_FORWARD * TICKS_TO_APEX
        val warning = if (breaches == 0) WARNING_LEAD else 0f
        return (bat + reach + warning).coerceIn(BREACH_MIN_X, frameWidth - BREACH_EDGE_MARGIN)
    }

    private fun batCenterX(): Float? {
        for (id in world.query(PlayerControlComponent::class, TransformComponent::class)) {
            if (world.getComponent(id, HealthComponent::class)?.alive == false) continue
            return world.getComponent(id, TransformComponent::class)?.rect?.centerX
        }
        return null
    }

    /**
     * Puts the head under the sand at [x], still, with the body hanging straight down beneath it -
     * so when it breaches, the body follows it up out of the same hole. Only ever done with the
     * whole wyrm under, where the jump cannot be seen.
     */
    private fun bury(x: Float) {
        val top = frameHeight + BURIED_DEPTH
        world.getComponent(headId, TransformComponent::class)?.rect = Rect.fromLTWH(x - HALF, top, FRAME, FRAME)
        world.getComponent(headId, VelocityComponent::class)?.velocity = Vector2.Zero
        rest(top)

        points = 0
        newest = -1
        val bodyLength = SAND_WYRM_SPACING * (parts.size + 1)
        var depth = bodyLength
        while (depth >= 0f) {
            record(x, top + HALF + depth, force = true)
            depth -= BURIED_STEP
        }
    }

    /** Holds the head where it is: a leap whose station is out of reach cruises, at no speed. */
    private fun rest(top: Float) {
        world.addComponent(
            headId,
            EnemyBehaviorComponent(EnemyMovementType.LEAP, initialY = top, holdX = -Float.MAX_VALUE),
        )
    }

    private fun record(x: Float, y: Float, force: Boolean = false) {
        if (!force && points > 0 && hypot(x - pathX[newest], y - pathY[newest]) < 1f) return
        newest = (newest + 1) % PATH_POINTS
        pathX[newest] = x
        pathY[newest] = y
        if (points < PATH_POINTS) points++
    }

    /**
     * Lays each part along the head's path, [SAND_WYRM_SPACING] behind the one in front of it, and
     * turns it to lie along the path there.
     *
     * By distance along the path, not by how many ticks ago the head was there: the head slows over
     * the top of every arc, and a body laid out by time would bunch up there and string out on the
     * way down.
     *
     * Each part is drawn from the head's row of the sheet as it goes. The plates have no health to
     * be wounded by, and a battered head on a pristine body would read as two animals.
     */
    private fun layBody() {
        val head = rectOf(headId) ?: return
        val woundRow = world.getComponent(headId, SpriteComponent::class)?.srcY ?: 0
        var x = head.centerX
        var y = head.centerY
        var aheadX = x
        var aheadY = y
        var cursor = newest
        var remaining = points
        for (part in parts) {
            // Walk SAND_WYRM_SPACING further back along the path.
            var left = SAND_WYRM_SPACING
            while (left > 0f) {
                if (remaining == 0) {
                    // Off the end of the path: the rest of the body is still straight down in the sand.
                    y += left
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
            place(part, x, y, aheadX, aheadY)
            world.getComponent(part, SpriteComponent::class)?.srcY = woundRow
            aheadX = x
            aheadY = y
        }
    }

    private fun place(id: EntityId, x: Float, y: Float, aheadX: Float, aheadY: Float) {
        val transform = world.getComponent(id, TransformComponent::class) ?: return
        val old = transform.rect
        val rect = Rect.fromLTWH(x - HALF, y - HALF, FRAME, FRAME)
        transform.rect = rect
        // What it moved this tick, so anything reading velocities - a forecast - sees it going the
        // way it is going. A jump under the sand is not movement and is left out.
        val dx = rect.left - old.left
        val dy = rect.top - old.top
        world.getComponent(id, VelocityComponent::class)?.velocity =
            if (abs(dx) + abs(dy) > FRAME) Vector2.Zero else Vector2(dx, dy)

        // Along the path toward the part in front, less the half turn the artwork already faces.
        if (aheadX != x || aheadY != y) {
            val heading = atan2(aheadY - y, aheadX - x) * DEGREES_PER_RADIAN
            world.getComponent(id, SpriteComponent::class)?.rotationDegrees = normalized(heading - 180f)
        }
    }

    private fun normalized(degrees: Float): Float {
        var turned = degrees % 360f
        if (turned > 180f) turned -= 360f
        if (turned <= -180f) turned += 360f
        return turned
    }

    private fun isUnder(id: EntityId): Boolean = (rectOf(id)?.top ?: Float.MAX_VALUE) > frameHeight + UNDER_MARGIN

    private fun rectOf(id: EntityId): Rect? = world.getComponent(id, TransformComponent::class)?.rect

    companion object {
        private const val FRAME = SAND_WYRM_FRAME.toFloat()
        private const val HALF = FRAME / 2f

        /** How long it stays under when it first arrives, so the banner announcing it can be read. */
        private const val ENTRANCE_SECONDS = 2.4f

        /** How far below the bottom edge the head waits, so not a pixel of it shows. */
        private const val BURIED_DEPTH = 20f
        private const val BURIED_STEP = 4f
        private const val UNDER_MARGIN = 2f

        /** Seconds between the sprays the tell throws up, and how much bigger the breach's own is. */
        private const val PLUME_GAP_SECONDS = 0.16f
        private const val BREACH_PLUME_SCALE = 1.5f

        /** Seconds between the volleys spat at the top of one arc. */
        private const val VOLLEY_GAP_SECONDS = 0.22f

        /**
         * The leap's forward push and roughly how long a breach takes to reach its top, in ticks,
         * from which how far ahead of the bat to come up. Rough on purpose: the apex is aimed
         * exactly in height and only near the bat across, so the player is threatened rather than
         * hit, and the answer is always to move.
         */
        private const val LEAP_FORWARD = 1.25f
        private const val TICKS_TO_APEX = 62f
        private const val BREACH_MIN_X = 130f

        /**
         * How much further ahead than an aimed breach the first one comes up. The arc is level
         * with the bat only at its top, but it keeps coming on as it falls, so the top has to be
         * far enough ahead that the way down is well below the bat by the time it gets there: at
         * two thirds of this, it went by within a few pixels.
         */
        private const val WARNING_LEAD = 170f
        private const val BREACH_EDGE_MARGIN = 26f

        /** Enough path for the body at its most strung out, a point at least a pixel apart. */
        private const val PATH_POINTS = 512

        private const val DEGREES_PER_RADIAN = 57.29578f

        /** Far longer than any fight: the brain pulls the trigger, never the cadence. */
        const val TRIGGERED_INTERVAL = 1_000_000f

        /** What it hunts with: a fan of three from the top of each arc. */
        val HUNTING_GUN = EnemyGun(
            TRIGGERED_INTERVAL,
            listOf(
                Volley(
                    ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 12f, speed = 2.6f,
                    damageFactor = SAND_WYRM_SPIT_DAMAGE,
                ),
            ),
        )

        /** A ring to thread, and a bolt at the player so threading it in place is not enough. */
        private val BROOD_GUN = EnemyGun(
            TRIGGERED_INTERVAL,
            listOf(
                Volley(ShotPattern.RADIAL, count = 10, speed = 2.0f, damageFactor = SAND_WYRM_RING_DAMAGE),
                Volley(ShotPattern.AIMED, speed = 2.8f, damageFactor = SAND_WYRM_SPIT_DAMAGE),
            ),
        )

        /** Denser and wider: the last third. */
        private val ENRAGED_GUN = EnemyGun(
            TRIGGERED_INTERVAL,
            listOf(
                Volley(ShotPattern.RADIAL, count = 14, speed = 2.2f, damageFactor = SAND_WYRM_RING_DAMAGE),
                Volley(
                    ShotPattern.AIMED_FAN, count = 5, spreadDegrees = 11f, speed = 2.8f,
                    damageFactor = SAND_WYRM_SPIT_DAMAGE,
                ),
            ),
        )

        fun gunFor(phase: Int): EnemyGun = when {
            phase >= 3 -> ENRAGED_GUN
            phase == 2 -> BROOD_GUN
            else -> HUNTING_GUN
        }

        /** The phase its health puts it in. */
        fun phaseFor(healthFraction: Float): Int = when {
            healthFraction <= SAND_WYRM_PHASE_3_AT -> 3
            healthFraction <= SAND_WYRM_PHASE_2_AT -> 2
            else -> 1
        }
    }
}
