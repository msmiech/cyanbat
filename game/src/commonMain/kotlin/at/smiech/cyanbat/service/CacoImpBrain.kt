package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.util.CACO_IMP_ABLAZE_CHARGE_REST_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_COLOR
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_TEMPO
import at.smiech.cyanbat.util.CACO_IMP_BOLT_DAMAGE
import at.smiech.cyanbat.util.CACO_IMP_BREATH
import at.smiech.cyanbat.util.CACO_IMP_BREATHS_PER_SECOND
import at.smiech.cyanbat.util.CACO_IMP_BURN_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_CHARGE_REST_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_CHARGE_SPEED
import at.smiech.cyanbat.util.CACO_IMP_COLD_CHARGE_REST_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_COLD_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_COLD_TINT
import at.smiech.cyanbat.util.CACO_IMP_EMBER_DAMAGE
import at.smiech.cyanbat.util.CACO_IMP_FLARE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_FLICKER
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_COLOR
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_PHASE_2_AT
import at.smiech.cyanbat.util.CACO_IMP_PHASE_3_AT
import at.smiech.cyanbat.util.CACO_IMP_PHASE_4_AT
import at.smiech.cyanbat.util.CACO_IMP_PHASE_5_AT
import at.smiech.cyanbat.util.CACO_IMP_RING_DAMAGE
import at.smiech.cyanbat.util.CACO_IMP_SHIELD_FRACTION
import at.smiech.cyanbat.util.CACO_IMP_SMOULDER_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_SUMMON_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_WINDUP_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_WINDUP_TINT
import at.smiech.cyanbat.util.HIT_FLASH_COLOR
import at.smiech.cyanbat.util.HIT_FLASH_SECONDS
import at.smiech.engine.EngineColors
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.HitFlashComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.ShieldComponent
import at.smiech.engine.ecs.TintComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Caco Imp's fight, in five phases at fifths of its health.
 *
 * 1. **Smouldering.** It weaves on station, alight, firing straight bolts and now and then a fan at
 *    the player.
 * 2. **The charge.** Between spells on station it winds up - rearing back, burning white-hot - and
 *    rushes along the line through where the bat was, out of the left edge, then slides back in
 *    from the right. See [Move].
 * 3. **Embers.** It stops charging and spits embers in fans at the player, which slow, hang and
 *    burst into rings where they hang unless they are shot down first.
 * 4. **Burning and cold.** By turns it burns inside a fire shield, firing rings, until the shield
 *    is shot down or burns out; then it goes cold - light out, sprite ashen, gun silent - and
 *    charges, with nothing between it and the bat's shots. See [burning].
 * 5. **Ablaze.** It lights up for good, brighter and guttering like a fire, weaves faster, fires
 *    fans, rings and embers, charges now and then, and calls in two of its kind every few seconds.
 *
 * None of it rests on the dark. Every tell is drawn on its sprite ([TintComponent]) as well as in
 * its light, and the fire shield is a bubble like any other, so the fight reads the same by day on
 * the endless stage, and in a cave the bat's own shots have lit from end to end.
 *
 * A hit that crosses several thresholds plays each entrance in order. Like every [BossBrain] this
 * only changes state on its components; without a light, a tint or a shield, as in a test, it
 * fights the same fight.
 *
 * @param frameWidth the width of the frame it charges across.
 * @param frameHeight the height of the frame it charges across.
 * @param onSummon called when it calls in its kind; the generator is what knows how to spawn them.
 * @param onPhaseChanged called with the phase it has just entered, 2 to 5, for the screen to
 *   announce.
 * @param onWindup called as it starts to wind up a charge, for the screen to sound: the one tell
 *   that a frame lit from end to end and a boss white with hit flashes cannot hide.
 */
