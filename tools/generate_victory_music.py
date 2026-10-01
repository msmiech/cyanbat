# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the victory's music: a fanfare the game plays once, as a stage's boss goes up in flames.

    uv run tools/generate_victory_music.py

The game over's lament turned inside out: brass instead of a pipe, a major key instead of the
Phrygian, and the beat landing with the tune instead of dropping out under it. A section of synth
brass calls out over timpani and a snare, a trap beat's 808s and hi-hats push it on, and the last
chord lands with a choir and a glockenspiel on top of it, all played by a 16-bit console.

C major at 140 BPM in 4/4. A bugle-call pickup leads into a phrase that climbs the tonic chord, then
the fanfare cadences the way so many video game victories do, stepping up from the flat sixth
through the flat seventh to the tonic:

    (pickup) | C . . . | Ab Bb | C . . .

The pickup is the first beat, so the track starts on its first note. The last chord lands at
[LANDING_BEAT] - three seconds in - which is the moment the game puts its stage complete overlay up
(STAGE_COMPLETE_DELAY_SECONDS): the music says the stage is won as the screen does. It then rings
out, and the track is rendered with room past its end and cut at [END_BEAT] once it has died away.
"""

import numpy as np

from musicsynth import (
    AH, RENDER_RATE, Piece, clap, cymbal, dark, eight08, eq, held, highpass, hz, lowpass, mallet,
    membrane, notes, peak, reverb, reverb_ir, saw, snare, swell, trap_hat, vowel, write_track,
)

PIECE = Piece("victory", bpm=140, beats_per_bar=4, seed=20261001)

# Four bars of music and two of room, so that nothing rings past the end of the loop it is rendered
# in and round onto its first beat.
BARS = 6

# Where the last chord lands: the beat the game's overlay is timed to.
LANDING_BEAT = 7

# Where the track is cut: once the last chord has rung out.
END_BEAT = 16

# The lead trumpet: (beat, note, beats held). The pickup's three triplet eighths into the downbeat,
# a climb up the tonic chord, then the cadence and the long top C. The cadence's two chords are
# stabbed short, so the gaps after them are what the last chord lands into.
LEAD = [
    (0, "G4", 1 / 3), (1 / 3, "G4", 1 / 3), (2 / 3, "G4", 1 / 3),
    (1, "C5", 1.5), (2.5, "G4", 0.5), (3, "C5", 0.5), (3.5, "E5", 0.5), (4, "G5", 1),
    (5, "Ab5", 0.7), (6, "Bb5", 0.7),
    (LANDING_BEAT, "C6", 4),
]

# The horns under it: (beat, chord, beats held).
HORNS = [
    (1, "C4 E4 G4", 3.8),
    (5, "Ab3 C4 Eb4", 0.65),
    (6, "Bb3 D4 F4", 0.65),
    (LANDING_BEAT, "C4 E4 G4 C5", 4),
]

# The bass, with the chords: (beat, root, beats held). Short under the first bar and the cadence,
# so the long one under the last chord is the first time the 808 holds.
ROOTS = [(1, "C2", 2), (5, "Ab1", 0.6), (6, "Bb1", 0.6), (LANDING_BEAT, "C2", 4)]

# The timpani's strokes: (beat, note, velocity).
TIMPANI = [
    (1, "C2", 1.0), (2.5, "G2", 0.55), (3, "C2", 0.7), (4, "G2", 0.8), (4.5, "G2", 0.6),
    (5, "Ab2", 0.95), (6, "Bb2", 0.95), (LANDING_BEAT, "C2", 1.0),
]


def hall(rng) -> np.ndarray:
    """A big bright hall, for a moment worth shouting about."""
    return reverb_ir(2.2, rng, predelay=0.03, brightness=0.6, early=8, early_spread=0.08)


def brass(midi, seconds, rng, *, cutoff, voices, velocity=1.0, vibrato=True):
    """Synth brass: a sawtooth darkened to [cutoff], with a brighter blat on the front of every
    note - the filter opening as the player's lips catch - and a scoop up into the pitch. Stereo."""
    f0 = hz(midi)
    body = held(f0, seconds, rng, dark(saw, f0, cutoff), attack=0.025, decay=0.3, sustain=0.75,
                release=0.14, vibrato_hz=5.6, vibrato_cents=14 if vibrato and seconds > 0.6 else 0,
                vibrato_delay=0.3, glide_from=hz(midi - 0.6), glide=0.045, voices=voices, detune_cents=6,
                spread=0.3, velocity=velocity)
    blat = held(f0, min(seconds, 0.09), rng, dark(saw, f0, cutoff * 2.4), attack=0.006, decay=0.04,
                sustain=0.1, release=0.06, voices=voices, detune_cents=6, spread=0.3, velocity=velocity * 0.6)
    body[:, :blat.shape[1]] += blat
    return body


