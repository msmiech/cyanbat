package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.util.SHOT_SPEED
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyBehaviorSystem
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LifetimeComponent
import at.smiech.engine.ecs.MovementSystem
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.PlayerInputSystem
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.VelocityComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.math.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Flies the bat for the recorder, as a virtual game pad on the run's [ControlHandler] - the seam a
 * real controller backend would feed.
 *
 * Every tick it copies everything hostile into a scratch [World] and runs it forward through the
 * engine's own [MovementSystem] and [EnemyBehaviorSystem], so it knows where the enemies, their shots
 * and the scenery will be over the next second. Then it weighs a grid of places the bat could fly
 * to: how long each stays clear of all of that, and, among the safe ones, how well it lines the
 * bat's guns up on something. It steers for the best one at stick speed, like a player would.
 *
 * It sees the world and nothing else: the run is not changed for it, it can be hit, and it can die.
 * What it cannot see coming is anything not yet on screen, and shots not yet fired.
 */
class Autopilot(
    private val controls: ControlHandler,
    private val frameWidth: Int,
    private val frameHeight: Int,
) {
    private var targetX = Float.NaN
    private var targetY = Float.NaN

    /** Sets the stick for the next tick. */
    fun fly(world: World, batId: EntityId) {
        val bat = world.getComponent(batId, TransformComponent::class)?.rect
        val alive = world.getComponent(batId, HealthComponent::class)?.alive == true
        if (bat == null || !alive) {
            letGo()
            return
        }
        val tolerance = world.getComponent(batId, CollisionComponent::class)?.tolerance ?: 0f
        val cooldown = world.getComponent(batId, PlayerControlComponent::class)?.hitCooldown ?: 0f

        val forecast = Forecast.of(world, bat)
        val plan = Plan(
            forecast = forecast,
            startX = bat.centerX,
            startY = bat.centerY,
            halfWidth = bat.width / 2f - tolerance,
            halfHeight = bat.height / 2f - tolerance,
            batHalfWidth = bat.width / 2f,
            batHalfHeight = bat.height / 2f,
            frameHeight = frameHeight.toFloat(),
            // Ticks inside the mercy window after a hit, when nothing can hurt the bat.
            protectedTicks = (cooldown / TICK).toInt(),
        )

        var bestScore = Float.NEGATIVE_INFINITY
        var bestX = bat.centerX
        var bestY = bat.centerY
        forEachCandidate(bat) { x, y ->
            val score = plan.score(x, y, targetX, targetY)
            if (score > bestScore) {
                bestScore = score
                bestX = x
                bestY = y
            }
        }
        targetX = bestX
        targetY = bestY
        steerToward(bat, bestX, bestY)
    }

    /** Centers the stick: nothing to steer, or a dialog is up. */
    fun letGo() {
        controls.onAxis(0f, 0f)
        targetX = Float.NaN
        targetY = Float.NaN
    }

    /**
     * The card to take from a level up offer.
     *
     * Chosen for the footage as much as for survival. More guns first, since a fan of shots is
     * what a run looks like at its best; then everything that keeps the bat flying; and damage
     * last, because a bat that has stacked it takes a boss apart before its fight gets going.
     */
    fun choose(offer: List<PowerUp>): Int =
        offer.indices.minBy { PICK_ORDER.indexOf(offer[it]).let { rank -> if (rank < 0) PICK_ORDER.size else rank } }

    /** Places the bat's center could head for: a coarse grid over the left of the frame, and the ground close by. */
    private inline fun forEachCandidate(bat: Rect, action: (Float, Float) -> Unit) {
        val minX = bat.width / 2f
        val maxX = frameWidth * MAX_X_FRACTION
        val minY = bat.height / 2f
        val maxY = frameHeight - bat.height / 2f
        var x = minX
        while (x <= maxX) {
            var y = minY
            while (y <= maxY) {
                action(x, y)
                y += GRID_STEP_Y
            }
            x += GRID_STEP_X
        }
        for (ring in NEAR_RINGS) {
            for (direction in 0 until NEAR_DIRECTIONS) {
                val angle = direction * (2.0 * Math.PI / NEAR_DIRECTIONS)
                action(
                    (bat.centerX + ring * cos(angle).toFloat()).coerceIn(minX, frameWidth - minX),
                    (bat.centerY + ring * sin(angle).toFloat()).coerceIn(minY, maxY),
                )
            }
        }
        action(bat.centerX, bat.centerY)
    }

    /**
     * Sets the stick so the bat covers the way to ([x], [y]) in the next tick, or as much of it as
     * full speed allows. Through the pad's dead zone, which the handler rescales: the stick is pushed
     * past it by the amount that comes out as the deflection wanted.
     */
    private fun steerToward(bat: Rect, x: Float, y: Float) {
        var moveX = (x - bat.centerX) / SPEED_PER_TICK
        var moveY = (y - bat.centerY) / SPEED_PER_TICK
        val deflection = hypot(moveX, moveY)
        if (deflection > 1f) {
            moveX /= deflection
            moveY /= deflection
        }
        controls.onAxis(rawAxis(moveX), rawAxis(moveY))
    }

    private fun rawAxis(value: Float): Float {
        if (abs(value) < MIN_DEFLECTION) return 0f
        val deadZone = ControlHandler.AXIS_DEAD_ZONE
        return sign(value) * (deadZone + (1f - deadZone) * abs(value).coerceAtMost(1f))
    }

    /**
     * Where everything that can hurt the bat will be on each of the next [HORIZON] ticks, as the
     * collision boxes the game tests, already shrunk by their tolerance.
     */
    private class Forecast(
        val count: Int,
        /** Which of them are enemies, the things worth shooting; the rest are shots and scenery. */
        val isEnemy: BooleanArray,
        val isBoss: BooleanArray,
        private val boxes: FloatArray,
        /** Each thing's box over the whole horizon, for a quick "can it matter at all" test. */
        val sweep: FloatArray,
    ) {
        fun left(tick: Int, k: Int) = boxes[(tick * count + k) * 4]
        fun top(tick: Int, k: Int) = boxes[(tick * count + k) * 4 + 1]
        fun right(tick: Int, k: Int) = boxes[(tick * count + k) * 4 + 2]
        fun bottom(tick: Int, k: Int) = boxes[(tick * count + k) * 4 + 3]

        companion object {
            /**
             * Runs a copy of [world]'s hostiles forward. Tick 0 is where they are now.
             *
             * Movement runs before behavior, as it does in the game: the velocity an enemy's pattern
             * sets on one tick is the one it moves by on the next. The bat is left where it is, as a
             * target for the patterns that aim at it; the ones that commit to a line re-aim as the
             * plan is remade every tick.
             */
            fun of(world: World, bat: Rect): Forecast {
                val scratch = World()
                scratch.addSystem(MovementSystem())
                scratch.addSystem(EnemyBehaviorSystem())
                val standIn = scratch.createEntity()
                scratch.addComponent(standIn, TransformComponent(bat))
                scratch.addComponent(standIn, PlayerControlComponent())
                scratch.addComponent(standIn, HealthComponent())

                val copies = ArrayList<EntityId>()
                val enemies = ArrayList<Boolean>()
                val tolerances = ArrayList<Float>()
                val bosses = ArrayList<Boolean>()
                for (id in world.query(TransformComponent::class, CollisionComponent::class)) {
                    val collision = world.getComponent(id, CollisionComponent::class) ?: continue
                    if (collision.group !in HOSTILE) continue
                    if (world.getComponent(id, HealthComponent::class)?.alive == false) continue
                    val enemy = collision.group == CollisionGroup.ENEMY
                    val copy = scratch.createEntity()
                    scratch.addComponent(copy, TransformComponent(world.getComponent(id, TransformComponent::class)!!.rect))
                    world.getComponent(id, VelocityComponent::class)?.let {
                        scratch.addComponent(copy, VelocityComponent(it.velocity))
                    }
                    world.getComponent(id, EnemyBehaviorComponent::class)?.let { scratch.addComponent(copy, it.copy()) }
                    copies += copy
                    enemies += enemy
                    tolerances += collision.tolerance
                    // Only a boss is never culled for leaving the frame.
                    bosses += enemy && world.getComponent(id, LifetimeComponent::class)?.removeIfOutOfBounds == false
                }

                val count = copies.size
                val boxes = FloatArray((HORIZON + 1) * count * 4)
                val sweep = FloatArray(count * 4)
                for (k in 0 until count) {
                    sweep[k * 4] = Float.MAX_VALUE
                    sweep[k * 4 + 1] = Float.MAX_VALUE
                    sweep[k * 4 + 2] = -Float.MAX_VALUE
                    sweep[k * 4 + 3] = -Float.MAX_VALUE
                }
                for (tick in 0..HORIZON) {
                    if (tick > 0) scratch.update(TICK, null)
                    for (k in 0 until count) {
                        val rect = scratch.getComponent(copies[k], TransformComponent::class)!!.rect
                        val tolerance = tolerances[k]
                        val base = (tick * count + k) * 4
                        boxes[base] = rect.left + tolerance
                        boxes[base + 1] = rect.top + tolerance
                        boxes[base + 2] = rect.right - tolerance
                        boxes[base + 3] = rect.bottom - tolerance
                        sweep[k * 4] = min(sweep[k * 4], boxes[base])
                        sweep[k * 4 + 1] = min(sweep[k * 4 + 1], boxes[base + 1])
                        sweep[k * 4 + 2] = maxOf(sweep[k * 4 + 2], boxes[base + 2])
                        sweep[k * 4 + 3] = maxOf(sweep[k * 4 + 3], boxes[base + 3])
                    }
                }
                return Forecast(count, enemies.toBooleanArray(), bosses.toBooleanArray(), boxes, sweep)
            }
        }
    }

    /** One tick's weighing of candidate destinations against a [Forecast]. */
    private class Plan(
        private val forecast: Forecast,
        private val startX: Float,
        private val startY: Float,
        private val halfWidth: Float,
        private val halfHeight: Float,
        private val batHalfWidth: Float,
        private val batHalfHeight: Float,
        private val frameHeight: Float,
        private val protectedTicks: Int,
    ) {
        private val relevant = IntArray(forecast.count)

        fun score(x: Float, y: Float, previousX: Float, previousY: Float): Float {
            val distance = hypot(x - startX, y - startY)
            val arrival = ceil(distance / SPEED_PER_TICK).toInt()

            val hardHit = firstHit(x, y, distance, arrival, HARD_MARGIN, ignoreProtected = true)
            val softHit = if (hardHit > HORIZON) firstHit(x, y, distance, arrival, SOFT_MARGIN, ignoreProtected = false) else hardHit

            var score = W_HARD * hardHit / (HORIZON + 1f) + W_SOFT * softHit / (HORIZON + 1f)
            score += W_AIM * aim(x, y, arrival)
            score -= W_X * square((x - PREFERRED_X) / PREFERRED_X_SPREAD)
            score -= W_EDGE * (square(((EDGE_BAND - y) / EDGE_BAND).coerceAtLeast(0f)) +
                square(((y - (frameHeight - EDGE_BAND)) / EDGE_BAND).coerceAtLeast(0f)))
            score -= W_CENTER * square((y - frameHeight / 2f) / (frameHeight / 2f))
            // Behind the score column the bat is hard to see, and so is the score.
            score -= W_HUD * hudCover(x, y)
            score -= W_MOVE * distance / 100f
            if (!previousX.isNaN()) {
                score += W_KEEP * (1f - hypot(x - previousX, y - previousY) / KEEP_RADIUS).coerceAtLeast(0f)
            }
            return score
        }

        /**
         * The first tick on which the bat, flying straight for ([x], [y]) at full speed and holding
         * there, overlaps something hostile grown by [margin] - or [HORIZON] + 1 if it never does.
         */
        private fun firstHit(
            x: Float,
            y: Float,
            distance: Float,
            arrival: Int,
            margin: Float,
            ignoreProtected: Boolean,
        ): Int {
            // Only what the whole flight could come near is worth testing tick by tick.
            val reachLeft = min(startX, x) - halfWidth - margin
            val reachRight = maxOf(startX, x) + halfWidth + margin
            val reachTop = min(startY, y) - halfHeight - margin
            val reachBottom = maxOf(startY, y) + halfHeight + margin
            var relevantCount = 0
            for (k in 0 until forecast.count) {
                val s = k * 4
                if (forecast.sweep[s] < reachRight && forecast.sweep[s + 2] > reachLeft &&
                    forecast.sweep[s + 1] < reachBottom && forecast.sweep[s + 3] > reachTop
                ) {
                    relevant[relevantCount++] = k
                }
            }
            if (relevantCount == 0) return HORIZON + 1

            val dx = if (distance > 0f) (x - startX) / distance else 0f
            val dy = if (distance > 0f) (y - startY) / distance else 0f
            for (tick in 1..HORIZON) {
                if (ignoreProtected && tick <= protectedTicks) continue
                val travelled = if (tick >= arrival) distance else tick * SPEED_PER_TICK
                val cx = startX + dx * travelled
                val cy = startY + dy * travelled
                val left = cx - halfWidth - margin
                val right = cx + halfWidth + margin
                val top = cy - halfHeight - margin
                val bottom = cy + halfHeight + margin
                for (i in 0 until relevantCount) {
                    val k = relevant[i]
                    if (left < forecast.right(tick, k) && right > forecast.left(tick, k) &&
                        top < forecast.bottom(tick, k) && bottom > forecast.top(tick, k)
                    ) {
                        return tick
                    }
                }
            }
            return HORIZON + 1
        }

        /**
         * How much a straight shot fired from ([x], [y]) once the bat is there would find to hit:
         * every enemy whose box its path crosses on the way, the nearer the better, a boss the most.
         */
        private fun aim(x: Float, y: Float, arrival: Int): Float {
            val muzzle = x + batHalfWidth
            val start = arrival.coerceAtMost(HORIZON)
            var total = 0f
            for (k in 0 until forecast.count) {
                if (!forecast.isEnemy[k]) continue
                if (forecast.right(start, k) < muzzle) continue
                for (tick in start + 1..HORIZON) {
                    val shotLeft = muzzle + SHOT_SPEED * (tick - start)
                    if (shotLeft + SHOT_LENGTH < forecast.left(tick, k)) continue
                    if (shotLeft > forecast.right(tick, k)) break
                    if (y + SHOT_HALF_HEIGHT > forecast.top(tick, k) && y - SHOT_HALF_HEIGHT < forecast.bottom(tick, k)) {
                        val nearness = 1f - ((forecast.left(start, k) - muzzle) / FAR_AWAY).coerceIn(0f, 1f)
                        total += (if (forecast.isBoss[k]) BOSS_WEIGHT else 1f) * (0.5f + nearness)
                        break
                    }
                }
            }
            return total
        }

        /** How much of the HUD's left column a bat centered on ([x], [y]) would cover, as 0..1 of its own box. */
        private fun hudCover(x: Float, y: Float): Float {
            val overlapX = (min(x + batHalfWidth, HUD_RIGHT) - maxOf(x - batHalfWidth, 0f)).coerceAtLeast(0f)
            val overlapY = (min(y + batHalfHeight, HUD_BOTTOM) - maxOf(y - batHalfHeight, 0f)).coerceAtLeast(0f)
            return overlapX * overlapY / (4f * batHalfWidth * batHalfHeight)
        }

        private fun square(value: Float) = value * value
    }

    private companion object {
        const val TICK = TICK_INITIAL

        /** How far the bat moves in a tick at full deflection. */
        const val SPEED_PER_TICK = PlayerInputSystem.DIRECTIONAL_SPEED * TICK

        /**
         * How many ticks ahead the plan looks, just under a second: time enough to get out of the way
         * of anything on screen, and not so long that the enemies' patterns have drifted from where
         * the forecast puts them.
         */
        const val HORIZON = 48

        /** What the bat must not touch. */
        val HOSTILE = setOf(CollisionGroup.ENEMY, CollisionGroup.ENEMY_PROJECTILE, CollisionGroup.OBSTACLE)

        const val GRID_STEP_X = 20f
        const val GRID_STEP_Y = 12f
        val NEAR_RINGS = floatArrayOf(6f, 14f, 26f)
        const val NEAR_DIRECTIONS = 12

        /** The bat keeps to the left of this share of the frame, where it has room to react. */
        const val MAX_X_FRACTION = 0.58f

        /** Grown onto every box before a collision counts, for what the forecast gets slightly wrong. */
        const val HARD_MARGIN = 2f

        /** Clearance the bat would like to keep, as a lesser weight than a hit. */
        const val SOFT_MARGIN = 12f

        /** The bat's shot: where it flies from the bat's center line, and its collision box. */
        const val SHOT_HALF_HEIGHT = 4f
        const val SHOT_LENGTH = 20f
        const val FAR_AWAY = 360f
        const val BOSS_WEIGHT = 3f

        /** Where the bat idles when nothing else decides it: a third of the way in, as a player holds it. */
        const val PREFERRED_X = 110f
        const val PREFERRED_X_SPREAD = 150f

        /** The strips along the top and bottom edges the scenery grows out of. */
        const val EDGE_BAND = 40f

        /** How near the last choice a candidate has to be to count as keeping to it. */
        const val KEEP_RADIUS = 30f

        /** The score, combo and wave readouts down the top left of the frame. */
        const val HUD_RIGHT = 115f
        const val HUD_BOTTOM = 66f

        /*
         * How the plan trades all of that off. A hit outweighs everything, and keeping clear comes
         * next; then lining up shots. The rest only choose between places that are all safe, and
         * keep the flight calm enough to watch: a bat twitching between two spots that score
         * nearly alike looks like nobody is playing it.
         */
        const val W_HARD = 100f
        const val W_SOFT = 24f
        const val W_AIM = 9f
        const val W_X = 4f
        const val W_EDGE = 6f
        const val W_CENTER = 2f
        const val W_HUD = 10f
        const val W_MOVE = 1.5f
        const val W_KEEP = 3f

        const val MIN_DEFLECTION = 0.01f

        val PICK_ORDER = listOf(
            PowerUp.SPREAD_SHOT,
            PowerUp.RAPID_FIRE,
            PowerUp.SECOND_LIFE,
            PowerUp.VITALITY,
            PowerUp.ARMOR_PLATING,
            PowerUp.REGENERATION,
            PowerUp.SECOND_WIND,
            PowerUp.PIERCING_SHOT,
            PowerUp.FAST_LEARNER,
            PowerUp.BOUNTY_HUNTER,
            PowerUp.SHARPSHOOTER,
            PowerUp.COUNTERWEIGHT,
            // Shots that bounce back off the far edge fill the frame with the bat's own fire.
            PowerUp.RICOCHET,
            PowerUp.HEAVY_ROUNDS,
        )
    }
}
