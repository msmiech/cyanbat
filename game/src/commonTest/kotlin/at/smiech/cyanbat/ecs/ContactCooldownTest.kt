package at.smiech.cyanbat.ecs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactCooldownTest {

    private val cooldown = ContactCooldownComponent()

    @Test
    fun `a weapon lands once and then waits out its rehit time`() {
        assertTrue(cooldown.take(ContactWeapon.ORB, now = 1f, seconds = 0.3f))
        assertFalse(cooldown.take(ContactWeapon.ORB, now = 1.1f, seconds = 0.3f), "landed again inside the wait")
        assertFalse(cooldown.take(ContactWeapon.ORB, now = 1.29f, seconds = 0.3f))
        assertTrue(cooldown.take(ContactWeapon.ORB, now = 1.3f, seconds = 0.3f))
    }

    /** A refused hit does not push the wait back, or a target held in a weapon would never be hit again. */
    @Test
    fun `a refused hit does not start the wait over`() {
        cooldown.take(ContactWeapon.WAKE, now = 0f, seconds = 0.4f)
        cooldown.take(ContactWeapon.WAKE, now = 0.3f, seconds = 0.4f)

        assertTrue(cooldown.take(ContactWeapon.WAKE, now = 0.4f, seconds = 0.4f))
    }

    @Test
    fun `each weapon waits on its own`() {
        assertTrue(cooldown.take(ContactWeapon.ORB, now = 0f, seconds = 0.3f))

        assertTrue(cooldown.take(ContactWeapon.WAKE, now = 0.1f, seconds = 0.4f), "the orb held the wake off")
    }
}
