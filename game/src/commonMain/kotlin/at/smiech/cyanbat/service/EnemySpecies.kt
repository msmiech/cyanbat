package at.smiech.cyanbat.service

import at.smiech.cyanbat.ecs.ShotPattern
import at.smiech.cyanbat.ecs.Volley
import at.smiech.engine.ecs.EnemyMovementType

/**
 * How a species arrives: alone, or as a group that moves as one.
 *
 * @param cost how many ordinary spawns' worth of time the group buys the player. A swarm of six is
 *   one spawn event, and without this the next one would arrive as soon as a lone scout's would -
 *   so the gap after a group is stretched by its cost, and a stage's density stays a density.
 */
enum class Squad(val cost: Float) {
    SOLO(1f),

    /** A loose flock, spread around a shared path; see [EnemyMovementType.SWARM]. */
    SWARM(2.6f),

    /** Five in a V, the leader at the point; see [EnemyMovementType.FORMATION]. */
    V_FORMATION(2.4f),

    /**
     * Alone, but from under the sand along the bottom edge rather than out of the right-hand one;
     * see [EnemyMovementType.LEAP]. Costed above a loner because the cruise in is time the player
     * spends watching it, and a leaper right behind it would arrive inside that warning.
     */
    BURROW(1.4f),

    /**
     * Alone, and from behind: in from the left edge along the waterline with only its fin showing,
     * overtaking the bat; see [EnemySpecies.SHARK]. Costed as a burrower is, for the same warning.
     */
    FROM_BEHIND(1.4f),
}

/**
 * A gun an enemy carries from the moment it spawns.
 *
 * @param interval seconds between volleys.
 * @param volleys what each trigger pull fires, in turn; see [at.smiech.cyanbat.ecs.GunComponent].
 */
data class EnemyGun(val interval: Float, val volleys: List<Volley>)

/**
 * Everything the game knows about one kind of enemy: which sprite it is, how it flies, how it
 * fights, and how it arrives.
 *
 * What a species is *not* is its strength. Health and damage come from the wave that spawns it,
 * and a species only scales them - so a species is the same animal in the first minute and the
 * fifth, just angrier, which is how the cave's three imps have always worked.
 *
 * @param strip which type strip of its stage's sheet it animates from, the way
 *   [EntityFactory.srcXOf] addresses them. Every stage's sheet counts from zero.
 * @param speedX closing speed in framebuffer pixels per tick, before the wave's multiplier.
 * @param shotVariant the colorway of `shot.png` its bolts are drawn in - the sprite's own color.
 * @param hitPointFactor/damageFactor scale the wave's numbers, so a swarm of small things can each
 *   be weak and a lone tank strong without either needing a table of its own.
 * @param collisionTolerance how far inside its frame the hit box sits. The cave's imps fill
 *   their frame; a wasp does not, and a hit box the size of the frame would make a visible miss
 *   count as a hit.
 * @param gun what it fires, for the species that always shoot.
 * @param armable whether a wave's `gunChance` can hand it a gun it would not otherwise carry.
 * @param innateShield a shield it always spawns with, as a fraction of its health. Zero for none.
 * @param shieldRegrowth how much of that shield grows back a second, as a fraction of it, once the
 *   enemy has gone [at.smiech.cyanbat.util.SHIELD_REGROWTH_DELAY_SECONDS] without being hit. Zero
 *   for a shield that stays broken.
 * @param canBeShielded whether a wave's `shieldChance` can give it a shield anyway.
 * @param facesHeading whether its sprite turns to point where it is flying. Only for the ones whose
 *   flight is an arc - a loop, a leap, a weave - which a sprite held level would fly sideways through.
 * @param drawnFacingRight whether its art faces right rather than left, as everything hostile's does:
 *   for the one that comes from behind, and so flies to the right.
 */
