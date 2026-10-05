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
    /**
     * The jungle's Moth Queen: a figure eight on station, three phases, a shield she raises as
     * she changes phase, and wasp swarms she calls in. See [MothQueenBrain].
     */
    MOTH_QUEEN,

    /**
     * The cave's Caco Imp, its crimson imp three times over and alight in the dark: it weaves on
     * station, then puts its light out and prowls the dark between ambushes, and ends ablaze and
     * calling in imps. See [CacoImpBrain].
     */
    CACO_IMP,

    /**
     * The desert's Sand Wyrm: a body of ten armored parts that breaches out of the dunes in arcs
     * aimed at the player, spits at the top of each one, and dives back under between them. Any
     * part of it can be hit. See [SandWyrmBrain].
     */
    SAND_WYRM,

    /**
     * The lagoon's Naga: a hooded serpent of thirteen parts that rears up out of the water, sways
     * and spits, strikes at the bat, and dives to come up somewhere else - in five phases, with its
     * brood, a shield and a swim across the frame among them. See [NagaBrain].
     */
    NAGA,
}

/**
 * What a stage *is*, separate from how hard it is: its waves, its boss, and what the boss is
 * called when it arrives. [StageProgression] reads this against the clock and scales it by the
 * stage's difficulty.
 *
 * @param bossToughness how hard the boss is against the stage's own difficulty: 1 for a boss scaled
 *   like its waves, more for one kept harder than the waves that lead up to it.
 * @param bossVitality how much more health the boss has on top of that, and nothing else: a longer
 *   fight, where [bossToughness] would also make every one of its blows hit harder.
 */
data class StageDesign(
    val waves: List<WaveDesign>,
    val boss: BossKind,
    val bossName: String,
    val bossToughness: Float = 1f,
    val bossVitality: Float = 1f,
) {
    companion object {
        /**
         * Stage 1, and the first thing a player flies. Its enemies do more than cross the screen:
         * they come in groups, they shoot back, and they hide behind shields. Each of those is
         * introduced before it is combined with the others, and the shields and guns a wave hands
         * out ramp in over the stage - sparingly, since this is where a player meets all three.
         *
         * Every stage sends its elites on the same ramp: none in the opening minute, which is for
         * learning the stage's own enemies, and then a few groups in a hundred, climbing to the
         * escort. That comes to a couple of elites a minute early on and one every six or seven
         * seconds in the escort, in any stage: the jungle's swarms are more enemies, but no more
         * groups.
         *
         * The Moth Queen is the exception to the easing. She was the second stage's boss, and is
         * fought as hard as she was there - her health, her bolts and the swarms she calls in -
         * at the end of a stage that leads up to her more gently.
         */
        val JUNGLE = StageDesign(
            waves = listOf(
                // A swarm to learn, and a shielded beetle to learn shields on.
                WaveDesign(listOf(WASP, BEETLE)),
                // Formations and the first thing that aims at the player, on their own: nothing
                // is handed a shield or a gun yet.
                WaveDesign(listOf(WISP, SPITTER, WASP), eliteChance = 0.04f),
                // Divers, with the swarms and tanks from the opening minute for cover, and the
                // first few shields and guns handed out.
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
            bossName = "THE MOTH QUEEN",
            bossToughness = 1f + STAGE_DIFFICULTY_STEP,
        )

        /**
         * Stage 2, flown in the dark by the bat's own light. Each minute brings a group that moves
         * in a way the last one did not, and the escort in the fifth is the two that are hardest
         * to lead. None of them shoots or hides behind a shield, as the jungle's do: the cave's
         * difficulty is its stage's, everything tougher and quicker, and the dark it is flown in.
         * Its boss is where the dark turns on the player.
         */
        val CAVE = StageDesign(
            waves = listOf(
                WaveDesign(listOf(SCOUT)),                                       // scouts only, straight and readable
                WaveDesign(
                    listOf(SCOUT, WEAVER),
                    eliteChance = 0.04f
                ),          // weavers join them
                WaveDesign(
                    listOf(WEAVER, STRIKER),
                    eliteChance = 0.05f
                ),        // the scouts give way to zigzags
                WaveDesign(
                    listOf(SCOUT, WEAVER, STRIKER),
                    eliteChance = 0.06f
                ), // everything at once
                WaveDesign(listOf(WEAVER, STRIKER), eliteChance = 0.07f),        // the boss escort
            ),
            boss = BossKind.CACO_IMP,
            bossName = "THE CACO IMP",
        )

        /**
         * Stage 3, flown from noon to nightfall. Harder again than the cave, and it adds one new
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
            bossName = "THE SAND WYRM",
        )

        /**
         * Stage 4, flown from night to noon across a bay of limestone islands to a temple in the
         * water, and the hardest stage in the game. Harder again than the desert on every axis it
         * had, and it adds one more direction to watch: sharks come in from behind, along the
         * waterline with their fins showing, and leap forward at the bat. They arrive in the second
         * minute, as the wyrmlings did, before anything else is shooting; the puffers' rings of
         * spines - a boss's pattern, in an ordinary enemy - in the third; the Naga's brood in the
         * fourth, as the temple comes into sight. Shields, guns and elites ramp higher than any stage
         * before it.
         *
         * The Naga is fought at the stage's own difficulty, with nearly twice the health it would
         * have for it: its fight is five phases long rather than three.
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
            bossName = "THE NAGA",
            bossVitality = NAGA_VITALITY,
        )

        /**
         * The design of the stage with this 1-based id. Past the last one the last design repeats,
         * scaled harder by [StageProgression.forStage], rather than a later stage having nothing
         * to spawn.
         */
        fun forStage(id: Int): StageDesign = when {
            id <= 1 -> JUNGLE
            id == 2 -> CAVE
            id == 3 -> DESERT
            else -> LAGOON
        }
    }
}
