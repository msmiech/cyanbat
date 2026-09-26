package at.smiech.cyanbat.desktop.recorder

import at.smiech.cyanbat.ScoreTracker
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
    private val scoringField = field("scoring")
    private val bannerTextField = field("bannerText")
    private val bannerTimeField = field("bannerTime")

    val world: World get() = worldField.get(screen) as World
    val batId: EntityId get() = batIdField.getInt(screen)

    /** The power-ups on offer, or empty when no level up dialog is up. */
    @Suppress("UNCHECKED_CAST")
    val offer: List<PowerUp> get() = offerField.get(screen) as List<PowerUp>

    val stageComplete: Boolean get() = stageCompleteField.getBoolean(screen)
    val level: Int get() = (progressField.get(screen) as PlayerProgress).level
    val score: Int get() = (scoringField.get(screen) as ScoreTracker).score

    /** The wave or boss announcement on screen, if one is. */
    val banner: String? get() = if (bannerTimeField.getFloat(screen) > 0f) bannerTextField.get(screen) as String? else null

    private fun field(name: String): Field =
        try {
            GameScreen::class.java.getDeclaredField(name).apply { isAccessible = true }
        } catch (e: NoSuchFieldException) {
            throw IllegalStateException("GameScreen has no field '$name' any more; update RunProbe to match", e)
        }
}
