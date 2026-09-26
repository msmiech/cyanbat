package at.smiech.cyanbat.service

import at.smiech.cyanbat.service.EnemySpecies.BEETLE
import at.smiech.cyanbat.service.EnemySpecies.OWL
import at.smiech.cyanbat.service.EnemySpecies.SCOUT
import at.smiech.cyanbat.service.EnemySpecies.SPITTER
import at.smiech.cyanbat.service.EnemySpecies.STRIKER
import at.smiech.cyanbat.service.EnemySpecies.WASP
import at.smiech.cyanbat.service.EnemySpecies.WEAVER
import at.smiech.cyanbat.service.EnemySpecies.WISP

/**
 * One minute of a stage's enemies, as designed rather than as scaled.
 *
 * @param species what this wave draws from. Changing the mix every minute is what makes a new wave
 *   read as a new *group* of enemies rather than as more of the last one.
 * @param shieldChance the chance that an enemy which can carry a shield spawns behind one, as 0..1.
 * @param gunChance the chance that an [EnemySpecies.armable] enemy is issued a gun, as 0..1.
 */
data class WaveDesign(
    val species: List<EnemySpecies>,
    val shieldChance: Float = 0f,
    val gunChance: Float = 0f,
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
         */
        val CAVE = StageDesign(
            waves = listOf(
                WaveDesign(listOf(SCOUT)),                  // scouts only, straight and readable
                WaveDesign(listOf(SCOUT, WEAVER)),          // weavers join them
                WaveDesign(listOf(WEAVER, STRIKER)),        // the scouts give way to zigzags
                WaveDesign(listOf(SCOUT, WEAVER, STRIKER)), // everything at once
                WaveDesign(listOf(WEAVER, STRIKER)),        // the boss escort
            ),
            boss = BossKind.CAVE_DRONE,
            bossName = "FINAL BOSS",
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
                WaveDesign(listOf(WISP, SPITTER, WASP), shieldChance = 0.1f),
                // Divers, with the swarms and tanks from the opening minute for cover.
                WaveDesign(listOf(OWL, WASP, BEETLE), shieldChance = 0.2f, gunChance = 0.15f),
                // Everything at once.
                WaveDesign(listOf(WASP, BEETLE, SPITTER, OWL, WISP), shieldChance = 0.3f, gunChance = 0.25f),
                // The escort: everything that shoots or dives, and most of it armed or shielded.
                WaveDesign(listOf(SPITTER, OWL, WISP), shieldChance = 0.4f, gunChance = 0.35f),
            ),
            boss = BossKind.MOTH_QUEEN,
            bossName = "THE MOTH QUEEN",
        )

        /**
         * The design of the stage with this 1-based id. Past the last one the last design repeats,
         * scaled harder by [StageProgression.forStage], rather than a later stage having nothing
         * to spawn.
         */
        fun forStage(id: Int): StageDesign = if (id <= 1) CAVE else FOREST
    }
}
