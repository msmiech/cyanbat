package at.smiech.cyanbat.service

import at.smiech.cyanbat.service.EnemySpecies.BEETLE
import at.smiech.cyanbat.service.EnemySpecies.CRAB
import at.smiech.cyanbat.service.EnemySpecies.DJINN
import at.smiech.cyanbat.service.EnemySpecies.HAWK
import at.smiech.cyanbat.service.EnemySpecies.KRAIT
import at.smiech.cyanbat.service.EnemySpecies.LOCUST
import at.smiech.cyanbat.service.EnemySpecies.OWL
import at.smiech.cyanbat.service.EnemySpecies.PIRANHA
import at.smiech.cyanbat.service.EnemySpecies.PUFFER
import at.smiech.cyanbat.service.EnemySpecies.SCARAB
import at.smiech.cyanbat.service.EnemySpecies.SCOUT
import at.smiech.cyanbat.service.EnemySpecies.SHARK
import at.smiech.cyanbat.service.EnemySpecies.SPITTER
import at.smiech.cyanbat.service.EnemySpecies.STRIKER
import at.smiech.cyanbat.service.EnemySpecies.WASP
import at.smiech.cyanbat.service.EnemySpecies.WEAVER
import at.smiech.cyanbat.service.EnemySpecies.WISP
import at.smiech.cyanbat.service.EnemySpecies.WYRMLING
import at.smiech.cyanbat.util.NAGA_VITALITY
import at.smiech.cyanbat.util.STAGE_DIFFICULTY_STEP

/**
 * One minute of a stage's enemies, as designed rather than as scaled.
 *
 * @param species what this wave draws from. A new mix every minute makes a new wave read as a new
 *   group of enemies rather than more of the last.
 * @param shieldChance the chance, 0..1, that an enemy which can carry a shield spawns behind one.
 * @param gunChance the chance, 0..1, that an [EnemySpecies.armable] enemy is issued a gun.
 * @param eliteChance the chance, 0..1, that a group arrives with an elite in it (a loner, or one
 *   member of a swarm or formation). Rolled per group rather than per enemy, so a swarm is no
 *   likelier to bring one than a lone scout and an elite always stands out from its group.
 */
data class WaveDesign(
    val species: List<EnemySpecies>,
    val shieldChance: Float = 0f,
    val gunChance: Float = 0f,
    val eliteChance: Float = 0f,
)

/** Which boss a stage ends on: a different fight, not just a different sprite. */
enum class BossKind {
    /**
     * The jungle's Moth Queen: a figure eight on station, three phases, a shield she raises as
     * she changes phase, and wasp swarms she calls in. See [MothQueenBrain].
     */
    MOTH_QUEEN,

    /**
     * The cave's Caco Imp, a giant crimson imp alight in the dark: it weaves on
     * station, then puts its light out and prowls between ambushes, and ends
     * ablaze, calling in imps. See [CacoImpBrain].
     */
    CACO_IMP,

    /**
     * The desert's Sand Wyrm: a body of ten armored parts that breaches out of the dunes in arcs
     * aimed at the player, spits at the top of each one, and dives back under between them. Any
     * part of it can be hit. See [SandWyrmBrain].
     */
    SAND_WYRM,

    /**
     * The lagoon's Naga: a hooded serpent of thirteen parts that rears out of the water, sways and
     * spits, strikes at the bat, and dives to surface elsewhere, over five phases that bring its
     * brood, a shield and a swim across the frame. See [NagaBrain].
     */
    NAGA,
}

/**
 * What a stage is, separate from how hard it is: its waves and its boss. [StageProgression] reads
 * this against the clock and scales it by the stage's difficulty.
 *
 * @param bossToughness the boss's difficulty relative to the stage's: 1 for a boss scaled like its
 *   waves, more for one kept harder.
 * @param bossVitality a multiplier on the boss's health alone: a longer fight, where
 *   [bossToughness] would also make its blows hit harder.
 */
