package at.smiech.cyanbat.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards desktop audio's one fragile dependency.
 *
 * The JDK ships decoders for WAV, AIFF and AU only. Every audio asset in this game is MP3, which
 * plays on desktop solely because the mp3spi/jlayer service providers are on the classpath and
 * register themselves with `AudioSystem`. That registration is invisible - nothing references
 * those libraries in code - so dropping the dependency, or a shading or module change that stops
 * the SPI being discovered, would break audio silently at runtime rather than at compile time.
 *
 * These tests fail loudly instead.
 */
class Mp3DecodingTest {

    private val audioAssets = listOf(
        "deathSound.mp3",
        "game_over.mp3",
        "game_theme.mp3",
        "menu_theme.mp3",
    )

    /**
     * The aura surge is the one asset here the JDK can decode on its own, and it is WAV precisely
     * so that it can be: Android's `AssetManager.openFd` needs an uncompressed asset to hand
     * SoundPool a file descriptor, and AGP leaves `.wav` uncompressed while it would repack an
     * MP3. It still has to be on the classpath and still has to decode, so it is checked alongside
     * the rest - just without the service provider being the thing under test.
     */
    private val pcmAssets = listOf("auraSurge.wav")

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
                        // Read a slice rather than the whole track: game_theme.mp3 alone is 2.5 MB
                        // encoded and far larger decoded, and a short read proves the codec ran.
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

    @Test
    fun `the aura surge decodes without a service provider`() {
        for (name in pcmAssets) {
            val resource = javaClass.getResourceAsStream("/$name")
                ?: error("$name is missing from the classpath")

            resource.buffered().use { raw ->
                AudioSystem.getAudioInputStream(raw).use { stream ->
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
