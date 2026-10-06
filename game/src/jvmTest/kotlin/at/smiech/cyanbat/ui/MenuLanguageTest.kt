package at.smiech.cyanbat.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AppLanguage
import at.smiech.cyanbat.data.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Choosing a language in Settings redraws the menu in it, there and then.
 *
 * Compose's resources pick their language when a string is first read, and nothing reads a string
 * again by itself, so the menu has to. The settings here put a language into effect the way the
 * desktop does, on the JVM's default locale, and the strings are the real ones.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MenuLanguageTest {

    private object NoHighscores : HighscoreStore {
        override val byStage = MutableStateFlow(emptyMap<Int, Int>())
        override fun saveAsync(stageId: Int, value: Int) = Unit
    }

    @Test
    fun `choosing a language redraws the menu in it`() {
        val system = Locale.getDefault()
        val settings = FakeSettingsRepository { language ->
            Locale.setDefault(language.tag?.let(Locale::forLanguageTag) ?: system)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runDesktopComposeUiTest(width = 915 * 2, height = 412 * 2) {
                mainClock.autoAdvance = false
                val viewModels = object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModels,
                        LocalDensity provides Density(2f),
                    ) {
                        CyanBatMenu(
                            MenuHost(
                                settings = settings,
                                menuMusic = null,
                                stageUnlocks = StageUnlockStore.InMemory(),
                                highscores = NoHighscores,
                                onStartGame = {},
                                onExit = {},
                            )
                        )
                    }
                }
                fun settle() = repeat(4) { mainClock.advanceTimeByFrame() }
                settle()
                onNodeWithText("Settings").performClick()
                settle()

                onNodeWithText("Deutsch").performClick()
                settle()
                assertEquals(AppLanguage.GERMAN, settings.chosenLanguage.value)
                // Still in Settings, now in German, down to the way back out.
                onNodeWithText("Einstellungen").assertExists()
                onNodeWithText("Sprache").assertExists()
                onNodeWithText("← Zurück").assertExists()

                onNodeWithText("Polski").performClick()
                settle()
                onNodeWithText("Ustawienia").assertExists()

                // The language's own System, after the theme's; back to English, as this JVM is.
                onAllNodesWithText("Systemowy")[1].performClick()
                settle()
                assertEquals(AppLanguage.SYSTEM, settings.chosenLanguage.value)
                onNodeWithText("Language").assertExists()
            }
        } finally {
            Dispatchers.resetMain()
            Locale.setDefault(system)
        }
    }
}
