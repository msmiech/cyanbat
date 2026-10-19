package at.smiech.engine.impl

import at.smiech.engine.Audio
import at.smiech.engine.LayeredMusic
import at.smiech.engine.Music
import at.smiech.engine.MusicGrid
import at.smiech.engine.Sound
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData

/**
 * iOS [Audio]: effects through AVAudioPlayer, which plays their WAVs as they are, and music decoded
 * by the shared [StemMixer] and played on the app's one engine
 * ([IosAudioDevice]); see [Audio.newMusic] and [Audio.newLayeredMusic].
 */
class IosAudio(
    private val device: IosAudioDevice,
    private val assets: IosAssets,
) : Audio {
    private val sounds = mutableListOf<Sound>()
    private val layered = mutableListOf<LayeredMusic>()

    override fun newMusic(filename: String): Music = TrackMusic.of(clip(filename), ::play)

    override fun newSound(filename: String): Sound =
        IosSound(assets.data(filename)).also { sounds.add(it) }

    override fun newLayeredMusic(stems: List<String>, grid: MusicGrid): LayeredMusic =
        play(StemMixer(stems.map(::clip), grid))

    override fun dispose() {
        sounds.forEach { it.dispose() }
        // Iterates a copy, because each instance removes itself from the list as it is disposed.
        layered.toList().forEach { it.dispose() }
        sounds.clear()
        layered.clear()
    }

    private fun clip(name: String): ImaAdpcmClip = ImaAdpcmClip.parse(assets.bytes(name))

    private fun play(mixer: StemMixer): LayeredMusic =
        IosLayeredMusic(mixer, device) { layered.remove(it) }.also { layered.add(it) }
}

/**
 * A short effect, decoded up front and replayed from the top, as the desktop's Clip is: a second
 * play of it while it sounds starts it over rather than stacking a copy. Silent if it cannot be
 * decoded.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosSound(data: NSData) : Sound {
    private val player: AVAudioPlayer = AVAudioPlayer(data = data, error = null)
        .also { it.prepareToPlay() }

    override fun play(volume: Float) {
        val p = player ?: return
        p.volume = volume.coerceIn(0f, 1f)
        p.currentTime = 0.0
        p.play()
    }

    override fun dispose() {
        player?.stop()
    }
}
