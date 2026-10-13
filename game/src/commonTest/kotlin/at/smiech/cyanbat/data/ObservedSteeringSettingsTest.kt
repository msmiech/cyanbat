package at.smiech.cyanbat.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `follows the stick's sensitivity and is medium until it arrives`() = runTest {
        val repository = FakeSettingsRepository()
        repository.sensitivity.value = StickSensitivity.LOW

        val settings = ObservedSteeringSettings(repository, backgroundScope)
        assertEquals(StickSensitivity.MEDIUM, settings.stickSensitivity)
        runCurrent()
        assertEquals(StickSensitivity.LOW, settings.stickSensitivity)

        repository.setStickSensitivity(StickSensitivity.HIGH)
        runCurrent()
        assertEquals(StickSensitivity.HIGH, settings.stickSensitivity)
    }

    /** Medium, the default, asks for more of a push than the stick first did, which was High. */
    @Test
    fun `the less sensitive the further the push`() {
        assertEquals(StickSensitivity.MEDIUM, StickSensitivity.DEFAULT)
        assertTrue(StickSensitivity.LOW.radius > StickSensitivity.MEDIUM.radius)
        assertTrue(StickSensitivity.MEDIUM.radius > StickSensitivity.HIGH.radius)
        assertEquals(StickSensitivity.MEDIUM, StickSensitivity.fromName("TWITCHY"))
    }

    @Test
    fun `a name a newer build stored reads as not chosen`() {
        assertEquals(null, TouchSteering.fromName("TILT"))
        assertEquals(null, TouchSteering.fromName(null))
        assertEquals(TouchSteering.STICK, TouchSteering.fromName("STICK"))
    }
}
