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

    @Test
    fun `audio assets are on the classpath`() {
        for (name in audioAssets) {
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

    private companion object {
        const val DECODE_PROBE_BYTES = 8192
    }
}
