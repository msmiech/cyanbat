package at.smiech.cyanbat

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.smiech.cyanbat.activity.CyanBatGameActivity
import at.smiech.cyanbat.data.AppLocale
import at.smiech.cyanbat.data.ClearsBeforeDifficultiesMigration
import at.smiech.cyanbat.data.DataStoreAchievementStore
import at.smiech.cyanbat.data.DataStoreHighscoreStore
import at.smiech.cyanbat.data.DataStoreSettingsRepository
import at.smiech.cyanbat.data.DataStoreStageUnlockStore
import at.smiech.cyanbat.data.LegacyHighscoreMigration
import at.smiech.cyanbat.data.StageOrderMigration
import at.smiech.cyanbat.ui.CyanBatMenu
import at.smiech.cyanbat.ui.MenuHost
import at.smiech.cyanbat.ui.rememberMenuBackStack
import at.smiech.engine.Audio
import at.smiech.engine.InputMethod
import at.smiech.engine.Music
import at.smiech.engine.impl.AndroidAudio
import at.smiech.engine.impl.ControlHandler
import at.smiech.engine.impl.inputMethodOf
import java.io.File
import kotlin.math.abs

internal val PREFS_KEY_MUSIC = booleanPreferencesKey("music_enabled")
internal val PREFS_KEY_SOUNDS = booleanPreferencesKey("sounds_enabled")
internal val PREFS_KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")

/** Stored by name, so reordering or adding modes cannot turn one player's choice into another. */
internal val PREFS_KEY_DISPLAY_MODE = stringPreferencesKey("display_mode")

/** Stored by name, for the same reason as the display mode. */
internal val PREFS_KEY_THEME_MODE = stringPreferencesKey("theme_mode")

/** Stored by name too, and only before Android 13, which keeps an app's language itself; see [AppLocale]. */
internal val PREFS_KEY_LANGUAGE = stringPreferencesKey("language")

/** The difficulty last chosen, by name too, for the same reason as the display mode. */
internal val PREFS_KEY_DIFFICULTY = stringPreferencesKey("difficulty")

/**
 * How a finger steers the bat, by name too. Absent until the player has said, which is what has the
 * menu ask before their first run.
 */
internal val PREFS_KEY_TOUCH_STEERING = stringPreferencesKey("touch_steering")

/** How far the stick on the screen has to be pushed, by name too. */
internal val PREFS_KEY_STICK_SENSITIVITY = stringPreferencesKey("stick_sensitivity")

/** The single highscore kept before they were per stage; see [LegacyHighscoreMigration]. */
internal val PREFS_KEY_LEGACY_HIGH_SCORE = intPreferencesKey("highscore")

/** A stage's highscore is stored under this followed by its id: "highscore_stage_1". */
internal const val PREFS_STAGE_HIGH_SCORE_PREFIX = "highscore_stage_"
internal fun prefsKeyStageHighScore(stageId: Int) =
    intPreferencesKey(PREFS_STAGE_HIGH_SCORE_PREFIX + stageId)

internal val PREFS_KEY_HIGHEST_STAGE = intPreferencesKey("highest_stage_unlocked")

/**
 * A stage's best clear is stored under this followed by its id: "cleared_stage_1". The value is its
 * difficulty's stars, which rank the difficulties without depending on their names or order.
 */
internal const val PREFS_STAGE_CLEARED_PREFIX = "cleared_stage_"
internal fun prefsKeyStageCleared(stageId: Int) =
    intPreferencesKey(PREFS_STAGE_CLEARED_PREFIX + stageId)

/** Whether the clears from before difficulties are in; see [ClearsBeforeDifficultiesMigration]. */
internal val PREFS_KEY_CLEARS_KEPT = booleanPreferencesKey("stage_clears_kept")

/** Which order the stages' highscores are stored in; see [StageOrderMigration]. */
internal val PREFS_KEY_STAGE_ORDER = intPreferencesKey("stage_order")
internal val Context.dataStore by preferencesDataStore(
    name = "cyanbat",
    // In the order they were needed: the single legacy score lands on stage 1, the cave it was earned
    // in, and the cave's scores then move to where the cave went. The clears only read the unlocks,
    // which neither of those moves.
    produceMigrations = {
        listOf(LegacyHighscoreMigration, StageOrderMigration, ClearsBeforeDifficultiesMigration)
    },
)

