package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.ScoreTracker
import at.smiech.cyanbat.progress.PlayerLoadout
import at.smiech.cyanbat.progress.PlayerProgress
import at.smiech.cyanbat.progress.PowerUp
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.World
import java.lang.reflect.Field

/**
 * The parts of a run the recorder needs and [GameScreen] keeps to itself, read by reflection.
 *
 * A public accessor on the screen for a tool's sake would be API the game has no use for, so the
 * recorder looks in from outside instead. The cost is that it names private fields: renaming one
 * breaks the recorder on its first frame, with the missing name in the error.
 */
class RunProbe(private val screen: GameScreen) {
    private val worldField = field("world")
    private val batIdField = field("batId")
    private val offerField = field("offer")
    private val stageCompleteField = field("stageComplete")
    private val progressField = field("progress")
    private val loadoutField = field("loadout")
    private val scoringField = field("scoring")
    private val bannerTextField = field("bannerText")
    private val bannerTimeField = field("bannerTime")

    /** The run's world. */
    val world: World get() = worldField.get(screen) as World

    /** The bat's entity. */
    val batId: EntityId get() = batIdField.getInt(screen)

    /**
     * The power-ups on offer, or empty when no level up dialog is up. Set, it puts a dialog up with
     * those cards on it, armed, for a test to take one off the way a player does.
     */
    @Suppress("UNCHECKED_CAST")
    var offer: List<PowerUp>
        get() = offerField.get(screen) as List<PowerUp>
        set(cards) = offerField.set(screen, cards)

    /** Whether the boss is down and the stage won. */
    val stageComplete: Boolean get() = stageCompleteField.getBoolean(screen)

    /** The bat's level. */
    val level: Int get() = (progressField.get(screen) as PlayerProgress).level

    /** Everything the run has earned toward its levels, from the start. */
    val experience: Int get() = (progressField.get(screen) as PlayerProgress).totalExperience

    /** What the run's power-ups have made of the bat. */
    val loadout: PlayerLoadout get() = loadoutField.get(screen) as PlayerLoadout

    /** The run's score. */
    val score: Int get() = (scoringField.get(screen) as ScoreTracker).score

    /** The wave or boss announcement on screen, if one is. */
    val banner: String?
        get() {
            if (bannerTimeField.getFloat(screen) <= 0f) return null
            return bannerTextField.get(screen) as String?
        }

    /** [GameScreen]'s private field [name], opened for reading. */
    private fun field(name: String): Field =
        try {
            GameScreen::class.java.getDeclaredField(name).apply { isAccessible = true }
        } catch (e: NoSuchFieldException) {
            throw IllegalStateException(
                "GameScreen has no field '$name' any more; update RunProbe to match",
                e,
            )
        }
}
