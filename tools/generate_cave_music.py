# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the cave's music, as the five stems the game layers.

    uv run tools/generate_cave_music.py

The first stage, and the calmest of the three: a campfire before the journey rather than a battle.
The model is the kind of music a camp at the edge of the wilds gets in an old action RPG - a
fingerpicked nylon guitar, rolling and a little melancholy, ringing on open strings in a cave that
gives it back - pushed a step toward adventure: a gallop under it once the fight picks up, and a
tune that climbs. The notes are this script's own; only the instrumentation and the mood are
borrowed.

E minor in 12/8, four dotted-quarter beats a bar at 63 of them a minute. The eight-bar progression
walks up from the tonic to a major IV - the Dorian lift, which is where the adventure is - and back
down to the dominant:

    Em | D/F# | G | A | C | G/B | Am | B7

The layers, bottom up:

* bed   - the guitar's arpeggios and a soft string pad, with the odd drip of water. Eight bars.
* pulse - a fingered bass walking the progression, a frame drum on beats one and three, a shaker.
* drive - a galloping strummed guitar, a marching kit, tom fills into the turnaround.
* lead  - the tune, on a warm flute: sixteen bars, a phrase and its answer, the answer climbing to
          the major third over the IV chord.
* fury  - for the boss: taiko, low brass driving the roots, a choir. Eight bars.
"""

import numpy as np

from musicsynth import (
    AH, OH, Piece, bell, cymbal, dark, eq, echo, held, highpass, hz, jingles, kick, lowpass, mallet,
    membrane, notes, peak, pluck, reverb, reverb_ir, saturate, saw, shaker, snare, vowel, write_stems,
)

PIECE = Piece("cave", bpm=63, beats_per_bar=4, seed=20260929)

# One eighth note, in beats: a beat is a dotted quarter.
EIGHTH = 1 / 3

# Chord, the guitar's voicing low to high, and the bass line's two notes in the bar.
PROGRESSION = [
    ("Em", notes("E2 B2 E3 G3 B3 E4"), notes("E1 B1")),
    ("D/F#", notes("F#2 A2 D3 A3 D4 F#4"), notes("F#1 A1")),
    ("G", notes("G2 B2 D3 G3 B3 D4"), notes("G1 D2")),
    ("A", notes("A2 E3 A3 C#4 E4 A4"), notes("A1 C#2")),
    ("C", notes("C3 G3 C4 E4 G4 C5"), notes("C2 G1")),
    ("G/B", notes("B2 G3 B3 D4 G4 B4"), notes("B1 D2")),
    ("Am", notes("A2 E3 A3 C4 E4 A4"), notes("A1 E2")),
    ("B7", notes("B2 F#3 A3 D#4 F#4 B4"), notes("B1 F#1")),
]

# The last bar starts suspended and resolves halfway, which is what pulls the loop back round.
B7_SUS4 = notes("B2 F#3 A3 E4 F#4 B4")

# The pad holds three of each chord's tones in the middle of the range.
PAD = [notes("G3 B3 E4"), notes("F#3 A3 D4"), notes("G3 B3 D4"), notes("A3 C#4 E4"),
       notes("G3 C4 E4"), notes("G3 B3 D4"), notes("A3 C4 E4"), notes("F#3 A3 D#4")]

# Which voice of the voicing each eighth of the bar plays, 0 being the bass: down on the bass at
# the top of the bar, rolling up and back, and the fifth taking the bass's place halfway.
ARPEGGIO = [0, 2, 3, 4, 3, 2, 1, 2, 3, 5, 4, 3]
ARPEGGIO_ACCENTS = [1.0, 0.62, 0.7, 0.82, 0.66, 0.6, 0.86, 0.62, 0.7, 0.84, 0.68, 0.62]

# The tune, bar by bar: (note, length in eighths); a rest is None. Sixteen bars over the eight-bar
# progression twice. The first half asks, rising from the tonic and leaning on the IV's bright
# third; the second answers, reaching the major third an octave up over the same chord.
TUNE = [
    [("E5", 3), ("F#5", 2), ("G5", 1), ("B5", 6)],
    [("A5", 2), ("G5", 1), ("F#5", 3), ("E5", 2), ("D5", 1), ("E5", 3)],
    [("D5", 3), ("G5", 2), ("A5", 1), ("B5", 3), ("A5", 2), ("G5", 1)],
    [("A5", 6), ("E5", 2), ("C#5", 1), ("E5", 3)],
    [("G5", 3), ("E5", 2), ("D5", 1), ("C5", 3), ("E5", 2), ("G5", 1)],
    [("B5", 3), ("A5", 2), ("G5", 1), ("D5", 3), ("G5", 2), ("F#5", 1)],
    [("E5", 3), ("C5", 2), ("D5", 1), ("E5", 3), ("A5", 2), ("G5", 1)],
    [("F#5", 3), ("E5", 2), ("F#5", 1), ("D#5", 4), ("B4", 2)],
    [("E5", 3), ("F#5", 2), ("G5", 1), ("B5", 3), ("C6", 2), ("B5", 1)],
    [("A5", 6), ("F#5", 2), ("E5", 1), ("D5", 3)],
    [("D5", 2), ("G5", 1), ("B5", 3), ("D6", 3), ("C6", 2), ("B5", 1)],
    [("C#6", 6), ("B5", 2), ("A5", 1), ("E5", 3)],
    [("C6", 3), ("B5", 2), ("A5", 1), ("G5", 3), ("E5", 2), ("G5", 1)],
    [("B5", 3), ("A5", 2), ("G5", 1), ("D5", 6)],
    [("E5", 2), ("A5", 1), ("C6", 3), ("B5", 2), ("A5", 1), ("E5", 3)],
    [("F#5", 3), ("E5", 2), ("D#5", 1), ("F#5", 3), ("D#5", 3)],
]


def cave_room(rng) -> np.ndarray:
    """A big, dark space with hard walls close by: long, damped, and full of early echoes."""
    return reverb_ir(3.6, rng, predelay=0.03, brightness=0.45, early=10, early_spread=0.09)


def guitar(midi, velocity, rng, seconds=2.8):
    f0 = hz(midi)
    return pluck(f0, seconds, rng, decay=1.9 * (196 / f0) ** 0.35, damping=0.42, pick=0.13, tilt=1.2,
                 brightness_hz=6000, attack_noise=0.05, velocity=velocity)


def guitar_body(audio):
    """The box the strings are over: a boom low down, a scooped middle, a little air on top."""
    return eq(audio, highpass(80), peak(110, 1.5, 1.2), peak(230, 0.5, 1.4), peak(480, -3.0, 1.0),
              peak(3000, 3.5, 1.2))


def voicing(bar, half):
    """The guitar's chord at [bar], [half] 0 or 1: the last bar resolves its suspension halfway."""
    _, chord, _ = PROGRESSION[bar % 8]
    return B7_SUS4 if bar % 8 == 7 and half == 0 else chord


