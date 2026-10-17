package at.smiech.cyanbat.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import at.smiech.cyanbat.AchievementStore
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the stage select lays its cards out: in rows as many as the screen has room for, and every
 * card in a row as tall as the others, a locked one's short description or not.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class StageSelectLayoutTest {

    private object NoHighscores : HighscoreStore {
        override val byStage = MutableStateFlow(emptyMap<Int, Int>())
        override fun saveAsync(stageId: Int, value: Int) = Unit
    }

    /** The stage select, with the first three stages open, on a screen [width] by [height] dp. */
    private fun stageSelect(width: Int, height: Int, test: ComposeUiTest.() -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runDesktopComposeUiTest(width = width * DENSITY, height = height * DENSITY) {
                mainClock.autoAdvance = false
                val viewModels = object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
                val backStack = MenuBackStack(MenuDestination.StageSelect)
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides viewModels,
                        LocalDensity provides Density(DENSITY.toFloat()),
                    ) {
                        CyanBatMenu(
                            MenuHost(
                                settings = FakeSettingsRepository(),
                                menuMusic = null,
                                stageUnlocks = StageUnlockStore.InMemory(3),
                                highscores = NoHighscores,
                                achievements = AchievementStore.InMemory(),
                                onStartGame = { _, _ -> },
                                onExit = {},
                            ),
                            backStack = backStack,
                        )
                    }
                }
                repeat(4) { mainClock.advanceTimeByFrame() }
                test()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun ComposeUiTest.card(name: String): DpRect =
        onNodeWithText(name).getBoundsInRoot()

    private val ComposeUiTest.cards
        get() = listOf(
            card("Stage 1: The Jungle"),
            card("Stage 2: The Cave"),
            card("Stage 3: The Desert"),
            card("Stage 4: The Lagoon"),
        )

    @Test
    fun `a phone held landscape has the four stages in one row, all as tall, the locked one too`() =
        stageSelect(width = 915, height = 412) {
            val cards = cards
            for (card in cards.drop(1)) {
                assertEquals(cards[0].top, card.top, "not in one row: $cards")
                assertEquals(cards[0].height, card.height, "not all as tall: $cards")
            }
        }

    @Test
    fun `a narrow window has the stages two to a row, each row's cards as tall`() =
        stageSelect(width = 390, height = 760) {
            val cards = cards
            for ((left, right) in cards.chunked(2)) {
                assertEquals(left.top, right.top, "not side by side: $cards")
                assertEquals(left.height, right.height, "not as tall: $cards")
            }
            assertTrue(cards[2].top >= cards[0].bottom, "the second row is not under the first: $cards")
        }

    private companion object {
        const val DENSITY = 2
    }
}