enum class EnemySpecies(
    val strip: Int,
    val speedX: Float,
    val movement: EnemyMovementType,
    val shotVariant: Int,
    val squad: Squad = Squad.SOLO,
    val hitPointFactor: Float = 1f,
    val damageFactor: Float = 1f,
    val collisionTolerance: Float = 5f,
    val gun: EnemyGun? = null,
    val armable: Boolean = false,
    val innateShield: Float = 0f,
    val shieldRegrowth: Float = 0f,
    val canBeShielded: Boolean = false,
    val facesHeading: Boolean = false,
    val drawnFacingRight: Boolean = false,
) {
    // --- the cave, on `enemies.png` ------------------------------------------------------------

    SCOUT(strip = 0, speedX = -2.5f, movement = EnemyMovementType.SCOUT, shotVariant = 1),
    WEAVER(strip = 1, speedX = -1.5f, movement = EnemyMovementType.SINE, shotVariant = 2),
    STRIKER(strip = 2, speedX = -1.2f, movement = EnemyMovementType.ZIGZAG, shotVariant = 3),

    // --- the jungle, on `jungleEnemies.png` ----------------------------------------------------

    /**
     * Comes in swarms, each one weak. A swarm is dangerous as a shape, not as six enemies: the
     * player has to find the gap in it or burn through one side.
     */
    WASP(
        strip = 0, speedX = -1.9f, movement = EnemyMovementType.SWARM, shotVariant = 2,
        squad = Squad.SWARM, hitPointFactor = 0.45f, damageFactor = 0.6f, collisionTolerance = 8f,
    ),

    /** The tank: slow, lurching, always behind a shield, and firing heavy bolts straight ahead. */
    BEETLE(
        strip = 1, speedX = -1.1f, movement = EnemyMovementType.SURGE, shotVariant = 3,
        hitPointFactor = 1.5f, damageFactor = 1.2f,
        gun = EnemyGun(3.2f, listOf(Volley(ShotPattern.STRAIGHT, speed = 2.6f))),
        innateShield = 0.8f,
    ),

    /** Stops on station, drifts into the player's lane, and shoots at them. */
    SPITTER(
        strip = 2, speedX = -1.6f, movement = EnemyMovementType.HOVER, shotVariant = 4,
        hitPointFactor = 1.0f, damageFactor = 0.8f,
        gun = EnemyGun(1.9f, listOf(Volley(ShotPattern.AIMED, speed = 2.4f))),
        canBeShielded = true,
    ),

    /** Picks the player's lane, checks its swing, and dives. */
    OWL(
        strip = 3, speedX = -1.5f, movement = EnemyMovementType.DIVE, shotVariant = 2,
        hitPointFactor = 1.1f, damageFactor = 1.3f, armable = true, canBeShielded = true,
    ),

    /** Flies in formation; later in the stage, some of the formation is armed. */
    WISP(
        strip = 4, speedX = -1.7f, movement = EnemyMovementType.FORMATION, shotVariant = 5,
        squad = Squad.V_FORMATION, hitPointFactor = 0.7f, damageFactor = 0.8f,
        collisionTolerance = 6f, armable = true, canBeShielded = true,
    ),

    // --- the desert, on `desertEnemies.png` ----------------------------------------------------

    /**
     * The desert's swarm: a cloud of locusts, faster and flimsier than the jungle's wasps. Better
     * cut through than flown round.
     */
    LOCUST(
        strip = 0, speedX = -2.1f, movement = EnemyMovementType.SWARM, shotVariant = 5,
        squad = Squad.SWARM, hitPointFactor = 0.4f, damageFactor = 0.55f, collisionTolerance = 8f,
    ),

    /** Throws a loop on its way through, turning to point along it; later in the day some are armed. */
    HAWK(
        strip = 1, speedX = -1.8f, movement = EnemyMovementType.LOOP, shotVariant = 5,
        hitPointFactor = 1.0f, damageFactor = 1.2f, armable = true, canBeShielded = true,
        facesHeading = true,
    ),

    /**
     * The Sand Wyrm's brood. Comes in under the sand with only its ridged back showing, and leaps
     * out at the player's height; see [EnemyMovementType.LEAP].
     */
    WYRMLING(
        strip = 2, speedX = -1.6f, movement = EnemyMovementType.LEAP, shotVariant = 3,
        squad = Squad.BURROW, hitPointFactor = 1.2f, damageFactor = 1.3f, collisionTolerance = 6f,
        facesHeading = true,
    ),

    /**
     * Hangs on station, drifting into the player's lane, and throws fans of three at them.
     *
     * Slower to fire than the spitter, because each pull is three bolts rather than one: at the
     * spitter's pace, the djinn that pile up on station by dusk filled the frame with fire just as
     * the Sand Wyrm came up to fight.
     */
    DJINN(
        strip = 3, speedX = -1.5f, movement = EnemyMovementType.HOVER, shotVariant = 4,
        hitPointFactor = 1.1f, damageFactor = 0.9f,
        gun = EnemyGun(
            3.4f,
            listOf(
                Volley(
                    ShotPattern.AIMED_FAN,
                    count = 3,
                    spreadDegrees = 14f,
                    speed = 2.3f,
                    damageFactor = 0.6f
                )
            ),
        ),
        canBeShielded = true,
    ),

    /**
     * The tank, and the desert's answer to the beetle: its shield grows back if it is left alone,
     * so a scarab is one to finish once it has been started on.
     */
    SCARAB(
        strip = 4, speedX = -1.0f, movement = EnemyMovementType.SURGE, shotVariant = 3,
        hitPointFactor = 1.4f, damageFactor = 1.1f,
        gun = EnemyGun(3.4f, listOf(Volley(ShotPattern.STRAIGHT, speed = 2.6f))),
        innateShield = 0.6f, shieldRegrowth = 0.35f,
    ),

    // --- the lagoon, on `lagoonEnemies.png` ----------------------------------------------------

    /**
     * The lagoon's swarm: a school of piranhas, quicker and hungrier than the desert's locusts, and
     * as flimsy one by one.
     */
    PIRANHA(
        strip = 0, speedX = -2.3f, movement = EnemyMovementType.SWARM, shotVariant = 5,
        squad = Squad.SWARM, hitPointFactor = 0.42f, damageFactor = 0.6f, collisionTolerance = 8f,
    ),

    /**
     * The tank: a crab in a shell that grows back if it is left alone, scuttling up and down as it
     * comes - a zigzag, which is hard to keep a gun on - and snapping heavy bolts straight ahead.
     */
    CRAB(
        strip = 1, speedX = -1.1f, movement = EnemyMovementType.ZIGZAG, shotVariant = 3,
        hitPointFactor = 1.25f, damageFactor = 1.2f,
        gun = EnemyGun(3.0f, listOf(Volley(ShotPattern.STRAIGHT, speed = 2.7f))),
        innateShield = 0.55f, shieldRegrowth = 0.3f,
    ),

    /**
     * Comes from behind: in from the left edge along the waterline with only its fin showing,
     * overtaking the bat, and leaping out at its height - forward, the way the bat flies, turning to
     * point along its arc. The one thing in the game that does not come from ahead, and so the one
     * drawn facing right.
     */
    SHARK(
        strip = 2,
        speedX = 1.5f,
        movement = EnemyMovementType.LEAP,
        shotVariant = 3,
        squad = Squad.FROM_BEHIND,
        hitPointFactor = 1.4f,
        damageFactor = 1.4f,
        collisionTolerance = 6f,
        facesHeading = true,
        drawnFacingRight = true,
    ),

    /**
     * Hangs on station, drifting into the player's lane, and puffs itself up to throw a ring of
     * spines - a boss's pattern, the first ordinary enemy to fire one. Slow to fire again, since a
     * ring is eight bolts at once: at the djinn's pace, the puffers on station by noon filled the
     * frame with spines before the Naga ever rose.
     */
    PUFFER(
        strip = 3, speedX = -1.5f, movement = EnemyMovementType.HOVER, shotVariant = 2,
        hitPointFactor = 1.1f, damageFactor = 0.9f,
        gun = EnemyGun(
            4.4f,
            listOf(Volley(ShotPattern.RADIAL, count = 8, speed = 1.9f, damageFactor = 0.5f))
        ),
        canBeShielded = true,
    ),

    /**
     * The Naga's brood: a sea krait weaving in a wide S, turning to point along it as it goes, so it
     * swims rather than slides. Later in the stage some are armed.
     */
    KRAIT(
        strip = 4, speedX = -1.7f, movement = EnemyMovementType.SINE, shotVariant = 5,
        hitPointFactor = 1.0f, damageFactor = 1.1f, collisionTolerance = 6f,
        armable = true, canBeShielded = true, facesHeading = true,
    ),
    ;

    companion object {
        /**
         * The gun a wave hands an [armable] species: slow, straight, and infrequent. It is a second
         * thing to watch rather than a second species, so it stays simple.
         */
        val ISSUED_GUN = EnemyGun(2.8f, listOf(Volley(ShotPattern.STRAIGHT, speed = 2.4f)))

        /**
         * What is issued instead to something [drawnFacingRight], which comes from behind: a bolt
         * fired straight ahead of it would fly away from the bat, so it aims.
         */
        val ISSUED_AIMED_GUN = EnemyGun(2.8f, listOf(Volley(ShotPattern.AIMED, speed = 2.4f)))
    }
}
