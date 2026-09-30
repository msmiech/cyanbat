# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the forest's music, as the five stems the game layers.

    uv run tools/generate_forest_music.py

The second stage goes somewhere older and less friendly than the cave: a jungle, humid and close,
with something watching from it. The model is the music of a flooded jungle city in an old action
RPG - wooden drums, a drone that is almost a voice, a bamboo flute calling out and bending into its
notes - but the notes are this script's own.

D minor at 98 BPM in 4/4, darkened by the flat second of the Phrygian mode: the sixth bar sits on
E-flat, a half step over the tonic, which is where the forest turns threatening. The eight-bar
progression turns back through a suspended dominant:

    Dm | Bb | C | Dm | Dm | Eb | C | Asus4 A

Its rhythms lean on 3+3+2, three dotted eighths and a quarter, which is what makes it sway rather
than march. The layers, bottom up:

* bed   - a drone shifting between vowels, a kalimba figure in 3+3+2, and a dark pad. Eight bars.
* pulse - a round bass, a two-toned log drum, shakers.
* drive - djembe, surdo and clave; toms tumbling into the turnaround.
* lead  - the bamboo flute: sixteen bars, a call and a higher answer, every long note bent up to.
* fury  - for the Moth Queen: war drums, low horns stabbing the tresillo, a choir. Eight bars.
"""

import numpy as np

from musicsynth import (
    AH, OO, Piece, bell, cymbal, dark, drone, echo, eq, held, highpass, hz, jingles, lowpass, mallet,
    membrane, notes, peak, pluck, reverb, reverb_ir, saturate, saw, shaker, vowel, woodblock,
    write_stems,
)

PIECE = Piece("forest", bpm=98, beats_per_bar=4, seed=20260930)

# One sixteenth, in beats.
STEP = 1 / 4

# Chord tones for the pad, low to high, and the bass line's root.
PROGRESSION = [
    ("Dm", notes("D3 F3 A3"), "D2"),
    ("Bb", notes("D3 F3 Bb3"), "Bb1"),
    ("C", notes("E3 G3 C4"), "C2"),
    ("Dm", notes("D3 F3 A3"), "D2"),
    ("Dm", notes("F3 A3 D4"), "D2"),
    ("Eb", notes("Eb3 G3 Bb3"), "Eb2"),
    ("C", notes("E3 G3 C4"), "C2"),
    ("A", notes("E3 A3 C#4"), "A1"),
]
A_SUS4 = notes("D3 E3 A3")

# The kalimba's figure: six plucks on 3+3+2, 3+3+2, cycling through the chord's upper tones.
KALIMBA_STEPS = [0, 3, 6, 8, 11, 14]
KALIMBA_TONES = [
    notes("D5 A4 F5 D5 A4 E5"),
    notes("D5 Bb4 F5 D5 Bb4 C5"),
    notes("C5 G4 E5 C5 G4 D5"),
    notes("D5 A4 F5 A5 F5 E5"),
    notes("D5 A4 F5 D5 A4 E5"),
    notes("Eb5 Bb4 G5 Eb5 Bb4 D5"),
    notes("C5 G4 E5 C5 G4 E5"),
    notes("D5 A4 E5 C#5 A4 E5"),
]

# The flute, bar by bar: (note, length in sixteenths), None for a rest. A trailing "~" bends the
# note up from a whole step below, the way a bamboo flute is shaded into a long tone.
TUNE = [
    [(None, 4), ("A4~", 4), ("D5", 6), ("C5", 2)],
    [("D5~", 10), (None, 2), ("F5", 4)],
    [("G5~", 6), ("F5", 2), ("E5", 4), ("C5", 4)],
    [("D5~", 12), (None, 4)],
    [(None, 2), ("F5", 2), ("A5~", 6), ("G5", 2), ("F5", 2), ("E5", 2)],
    [("G5~", 8), ("F5", 4), ("Eb5", 4)],
    [("D5", 4), ("C5~", 8), (None, 4)],
    [(None, 4), ("D5", 4), ("C#5~", 6), (None, 2)],
    [("D5", 2), ("F5", 2), ("A5~", 8), ("G5", 2), ("A5", 2)],
    [("Bb5~", 6), ("A5", 2), ("F5", 8)],
    [("G5", 4), ("C6~", 8), ("Bb5", 2), ("A5", 2)],
    [("A5~", 12), (None, 4)],
    [(None, 2), ("D6~", 6), ("C6", 4), ("A5", 4)],
    [("Bb5~", 8), ("G5", 4), ("Eb5", 4)],
    [("G5~", 6), ("F5", 2), ("E5", 4), ("C5", 4)],
    [("E5", 4), ("D5", 4), ("C#5~", 8)],
]


def jungle(rng) -> np.ndarray:
    """Close and damp: a middling tail with its treble soaked up, and few hard surfaces."""
    return reverb_ir(2.3, rng, predelay=0.018, brightness=0.3, early=4, early_spread=0.05)


def kalimba(midi, velocity, rng):
    return mallet(hz(midi), 1.6, rng, (1, 5.93, 13.4), (1, 0.4, 0.1), (1.1, 0.12, 0.04),
                  velocity=velocity, knock=0.15)


def render_bed():
    rng = PIECE.rng(1)
    loop = PIECE.loop(8)

    # The drone breathes between an "oo" and an "ah", a bar to each, as if something were
    # singing it with its mouth half closed.
    root = hz(38)  # D2
    closed = drone(root, loop.length, rng, vowel(root, OO), voices=3, detune_cents=6, spread=0.5)
    opened = drone(root, loop.length, rng, vowel(root, AH), voices=3, detune_cents=6, spread=0.5)
    fifth = drone(hz(45), loop.length, rng, dark(saw, hz(45), 600), voices=2, detune_cents=5, spread=0.8,
                  sway=0.5, sway_cycles=2)
    morph = 0.5 * (1 + np.sin(2 * np.pi * 4 * np.arange(loop.length) / loop.length))
    loop.audio += 0.9 * (closed * (1 - morph) + opened * morph) + 0.3 * fifth

    pad = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for midi in chord:
            f0 = hz(midi)
            pad.add(held(f0, PIECE.seconds(4) - 0.15, rng, dark(saw, f0, 1100), attack=0.6, decay=1.0,
                         sustain=0.85, release=1.0, voices=3, detune_cents=10, spread=0.7,
                         vibrato_hz=3.8, vibrato_cents=6, breath=0.02, breath_band=(300, 1500)),
                    bar * 4, gain=0.15)
    pad.audio = eq(pad.audio, highpass(140))
    loop.mix(pad)

    tines = PIECE.loop(8)
    for bar, tones in enumerate(KALIMBA_TONES):
        for step, midi in zip(KALIMBA_STEPS, tones):
            velocity = (0.9 if step in (0, 8) else 0.7) * (1 + 0.08 * rng.standard_normal())
            tines.add(kalimba(midi, velocity, rng), bar * 4 + step * STEP, pan=0.3 if step % 2 else -0.2,
                      nudge_seconds=0.004 * rng.standard_normal())
    tines.audio += echo(tines.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.3, 2000) * 0.3
    loop.mix(tines, 0.9)

    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.3
    return loop


def log_drum(pitch_hz, velocity, rng):
    """A slit drum: a hollow log with a tongue cut into each side, one higher than the other."""
    return membrane(pitch_hz, 0.22, rng, bend=1.02, modes=(1.0, 2.76), mode_levels=(1.0, 0.35),
                    thump=0.2, thump_hz=2500, velocity=velocity)


def render_pulse():
    rng = PIECE.rng(2)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0]
        # Root on the one, again on the and-of-two, and an octave up to push into the next bar.
        for step, interval, sixteenths, velocity in ((0, 0, 5, 1.0), (6, 0, 3, 0.75), (10, 0, 3, 0.8),
                                                     (14, 12, 2, 0.55)):
            f0 = hz(root + interval)
            bass.add(pluck(f0, PIECE.seconds(sixteenths * STEP) + 0.15, rng, decay=0.9, damping=0.6,
                           pick=0.25, tilt=1.5, brightness_hz=1800, attack_noise=0.03, velocity=velocity),
                     bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(36), peak(140, 3.0, 1.0), peak(650, 1.5, 1.0), lowpass(2800))
    loop.mix(bass, 0.65)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, pitch, velocity in ((0, 170, 1.0), (3, 230, 0.7), (6, 230, 0.75), (8, 170, 0.85),
                                      (10, 230, 0.6), (11, 230, 0.7), (14, 170, 0.65)):
            drums.add(log_drum(pitch, velocity, rng), base + step * STEP, pan=-0.25 if pitch > 200 else 0.1)
        for step in range(16):
            velocity = (0.5 if step % 4 == 0 else 0.35 if step % 2 == 0 else 0.25) * (1 + 0.1 * rng.standard_normal())
            drums.add(shaker(rng, seconds=0.04, velocity=velocity), base + step * STEP, pan=0.5,
                      nudge_seconds=0.003 * rng.standard_normal())
    loop.mix(drums, 0.5)

    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.2
    return loop


def djembe(stroke, velocity, rng):
    """The three strokes: a bass in the middle of the head, a tone at its edge, a slap."""
    if stroke == "B":
        return membrane(72, 0.28, rng, bend=1.3, thump=0.35, thump_hz=900, velocity=velocity)
    if stroke == "T":
        return membrane(300, 0.13, rng, bend=1.05, modes=(1.0, 1.59, 2.14), mode_levels=(1.0, 0.5, 0.25),
                        thump=0.35, thump_hz=3500, velocity=velocity)
    return membrane(410, 0.07, rng, bend=1.02, modes=(1.0, 1.59, 2.14, 2.3, 2.65),
                    mode_levels=(0.6, 0.5, 0.4, 0.3, 0.3), thump=1.0, thump_hz=7000, velocity=velocity)


DJEMBE = "B.TTS.TTB.TTS.ST"
CLAVE_STEPS = (0, 3, 6, 10, 12)


def render_drive():
    rng = PIECE.rng(3)
    loop = PIECE.loop(8)

    kit = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, stroke in enumerate(DJEMBE):
            if stroke != ".":
                velocity = (1.0 if stroke == "S" else 0.85 if stroke == "B" else 0.65) * (1 + 0.08 * rng.standard_normal())
                kit.add(djembe(stroke, velocity, rng), base + step * STEP, pan=-0.3,
                        nudge_seconds=0.003 * rng.standard_normal())
        for step, velocity in ((0, 1.0), (8, 0.85), (13, 0.5), (14, 0.7)):
            kit.add(membrane(58, 0.55, rng, bend=1.3, thump=0.4, thump_hz=1500, velocity=velocity),
                    base + step * STEP, pan=0.15)
        for step in CLAVE_STEPS:
            kit.add(woodblock(1750, rng, velocity=0.55), base + step * STEP, pan=0.55)
        for step in (4, 12):
            kit.add(jingles(rng, seconds=0.12, velocity=0.4, count=6), base + step * STEP, pan=0.6)
        if bar == 7:
            for i, step in enumerate(range(8, 16)):
                kit.add(membrane(220 - 14 * i, 0.25, rng, bend=1.35, velocity=0.6 + 0.05 * i),
                        base + step * STEP, pan=0.5 - 0.14 * i)
    kit.add(cymbal(rng, seconds=1.2, velocity=0.4), 0, pan=-0.2)
    kit.audio = saturate(kit.audio, 1.6)
    loop.mix(kit, 1.17)

    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.2
    return loop


def bamboo_flute(midi, seconds, rng, bend=False, glide_from=None, velocity=0.76):
    """A breathy end-blown flute: little more than a fundamental, a hollow third harmonic, and air."""
    f0 = hz(midi)
    shape = lambda k: (1.0, 0.08, 0.2, 0.03, 0.05)[k - 1] if k <= 5 else 0.0
    start = hz(midi - 2) if bend else glide_from
    return held(f0, seconds, rng, shape, count=5, attack=0.07, decay=0.4, sustain=0.8, release=0.18,
                vibrato_hz=4.6, vibrato_cents=22 if seconds > 0.6 else 8, vibrato_delay=0.45,
                glide_from=start, glide=0.22 if bend else 0.04, breath=0.16, breath_band=(700, 5000),
                velocity=velocity)


def render_lead():
    rng = PIECE.rng(4)
    loop = PIECE.loop(16)
    previous = None
    for bar, phrase in enumerate(TUNE):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                bend = name.endswith("~")
                midi = notes(name.rstrip("~"))[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.95
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 and not bend else None
                loop.add(bamboo_flute(midi, seconds, rng, bend=bend, glide_from=glide),
                         bar * 4 + at * STEP, pan=0.1, nudge_seconds=0.008 * rng.standard_normal())
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {bar + 1} of the tune is {at} sixteenths long"
    loop.audio = eq(loop.audio, highpass(260), peak(2200, 1.5, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(1.0), 0.35, 1800) * 0.25
    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.35
    return loop


def horn(midi, seconds, rng, velocity=1.0):
    f0 = hz(midi)
    body = held(f0, seconds, rng, dark(saw, f0, 1500), attack=0.03, decay=0.25, sustain=0.6, release=0.15,
                voices=3, detune_cents=8, spread=0.5)
    return saturate(body * velocity, 1.5)


def render_fury():
    rng = PIECE.rng(5)
    loop = PIECE.loop(8)

    horns = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 12
        for step, sixteenths, velocity in ((0, 3, 1.0), (3, 3, 0.8), (6, 2, 0.85), (8, 3, 0.95),
                                           (11, 3, 0.8), (14, 2, 0.9)):
            for interval in (0, 7, 12):
                horns.add(horn(root + interval, PIECE.seconds(sixteenths * STEP) * 0.8, rng, velocity),
                          bar * 4 + step * STEP, pan={0: 0.0, 7: -0.4, 12: 0.4}[interval])
    horns.audio = eq(horns.audio, highpass(80), peak(1100, 2.5, 1.0), lowpass(4500))
    loop.mix(horns, 0.3)

    choir = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        chord = A_SUS4 if bar == 7 else chord
        for midi in chord:
            f0 = hz(midi + 12)
            choir.add(held(f0, PIECE.seconds(4) - 0.1, rng, vowel(f0, OO if bar % 2 else AH), attack=0.3,
                           decay=0.8, sustain=0.9, release=0.5, voices=4, detune_cents=14, spread=0.8,
                           vibrato_hz=5.1, vibrato_cents=14, vibrato_delay=0.2), bar * 4, gain=0.18)
    loop.mix(choir)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, velocity in ((0, 1.0), (3, 0.6), (6, 0.8), (8, 0.95), (10, 0.5), (11, 0.7), (14, 0.85),
                               (15, 0.6)):
            drums.add(membrane(72, 0.75, rng, bend=1.5, bend_seconds=0.035, thump=0.45, thump_hz=1500,
                               velocity=velocity), base + step * STEP, pan=-0.2)
    drums.add(bell(88, rng, seconds=3.5, ratios=(1.0, 1.52, 2.13, 2.71, 3.3, 4.1), velocity=0.8), 0)
    drums.audio = eq(saturate(drums.audio, 1.5), highpass(45))
    loop.mix(drums, 0.3)

    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.25
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
