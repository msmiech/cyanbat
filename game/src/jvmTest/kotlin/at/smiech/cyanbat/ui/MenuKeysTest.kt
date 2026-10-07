package at.smiech.cyanbat.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.FakeSettingsRepository
import at.smiech.cyanbat.data.ThemeMode
import at.smiech.engine.DisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The whole menu, worked from a keyboard alone, as a player with no mouse and no touch screen works
 * it: the cursor, where it starts, how the keys move it and take what it is on, and the way back.
 *
 * On the desktop's own terms - Compose's test harness is the desktop's - so its Back buttons are
 * there, and nothing here covers what Android does with a pad before the menu sees it.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MenuKeysTest {

    private class Menu(val settings: FakeSettingsRepository, val started: MutableList<Int>)

    private object NoHighscores : HighscoreStore {
        override val byStage = MutableStateFlow(emptyMap<Int, Int>())
        override fun saveAsync(stageId: Int, value: Int) = Unit
    }

    /**
     * The menu, opened on its main screen with stages up to [highestUnlocked] open, for [test] to
     * work, in a window the size of a phone held landscape, which is what the menu is laid out for
     * at its tightest. The view models run on a dispatcher of the test's own, so what they hold is
     * there the moment it is asked for.
     */
    private fun menu(highestUnlocked: Int = 1, test: ComposeUiTest.(Menu) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runDesktopComposeUiTest(
                width = PHONE_WIDTH * PHONE_DENSITY,
                height = PHONE_HEIGHT * PHONE_DENSITY
            ) {
                // The menu's sky moves for as long as it is up, so the clock never stops by itself.
                mainClock.autoAdvance = false
                val menu = Menu(FakeSettingsRepository(), mutableListOf())
                val viewModels = object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModels,
                        LocalDensity provides Density(PHONE_DENSITY.toFloat()),
                    ) {
                        CyanBatMenu(
                            MenuHost(
                                settings = menu.settings,
                                menuMusic = null,
                                stageUnlocks = StageUnlockStore.InMemory(highestUnlocked),
                                highscores = NoHighscores,
                                onStartGame = { menu.started += it },
                                onExit = {},
                            )
                        )
                    }
                }
                settle()
                test(menu)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** A few frames, for the effects a key or a new screen sets off to run. */
    private fun ComposeUiTest.settle() = repeat(4) { mainClock.advanceTimeByFrame() }

    /**
     * Presses and lets go of [key], [times] over, as a player does. Sent to the control under the
     * cursor, where there is one, and with none to the menu itself. A dialog is a window of its
     * own, with a cursor of its own over the menu's, which keeps its place underneath; the dialog's
     * is the later one.
     */
    private fun ComposeUiTest.press(key: Key, times: Int = 1) {
        repeat(times) {
            val focused = onAllNodes(isFocused())
            val target = if (focused.fetchSemanticsNodes().isEmpty()) {
                onAllNodes(isRoot()).onFirst()
            } else {
                focused.onLast()
            }
            target.performKeyInput { pressKey(key) }
            settle()
        }
    }

    /** The cursor is on the control saying [text], the [nth] of them on a screen with more than one. */
    private fun ComposeUiTest.cursorOn(text: String, nth: Int = 0) {
        onAllNodesWithText(text)[nth].assertIsFocused()
    }

    private fun ComposeUiTest.noCursor() {
        onAllNodes(isFocused()).assertCountEquals(0)
    }

    @Test
    fun `the cursor starts on Start Game`() = menu {
        cursorOn("Start Game")
    }

    @Test
    fun `the arrows and WASD move the cursor between the buttons`() = menu {
        press(Key.DirectionDown)
        cursorOn("Settings")
        press(Key.S)
        cursorOn("Help")
        press(Key.DirectionUp)
        press(Key.W)
        cursorOn("Start Game")
        // Exit stands apart, at the bottom right.
        press(Key.DirectionRight)
        cursorOn("Exit")
        press(Key.A)
        cursorOn("Credits")
    }

    @Test
    fun `there is nowhere past the first button`() = menu {
        press(Key.DirectionUp)
        cursorOn("Start Game")
    }

    @Test
    fun `Enter opens what the cursor is on, and its screen starts the cursor on its first choice`() =
        menu {
            press(Key.DirectionDown)
            press(Key.Enter)
            cursorOn("Music")
        }

    @Test
    fun `Space and Enter work the setting under the cursor`() = menu { menu ->
        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.Spacebar)
        assertFalse(menu.settings.music.value, "Space did not turn the music off")

        // Down past Sounds, the theme and the language to the display: Fullscreen, which only the
        // desktop has, then the choices Stretch, Black bars, Ambient bars.
        press(Key.DirectionDown, times = 4)
        cursorOn("Fullscreen")
        press(Key.Enter)
        assertTrue(menu.settings.fullscreen.value, "Enter did not go full screen")
        press(Key.DirectionDown)
        press(Key.Enter)
        assertEquals(DisplayMode.STRETCH, menu.settings.display.value)
    }

    @Test
    fun `the theme's segments take the cursor one at a time`() = menu { menu ->
        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.DirectionDown, times = 2)
        cursorOn("System")
        press(Key.DirectionRight)
        cursorOn("Dark")
        press(Key.Enter)
        assertEquals(ThemeMode.DARK, menu.settings.theme.value)
        // The menu turning dark under it leaves the cursor where it was.
        cursorOn("Dark")
    }

    /**
     * The language's segments work as the theme's do. Choosing one redraws the whole menu in it,
     * which puts the cursor back on the screen's first choice - but on the same screen, Settings.
     */
    @Test
    fun `the language's segments take the cursor one at a time`() = menu { menu ->
        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.DirectionDown, times = 3)
        // Down from the theme's System lands on the segment under it, the language row being longer.
        cursorOn("English")
        press(Key.DirectionLeft)
        // The second System: the theme's is the first.
        cursorOn("System", nth = 1)
        press(Key.DirectionRight, times = 2)
        cursorOn("Deutsch")
        press(Key.Enter)
        assertEquals(AppLanguage.GERMAN, menu.settings.chosenLanguage.value)
        cursorOn("Music")
        press(Key.DirectionDown, times = 5)
        cursorOn("Stretch to fit screen")
    }

    @Test
    fun `Escape goes back, to the button the screen was opened from`() = menu {
        press(Key.DirectionDown, times = 3)
        press(Key.Enter)
        onNodeWithText("Credits").assertExists()
        press(Key.Escape)
        cursorOn("Credits")
    }

    @Test
    fun `Backspace goes back too, and up from the first choice is the Back button`() = menu {
        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.DirectionUp)
        cursorOn("← Back")
        press(Key.Backspace)
        cursorOn("Settings")
    }

    @Test
    fun `a pad's B button goes back`() = menu {
        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.ButtonB)
        cursorOn("Settings")
    }

    /**
     * A key held down repeats. Back is one step however long it is held: on Android, the repeats of
     * an Escape that had gone back a screen went on to the main screen, and out of the app from it.
     */
    @Test
    fun `Escape held down goes back once`() {
        var backs = 0
        runComposeUiTest {
            setContent {
                MenuKeys(onBack = { backs++; true }) {
                    val home = remember { FocusRequester() }
                    HomeCursor(home)
                    Button(onClick = {}, modifier = Modifier.focusRequester(home)) { Text("Stay") }
                }
            }
            onNodeWithText("Stay").assertIsFocused().performKeyInput {
                keyDown(Key.Escape)
                // Long enough for the platform's repeats to start and run on.
                advanceEventTime(1_000)
                keyUp(Key.Escape)
            }
            assertEquals(1, backs)
        }
    }

    @Test
    fun `Escape on the main screen stays there`() = menu {
        press(Key.Escape)
        cursorOn("Start Game")
    }

    @Test
    fun `the credits start the cursor on the way back out`() = menu {
        press(Key.DirectionDown, times = 3)
        press(Key.Enter)
        cursorOn("← Back")
        press(Key.Enter)
        cursorOn("Credits")
    }

    @Test
    fun `the stage select starts on the furthest stage open, and Enter starts the one the cursor is on`() =
        menu(highestUnlocked = 3) { menu ->
            press(Key.Enter)
            cursorOn("Stage 3: The Desert")
            press(Key.DirectionRight)
            // The lagoon is locked, and a locked card takes no cursor.
            cursorOn("Stage 3: The Desert")
            press(Key.DirectionLeft)
            press(Key.Enter)
            assertEquals(listOf(2), menu.started)
        }

    /**
     * A click puts the menu in touch mode, where no control can hold the cursor. The first key
     * after it brings the cursor out on the screen's first choice, and takes nothing.
     */
    @Test
    fun `a key after a click brings the cursor back, onto the first choice`() = menu { menu ->
        onNodeWithText("Settings").performMouseInput { click() }
        settle()
        noCursor()

        press(Key.Spacebar)
        cursorOn("Music")
        assertEquals(
            true,
            menu.settings.music.value,
            "the key that brought the cursor out also took the setting"
        )

        press(Key.DirectionDown)
        cursorOn("Sounds")
    }

    private companion object {
        /**
         * A phone held landscape: about 915 by 412 dp, at a density of its own. At the harness's
         * own density of 1, the title is drawn larger and squeezes the buttons under it to nothing.
         */
        const val PHONE_WIDTH = 915
        const val PHONE_HEIGHT = 412
        const val PHONE_DENSITY = 2
    }

    @Test
    fun `the help dialog starts on OK, and Escape closes it`() = menu {
        press(Key.DirectionDown, times = 2)
        press(Key.Enter)
        cursorOn("OK")
        // Up and down scroll the text, which is longer than the dialog, rather than move off OK.
        val text = onNodeWithText("Shoot:", substring = true)
        fun scrolled() =
            text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(0f, scrolled())
        press(Key.DirectionDown)
        cursorOn("OK")
        assertTrue(scrolled() > 0f, "Down did not scroll the help text")
        press(Key.DirectionUp)
        repeat(30) { mainClock.advanceTimeByFrame() }
        assertEquals(0f, scrolled(), "Up did not scroll the help text back")
        press(Key.Escape)
        onAllNodesWithText("OK").assertCountEquals(0)
        cursorOn("Help")
    }
}
