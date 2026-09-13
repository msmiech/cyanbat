# /// script
# requires-python = ">=3.9"
# ///
"""Generate the sound the aura makes when it steps up a tier.

    uv run tools/generate_aura_sound.py

Synthesized rather than sampled, and kept in the repository as the script that made it, for the
same reason the sprites are: a sound nobody can regenerate is a sound nobody can adjust. It needs
no third-party package - a WAV is a header and some samples.

WAV rather than MP3, unlike the rest of the audio here. Both backends read it without a codec:
`javax.sound.sampled` decodes PCM natively, and AGP leaves `.wav` uncompressed in the APK, which is
what `AssetManager.openFd` needs to hand SoundPool a file descriptor.

The brief was "subtle". It is a short swell with a shimmer over it and a little crackle at the
front - something that says the bat just got stronger without stepping on the level-up jingle or
the music, which are both playing at the same moment.
"""

import math
import pathlib
import random
import struct
import wave

SAMPLE_RATE = 22050
DURATION = 0.85

# Well under the death sound and the level theme. This fires on a beat that already has a banner
# and a power-up dialog on it, so it is meant to be felt more than heard.
PEAK = 0.22

# The swell: a low body that rises about a fifth over the length of the sound, which is the shape
# an increase in power reads as. Two detuned partials rather than one, so it beats slightly instead
# of sounding like a test tone.
BODY_HZ = 110.0
BODY_RISE = 1.5
BODY_DETUNE = 1.004

# The shimmer riding on top, an octave and a fifth above, fading in behind the body.
SHIMMER_HZ = 660.0
SHIMMER_LEVEL = 0.30

# The crackle at the front - filtered noise, gone in a fifth of a second. This is the electrical
# half of it, and the reason the sound belongs to sparks and bolts rather than to a level up.
CRACKLE_SECONDS = 0.2
CRACKLE_LEVEL = 0.45

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "auraSurge.wav"


def envelope(t: float) -> float:
    """Fast in, slow out. A slow attack would land the sound after the flare it belongs to."""
    attack = 0.04
    if t < attack:
        return t / attack
    return math.exp(-(t - attack) * 4.2)


def main() -> int:
    frames = int(SAMPLE_RATE * DURATION)
    rng = random.Random(20260912)

    body_phase = 0.0
    detuned_phase = 0.0
    shimmer_phase = 0.0
    # One-pole low pass, so the crackle is a hiss with some body rather than white noise.
    filtered = 0.0

    samples = bytearray()
    for frame in range(frames):
        t = frame / SAMPLE_RATE
        progress = t / DURATION

        body_hz = BODY_HZ * (1.0 + (BODY_RISE - 1.0) * progress)
        body_phase += 2 * math.pi * body_hz / SAMPLE_RATE
        detuned_phase += 2 * math.pi * body_hz * BODY_DETUNE / SAMPLE_RATE
        shimmer_phase += 2 * math.pi * SHIMMER_HZ * (1.0 + 0.6 * progress) / SAMPLE_RATE

        value = 0.5 * (math.sin(body_phase) + math.sin(detuned_phase))
        # Held back at the start so the shimmer arrives as the swell opens out, rather than both
        # landing together as one thicker tone.
        value += SHIMMER_LEVEL * math.sin(shimmer_phase) * progress

        if t < CRACKLE_SECONDS:
            filtered += (rng.uniform(-1.0, 1.0) - filtered) * 0.55
            value += CRACKLE_LEVEL * filtered * (1.0 - t / CRACKLE_SECONDS)

        value *= envelope(t) * PEAK
        samples += struct.pack("<h", int(max(-1.0, min(1.0, value)) * 32767))

    with wave.open(str(OUTPUT), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(bytes(samples))

    print(f"{OUTPUT} ({DURATION:g}s, {SAMPLE_RATE} Hz mono)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
