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
 * fifth, just angrier, which is how the cave's three drones have always worked.
 *
 * @param strip which type strip of its stage's sheet it animates from, the way
 *   [EntityFactory.srcXOf] addresses them. The cave's and the forest's sheets both count from zero.
 * @param speedX closing speed in framebuffer pixels per tick, before the wave's multiplier.
 * @param shotVariant the colorway of `shot.png` its bolts are drawn in - the sprite's own color.
 * @param hitPointFactor/damageFactor scale the wave's numbers, so a swarm of small things can each
 *   be weak and a lone tank strong without either needing a table of its own.
 * @param collisionTolerance how far inside its frame the hit box sits. The cave's drones fill
 *   their frame; a wasp does not, and a hit box the size of the frame would make a visible miss
 *   count as a hit.
 * @param gun what it fires, for the species that always shoot.
 * @param armable whether a wave's `gunChance` can hand it a gun it would not otherwise carry.
 * @param innateShield a shield it always spawns with, as a fraction of its health. Zero for none.
 * @param canBeShielded whether a wave's `shieldChance` can give it a shield anyway.
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
    val canBeShielded: Boolean = false,
) {
    // --- the cave, on `enemies.png` ------------------------------------------------------------

    SCOUT(strip = 0, speedX = -2.5f, movement = EnemyMovementType.SCOUT, shotVariant = 1),
    WEAVER(strip = 1, speedX = -1.5f, movement = EnemyMovementType.SINE, shotVariant = 2),
    STRIKER(strip = 2, speedX = -1.2f, movement = EnemyMovementType.ZIGZAG, shotVariant = 3),

    // --- the forest, on `forestEnemies.png` ----------------------------------------------------

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
    ;

    companion object {
        /**
         * The gun a wave hands an [armable] species: slow, straight, and infrequent. It is a second
         * thing to watch rather than a second species, so it stays simple.
         */
        val ISSUED_GUN = EnemyGun(2.8f, listOf(Volley(ShotPattern.STRAIGHT, speed = 2.4f)))
    }
}
