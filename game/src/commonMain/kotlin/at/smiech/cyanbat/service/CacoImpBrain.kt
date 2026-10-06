package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.GunComponent
import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_COLOR
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_BLAZE_TEMPO
import at.smiech.cyanbat.util.CACO_IMP_BOLT_DAMAGE
import at.smiech.cyanbat.util.CACO_IMP_BREATH
import at.smiech.cyanbat.util.CACO_IMP_BREATHS_PER_SECOND
import at.smiech.cyanbat.util.CACO_IMP_BURN_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_DOUSE_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_FLARE_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_FLARE_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_FLICKER
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_COLOR
import at.smiech.cyanbat.util.CACO_IMP_LIGHT_RADIUS
import at.smiech.cyanbat.util.CACO_IMP_LURK_SECONDS
import at.smiech.cyanbat.util.CACO_IMP_PHASE_2_AT
import at.smiech.cyanbat.util.CACO_IMP_PHASE_3_AT
import at.smiech.cyanbat.util.CACO_IMP_PROWL_BAT_CLEARANCE
import at.smiech.cyanbat.util.CACO_IMP_PROWL_LEAST_MOVE
import at.smiech.cyanbat.util.CACO_IMP_PROWL_LEFTMOST
import at.smiech.cyanbat.util.CACO_IMP_PROWL_SPEED
import at.smiech.cyanbat.util.CACO_IMP_RING_DAMAGE
import at.smiech.cyanbat.util.CACO_IMP_SMOULDER_INTENSITY
import at.smiech.cyanbat.util.CACO_IMP_SUMMON_SECONDS
import at.smiech.engine.ecs.EnemyBehaviorComponent
import at.smiech.engine.ecs.EnemyMovementType
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.PlayerControlComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.ecs.World
import at.smiech.engine.math.Rect
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Caco Imp's fight, in three phases marked by its health, played out in the light it gives off.
 *
 * 1. **Smouldering.** It weaves on station, alight, firing straight bolts and now and then a fan at
 *    the player.
 * 2. **Lights out.** At two thirds its light goes out and it repeats a round: it glides through the
 *    dark to a station of its choosing and lurks there, then flares up (the only warning of where
 *    it is), fires a ring and a fan, and burns a moment before going dark and moving again. It
 *    never fires in the dark, since a bolt is a light and would give it away.
 * 3. **Ablaze.** At one third it lights up for good, brighter and guttering like a fire, weaves
 *    faster, fires rings and wide fans, and calls in two of its kind every few seconds.
 *
 * Unlit, it is as dim as anything else no light reaches. Light finds it: the bat's own as it comes
 * near, the bat's shots flying past, and the flash of a hit. So its health bar is pinned to the
 * screen rather than hung under it.
 *
 * A hit that crosses both thresholds plays both entrances in order. Like every
 * [BossBrain] this only changes state on its components; in daylight, as in a test,
 * it has no light and fights the same fight.
 *
 * @param frameWidth the width of the frame it prowls in.
 * @param frameHeight the height of the frame it prowls in.
 * @param random where it chooses to prowl to.
 * @param onSummon called when it calls in its kind; the generator is what knows how to spawn them.
 * @param onPhaseChanged called with the phase it has just entered, 2 or 3, for the screen to announce.
 */
