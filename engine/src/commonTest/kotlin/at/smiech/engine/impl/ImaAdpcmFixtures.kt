package at.smiech.engine.impl

/**
 * Encodes [samples], interleaved [channels] wide, as an IMA ADPCM WAV in the layout
 * [ImaAdpcmClip] reads - the same thing `tools/musicsynth.py` writes, so the tests can make clips
 * of whatever shape they need.
 */
internal fun imaAdpcmWav(
    samples: ShortArray,
    channels: Int,
    sampleRate: Int,
    blockAlign: Int = 256 * channels,
): ByteArray {
    val frames = samples.size / channels
    val framesPerBlock = (blockAlign - 4 * channels) * 8 / (4 * channels) + 1
    val blocks = (frames + framesPerBlock - 1) / framesPerBlock
    val data = ByteArray(blocks * blockAlign)
    val stepIndex = IntArray(channels)

    fun frame(index: Int, ch: Int): Int =
        if (index < frames) samples[index * channels + ch].toInt() else 0

    for (block in 0 until blocks) {
        val first = block * framesPerBlock
        var p = block * blockAlign
        val predictor = IntArray(channels)
        for (ch in 0 until channels) {
            predictor[ch] = frame(first, ch)
            data[p] = predictor[ch].toByte()
            data[p + 1] = (predictor[ch] shr 8).toByte()
            data[p + 2] = stepIndex[ch].toByte()
            data[p + 3] = 0
            p += 4
        }
        var offset = 1
        while (offset < framesPerBlock) {
            for (ch in 0 until channels) {
                for (b in 0 until 4) {
                    var byte = 0
                    for (half in 0 until 2) {
                        val target = frame(first + offset + b * 2 + half, ch)
                        val nibble = encodeNibble(target, predictor, stepIndex, ch)
                        byte = byte or (nibble shl (4 * half))
                    }
                    data[p++] = byte.toByte()
                }
            }
            offset += 8
        }
    }

    val out = ArrayList<Byte>()
    fun ascii(text: String) = text.forEach { out.add(it.code.toByte()) }
    fun int16(value: Int) {
        out.add(value.toByte()); out.add((value shr 8).toByte())
    }

    fun int32(value: Int) {
        int16(value and 0xFFFF); int16(value ushr 16)
    }

    ascii("RIFF"); int32(4 + (8 + 20) + (8 + 4) + (8 + data.size)); ascii("WAVE")
    ascii("fmt "); int32(20)
    int16(0x0011); int16(channels); int32(sampleRate)
    int32(sampleRate * blockAlign / framesPerBlock); int16(blockAlign); int16(4)
    int16(2); int16(framesPerBlock)
    ascii("fact"); int32(4); int32(frames)
    ascii("data"); int32(data.size)
    return out.toByteArray() + data
}

private fun encodeNibble(target: Int, predictor: IntArray, stepIndex: IntArray, ch: Int): Int {
    val step = STEPS[stepIndex[ch]]
    var diff = target - predictor[ch]
    var nibble = 0
    if (diff < 0) {
        nibble = 8
        diff = -diff
    }
    var quantized = step shr 3
    var part = step
    var mask = 4
    repeat(3) {
        if (diff >= part) {
            nibble = nibble or mask
            diff -= part
            quantized += part
        }
        part = part shr 1
        mask = mask shr 1
    }
    val next = if (nibble and 8 != 0) predictor[ch] - quantized else predictor[ch] + quantized
    predictor[ch] = next.coerceIn(-32768, 32767)
    stepIndex[ch] = (stepIndex[ch] + INDEX_STEPS[nibble and 7]).coerceIn(0, STEPS.lastIndex)
    return nibble
}

private val INDEX_STEPS = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8)

private val STEPS = intArrayOf(
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55,
    60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
    337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411,
    1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
    5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500,
    20350, 22385, 24623, 27086, 29794, 32767,
)

/** A mono clip of [frames] frames holding [value] throughout: IMA ADPCM carries a constant exactly. */
internal fun constantClip(value: Short, frames: Int, sampleRate: Int): ImaAdpcmClip =
    ImaAdpcmClip.parse(
        imaAdpcmWav(
            ShortArray(frames) { value },
            channels = 1,
            sampleRate = sampleRate
        )
    )