def timpani(midi, rng, velocity):
    """A kettledrum: a tuned head, settling onto its note, ringing for a second or so. Mono."""
    return membrane(hz(midi), 0.9, rng, bend=1.12, bend_seconds=0.04, modes=(1.0, 1.5, 1.98, 2.44),
                    mode_levels=(1.0, 0.5, 0.28, 0.14), thump=0.3, thump_hz=1500, velocity=velocity)


def glockenspiel(midi, rng, velocity=1.0):
    return mallet(hz(midi), 1.6, rng, ratios=(1.0, 2.76, 5.4), levels=(1.0, 0.28, 0.1),
                  decays=(0.8, 0.25, 0.1), velocity=velocity, knock=0.12)


def boom(midi, seconds, rng, **shape):
    """The 808s: driven, with the buzz that carries them through a phone's speaker."""
    return eight08(hz(midi), seconds, rng, drive=3.0, presence=0.8, **shape)


# --- Parts ---------------------------------------------------------------------------------------


def render_lead(rng):
    loop = PIECE.loop(BARS)
    for beat, name, beats in LEAD:
        seconds = PIECE.seconds(beats) * (0.8 if beats < 0.5 else 0.94)
        loop.add(brass(notes(name)[0], seconds, rng, cutoff=3200, voices=2), beat, pan=0.1,
                 nudge_seconds=0.003 * rng.standard_normal())
    loop.audio = eq(loop.audio, highpass(180), peak(1600, 2.5, 1.0))
    return loop


def render_horns(rng):
    loop = PIECE.loop(BARS)
    for beat, chord, beats in HORNS:
        for i, midi in enumerate(notes(chord)):
            loop.add(brass(midi, PIECE.seconds(beats), rng, cutoff=1300, voices=3, velocity=0.8, vibrato=False),
                     beat, pan=-0.35 + 0.2 * i, nudge_seconds=0.004 * rng.standard_normal())
    loop.audio = eq(loop.audio, highpass(110), lowpass(4000))
    return loop


def render_choir(rng):
    loop = PIECE.loop(BARS)
    for midi in notes("C3 G3 C4 E4 G4"):
        f0 = hz(midi)
        voice = held(f0, PIECE.seconds(4), rng, vowel(f0, AH), attack=0.12, decay=0.8, sustain=0.85,
                     release=1.2, voices=4, detune_cents=10, spread=0.8, vibrato_hz=5.0, vibrato_cents=12,
                     vibrato_delay=0.3)
        loop.add(voice, LANDING_BEAT, gain=0.2)
    loop.audio = eq(loop.audio, highpass(120), peak(800, 1.5, 1.0), lowpass(5000))
    return loop


def render_bells(rng):
    loop = PIECE.loop(BARS)
    # A sparkle thrown up over the last chord, and an answer coming back down.
    for i, name in enumerate(("C6", "E6", "G6", "C7", "G6", "E6", "G6", "C7")):
        loop.add(glockenspiel(notes(name)[0], rng, velocity=0.9 if i in (0, 3, 7) else 0.65),
                 LANDING_BEAT + i * 0.25, pan=0.4)
    loop.audio = eq(loop.audio, highpass(900))
    return loop