class CacoImpBrain(
    private val world: World,
    private val bossId: EntityId,
    private val frameWidth: Int,
    private val frameHeight: Int,
    private val random: Random = Random.Default,
    private val onSummon: () -> Unit = {},
    private val onPhaseChanged: (Int) -> Unit = {},
) : BossBrain {
    /** The phase it is in, 1 to 3. */
    var phase = 1
        private set

    /** Where in its round it is, through the second phase; see [Prowl]. */
    var prowl = Prowl.DOUSE
        private set

    /** Whether it is giving off light, or hiding in the dark with it out. */
    val dark: Boolean
        get() = phase == 2 && (prowl == Prowl.PROWL || prowl == Prowl.LURK)

    /** The station it is gliding to or holding through the second phase, as its box's corner. */
    var stationX = 0f
        private set
    var stationY = 0f
        private set

    /** Seconds into the current [prowl] step. */
    private var prowlTime = 0f

    /** Seconds of the fight, which its light breathes and gutters by. */
    private var clock = 0f

    /** Seconds until the next summons, in the third phase. */
    private var summonTimer = 0f

    /** Volleys of the current ambush still to fire, and the seconds until the next. */
    private var volleysLeft = 0
    private var untilVolley = 0f

    override fun update(deltaTime: Float) {
        val health = world.getComponent(bossId, HealthComponent::class) ?: return
        if (!health.alive) return

        val due = phaseFor(health.fraction)
        while (phase < due) enter(phase + 1)

        clock += deltaTime
        when (phase) {
            1 -> shine(CACO_IMP_LIGHT_COLOR, CACO_IMP_LIGHT_RADIUS, smoulder())
            2 -> stalk(deltaTime)
            else -> {
                shine(CACO_IMP_BLAZE_COLOR, CACO_IMP_BLAZE_RADIUS, blaze())
                summonTimer -= deltaTime
                if (summonTimer <= 0f) {
                    summonTimer += CACO_IMP_SUMMON_SECONDS
                    onSummon()
                }
            }
        }
        fireVolleys(deltaTime)
    }

    /** Plays the entrance of phase [next]: its gun, and its light and movement. */
    private fun enter(next: Int) {
        phase = next
        val gun = gunFor(next)
        world.getComponent(bossId, GunComponent::class)?.apply {
            volleys = gun.volleys
            this.next = 0
        }
        world.getComponent(bossId, WeaponComponent::class)?.apply {
            interval = gun.interval
            timeSinceLastShot = 0f
        }
        volleysLeft = 0

        if (next == 2) {
            // It goes dark from where it is: the first douse is the change of phase.
            step(Prowl.DOUSE)
        } else if (next >= 3) {
            // It takes up its station wherever the dark left it, stopping any glide where it was.
            val rect = rectOf() ?: return
            weaveAt(rect.left, rect.top, CACO_IMP_BLAZE_TEMPO)
            // Its summons come a moment after it lights up, so the two land as separate events.
            summonTimer = FIRST_SUMMON_DELAY
        }
        onPhaseChanged(next)
    }

    /** One tick of the second phase's round: put the light out, glide, lurk, flare, burn, and again. */
    private fun stalk(deltaTime: Float) {
        prowlTime += deltaTime
        when (prowl) {
            Prowl.DOUSE -> {
                val left = 1f - prowlTime / CACO_IMP_DOUSE_SECONDS
                shine(
                    CACO_IMP_LIGHT_COLOR,
                    CACO_IMP_LIGHT_RADIUS,
                    CACO_IMP_SMOULDER_INTENSITY * left.coerceAtLeast(0f)
                )
                if (prowlTime >= CACO_IMP_DOUSE_SECONDS) {
                    chooseStation()
                    glideTo(stationX, stationY)
                    step(Prowl.PROWL)
                }
            }

            Prowl.PROWL -> {
                shine(CACO_IMP_LIGHT_COLOR, CACO_IMP_LIGHT_RADIUS, 0f)
                val rect = rectOf() ?: return
                // Also capped in time, so a glide stopped short cannot leave it in
                // the dark for good.
                val arrived = hypot(rect.left - stationX, rect.top - stationY) < ARRIVED
                if (arrived || prowlTime >= LONGEST_PROWL_SECONDS) step(Prowl.LURK)
            }

            Prowl.LURK -> {
                shine(CACO_IMP_LIGHT_COLOR, CACO_IMP_LIGHT_RADIUS, 0f)
                if (prowlTime >= CACO_IMP_LURK_SECONDS) step(Prowl.FLARE)
            }

            Prowl.FLARE -> {
                // Up quickly, then easing in, so the flare catches rather than fades up.
                val t = (prowlTime / CACO_IMP_FLARE_SECONDS).coerceAtMost(1f)
                val up = 1f - (1f - t) * (1f - t)
                shine(CACO_IMP_LIGHT_COLOR, CACO_IMP_LIGHT_RADIUS, CACO_IMP_FLARE_INTENSITY * up)
                if (prowlTime >= CACO_IMP_FLARE_SECONDS) {
                    val rect = rectOf()
                    if (rect != null) weaveAt(rect.left, rect.top, tempo = 1f)
                    // The ambush: every volley its gun holds, one after another, from the flare.
                    volleysLeft =
                        world.getComponent(bossId, GunComponent::class)?.volleys?.size ?: 0
                    untilVolley = 0f
                    step(Prowl.BURN)
                }
            }

            Prowl.BURN -> {
                // Dying back from the flare to its smoulder until it goes out.
                val t = (prowlTime / CACO_IMP_BURN_SECONDS).coerceAtMost(1f)
                val intensity =
                    CACO_IMP_FLARE_INTENSITY + (smoulder() - CACO_IMP_FLARE_INTENSITY) * t
                shine(CACO_IMP_LIGHT_COLOR, CACO_IMP_LIGHT_RADIUS, intensity)
                if (prowlTime >= CACO_IMP_BURN_SECONDS) step(Prowl.DOUSE)
            }
        }
    }

    /** Moves the round on to [next]. */
    private fun step(next: Prowl) {
        prowl = next
        prowlTime = 0f
    }

    /**
     * Picks where to prowl to: the first of a handful of random stations in the right of the frame,
     * with room to weave, that is both clear of the bat and a real move away. Failing that, the
     * farthest move still clear of the bat, and failing that the station farthest from it: the dark
     * is where it hides, not where it hunts.
     */
    private fun chooseStation() {
        val rect = rectOf() ?: return
        val minX = frameWidth * CACO_IMP_PROWL_LEFTMOST
        val maxX = (frameWidth - rect.width - EDGE_MARGIN).coerceAtLeast(minX)
        val minY = WEAVE_ROOM
        val maxY = (frameHeight - rect.height - WEAVE_ROOM).coerceAtLeast(minY)
        val bat = batCenter()

        var bestX = rect.left
        var bestY = rect.top
        var bestScore = -Float.MAX_VALUE
        repeat(STATION_TRIES) {
            val x = minX + random.nextFloat() * (maxX - minX)
            val y = minY + random.nextFloat() * (maxY - minY)
            val moved = hypot(x - rect.left, y - rect.top)
            val clearance =
                bat?.let { (bx, by) -> hypot(x + rect.width / 2f - bx, y + rect.height / 2f - by) }
                    ?: Float.MAX_VALUE
            val clear = clearance >= CACO_IMP_PROWL_BAT_CLEARANCE
            if (clear && moved >= CACO_IMP_PROWL_LEAST_MOVE) {
                stationX = x
                stationY = y
                return
            }
            val score = if (clear) CLEAR + moved else clearance.coerceAtMost(CLEAR)
            if (score > bestScore) {
                bestScore = score
                bestX = x
                bestY = y
            }
        }
        stationX = bestX
        stationY = bestY
    }

    /** Starts a glide to the station whose corner is at [x], [y]. */
    private fun glideTo(x: Float, y: Float) {
        world.addComponent(
            bossId,
            EnemyBehaviorComponent(
                EnemyMovementType.GLIDE,
                initialY = y,
                holdX = x,
                baseSpeedX = CACO_IMP_PROWL_SPEED
            ),
        )
    }

    /**
     * Takes up a station where it is and weaves there at [tempo]. Its lane is kept
     * far enough from the top and bottom for the whole weave, so one taken up near
     * an edge eases back into the frame.
     */
    private fun weaveAt(x: Float, y: Float, tempo: Float) {
        val height = rectOf()?.height ?: 0f
        val lane =
            y.coerceIn(WEAVE_ROOM, (frameHeight - height - WEAVE_ROOM).coerceAtLeast(WEAVE_ROOM))
        world.addComponent(
            bossId,
            EnemyBehaviorComponent(
                EnemyMovementType.BOSS,
                initialY = lane,
                holdX = x,
                tempo = tempo
            ),
        )
    }

    /**
     * Fires each of an ambush's volleys in turn through its ordinary weapon; see
     * [TRIGGERED_INTERVAL].
     */
    private fun fireVolleys(deltaTime: Float) {
        if (volleysLeft <= 0) return
        untilVolley -= deltaTime
        if (untilVolley > 0f) return
        untilVolley += VOLLEY_GAP_SECONDS
        volleysLeft--
        world.getComponent(bossId, WeaponComponent::class)
            ?.let { it.timeSinceLastShot = it.interval }
    }

    /** Sets its light, which only the cave gives it. */
    private fun shine(color: Int, radius: Int, intensity: Float) {
        val light = world.getComponent(bossId, LightComponent::class) ?: return
        light.color = color
        light.radius = radius
        light.intensity = intensity.coerceIn(0f, 1f)
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

    /** The living bat's center, or null. */
    private fun batCenter(): Pair<Float, Float>? {
        for (id in world.query(PlayerControlComponent::class, TransformComponent::class)) {
            if (world.getComponent(id, HealthComponent::class)?.alive == false) continue
            val rect = world.getComponent(id, TransformComponent::class)?.rect ?: continue
            return rect.centerX to rect.centerY
        }
        return null
    }

    /** Its own box, or null once it is gone. */
    private fun rectOf(): Rect? = world.getComponent(bossId, TransformComponent::class)?.rect

    /**
     * The round it repeats in the dark: [DOUSE] its light, [PROWL] to a new station unlit, [LURK]
     * there, [FLARE] up as a warning and fire, and [BURN] there lit.
     */
    enum class Prowl { DOUSE, PROWL, LURK, FLARE, BURN }

    companion object {
        /** Seconds from lighting up for good to the first summons. */
        private const val FIRST_SUMMON_DELAY = 2.5f

        /** Seconds between the volleys of one ambush. */
        private const val VOLLEY_GAP_SECONDS = 0.35f

        /** How near its station counts as there, in frame pixels. */
        private const val ARRIVED = 1f

        /** The longest it prowls before lurking wherever it has got to. */
        private const val LONGEST_PROWL_SECONDS = 6f

        /**
         * How far from the top and bottom edges a station's lane is kept: a little more than the
         * weave's reach, so the whole weave stays in the frame.
         */
        private const val WEAVE_ROOM = 68f

        /** How far in from the right edge a station is kept, so it is all in sight. */
        private const val EDGE_MARGIN = 16f

        /** How many stations it weighs up before it prowls, at most. */
        private const val STATION_TRIES = 24

        /** A score only stations clear of the bat reach, so any of them beats any that is not. */
        private const val CLEAR = 10_000f

        private const val FLICKER_FAST = 13f
        private const val FLICKER_SLOW = 7.3f
        private const val TWO_PI = (2.0 * PI).toFloat()

        /** Longer than any fight: in the dark the brain fires the gun, never its cadence. */
        const val TRIGGERED_INTERVAL = 1_000_000f

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

        /**
         * Its ambush, fired from each flare: a ring, which lights the dark as it spreads, and a fan
         * at the player so threading the ring in place is not enough.
         */
        private val AMBUSH_GUN = EnemyGun(
            TRIGGERED_INTERVAL,
            listOf(
                Volley(
                    ShotPattern.RADIAL,
                    count = 10,
                    speed = 2.0f,
                    damageFactor = CACO_IMP_RING_DAMAGE
                ),
                Volley(
                    ShotPattern.AIMED_FAN, count = 3, spreadDegrees = 14f, speed = 2.8f,
                    damageFactor = CACO_IMP_BOLT_DAMAGE,
                ),
            ),
        )

        /** Ablaze: wider fans and denser rings, sooner. */
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
            ),
        )

        /** Its gun in [phase]. */
        fun gunFor(phase: Int): EnemyGun = when {
            phase >= 3 -> BLAZING_GUN
            phase == 2 -> AMBUSH_GUN
            else -> SMOULDERING_GUN
        }

        /** The phase its health puts it in. */
        fun phaseFor(healthFraction: Float): Int = when {
            healthFraction <= CACO_IMP_PHASE_3_AT -> 3
            healthFraction <= CACO_IMP_PHASE_2_AT -> 2
            else -> 1
        }
    }
}
