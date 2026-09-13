# /// script
# requires-python = ">=3.9"
# ///
"""Generate the sound the bat's gun makes.

    uv run tools/generate_shot_sound.py

Synthesised and kept as the script that made it, for the same reason the sprites and the aura
surge are: a sound nobody can regenerate is a sound nobody can adjust. It needs no third-party
package - a WAV is a header and some samples.

WAV rather than MP3, like `auraSurge.wav` and unlike the music: `javax.sound.sampled` decodes PCM
natively, and AGP leaves `.wav` uncompressed in the APK, which is what `AssetManager.openFd` needs
to hand SoundPool a file descriptor.

The hard constraint here is repetition. This plays about once a second at the base fire rate and
better than three times a second once Rapid Fire is stacked, which is a sound the player will hear
several thousand times in a run - so it is very short, quiet, and has no tail to speak of. Anything
with a ring on it turns into a drone at that rate.

A descending blip: a pitch sweep falling roughly two octaves in a tenth of a second, with a click
of noise on the front to give it an edge. Falling rather than rising, because a rising sweep reads
as something charging up and this is something leaving.
"""

import math
import pathlib
import random
import struct
import wave

SAMPLE_RATE = 22050
DURATION = 0.12

# Quieter than the aura surge, because where that one fires on a milestone this fires constantly.
# It sits under the music rather than over it, and is meant to confirm the trigger rather than
# announce it.
PEAK = 0.16

# The sweep. Two octaves down over the length of the blip, which is what makes it read as a shot
# going away rather than as a beep.
START_HZ = 880.0
END_HZ = 220.0

# A square-ish edge, which is what gives it the retro arcade character the rest of the art has.
# Blended rather than switched: a pure square at this pitch is harsh over headphones.
SQUARE_BLEND = 0.35

# The click on the front. Very short - it is the transient that makes the sound feel like an
# impact rather than a tone starting.
CLICK_SECONDS = 0.012
CLICK_LEVEL = 0.5

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "shotFire.wav"


def envelope(t: float) -> float:
    """
    Near-instant attack and a fast exponential decay to silence.

    The tail is forced to zero at the end rather than merely being small: a WAV that stops on a
    non-zero sample clicks when it is retriggered, and this sound is retriggered constantly.
    """
    attack = 0.004
    if t < attack:
        return t / attack
    decayed = math.exp(-(t - attack) * 34.0)
    # Linear fade over the last stretch, so the waveform actually reaches zero at the end.
    remaining = 1.0 - t / DURATION
    return decayed * min(1.0, remaining * 6.0)


def main() -> int:
    frames = int(SAMPLE_RATE * DURATION)
    rng = random.Random(20260913)

    phase = 0.0
    filtered = 0.0

    samples = bytearray()
    for frame in range(frames):
        t = frame / SAMPLE_RATE
        progress = t / DURATION

        # Exponential rather than linear, so the fall sounds even across the sweep instead of
        # dropping through the high end and then crawling through the low.
        hz = START_HZ * (END_HZ / START_HZ) ** progress
        phase += 2 * math.pi * hz / SAMPLE_RATE

        sine = math.sin(phase)
        square = 1.0 if sine >= 0.0 else -1.0
        value = (1.0 - SQUARE_BLEND) * sine + SQUARE_BLEND * square

        if t < CLICK_SECONDS:
            filtered += (rng.uniform(-1.0, 1.0) - filtered) * 0.7
            value += CLICK_LEVEL * filtered * (1.0 - t / CLICK_SECONDS)

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