/**
 * The achievements, in the same DataStore as the rest, with their pictures in the app's own files,
 * where the menu and the game activity both find them.
 */
internal fun Context.achievementStore(): DataStoreAchievementStore =
    DataStoreAchievementStore(dataStore, File(filesDir, "achievements"))

/**
 * Android host for the shared menu. Its job is to supply the platform pieces - DataStore-backed
 * settings, the menu track, and how to start the game - and let [CyanBatMenu] do the rest.
 *
 * @author msmiech, KittysCode
 */
class MainActivity : ComponentActivity() {

    private lateinit var audio: Audio

    /**
     * Created once per activity rather than inside [setContent]. That lambda recomposes whenever
     * the back stack changes, and a track built there read and set up a fresh player on every trip
     * into Settings or Credits - none of which the menu ever played.
     */
    private lateinit var menuMusic: Music

    /** Whether [onStop] silenced the menu track, so [onStart] knows to bring it back. */
    private var resumeMusicOnStart = false

    /**
     * What the menu was last worked with, for the help's controls and for the run it starts, whose
     * first prompts are then for the same. Only touched on the main thread.
     */
    private var inputMethod by mutableStateOf(InputMethod.TOUCH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        AppLocale.start(this)
        audio = AndroidAudio(this)
        menuMusic = audio.newMusic("music/menu.wav")
        val settings = DataStoreSettingsRepository(dataStore)
        val stageUnlocks = DataStoreStageUnlockStore(dataStore)
        val highscores = DataStoreHighscoreStore(dataStore)
        val achievements = achievementStore()

        setContent {
            // The shared back stack is host-owned so the Android back gesture can drive it;
            // Navigation3 used to provide this before the menu became multiplatform.
            val backStack = rememberMenuBackStack()
            BackHandler(enabled = backStack.canGoBack) { backStack.back() }

            // Keys seen on their way in, ahead of whatever in the menu takes them. Back, Home and
            // the volume keys are the system's, pressed on a phone as much as with a keyboard, so
            // they say nothing.
            Box(
                Modifier.onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    if (key.action == KeyEvent.ACTION_DOWN && !key.isSystem) {
                        inputMethod = inputMethodOf(key.device)
                    }
                    false
                }
            ) {
                CyanBatMenu(
                    backStack = backStack,
                    host = MenuHost(
                        settings = settings,
                        menuMusic = menuMusic,
                        stageUnlocks = stageUnlocks,
                        highscores = highscores,
                        achievements = achievements,
                        onStartGame = { stageId, difficulty ->
                            startActivity(
                                Intent(this@MainActivity, CyanBatGameActivity::class.java)
                                    .putExtra(CyanBatGameActivity.EXTRA_STAGE_ID, stageId)
                                    .putExtra(CyanBatGameActivity.EXTRA_DIFFICULTY, difficulty.name)
                                    .putExtra(
                                        CyanBatGameActivity.EXTRA_INPUT_METHOD,
                                        inputMethod.name,
                                    )
                            )
                        },
                        onExit = { finishAffinity() },
                        inputMethod = { inputMethod },
                    )
                )
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val deflection = ControlHandler.PAD_USE_DEFLECTION
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            (abs(event.getAxisValue(MotionEvent.AXIS_X)) > deflection ||
                    abs(event.getAxisValue(MotionEvent.AXIS_Y)) > deflection ||
                    event.getAxisValue(MotionEvent.AXIS_HAT_X) != 0f ||
                    event.getAxisValue(MotionEvent.AXIS_HAT_Y) != 0f)
        ) {
            inputMethod = inputMethodOf(event.device)
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val mouse = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
            inputMethod = if (mouse) InputMethod.KEYBOARD_MOUSE else InputMethod.TOUCH
        }
        return super.dispatchTouchEvent(event)
    }

    /** The language goes back on, or is read again, before the menu next reads a string. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.reapply()
    }

    // Nothing else stops the menu track when the app leaves the screen - the menu only silences
    // it on the way into a game - so without these it played on behind the home screen.
    override fun onStart() {
        super.onStart()
        if (resumeMusicOnStart) {
            resumeMusicOnStart = false
            menuMusic.play()
        }
    }

    override fun onStop() {
        super.onStop()
        resumeMusicOnStart = menuMusic.isPlaying
        if (resumeMusicOnStart) menuMusic.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.dispose()
    }
}