def render_bed():
    rng = PIECE.rng(1)
    loop = PIECE.loop(8)

    picked = PIECE.loop(8)
    for bar in range(8):
        for eighth, voice in enumerate(ARPEGGIO):
            chord = voicing(bar, eighth // 6)
            velocity = ARPEGGIO_ACCENTS[eighth] * (1 + 0.08 * rng.standard_normal())
            beat = bar * 4 + eighth * EIGHTH
            picked.add(guitar(chord[min(voice, len(chord) - 1)], velocity, rng), beat,
                       pan=0.15, nudge_seconds=0.004 * rng.standard_normal())
    loop.mix(picked)
    loop.audio = guitar_body(loop.audio)

    pad = PIECE.loop(8)
    for bar, chord in enumerate(PAD):
        for midi in chord:
            f0 = hz(midi)
            pad.add(held(f0, PIECE.seconds(4) - 0.2, rng, dark(saw, f0, 1400), attack=0.9, decay=1.0,
                         sustain=0.85, release=1.3, voices=3, detune_cents=9, spread=0.7,
                         vibrato_hz=4.2, vibrato_cents=5), bar * 4, gain=0.05)
    pad.audio = eq(pad.audio, highpass(160))
    loop.mix(pad)

    # Water dripping somewhere in the dark: a few high, soft tines, with a long echo each.
    drips = PIECE.loop(8)
    for beat, name in ((5.67, "B5"), (14.33, "E6"), (19.0, "G5"), (27.67, "D6")):
        drip = mallet(hz(notes(name)[0]), 1.2, rng, (1, 2.76, 5.4), (1, 0.3, 0.12), (0.6, 0.15, 0.05),
                      velocity=0.5, knock=0.2)
        drips.add(drip, beat, pan=float(rng.uniform(-0.8, 0.8)))
    drips.audio += echo(drips.audio, PIECE.seconds(1.5), PIECE.seconds(1.0), 0.45, 2500) * 0.6
    loop.mix(drips, 0.5)

    loop.audio += reverb(loop.audio, cave_room(PIECE.rng(100))) * 0.32
    return loop


def render_pulse():
    rng = PIECE.rng(2)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    for bar in range(8):
        root, fifth = PROGRESSION[bar][2]
        following_root = PROGRESSION[(bar + 1) % 8][2][0]
        # Root for half the bar, then the second note, then a stepping-stone into the next bar.
        for beat, midi, seconds, velocity in (
            (0.0, root, PIECE.seconds(2), 1.0),
            (2.0, fifth, PIECE.seconds(1.3), 0.8),
            (3 + 2 * EIGHTH, following_root + (2 if following_root < root else -1), PIECE.seconds(0.3), 0.55),
        ):
            f0 = hz(midi)
            bass.add(pluck(f0, seconds + 0.4, rng, decay=1.1, damping=0.55, pick=0.22, tilt=1.35,
                           brightness_hz=2600, attack_noise=0.03, velocity=velocity),
                     bar * 4 + beat)
    bass.audio = eq(bass.audio, highpass(36), peak(160, 3.0, 1.0), peak(700, 2.0, 1.0), lowpass(3000))
    loop.mix(bass, 0.9)

    drums = PIECE.loop(8)
    for bar in range(8):
        for eighth, velocity in ((0, 1.0), (6, 0.85), (11, 0.35)):
            hit = membrane(105, 0.32, rng, bend=1.35, thump=0.35, thump_hz=1800, velocity=velocity)
            drums.add(hit, bar * 4 + eighth * EIGHTH, pan=-0.1)
        for eighth in range(12):
            velocity = (0.55 if eighth % 3 == 0 else 0.32) * (1 + 0.1 * rng.standard_normal())
            drums.add(shaker(rng, seconds=0.045, velocity=velocity), bar * 4 + eighth * EIGHTH,
                      pan=0.45, nudge_seconds=0.003 * rng.standard_normal())
    loop.mix(drums, 0.7)

    loop.audio += reverb(loop.audio, cave_room(PIECE.rng(100))) * 0.22
    return loop


def strum(loop, chord, beat, velocity, rng, *, down=True, seconds=0.9, pan=-0.35):
    """All the strings in turn, a few milliseconds apart: low to high down, high to low up."""
    order = chord if down else chord[::-1]
    for i, midi in enumerate(order):
        f0 = hz(midi)
        tone = pluck(f0, seconds, rng, decay=0.5 * (196 / f0) ** 0.3, damping=0.6, pick=0.12, tilt=1.2,
                     brightness_hz=5000, attack_noise=0.05, velocity=velocity * (0.85 + 0.15 * rng.random()))
        loop.add(tone, beat, pan=pan, nudge_seconds=0.011 * i)


def render_drive():
    rng = PIECE.rng(3)
    loop = PIECE.loop(8)

    # The gallop: each beat struck long and its last eighth short, down and then up.
    strums = PIECE.loop(8)
    for bar in range(8):
        for beat in range(4):
            chord = voicing(bar, beat // 2)
            strum(strums, chord, bar * 4 + beat, 0.75 if beat % 2 == 0 else 0.62, rng, down=True)
            strum(strums, chord[2:], bar * 4 + beat + 2 * EIGHTH, 0.42, rng, down=False, seconds=0.4)
    strums.audio = eq(strums.audio, highpass(140), peak(3000, 2.0, 1.0))
    loop.mix(strums, 0.8)

    kit = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for eighth, velocity in ((0, 1.0), (5, 0.45), (6, 0.85)):
            kit.add(kick(rng, low=50, high=130, seconds=0.28, velocity=velocity), base + eighth * EIGHTH)
        for beat in (1, 3):
            kit.add(snare(rng, brush=0.5, velocity=0.8), base + beat, pan=0.1)
        # Ghost notes before the backbeats, the drag a marching snare leans on.
        for eighth in (2, 8):
            kit.add(snare(rng, brush=0.8, seconds=0.08, velocity=0.25), base + eighth * EIGHTH, pan=0.1)
        for eighth in range(12):
            kit.add(jingles(rng, seconds=0.08, velocity=0.35 if eighth % 3 else 0.5),
                    base + eighth * EIGHTH, pan=-0.5, nudge_seconds=0.002 * rng.standard_normal())
        # Into the halfway point and into the turnaround, the toms roll down.
        if bar in (3, 7):
            for i, (eighth, tom) in enumerate(((9, 190), (10, 150), (11, 115))):
                kit.add(membrane(tom, 0.3, rng, bend=1.4, velocity=0.8 + 0.07 * i), base + eighth * EIGHTH,
                        pan=0.5 - 0.5 * i)
    kit.add(cymbal(rng, velocity=0.5), 0, pan=0.3)
    kit.audio = saturate(kit.audio, 1.5)
    loop.mix(kit, 1.1)

    loop.audio += reverb(loop.audio, cave_room(PIECE.rng(100))) * 0.2
    return loop


def flute(midi, seconds, rng, glide_from=None, velocity=0.9):
    """A warm, round flute - close to an ocarina - with a little air on each note."""
    f0 = hz(midi)
    shape = lambda k: (1.0, 0.24, 0.13, 0.06, 0.035, 0.02)[k - 1] if k <= 6 else 0.0
    return held(f0, seconds, rng, shape, count=6, attack=0.05, decay=0.3, sustain=0.85,
                release=0.12, vibrato_hz=5.2, vibrato_cents=14, vibrato_delay=0.35,
                glide_from=glide_from, glide=0.05, breath=0.06, breath_band=(900, 4500),
                velocity=velocity)


def render_lead():
    rng = PIECE.rng(4)
    loop = PIECE.loop(16)
    previous = None
    for bar, phrase in enumerate(TUNE):
        at = 0
        for name, eighths in phrase:
            if name is not None:
                midi = notes(name)[0]
                seconds = PIECE.seconds(eighths * EIGHTH) * 0.94
                # Slurred up from the note before when it is close, as a player would.
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 else None
                accent = 1.0 if at % 6 == 0 else 0.85
                loop.add(flute(midi, seconds, rng, glide_from=glide, velocity=0.9 * accent),
                         bar * 4 + at * EIGHTH, pan=-0.05, nudge_seconds=0.006 * rng.standard_normal())
                previous = midi
            at += eighths
        assert at == 12, f"bar {bar + 1} of the tune is {at} eighths long"
    loop.audio = eq(loop.audio, highpass(250), peak(1800, 1.5, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(1.0), PIECE.seconds(2 / 3), 0.25, 2200) * 0.18
    loop.audio += reverb(loop.audio, cave_room(PIECE.rng(100))) * 0.3
    return loop


def brass(midi, seconds, rng, velocity=1.0):
    """Low horns, darker as they start and opening up as the note swells."""
    f0 = hz(midi)
    bright = held(f0, seconds, rng, dark(saw, f0, 1800), attack=0.08, decay=0.4, sustain=0.8,
                  release=0.18, voices=3, detune_cents=7, spread=0.5, vibrato_hz=5, vibrato_cents=4)
    mellow = held(f0, seconds, rng, dark(saw, f0, 500), attack=0.02, decay=0.3, sustain=0.7,
                  release=0.18, voices=2, detune_cents=5, spread=0.3)
    swell = np.clip(np.arange(bright.shape[1]) / (0.25 * 44100), 0, 1)
    return saturate((mellow * (1 - swell) + bright * swell) * velocity, 1.4)


def render_fury():
    rng = PIECE.rng(5)
    loop = PIECE.loop(8)

    horns = PIECE.loop(8)
    for bar in range(8):
        root = PROGRESSION[bar][2][0] + 24
        for beat in range(4):
            # The same gallop as the strumming, as power fifths.
            for offset, length, velocity in ((0, 2 * EIGHTH, 1.0), (2 * EIGHTH, EIGHTH, 0.7)):
                for interval in (0, 7):
                    horns.add(brass(root + interval, PIECE.seconds(length) * 0.85, rng, velocity),
                              bar * 4 + beat + offset, pan=-0.3 if interval else 0.3)
    horns.audio = eq(horns.audio, highpass(90), peak(1200, 2.5, 1.0), lowpass(4500))
    loop.mix(horns, 0.5)

    choir = PIECE.loop(8)
    for bar, chord in enumerate(PAD):
        for i, midi in enumerate(chord):
            f0 = hz(midi)
            formants = AH if i != 1 else OH
            choir.add(held(f0, PIECE.seconds(4) - 0.1, rng, vowel(f0, formants), attack=0.35, decay=0.8,
                           sustain=0.9, release=0.6, voices=4, detune_cents=12, spread=0.8,
                           vibrato_hz=5.3, vibrato_cents=12, vibrato_delay=0.2), bar * 4, gain=0.3)
    loop.mix(choir)

    drums = PIECE.loop(8)
    for bar in range(8):
        for eighth, velocity in ((0, 1.0), (3, 0.55), (6, 0.9), (9, 0.55), (10, 0.45), (11, 0.6)):
            drums.add(membrane(78, 0.8, rng, bend=1.5, bend_seconds=0.04, thump=0.45, thump_hz=1500,
                               velocity=velocity), bar * 4 + eighth * EIGHTH, pan=-0.15)
    drums.add(bell(92, rng, seconds=4.0, ratios=(1.0, 1.52, 2.13, 2.71, 3.3, 4.1), velocity=0.8), 0)
    drums.audio = saturate(drums.audio, 1.5)
    loop.mix(drums, 0.9)

    loop.audio += reverb(loop.audio, cave_room(PIECE.rng(100))) * 0.28
    return loop


def main() -> int:
    write_stems(PIECE, {
        "bed": render_bed(),
        "pulse": render_pulse(),
        "drive": render_drive(),
        "lead": render_lead(),
        "fury": render_fury(),
    })
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
