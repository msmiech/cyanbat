package at.smiech.cyanbat.service

import at.smiech.cyanbat.service.EnemySpecies.BEETLE
import at.smiech.cyanbat.service.EnemySpecies.DJINN
import at.smiech.cyanbat.service.EnemySpecies.HAWK
import at.smiech.cyanbat.service.EnemySpecies.LOCUST
import at.smiech.cyanbat.service.EnemySpecies.OWL
import at.smiech.cyanbat.service.EnemySpecies.SCARAB
import at.smiech.cyanbat.service.EnemySpecies.SCOUT
import at.smiech.cyanbat.service.EnemySpecies.SPITTER
import at.smiech.cyanbat.service.EnemySpecies.STRIKER
import at.smiech.cyanbat.service.EnemySpecies.WASP
import at.smiech.cyanbat.service.EnemySpecies.WEAVER
import at.smiech.cyanbat.service.EnemySpecies.WISP
import at.smiech.cyanbat.service.EnemySpecies.WYRMLING

/**
 * One minute of a stage's enemies, as designed rather than as scaled.
 *
 * @param species what this wave draws from. Changing the mix every minute is what makes a new wave
 *   read as a new *group* of enemies rather than as more of the last one.
 * @param shieldChance the chance that an enemy which can carry a shield spawns behind one, as 0..1.
 * @param gunChance the chance that an [EnemySpecies.armable] enemy is issued a gun, as 0..1.
 * @param eliteChance the chance that a group arrives with an elite in it, as 0..1: a loner that is
 *   one, or one of a swarm or a formation. Rolled per group rather than per enemy, so that a swarm
 *   of seven is no likelier to bring one than a lone scout - an elite is meant to stand out from
 *   what it arrives with, and a flock of them would just be a harder swarm.
 */
data class WaveDesign(
    val species: List<EnemySpecies>,
    val shieldChance: Float = 0f,
    val gunChance: Float = 0f,
    val eliteChance: Float = 0f,
)

/** Which boss a stage ends on - a different fight, not just a different sprite. */
enum class BossKind {
    /** The cave's crimson drone, three times over: weaves on station and fires straight. */
    CAVE_DRONE,

    /**
     * The forest's Moth Queen: a figure eight on station, three phases, a shield she raises as
     * she changes phase, and wasp swarms she calls in. See [MothQueenBrain].
     */
    MOTH_QUEEN,

    /**
     * The desert's Sand Wyrm: a body of ten armored parts that breaches out of the dunes in arcs
     * aimed at the player, spits at the top of each one, and dives back under between them. Any
     * part of it can be hit. See [SandWyrmBrain].
     */
    SAND_WYRM,
}

/**
 * What a stage *is*, separate from how hard it is: its waves, its boss, and what the boss is
 * called when it arrives. [StageProgression] reads this against the clock and scales it by the
 * stage's difficulty.
 */
data class StageDesign(
    val waves: List<WaveDesign>,
    val boss: BossKind,
    val bossName: String,
) {
    companion object {
        /**
         * Stage 1. Each minute brings a group that moves in a way the last one did not, and the
         * escort in the fifth is the two that are hardest to lead.
         *
         * Every stage sends its elites on the same ramp: none in the opening minute, which is for
         * learning the stage's own enemies, and then a few groups in a hundred, climbing to the
         * escort. That comes to a couple of elites a minute early on and one every six or seven
         * seconds in the escort, in any stage: the forest's swarms are more enemies, but no more
         * groups.
         */
        val CAVE = StageDesign(
            waves = listOf(
                WaveDesign(listOf(SCOUT)),                                       // scouts only, straight and readable
                WaveDesign(listOf(SCOUT, WEAVER), eliteChance = 0.04f),          // weavers join them
                WaveDesign(listOf(WEAVER, STRIKER), eliteChance = 0.05f),        // the scouts give way to zigzags
                WaveDesign(listOf(SCOUT, WEAVER, STRIKER), eliteChance = 0.06f), // everything at once
                WaveDesign(listOf(WEAVER, STRIKER), eliteChance = 0.07f),        // the boss escort
            ),
            boss = BossKind.CAVE_DRONE,
            bossName = "THE CACO IMP",
        )

        /**
         * Stage 2. Harder than the cave on every axis the cave had - it is scaled by the stage's
         * difficulty like any later stage - and on three it did not: enemies come in groups, they
         * shoot back, and they hide behind shields. Each of those is introduced before it is
         * combined with the others, and the shields and guns ramp in over the stage rather than
         * arriving all at once.
         */
        val FOREST = StageDesign(
            waves = listOf(
                // A swarm to learn, and a shielded beetle to learn shields on.
                WaveDesign(listOf(WASP, BEETLE)),
                // Formations and the first thing that aims at the player.
                WaveDesign(listOf(WISP, SPITTER, WASP), shieldChance = 0.1f, eliteChance = 0.04f),
                // Divers, with the swarms and tanks from the opening minute for cover.
                WaveDesign(listOf(OWL, WASP, BEETLE), shieldChance = 0.2f, gunChance = 0.15f, eliteChance = 0.05f),
                // Everything at once.
                WaveDesign(listOf(WASP, BEETLE, SPITTER, OWL, WISP), shieldChance = 0.3f, gunChance = 0.25f, eliteChance = 0.06f),
                // The escort: everything that shoots or dives, and most of it armed or shielded.
                WaveDesign(listOf(SPITTER, OWL, WISP), shieldChance = 0.4f, gunChance = 0.35f, eliteChance = 0.07f),
            ),
            boss = BossKind.MOTH_QUEEN,
            bossName = "THE MOTH QUEEN",
        )

        /**
         * Stage 3, flown from noon to nightfall. Harder again than the forest, and it adds one new
         * direction to watch: things come up out of the sand. The first minute is the swarm and the
         * loop, one to cut through and one to read; the wyrmlings arrive in the second, before
         * anything else is shooting; the djinn's fans in the third; and by dusk everything is
         * armed, shielded, or leaping.
         */
        val DESERT = StageDesign(
            waves = listOf(
                // Noon: a cloud of locusts, and a hawk's loop to learn.
                WaveDesign(listOf(LOCUST, HAWK)),
                // Something comes up out of the sand, and a scarab whose shell grows back.
                WaveDesign(listOf(WYRMLING, LOCUST, SCARAB), shieldChance = 0.1f, eliteChance = 0.04f),
                // The golden hour: the djinn and its fans, with the hawks and the leapers for cover.
                WaveDesign(listOf(DJINN, HAWK, WYRMLING), shieldChance = 0.2f, gunChance = 0.15f, eliteChance = 0.05f),
                // Sunset, and everything at once.
                WaveDesign(listOf(LOCUST, HAWK, WYRMLING, DJINN, SCARAB), shieldChance = 0.3f, gunChance = 0.25f, eliteChance = 0.06f),
                // Dusk, and the escort: everything that leaps, loops or shoots.
                WaveDesign(listOf(WYRMLING, DJINN, HAWK, SCARAB), shieldChance = 0.4f, gunChance = 0.35f, eliteChance = 0.07f),
            ),
            boss = BossKind.SAND_WYRM,
            bossName = "THE SAND WYRM",
        )

        /**
         * The design of the stage with this 1-based id. Past the last one the last design repeats,
         * scaled harder by [StageProgression.forStage], rather than a later stage having nothing
         * to spawn.
         */
        fun forStage(id: Int): StageDesign = when {
            id <= 1 -> CAVE
            id == 2 -> FOREST
            else -> DESERT
        }
    }
}
