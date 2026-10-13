package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.resource.SoundEffect
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every sound effect is a plain PCM WAV on the classpath, which every platform plays without a
 * codec of its own: the JDK decodes PCM natively, and AGP leaves `.wav` uncompressed in the APK,
 * which is what `AssetManager.openFd` needs to hand SoundPool a file descriptor.
 *
 * The desktop once carried MP3 service providers for a death sound that was MP3. They registered
 * themselves invisibly, so an MP3 effect added now would build and then play nothing on the
 * desktop; this fails instead. The music is IMA ADPCM, not PCM; see `MusicStemTest`.
 */
class SoundEffectFilesTest {

    private val files = SoundEffect.entries.map { it.file }

    @Test
    fun `every effect is a wav`() {
        for (name in files) assertTrue(name.endsWith(".wav"), "$name is not a WAV")
    }

    @Test
    fun `every effect is on the classpath`() {
        for (name in files) {
            assertNotNull(
                javaClass.getResourceAsStream("/$name"),
                "$name is missing - check the assets/ resources srcDir in build.gradle.kts"
            )
        }
    }

    /**
     * As plain PCM, too: a WAV can hold compressed audio - the music's are IMA ADPCM - and SoundPool
     * would refuse an effect written that way.
     */
    @Test
    fun `every effect is plain pcm and decodes without a service provider`() {
        for (name in files) {
            val resource = javaClass.getResourceAsStream("/$name")
                ?: error("$name is missing from the classpath")

            resource.buffered().use { raw ->
                AudioSystem.getAudioInputStream(raw).use { stream ->
                    assertEquals(
                        AudioFormat.Encoding.PCM_SIGNED,
                        stream.format.encoding,
                        "$name's encoding"
                    )
                    assertTrue(
                        stream.readNBytes(DECODE_PROBE_BYTES).isNotEmpty(),
                        "$name produced no PCM"
                    )
                }
            }
        }
    }

    private companion object {
        const val DECODE_PROBE_BYTES = 8192
    }
}
