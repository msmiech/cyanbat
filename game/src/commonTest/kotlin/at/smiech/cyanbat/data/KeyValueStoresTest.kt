package at.smiech.cyanbat.data

import at.smiech.cyanbat.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyValueStoresTest {

    @Test
    fun settingsAreReadBackByTheNextLaunch() = runTest {
        val storage = MemoryKeyValueStorage()
        KeyValueSettingsRepository(storage) {}.apply {
            setMusicEnabled(false)
            setDifficulty(Difficulty.EASY)
            setTouchSteering(TouchSteering.STICK)
            setLanguage(AppLanguage.POLISH)
        }

        val applied = mutableListOf<AppLanguage>()
        val again = KeyValueSettingsRepository(storage) { applied += it }

        assertEquals(false, again.isMusicEnabled.first())
        assertEquals(true, again.isSoundEnabled.first())
        assertEquals(Difficulty.EASY, again.difficulty.first())
        assertEquals(TouchSteering.STICK, again.touchSteering.first())
        assertEquals(AppLanguage.POLISH, again.language.first())
        // Put into effect as the store is made, before the menu reads a string.
        assertEquals(listOf(AppLanguage.POLISH), applied)
    }

    @Test
    fun aLanguageThePlatformKeepsBeatsTheStoredOne() = runTest {
        val storage = MemoryKeyValueStorage()
        KeyValueSettingsRepository(storage) {}.setLanguage(AppLanguage.POLISH)

        val settings = KeyValueSettingsRepository(storage, AppLanguage.GERMAN) {}

        assertEquals(AppLanguage.GERMAN, settings.language.first())
    }

    @Test
    fun nobodyHasChosenHowToSteerUntilTheyDo() = runTest {
        assertNull(KeyValueSettingsRepository(MemoryKeyValueStorage()) {}.touchSteering.first())
    }

    @Test
    fun aHighscoreOnlyEverRises() = runTest {
        val storage = MemoryKeyValueStorage()
        val scores = KeyValueHighscoreStore(storage)
        scores.saveAsync(2, 500)
        scores.saveAsync(2, 300)

        assertEquals(mapOf(2 to 500), KeyValueHighscoreStore(storage).byStage.first())
    }

    @Test
    fun aClearKeepsTheHardestDifficulty() = runTest {
        val storage = MemoryKeyValueStorage()
        val unlocks = KeyValueStageUnlockStore(storage)
        unlocks.recordClearAsync(1, Difficulty.NORMAL)
        unlocks.recordClearAsync(1, Difficulty.EASY)
        unlocks.unlockAsync(2)

        val again = KeyValueStageUnlockStore(storage)
        assertEquals(mapOf(1 to Difficulty.NORMAL), again.clears.first())
        assertEquals(2, again.highestUnlocked.first())
    }

    @Test
    fun unlocksKeptBeforeDifficultiesCountAsHardClearsOnce() = runTest {
        val storage = MemoryKeyValueStorage()
        storage.put("highest_stage_unlocked", "3")

        val unlocks = KeyValueStageUnlockStore(storage)
        assertEquals(mapOf(1 to Difficulty.HARD, 2 to Difficulty.HARD), unlocks.clears.first())

        // A later unlock is not taken for a Hard clear by the next launch.
        unlocks.unlockAsync(4)
        assertEquals(
            mapOf(1 to Difficulty.HARD, 2 to Difficulty.HARD),
            KeyValueStageUnlockStore(storage).clears.first(),
        )
    }
}