class CacoImpBrain(
    private val world: World,
    private val bossId: EntityId,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val onSummon: () -> Unit = {},
    private val onPhaseChanged: (Int) -> Unit = {},
    private val onWindup: () -> Unit = {},
) : BossBrain {
    /** The phase it is in, 1 to 5. */
    var phase = 1
        private set

    /** Where in its charge it is; see [Move]. */
    var move = Move.STATION
        private set

    /** In the fourth phase, whether it is burning in its shield rather than cold and charging. */
    var burning = false
        private set

    /** Where its station is, as its box's left edge, read where it first holds. */
    private var stationX = Float.NaN

    /** Seconds into the current [move]. */
    private var moveTime = 0f

    /** How far its wind-up's shudder has it off its path, up or down, in frame pixels. */
    private var shudder = 0f

    /** Seconds into the fourth phase's current stance, burning or cold. */
    private var stanceTime = 0f

    /** Seconds of the fight, which its light breathes and gutters by. */
    private var clock = 0f

    /** Seconds until the next summons, in the last phase. */
    private var summonTimer = 0f

    override fun update(deltaTime: Float) {
        val health = world.getComponent(bossId, HealthComponent::class) ?: return
        if (!health.alive) return
        if (stationX.isNaN()) stationX = world.getComponent(bossId, EnemyBehaviorComponent::class)
            ?.holdX ?: return

        val due = phaseFor(health.fraction)
        while (phase < due) enter(phase + 1, health)

        clock += deltaTime
        if (phase == 4) stand(deltaTime, health)
        charge(deltaTime)
        if (move != Move.STATION || (phase == 4 && !burning)) holdFire()
        shine()
        tint()

        if (phase >= 5) {
            summonTimer -= deltaTime
            if (summonTimer <= 0f) {
                summonTimer += CACO_IMP_SUMMON_SECONDS
                onSummon()
            }
        }
    }

    /** Plays the entrance of phase [next]: its gun, and what else the phase begins with. */
    private fun enter(next: Int, health: HealthComponent) {
        phase = next
        arm(gunFor(next))
        when (next) {
            4 -> burn(health)
            5 -> {
                // Whatever was left of its fire shield goes into the blaze.
                world.getComponent(bossId, ShieldComponent::class)?.points = 0
                burning = false
                // Its summons come a moment after it lights up, so the two land as separate events.
                summonTimer = FIRST_SUMMON_DELAY
            }
        }
        // On station it takes up the phase's weave at once, and rests a full spell before any
        // charge, so the banner is read before it comes; mid-charge it does both as it returns.
        if (move == Move.STATION) {
            weave()
            moveTime = 0f
        }
        onPhaseChanged(next)
    }

    /** Hands its weapon [gun], to fire from the top of its volleys. */
    private fun arm(gun: EnemyGun) {
        world.getComponent(bossId, GunComponent::class)?.apply {
            volleys = gun.volleys
            next = 0
        }
        world.getComponent(bossId, WeaponComponent::class)?.apply {
            interval = gun.interval
            timeSinceLastShot = 0f
        }
    }

    /** Keeps its gun from going off, since a gun counts up whether or not it may fire. */
    private fun holdFire() {
        world.getComponent(bossId, WeaponComponent::class)?.timeSinceLastShot = 0f
    }

    // region the fourth phase's stances

    /** One tick of the fourth phase: burning until the shield is down or burnt out, then cold. */
    private fun stand(deltaTime: Float, health: HealthComponent) {
        stanceTime += deltaTime
        if (burning) {
            val shield = world.getComponent(bossId, ShieldComponent::class)
            val shotOut = shield != null && !shield.isUp
            if (shotOut || stanceTime >= CACO_IMP_BURN_SECONDS) goCold()
        } else if (stanceTime >= CACO_IMP_COLD_SECONDS && move == Move.STATION) {
            // Only on station: it does not light up halfway through a charge.
            burn(health)
        }
    }

    /** Lights up inside a fresh fire shield, gun armed with rings. */
    private fun burn(health: HealthComponent) {
        burning = true
        stanceTime = 0f
        world.getComponent(bossId, ShieldComponent::class)
            ?.raise((health.maxHitPoints * CACO_IMP_SHIELD_FRACTION).roundToInt().coerceAtLeast(1))
        arm(BURNING_GUN)
    }

    /**
     * Goes cold: shield and light out, gun silent, and a charge soon - but a short rest first if it
     * is on station, so the change is seen before it rushes.
     */
    private fun goCold() {
        burning = false
        stanceTime = 0f
        world.getComponent(bossId, ShieldComponent::class)?.points = 0
        if (move == Move.STATION) moveTime = 0f
    }

    // endregion

    // region the charge

    /**
     * One tick of its charge, in phases that charge: rest on station, wind up, rush through the
     * frame, slide back in. See [Move].
     */
    private fun charge(deltaTime: Float) {
        moveTime += deltaTime
        val rect = rectOf() ?: return
        when (move) {
            Move.STATION -> {
                val rest = restSeconds() ?: return
                if (moveTime >= rest) {
                    // Rearing back: a short glide right, which the rush starts from.
                    glideTo(rect.left + WINDUP_RECOIL, rect.top, WINDUP_RECOIL_SPEED)
                    step(Move.WINDUP)
                    flashOnHit(false)
                    onWindup()
                }
            }

            Move.WINDUP -> {
                shake(rect)
                if (moveTime >= CACO_IMP_WINDUP_SECONDS) {
                    val (x, y) = chargeTarget(rectOf() ?: rect)
                    glideTo(x, y, CACO_IMP_CHARGE_SPEED)
                    step(Move.CHARGE)
                }
            }

            Move.CHARGE -> {
                // Capped in time too, so a rush stopped short cannot leave it out of the fight.
                if (!inFrame(rect) || moveTime >= LONGEST_CHARGE_SECONDS) {
                    // Out of sight past an edge, it comes round to the right, at the bat's height
                    // as it is now, kept in from the edges for its weave.
                    val transform = world.getComponent(bossId, TransformComponent::class) ?: return
                    transform.rect = Rect.fromLTWH(
                        frameWidth + RETURN_MARGIN,
                        laneFor(batCenterY() ?: rect.top),
                        rect.width,
                        rect.height,
                    )
                    weave()
                    step(Move.RETURN)
                    flashOnHit(true)
                }
            }

            Move.RETURN -> if (rect.left <= stationX + ARRIVED) step(Move.STATION)
        }
    }

    /** How long it holds station between charges in this phase and stance, or null for none. */
    private fun restSeconds(): Float? = when (phase) {
        2 -> CACO_IMP_CHARGE_REST_SECONDS
        4 -> if (burning) null else CACO_IMP_COLD_CHARGE_REST_SECONDS
        5 -> CACO_IMP_ABLAZE_CHARGE_REST_SECONDS
        else -> null
    }

    /**
     * Where its rush ends, as its box's corner: on the line from its middle through the bat's, so
     * it passes straight through where the bat was, and far enough beyond the frame that the glide
     * is still at full speed as it leaves, by whichever edge the line crosses. Straight left with
     * the bat level with it or behind it.
     */
    private fun chargeTarget(rect: Rect): Pair<Float, Float> {
        val bat = batCenter()
        var dx = -1f
        var dy = 0f
        if (bat != null && bat.first < rect.centerX - LEVEL_CHARGE_WITHIN) {
            val length = hypot(bat.first - rect.centerX, bat.second - rect.centerY)
            dx = (bat.first - rect.centerX) / length
            dy = (bat.second - rect.centerY) / length
        }
        return (rect.left + dx * CHARGE_REACH) to (rect.top + dy * CHARGE_REACH)
    }

    /** Whether any of [rect] is inside the frame. */
    private fun inFrame(rect: Rect): Boolean =
        rect.right > 0f && rect.left < frameWidth && rect.bottom > 0f && rect.top < frameHeight

    /**
     * Shudders it up and down through its wind-up, harder as the rush nears: a tell in its
     * movement, which the boss's hit flashes cannot wash out as they can the white-hot on its
     * sprite. Taken off again by the last tick of it, so the rush sets off from its own path.
     */
    private fun shake(rect: Rect) {
        val t = (moveTime / CACO_IMP_WINDUP_SECONDS).coerceAtMost(1f)
        val side = if ((moveTime / SHUDDER_SECONDS).toInt() % 2 == 0) 1f else -1f
        val next = if (t >= 1f) 0f else side * SHUDDER * (0.3f + 0.7f * t)
        val transform = world.getComponent(bossId, TransformComponent::class) ?: return
        transform.rect = rect.offset(0f, next - shudder)
        shudder = next
    }

    /**
     * Whether a hit flashes it. Not through a wind-up and a rush: under a gun that lands every few
     * ticks the flash, drawn over its wash, would leave it white with hits and the wind-up unseen.
     * A hit re-arms the flash it has, so one with no color keeps it from showing; the run's own is
     * put back as it comes round.
     */
    private fun flashOnHit(on: Boolean) {
        val color = if (on) HIT_FLASH_COLOR else 0
        world.addComponent(bossId, HitFlashComponent(HIT_FLASH_SECONDS, color, remaining = 0f))
    }

    /** Moves the charge on to [next]. */
    private fun step(next: Move) {
        move = next
        moveTime = 0f
    }

    /** Starts a glide to the corner [x], [y] at [speed]. */
    private fun glideTo(x: Float, y: Float, speed: Float) {
        world.addComponent(
            bossId,
            EnemyBehaviorComponent(
                EnemyMovementType.GLIDE,
                initialY = y,
                holdX = x,
                baseSpeedX = speed
            ),
        )
    }

    /**
     * Takes up its weave at its station, at the phase's tempo, closing to it from wherever it is.
     * Its lane is kept far enough from the top and bottom for the whole weave.
     */
    private fun weave() {
        val rect = rectOf() ?: return
        world.addComponent(
            bossId,
            EnemyBehaviorComponent(
                EnemyMovementType.BOSS,
                initialY = laneFor(rect.top),
                holdX = stationX,
                tempo = if (phase >= 5) CACO_IMP_BLAZE_TEMPO else 1f,
            ),
        )
    }

    /** [top] kept far enough from the frame's edges for a whole weave around it. */
    private fun laneFor(top: Float): Float {
        val height = rectOf()?.height ?: 0f
        val lowest = (frameHeight - height - WEAVE_ROOM).coerceAtLeast(WEAVE_ROOM)
        return top.coerceIn(WEAVE_ROOM, lowest)
    }

    // endregion

    // region what it looks like

    /** Sets its light, which only the dark gives it, from its phase and what it is doing. */
    private fun shine() {
        val light = world.getComponent(bossId, LightComponent::class) ?: return
        val ablaze = phase >= 5 || (phase == 4 && burning)
        light.color = if (ablaze) CACO_IMP_BLAZE_COLOR else CACO_IMP_LIGHT_COLOR
        light.radius = if (ablaze) CACO_IMP_BLAZE_RADIUS else CACO_IMP_LIGHT_RADIUS
        val resting = when {
            phase == 4 && !burning -> 0f
            ablaze -> blaze()
            else -> smoulder()
        }
        light.intensity = when (move) {
            // Up quickly, then easing in, so the flare catches rather than fades up.
            Move.WINDUP -> {
                val t = (moveTime / CACO_IMP_WINDUP_SECONDS).coerceAtMost(1f)
                resting + (CACO_IMP_FLARE_INTENSITY - resting) * (1f - (1f - t) * (1f - t))
            }

            Move.CHARGE -> CACO_IMP_FLARE_INTENSITY
            else -> resting
        }.coerceIn(0f, 1f)
    }

    /**
     * Washes its sprite: white-hot and quickening through a wind-up, held through the rush, and
     * ashen while it is cold. Clear otherwise.
     */
    private fun tint() {
        val tint = world.getComponent(bossId, TintComponent::class) ?: return
        tint.color = when {
            move == Move.WINDUP -> {
                val t = (moveTime / CACO_IMP_WINDUP_SECONDS).coerceAtMost(1f)
                // A throb that quickens as the rush nears, over a wash that deepens.
                val throb = 0.5f + 0.5f * sin(moveTime * (WINDUP_THROB + WINDUP_THROB * 2f * t))
                withAlpha(CACO_IMP_WINDUP_TINT, WINDUP_TINT_ALPHA * t * (0.6f + 0.4f * throb))
            }

            move == Move.CHARGE -> withAlpha(CACO_IMP_WINDUP_TINT, CHARGE_TINT_ALPHA)
            phase == 4 && !burning -> withAlpha(CACO_IMP_COLD_TINT, COLD_TINT_ALPHA)
            else -> 0
        }
    }

    /** Its smoulder, breathing slowly about [CACO_IMP_SMOULDER_INTENSITY]. */
    private fun smoulder(): Float =
        CACO_IMP_SMOULDER_INTENSITY + CACO_IMP_BREATH * sin(clock * CACO_IMP_BREATHS_PER_SECOND * TWO_PI)

    /**
     * Its blaze, guttering like a fire: two flickers at unrelated rates, so it never settles into a
     * beat, dimming it by up to [CACO_IMP_FLICKER].
     */
    private fun blaze(): Float {
        val gutter = 0.5f + 0.3f * sin(clock * FLICKER_FAST) + 0.2f * sin(clock * FLICKER_SLOW + 1f)
        return CACO_IMP_BLAZE_INTENSITY * (1f - CACO_IMP_FLICKER * gutter)
    }

    // endregion

    /** The living bat's center, or null. */
    private fun batCenter(): Pair<Float, Float>? {
        for (id in world.query(PlayerControlComponent::class, TransformComponent::class)) {
            if (world.getComponent(id, HealthComponent::class)?.alive == false) continue
            val rect = world.getComponent(id, TransformComponent::class)?.rect ?: continue
            return rect.centerX to rect.centerY
        }
        return null
    }

    /** Where its box's top would be to put its middle level with the bat's, or null with no bat. */
    private fun batCenterY(): Float? {
        val bat = batCenter() ?: return null
        val height = rectOf()?.height ?: 0f
        return bat.second - height / 2f
    }

    /** Its own box, or null once it is gone. */
    private fun rectOf(): Rect? = world.getComponent(bossId, TransformComponent::class)?.rect

    /**
     * Its charge, in turn: holding [STATION] and fighting from it, the [WINDUP], the [CHARGE]
     * itself through the frame and out of its left edge, and the [RETURN] from the right edge to
     * its station. It fires only on station: a bolt from a charging imp would come at the bat from
     * wherever it happened to be.
     */
    enum class Move { STATION, WINDUP, CHARGE, RETURN }

    companion object {
        /** Seconds from lighting up for good to the first summons. */
        private const val FIRST_SUMMON_DELAY = 2.5f

        /** How far it rears back over a wind-up, and how fast, in frame pixels and per tick. */
        private const val WINDUP_RECOIL = 30f
        private const val WINDUP_RECOIL_SPEED = 0.9f

        /** Its wind-up's shudder at its hardest, in frame pixels, and how long each way. */
        private const val SHUDDER = 2f
        private const val SHUDDER_SECONDS = 0.04f

        /** How fast its wind-up throbs at first, in radians a second; it ends thrice as fast. */
        private const val WINDUP_THROB = 14f

        /** How strongly its sprite is washed: at the end of a wind-up, through a rush, and cold. */
        private const val WINDUP_TINT_ALPHA = 0.75f
        private const val CHARGE_TINT_ALPHA = 0.2f
        private const val COLD_TINT_ALPHA = 0.55f

        /**
         * How far a rush is aimed, in frame pixels: past any edge from anywhere in the frame, with
         * the glide's easing stretch to spare, so it leaves the frame at full speed.
         */
        private const val CHARGE_REACH = 1200f

        /** A bat no further left of its middle than this is charged level, not along a line. */
        private const val LEVEL_CHARGE_WITHIN = 40f

        /** The longest a rush can last, from the right edge to past the left with time to spare. */
        private const val LONGEST_CHARGE_SECONDS = 4f

        /** How far past the right edge it comes back in from, so it is never seen to appear. */
        private const val RETURN_MARGIN = 8f

        /** How near its station counts as back on it, in frame pixels. */
        private const val ARRIVED = 1f

        /**
         * How far from the top and bottom edges its lane is kept: a little more than the weave's
         * reach, so the whole weave stays in the frame.
         */
        private const val WEAVE_ROOM = 68f

        private const val FLICKER_FAST = 13f
        private const val FLICKER_SLOW = 7.3f
        private const val TWO_PI = (2.0 * PI).toFloat()

        /** [color] at [alpha], 0..1, for a tint. */
        private fun withAlpha(color: Int, alpha: Float): Int = EngineColors.withAlpha(color, alpha)

        /** What it opens with: straight bolts, and a fan between them. */
        val SMOULDERING_GUN = EnemyGun(
            1.6f,
            listOf(
                Volley(ShotPattern.STRAIGHT, speed = 3.4f, damageFactor = CACO_IMP_BOLT_DAMAGE),
                Volley(ShotPattern.STRAIGHT, speed = 3.4f, damageFactor = CACO_IMP_BOLT_DAMAGE),
                Volley(
                    ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 16f, speed = 2.6f,
                    damageFactor = CACO_IMP_BOLT_DAMAGE,
                ),
            ),
        )

        /** Between charges: fans only, a little quicker, so the spells on station still press. */
        private val CHARGING_GUN = EnemyGun(
            1.4f,
            listOf(
                Volley(
                    ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 16f, speed = 2.8f,
                    damageFactor = CACO_IMP_BOLT_DAMAGE,
                ),
                Volley(ShotPattern.STRAIGHT, speed = 3.4f, damageFactor = CACO_IMP_BOLT_DAMAGE),
            ),
        )

        /**
         * Embers in pairs, thrown wide either side of the bat, where its own gun, firing straight
         * ahead, does not put them out by the way: shooting one down is a move to make. And a tight
         * fan of bolts between them, so hanging back to let the embers burn out is not safe either.
         */
        private val EMBER_GUN = EnemyGun(
            1.5f,
            listOf(
                Volley(
                    ShotPattern.EMBER, count = 2, spreadDegrees = 60f, speed = 4.5f,
                    damageFactor = CACO_IMP_EMBER_DAMAGE,
                ),
                Volley(
                    ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 14f, speed = 2.8f,
                    damageFactor = CACO_IMP_BOLT_DAMAGE,
                ),
            ),
        )

        /** Burning in its shield: rings, turned half a step each, and a bolt at the player. */
        private val BURNING_GUN = EnemyGun(
            1.3f,
            listOf(
                Volley(
                    ShotPattern.RADIAL,
                    count = 10,
                    speed = 2.0f,
                    damageFactor = CACO_IMP_RING_DAMAGE
                ),
                Volley(ShotPattern.AIMED, speed = 2.8f, damageFactor = CACO_IMP_BOLT_DAMAGE),
                Volley(
                    ShotPattern.RADIAL,
                    count = 10,
                    speed = 2.0f,
                    damageFactor = CACO_IMP_RING_DAMAGE
                ),
            ),
        )

        /** Ablaze: wider fans, denser rings and embers, sooner. */
        private val BLAZING_GUN = EnemyGun(
            1.2f,
            listOf(
                Volley(
                    ShotPattern.AIMED_FAN, count = 5, spreadDegrees = 13f, speed = 2.9f,
                    damageFactor = CACO_IMP_BOLT_DAMAGE,
                ),
                Volley(
                    ShotPattern.RADIAL,
                    count = 12,
                    speed = 2.2f,
                    damageFactor = CACO_IMP_RING_DAMAGE
                ),
                Volley(
                    ShotPattern.EMBER, count = 2, spreadDegrees = 70f, speed = 4.5f,
                    damageFactor = CACO_IMP_EMBER_DAMAGE,
                ),
            ),
        )

        /** Its gun on entering [phase]; the fourth phase's burning stance arms [BURNING_GUN]. */
        fun gunFor(phase: Int): EnemyGun = when {
            phase >= 5 -> BLAZING_GUN
            phase == 4 -> BURNING_GUN
            phase == 3 -> EMBER_GUN
            phase == 2 -> CHARGING_GUN
            else -> SMOULDERING_GUN
        }

        /** The phase its health puts it in. */
        fun phaseFor(healthFraction: Float): Int = when {
            healthFraction <= CACO_IMP_PHASE_5_AT -> 5
            healthFraction <= CACO_IMP_PHASE_4_AT -> 4
            healthFraction <= CACO_IMP_PHASE_3_AT -> 3
            healthFraction <= CACO_IMP_PHASE_2_AT -> 2
            else -> 1
        }
    }
}
