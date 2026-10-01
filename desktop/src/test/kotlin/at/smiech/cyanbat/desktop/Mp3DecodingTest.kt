package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.resource.SoundEffect
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards desktop audio's one fragile dependency.
 *
 * The JDK ships decoders for WAV, AIFF and AU only. The death sound is MP3, which plays on desktop
 * solely because the mp3spi/jlayer service providers are on the classpath and register themselves
 * with `AudioSystem`. That registration is invisible - nothing references those libraries in code
 * - so dropping the dependency, or a shading or module change that stops the SPI being discovered,
 * would break audio silently at runtime rather than at compile time. The music is not MP3; see
 * `MusicStemTest`.
 *
 * These tests fail loudly instead.
 */
class Mp3DecodingTest {

    private val audioAssets = SoundEffect.entries.map { it.file }.filter { it.endsWith(".mp3") }

    /**
     * Every other effect is a WAV the JDK can decode on its own, and WAV precisely so that it can
     * be: Android's `AssetManager.openFd` needs an uncompressed asset to hand SoundPool a file
     * descriptor, and AGP leaves `.wav` uncompressed while it would repack an MP3. They still have
     * to be on the classpath and still have to decode, so they are checked alongside the rest -
     * just without the service provider being the thing under test.
     */
    private val pcmAssets = SoundEffect.entries.map { it.file }.filter { it.endsWith(".wav") }

    @Test
    fun `audio assets are on the classpath`() {
        for (name in audioAssets + pcmAssets) {
            assertNotNull(
                javaClass.getResourceAsStream("/$name"),
                "$name is missing - check the assets/ resources srcDir in build.gradle.kts"
            )
        }
    }

    @Test
    fun `every mp3 asset decodes to pcm`() {
        for (name in audioAssets) {
            val resource = javaClass.getResourceAsStream("/$name")
                ?: error("$name is missing from the classpath")

            resource.buffered().use { raw ->
                // Without an MP3 service provider this call throws UnsupportedAudioFileException.
                AudioSystem.getAudioInputStream(raw).use { encoded ->
                    val target = AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        encoded.format.sampleRate,
                        16,
                        encoded.format.channels,
                        encoded.format.channels * 2,
                        encoded.format.sampleRate,
                        false
                    )
                    AudioSystem.getAudioInputStream(target, encoded).use { pcm ->
                        // Read a slice rather than the whole file: a short read proves the codec ran.
                        val decoded = pcm.readNBytes(DECODE_PROBE_BYTES)
                        assertTrue(
                            decoded.isNotEmpty(),
                            "$name produced no PCM - the MP3 service provider is not decoding"
                        )
                    }
                }
            }
        }
    }

    /**
     * As plain PCM, too: a WAV can hold compressed audio - the music's are IMA ADPCM - and SoundPool
     * would refuse an effect written that way.
     */
    @Test
    fun `every wav effect is plain pcm and decodes without a service provider`() {
        for (name in pcmAssets) {
            val resource = javaClass.getResourceAsStream("/$name")
                ?: error("$name is missing from the classpath")

            resource.buffered().use { raw ->
                AudioSystem.getAudioInputStream(raw).use { stream ->
                    assertEquals(AudioFormat.Encoding.PCM_SIGNED, stream.format.encoding, "$name's encoding")
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
