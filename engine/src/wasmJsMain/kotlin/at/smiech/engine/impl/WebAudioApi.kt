package at.smiech.engine.impl

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import kotlin.js.Promise

/*
 * The few parts of the Web Audio API the game plays through, which kotlinx-browser does not
 * declare. Named as in the browser; see https://webaudio.github.io/web-audio-api/.
 */

internal external class AudioContext(contextOptions: JsAny = definedExternally) : JsAny {
    /** Seconds since the context started, on the audio clock, which stops while it is suspended. */
    val currentTime: Double
    val sampleRate: Float

    /** "suspended", "running" or "closed". */
    val state: String
    val destination: AudioNode
    fun createBuffer(numberOfChannels: Int, length: Int, sampleRate: Float): AudioBuffer
    fun createBufferSource(): AudioBufferSourceNode
    fun createGain(): GainNode
    fun decodeAudioData(audioData: ArrayBuffer): Promise<AudioBuffer>
    fun resume(): Promise<JsAny?>
    fun `suspend`(): Promise<JsAny?>
}

internal open external class AudioNode : JsAny {
    fun connect(destination: AudioNode): AudioNode
    fun disconnect()
}

internal external class AudioParam : JsAny {
    var value: Float
}

internal external class GainNode : AudioNode {
    val gain: AudioParam
}

internal external class AudioBuffer : JsAny {
    fun copyToChannel(source: Float32Array, channelNumber: Int)
}

internal external class AudioBufferSourceNode : AudioNode {
    var buffer: AudioBuffer?

    /** Starts playing at [when] on the context's clock, or at once if that has passed. */
    fun start(`when`: Double)
}

/** The options for an [AudioContext] running at [sampleRate]. */
@Suppress("UNUSED_PARAMETER")
internal fun audioContextOptions(sampleRate: Int): JsAny = js("({ sampleRate: sampleRate })")
