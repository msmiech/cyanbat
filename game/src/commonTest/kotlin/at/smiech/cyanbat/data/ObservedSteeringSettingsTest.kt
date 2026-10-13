package at.smiech.cyanbat.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObservedSteeringSettingsTest {

    @Test
    fun `reflects the stored choice and follows a change`() = runTest {
        val repository = FakeSettingsRepository()
        repository.steering.value = TouchSteering.STICK

        val settings = ObservedSteeringSettings(repository, backgroundScope)
        runCurrent()
        assertEquals(TouchSteering.STICK, settings.touchSteering)

        repository.setTouchSteering(TouchSteering.DRAG)
        runCurrent()
        assertEquals(TouchSteering.DRAG, settings.touchSteering)
    }

    /** A player who has not been asked flies as the game always flew: by dragging the bat. */
    @Test
    fun `a player who has not chosen drags`() = runTest {
        val settings = ObservedSteeringSettings(FakeSettingsRepository(), backgroundScope)
        runCurrent()
        assertEquals(TouchSteering.DRAG, settings.touchSteering)
    }

    /** The store answers asynchronously; until it does, the run is flown as it always was. */
    @Test
    fun `drags before the first value arrives`() = runTest {
        val repository = FakeSettingsRepository()
        repository.steering.value = TouchSteering.STICK

        val settings = ObservedSteeringSettings(repository, backgroundScope)
        // Deliberately no runCurrent: the collector has not been dispatched yet.

        assertEquals(TouchSteering.DRAG, settings.touchSteering)
    }

    @Test
    fun `a name a newer build stored reads as not chosen`() {
        assertEquals(null, TouchSteering.fromName("TILT"))
        assertEquals(null, TouchSteering.fromName(null))
        assertEquals(TouchSteering.STICK, TouchSteering.fromName("STICK"))
    }
}