def render_timpani(rng):
    loop = PIECE.loop(BARS)
    for beat, name, velocity in TIMPANI:
        loop.add(timpani(notes(name)[0], rng, velocity), beat, pan=-0.2)
    # A roll under the last chord, swelling and then dying with it.
    for i in range(24):
        along = i / 23
        level = 0.25 + 0.35 * np.sin(np.pi * along)
        loop.add(timpani(notes("C2")[0], rng, level), LANDING_BEAT + 0.5 + i / 8, pan=-0.2,
                 nudge_seconds=0.004 * rng.standard_normal())
    loop.audio = eq(loop.audio, highpass(45), lowpass(5000))
    return loop


def render_kit(rng):
    loop = PIECE.loop(BARS)
    # The pickup: a snare roll swelling into the downbeat, with a cymbal growing into it backwards.
    for i in range(8):
        loop.add(snare(rng, velocity=0.25 + 0.5 * i / 7), i / 8, pan=0.1)
    # Only its last beat: the track starts on the pickup, and anything earlier would wrap round to
    # the end of the loop.
    beat = PIECE.samples_per_beat
    rising = swell(cymbal(rng, seconds=0.6, velocity=0.5))[-beat:] * np.linspace(0, 1, beat) ** 2
    loop.add(rising, 0, pan=-0.3)

    loop.add(cymbal(rng, seconds=1.8, velocity=0.75), 1, pan=-0.3)
    loop.add(snare(rng, velocity=0.85), 2, pan=0.1)
    loop.add(snare(rng, velocity=0.9), 4, pan=0.1)
    loop.add(clap(rng, velocity=0.6), 4, pan=-0.1)
    for eighth in range(8):
        loop.add(trap_hat(rng, velocity=0.4 if eighth % 2 == 0 else 0.28), 1 + eighth * 0.5, pan=0.35)
    # Into the cadence the hats roll in thirty-seconds and climb, as a trap beat throws itself into
    # a drop.
    for i in range(16):
        along = i / 15
        loop.add(trap_hat(rng, pitch=1 + 0.25 * along, velocity=0.3 + 0.4 * along), 5 + i / 8, pan=0.35)
    loop.add(snare(rng, velocity=0.8), 5, pan=0.1)
    loop.add(snare(rng, velocity=0.9), 6, pan=0.1)

    loop.add(cymbal(rng, seconds=2.6, velocity=1.0), LANDING_BEAT, pan=-0.3)
    loop.add(cymbal(rng, seconds=2.2, velocity=0.6), LANDING_BEAT, pan=0.4)
    loop.add(clap(rng, velocity=0.8), LANDING_BEAT, pan=0.0)
    loop.audio = eq(loop.audio, highpass(70))
    return loop


def render_808s(rng):
    loop = PIECE.loop(BARS)
    for beat, name, beats in ROOTS:
        long = beats >= 4
        loop.add(boom(notes(name)[0], PIECE.seconds(beats) - 0.02, rng, decay=1.6 if long else 0.9, punch=10),
                 beat)
    # An octave's pop on the third beat, to keep the first bar moving.
    loop.add(boom(notes("C3")[0], PIECE.seconds(0.5), rng, decay=0.4, punch=7, velocity=0.6), 3)
    loop.audio = eq(loop.audio, highpass(30), lowpass(3000))
    return loop


def render():
    loop = PIECE.loop(BARS)
    for part, gain in ((render_lead(PIECE.rng(1)), 0.5), (render_horns(PIECE.rng(2)), 0.3),
                       (render_choir(PIECE.rng(3)), 1.0), (render_bells(PIECE.rng(4)), 0.22)):
        loop.mix(part, gain)
    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.35

    dry = PIECE.loop(BARS)
    for part, gain in ((render_timpani(PIECE.rng(5)), 0.5), (render_kit(PIECE.rng(6)), 0.4),
                       (render_808s(PIECE.rng(7)), 0.34)):
        dry.mix(part, gain)
    dry.audio += reverb(dry.audio, hall(PIECE.rng(100))) * 0.18
    loop.audio += dry.audio
    return loop


def main() -> int:
    write_track(PIECE, render(), loudness_db=-15.0, end_beat=END_BEAT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