data class StageDesign(
    val waves: List<WaveDesign>,
    val boss: BossKind,
    val bossToughness: Float = 1f,
    val bossVitality: Float = 1f,
) {
    companion object {
        /**
         * Stage 1, the first a player flies. Its enemies come in groups, shoot back and hide behind
         * shields; each is introduced before being combined with the others, and shields and guns
         * ramp in sparingly over the stage.
         *
         * Every stage ramps its elites the same way: none in the opening minute,
         * which is for learning the stage's enemies, then a few groups in a
         * hundred, climbing to the escort. That is a couple of elites a minute
         * early on and one every six or seven seconds in the escort.
         *
         * The Moth Queen is fought harder than the stage's easing would make her, at the strength
         * she had as stage 2's boss.
         */
        val JUNGLE = StageDesign(
            waves = listOf(
                // A swarm to learn, and a shielded beetle to learn shields on.
                WaveDesign(listOf(WASP, BEETLE)),
                // Formations and the first enemy that aims, with no shields or guns handed out yet.
                WaveDesign(listOf(WISP, SPITTER, WASP), eliteChance = 0.04f),
                // Divers, with the opening minute's swarms and tanks for cover, and
                // the first shields and guns.
                WaveDesign(
                    listOf(OWL, WASP, BEETLE),
                    shieldChance = 0.1f,
                    gunChance = 0.1f,
                    eliteChance = 0.05f
                ),
                // Everything at once.
                WaveDesign(
                    listOf(WASP, BEETLE, SPITTER, OWL, WISP),
                    shieldChance = 0.2f,
                    gunChance = 0.15f,
                    eliteChance = 0.06f
                ),
                // The escort: everything that shoots or dives, and a fair part of it armed or shielded.
                WaveDesign(
                    listOf(SPITTER, OWL, WISP),
                    shieldChance = 0.25f,
                    gunChance = 0.2f,
                    eliteChance = 0.07f
                ),
            ),
            boss = BossKind.MOTH_QUEEN,
            bossToughness = 1f + STAGE_DIFFICULTY_STEP,
        )

        /**
         * Stage 2, flown in the dark by the bat's own light. Each minute brings a
         * group that moves in a new way, and the escort is the two hardest to lead.
         * None shoots or carries a shield: the cave's difficulty is its tougher,
         * quicker enemies and the dark. Its boss turns the dark on the player.
         */
        val CAVE = StageDesign(
            waves = listOf(
                // Scouts only, straight and readable.
                WaveDesign(listOf(SCOUT)),
                // Weavers join them.
                WaveDesign(listOf(SCOUT, WEAVER), eliteChance = 0.04f),
                // The scouts give way to zigzags.
                WaveDesign(listOf(WEAVER, STRIKER), eliteChance = 0.05f),
                // Everything at once.
                WaveDesign(listOf(SCOUT, WEAVER, STRIKER), eliteChance = 0.06f),
                // The boss escort.
                WaveDesign(listOf(WEAVER, STRIKER), eliteChance = 0.07f),
            ),
            boss = BossKind.CACO_IMP,
        )

        /**
         * Stage 3, flown from noon to nightfall. Harder than the cave, with a new direction to
         * watch: things come up out of the sand. The first minute is a swarm to cut through and a
         * loop to read; the wyrmlings arrive in the second, before anything shoots; the djinn's
         * fans in the third; and by dusk everything is armed, shielded or leaping.
         */
        val DESERT = StageDesign(
            waves = listOf(
                // Noon: a cloud of locusts, and a hawk's loop to learn.
                WaveDesign(listOf(LOCUST, HAWK)),
                // Something comes up out of the sand, and a scarab whose shell grows back.
                WaveDesign(
                    listOf(WYRMLING, LOCUST, SCARAB),
                    shieldChance = 0.1f,
                    eliteChance = 0.04f
                ),
                // The golden hour: the djinn and its fans, with the hawks and the leapers for cover.
                WaveDesign(
                    listOf(DJINN, HAWK, WYRMLING),
                    shieldChance = 0.2f,
                    gunChance = 0.15f,
                    eliteChance = 0.05f
                ),
                // Sunset, and everything at once.
                WaveDesign(
                    listOf(LOCUST, HAWK, WYRMLING, DJINN, SCARAB),
                    shieldChance = 0.3f,
                    gunChance = 0.25f,
                    eliteChance = 0.06f
                ),
                // Dusk, and the escort: everything that leaps, loops or shoots.
                WaveDesign(
                    listOf(WYRMLING, DJINN, HAWK, SCARAB),
                    shieldChance = 0.4f,
                    gunChance = 0.35f,
                    eliteChance = 0.07f
                ),
            ),
            boss = BossKind.SAND_WYRM,
        )

        /**
         * Stage 4, flown from night to noon across a bay of limestone islands to a temple in the
         * water: the hardest stage. Harder than the desert on every axis, with one more direction
         * to watch: sharks come in from behind along the waterline and leap forward at the bat.
         * They arrive in the second minute, before anything shoots; the puffers' rings of spines in
         * the third; the Naga's brood in the fourth, as the temple comes into sight. Shields, guns
         * and elites ramp higher than in any earlier stage.
         *
         * The Naga is fought at the stage's difficulty with nearly twice the health, since its
         * fight is five phases rather than three.
         */
        val LAGOON = StageDesign(
            waves = listOf(
                // Night: a school of piranhas, and a crab's shell to learn.
                WaveDesign(listOf(PIRANHA, CRAB)),
                // Dawn, and something behind the bat: fins along the waterline.
                WaveDesign(listOf(SHARK, PIRANHA, CRAB), shieldChance = 0.15f, eliteChance = 0.05f),
                // Sunrise: the puffers and their rings, with the sharks and the schools for cover.
                WaveDesign(
                    listOf(PUFFER, SHARK, PIRANHA),
                    shieldChance = 0.25f,
                    gunChance = 0.2f,
                    eliteChance = 0.06f
                ),
                // Morning, the temple in sight, and everything at once.
                WaveDesign(
                    listOf(KRAIT, PUFFER, SHARK, CRAB, PIRANHA),
                    shieldChance = 0.35f,
                    gunChance = 0.3f,
                    eliteChance = 0.07f
                ),
                // Noon, and the escort: the Naga's brood with everything that shoots or comes from behind.
                WaveDesign(
                    listOf(KRAIT, PUFFER, SHARK, CRAB),
                    shieldChance = 0.45f,
                    gunChance = 0.4f,
                    eliteChance = 0.08f
                ),
            ),
            boss = BossKind.NAGA,
            bossVitality = NAGA_VITALITY,
        )

        /**
         * The design of the stage with this 1-based [id]. Past the last stage the last design
         * repeats, scaled harder by [StageProgression.forStage].
         */
        fun forStage(id: Int): StageDesign = when {
            id <= 1 -> JUNGLE
            id == 2 -> CAVE
            id == 3 -> DESERT
            else -> LAGOON
        }
    }
}
