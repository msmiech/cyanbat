# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the Naga's music: the lagoon's boss fight, as the eight stems the game layers.

    uv run tools/generate_naga_music.py

The one boss in the game fought to a piece of its own rather than to its stage's: when the Naga
rises, the lagoon's music stops dead and this takes over, opening on its bed alone for a bar and
slamming in whole on the downbeat - and every one of the Naga's five phases drops back to the bed
and slams in again. So every layer here is up for the whole fight; they are stems so the drops can
be made, not so a streak can build them.

It is the lagoon's unease turned to panic: faster, lower and louder, a bass figure hammering the home
note in eighths with the leading tone kicking under it, tribal toms in a torrent of sixteenths, a reed
shrieking runs up the mode, voices howling and ululating, and brass and a choir over all of it. The
notes are this script's own.

B-flat harmonic minor - B-flat, C, D-flat, E-flat, F, G-flat, A - whose A, a half step under the
home note, is what the bass keeps falling to and climbing back from. 150 BPM in 4/4. The harmony
moves over a bass that never leaves its home:

    Bbm | Bbm | Gb | F7 | Bbm | Ebm | Gb | F7

The layers, bottom up:

* bed   - low strings trembling on the home note and its fifth, a gong, a high shimmer of struck
          metal, and a low swell of brass. It is the whole of the music for a bar at a time, at the
          boss's arrival and at each of its phases, so it is built to hold the tension on its own.
* pulse - the bass figure in eighths over a sub, and a shaker.
* drive - toms in sixteenths, a kick on every beat, claps and a snare on the backbeat, a roll into
          the top.
* lead  - the reed, sixteen bars of it, doubled an octave down by a buzzing synth.
* boom  - 808s on the chords' roots, sliding.
* roll  - hats in sixteenths, rolling in triplets and thirty-seconds. Four bars.
* chop  - voices howling - a long cry falling off its note - and ululating on the half step.
* fury  - brass stabbing the chords, a choir, and the biggest drums.
"""

import numpy as np

from musicsynth import (
    AH, OH, OO, Piece, bell, chop, clap, cymbal, dark, echo, eight08, eq, held, highpass, hz, jingles,
    kick, lowpass, membrane, notes, peak, reverb, reverb_ir, saturate, saw, shaker, snare, square,
    swell, trap_hat, vowel, write_stems,
)

PIECE = Piece("naga", bpm=150, beats_per_bar=4, seed=20261006)

STEP = 1 / 4

# The chord low to high, and the root the 808s follow; the bass figure stays on B-flat throughout.
PROGRESSION = [
    ("Bbm", notes("Bb3 Db4 F4"), "Bb1"),
    ("Bbm", notes("Bb3 Db4 F4"), "Bb1"),
    ("Gb", notes("Bb3 Db4 Gb4"), "Gb1"),
    ("F7", notes("A3 C4 Eb4 F4"), "F1"),
    ("Bbm", notes("Bb3 Db4 F4"), "Bb1"),
    ("Ebm", notes("Bb3 Eb4 Gb4"), "Eb2"),
    ("Gb", notes("Bb3 Db4 Gb4"), "Gb1"),
    ("F7", notes("A3 C4 Eb4 F4"), "F1"),
]

# The bass figure, in eighths, as semitones off B-flat: the home note hammered, its fifth, and the
# leading tone kicking under it - and in the last eighths of every other bar a climb back up.
FIGURE = (0, 0, 7, 0, -1, 0, 7, -1)
CLIMB = (0, 0, 7, 0, -1, 0, -4, -1)

TUNE = [
    [("F5", 2), ("Gb5", 2), ("F5", 2), ("Db5", 2), ("Bb4", 4), ("C5", 2), ("Db5", 2)],
    [("Eb5", 2), ("Db5", 2), ("C5", 2), ("A4", 2), ("Bb4~", 8)],
    [("Gb5", 4), ("F5", 2), ("Eb5", 2), ("Db5", 4), ("Bb4", 4)],
    [("A4", 2), ("C5", 2), ("Eb5", 2), ("Gb5", 2), ("F5~", 8)],
    [("Bb5", 2), ("A5", 2), ("Bb5", 2), ("F5", 2), ("Db5", 2), ("F5", 2), ("Bb4", 4)],
    [("Gb5", 2), ("F5", 2), ("Eb5", 2), ("Db5", 2), ("Eb5~", 8)],
    [("Db6", 4), ("Bb5", 2), ("Gb5", 2), ("Ab5", 2), ("Gb5", 2), ("F5", 4)],
    [("Eb5", 2), ("Gb5", 2), ("F5", 2), ("Eb5", 2), ("Db5", 2), ("C5", 2), ("A4", 4)],
    [(None, 2), ("Bb5", 2), ("Db6", 2), ("Bb5", 2), ("F5~", 8)],
    [("Gb5", 2), ("F5", 2), ("Db5", 2), ("C5", 2), ("Db5", 4), ("Bb4", 4)],
    [("Bb5~", 6), ("Ab5", 2), ("Gb5", 4), ("Db5", 4)],
    [("C6", 4), ("A5", 2), ("F5", 2), ("Eb5~", 8)],
    [("F5", 2), ("Bb5", 2), ("Db6", 2), ("F6", 2), ("Eb6", 4), ("Db6", 4)],
    [("C6", 2), ("Bb5", 2), ("Gb5", 2), ("Eb5", 2), ("Gb5~", 8)],
    [("F5", 2), ("Gb5", 2), ("A5", 2), ("Bb5", 2), ("C6", 2), ("Db6", 2), ("C6", 4)],
    [("A5", 4), ("Gb5", 2), ("Eb5", 2), ("F5~", 8)],
]

LOW_TOM = {0: 1.0, 2: 0.6, 3: 0.8, 6: 0.9, 8: 1.0, 10: 0.6, 11: 0.8, 14: 0.9}
HIGH_TOM = {1: 0.45, 5: 0.5, 7: 0.55, 9: 0.45, 13: 0.5, 15: 0.6}


def court(rng) -> np.ndarray:
    """The temple's flooded court: a long, dark tail off stone and water."""
    return reverb_ir(2.6, rng, predelay=0.025, brightness=0.45, early=6, early_spread=0.07)


def gong(rng, f0=58.3, seconds=4.0, velocity=1.0):
    return bell(f0, rng, seconds=seconds, ratios=(1.0, 1.52, 2.13, 2.71, 3.3, 4.1), velocity=velocity)


def tom(f0, velocity, rng, seconds=0.32):
    return membrane(f0, seconds, rng, bend=1.4, bend_seconds=0.022, thump=0.4, thump_hz=1800, velocity=velocity)


def slap(velocity, rng):
    return membrane(430, 0.065, rng, bend=1.05, modes=(1.0, 1.7, 2.45), mode_levels=(1.0, 0.55, 0.3),
                    thump=0.9, thump_hz=7000, velocity=velocity)


REED = ((700, 120, 1.0), (1350, 150, 0.85), (2650, 220, 0.4))


def reed(midi, seconds, rng, slide=False, glide_from=None, velocity=0.8):
    """The reed, played hard: a nasal buzz, a fast, wide vibrato, bending up into its notes."""
    f0 = hz(midi)
    start = hz(midi - 1) if slide else glide_from
    return held(f0, seconds, rng, vowel(f0, REED), attack=0.02, decay=0.2, sustain=0.9, release=0.08,
                vibrato_hz=6.4, vibrato_cents=32 if seconds > 0.3 else 6, vibrato_delay=0.12,
                glide_from=start, glide=0.08 if slide else 0.03, breath=0.14, breath_band=(900, 5000),
                velocity=velocity)


def strings(midi, seconds, rng, velocity=1.0):
    """Low strings bowed in a tremolo: a saw ensemble, trembling."""
    f0 = hz(midi)
    return held(f0, seconds, rng, dark(saw, f0, 1600), attack=0.08, decay=0.3, sustain=0.85, release=0.2,
                voices=3, detune_cents=10, spread=0.6, vibrato_hz=11.0, vibrato_cents=10, vibrato_delay=0.0,
                velocity=velocity)


def render_bed():
    rng = PIECE.rng(1)
    loop = PIECE.loop(8)

    low = PIECE.loop(8)
    for bar in range(8):
        for midi in notes("Bb1 F2 Bb2"):
            low.add(strings(midi, PIECE.seconds(4) - 0.05, rng, velocity=0.9), bar * 4, gain=0.3)
    low.audio = eq(low.audio, highpass(45), lowpass(2400))
    loop.mix(low, 2.2)

    # A shimmer of struck metal high over it, never settling: the jungle at night.
    shimmer = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for step in range(16):
            midi = chord[(step * 3 + bar) % len(chord)] + 24
            shimmer.add(bell(hz(midi), rng, seconds=0.5, ratios=(1.0, 2.76, 5.4), velocity=0.3 + 0.15 * (step % 3 == 0)),
                        bar * 4 + step * STEP, pan=0.5 if step % 2 else -0.5)
    shimmer.audio = eq(shimmer.audio, highpass(900))
    loop.mix(shimmer, 0.9)

    swells = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        if bar % 2:
            continue
        for midi in chord[:3]:
            f0 = hz(midi - 12)
            swells.add(saturate(held(f0, PIECE.seconds(8) - 0.1, rng, dark(saw, f0, 900), attack=2.4, decay=1.0,
                                     sustain=1.0, release=0.4, voices=3, detune_cents=8, spread=0.4), 1.3),
                       bar * 4, gain=0.4)
    swells.audio = eq(swells.audio, highpass(70))
    loop.mix(swells)

    gongs = PIECE.loop(8)
    gongs.add(gong(rng), 0)
    gongs.add(gong(rng, f0=55.0, seconds=3.0, velocity=0.6), 16)
    loop.mix(gongs, 1.2)

    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.3
    return loop


def render_pulse():
    rng = PIECE.rng(2)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    home = notes("Bb2")[0]
    for bar in range(8):
        figure = CLIMB if bar % 2 else FIGURE
        for eighth, offset in enumerate(figure):
            f0 = hz(home + offset)
            velocity = (1.0 if eighth in (0, 4) else 0.8) * (1 + 0.05 * rng.standard_normal())
            tone = held(f0, PIECE.seconds(0.5) * 0.8, rng, dark(saw, f0, 1400), attack=0.004, decay=0.1, sustain=0.6,
                        release=0.04, voices=2, detune_cents=7, spread=0.2, velocity=velocity)
            bass.add(saturate(tone, 1.6), bar * 4 + eighth * 0.5)
    bass.audio = eq(bass.audio, highpass(45), peak(160, 2.5, 1.0), peak(900, 2.0, 1.0), lowpass(3200))
    loop.mix(bass, 0.56)

    sub = PIECE.loop(8)
    sub_rng = PIECE.rng(20)
    for bar in range(8):
        for beat in range(4):
            sub.add(eight08(hz(notes("Bb1")[0]), PIECE.seconds(1) - 0.03, sub_rng, punch=3, decay=0.9, drive=1.4, click=0),
                    bar * 4 + beat)
    loop.mix(sub, 0.22)

    hands = PIECE.loop(8)
    for bar in range(8):
        for step in range(16):
            hands.add(shaker(rng, velocity=0.45 if step % 2 else 0.25), bar * 4 + step * STEP, pan=-0.4,
                      nudge_seconds=0.002 * rng.standard_normal())
    loop.mix(hands, 0.5)

    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.15
    return loop


def render_drive():
    rng = PIECE.rng(3)
    loop = PIECE.loop(8)

    kit = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, velocity in LOW_TOM.items():
            kit.add(tom(92, velocity * (1 + 0.05 * rng.standard_normal()), rng), base + step * STEP, pan=-0.2,
                    nudge_seconds=0.002 * rng.standard_normal())
        for step, velocity in HIGH_TOM.items():
            kit.add(tom(150, velocity, rng, seconds=0.2), base + step * STEP, pan=0.25)
        for step in range(16):
            if step not in LOW_TOM and step not in HIGH_TOM:
                kit.add(slap(0.5, rng), base + step * STEP, pan=0.35)
        for step in (4, 12):
            kit.add(clap(rng, velocity=0.8), base + step * STEP, pan=-0.05)
            kit.add(slap(1.0, rng), base + step * STEP, pan=0.05)
        for beat in range(4):
            kit.add(kick(rng, low=46, high=140, seconds=0.22, velocity=1.0 if beat % 2 == 0 else 0.85), base + beat)
        if bar % 4 == 3:
            for i, step in enumerate(range(8, 16)):
                kit.add(tom(92 + 10 * (7 - i), 0.6 + 0.05 * i, rng, seconds=0.18), base + step * STEP, pan=-0.3 + 0.08 * i)
    kit.add(cymbal(rng, seconds=1.6, velocity=0.5), 0, pan=0.3)
    kit.add(cymbal(rng, seconds=1.2, velocity=0.35), 16, pan=-0.3)
    kit.audio = eq(saturate(kit.audio, 1.5), peak(3200, 3.0, 1.0))
    loop.mix(kit, 0.9)

    snares = PIECE.loop(8)
    snare_rng = PIECE.rng(30)
    for bar in range(8):
        for step in (4, 12):
            snares.add(snare(snare_rng, tone_hz=230, seconds=0.13, velocity=0.9), bar * 4 + step * STEP, pan=-0.05)
    loop.mix(snares, 0.6)

    tambourine = PIECE.loop(8)
    for bar in range(8):
        for step in range(16):
            tambourine.add(jingles(snare_rng, seconds=0.05, velocity=0.6 if step % 4 == 2 else 0.3, count=7),
                           bar * 4 + step * STEP, pan=0.5)
    loop.mix(tambourine, 0.3)

    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.18
    return loop


def render_lead():
    rng = PIECE.rng(4)
    loop = PIECE.loop(16)
    synth = PIECE.loop(16)
    previous = None
    for bar, phrase in enumerate(TUNE):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                slide = name.endswith("~")
                midi = notes(name.rstrip("~"))[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.92
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 and not slide else None
                loop.add(reed(midi, seconds, rng, slide=slide, glide_from=glide), bar * 4 + at * STEP, pan=-0.1,
                         nudge_seconds=0.004 * rng.standard_normal())
                f0 = hz(midi - 12)
                synth.add(saturate(held(f0, seconds, rng, dark(square, f0, 2400), attack=0.008, decay=0.15, sustain=0.7,
                                        release=0.05, voices=2, detune_cents=9, spread=0.4, glide_from=None), 1.8),
                          bar * 4 + at * STEP, pan=0.2)
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {bar + 1} of the tune is {at} sixteenths long"
    loop.audio = eq(loop.audio, highpass(280), peak(1500, 2.0, 1.0))
    synth.audio = eq(synth.audio, highpass(160), lowpass(3800))
    loop.mix(synth, 0.3)
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.28, 2600) * 0.18
    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.25
    return loop


def brass(midi, seconds, rng):
    f0 = hz(midi)
    tone = held(f0, seconds, rng, dark(saw, f0, 2000), attack=0.012, decay=0.15, sustain=0.7, release=0.08,
                voices=3, detune_cents=8, spread=0.5, vibrato_hz=5, vibrato_cents=4)
    return saturate(tone, 1.5)


def render_fury():
    rng = PIECE.rng(5)
    loop = PIECE.loop(8)

    stabs = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for step, sixteenths in ((0, 3), (3, 2), (6, 2), (8, 3), (11, 2), (14, 2)):
            for midi in chord:
                stabs.add(brass(midi, PIECE.seconds(sixteenths * STEP) * 0.85, rng), bar * 4 + step * STEP, pan=0.25)
    stabs.audio = eq(stabs.audio, highpass(120), peak(1300, 2.5, 1.0), lowpass(5200))
    loop.mix(stabs, 0.24)

    choir = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for i, midi in enumerate(chord[:3]):
            f0 = hz(midi + 12)
            choir.add(held(f0, PIECE.seconds(4) - 0.1, rng, vowel(f0, AH if i != 1 else OH), attack=0.2,
                           decay=0.6, sustain=0.9, release=0.4, voices=4, detune_cents=14, spread=0.8,
                           vibrato_hz=5.6, vibrato_cents=16, vibrato_delay=0.15), bar * 4, gain=0.24)
    loop.mix(choir)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, velocity in ((0, 1.0), (3, 0.75), (6, 0.85), (8, 1.0), (10, 0.7), (11, 0.75), (14, 0.85)):
            drums.add(membrane(70, 0.7, rng, bend=1.5, bend_seconds=0.035, thump=0.45, thump_hz=1500, velocity=velocity),
                      base + step * STEP, pan=-0.1)
    drums.add(gong(rng, f0=52.0, seconds=4.0), 0)
    drums.audio = eq(saturate(drums.audio, 1.5), highpass(45))
    loop.mix(drums, 0.5)

    growl = PIECE.loop(8)
    growl_rng = PIECE.rng(50)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 12
        for step, sixteenths in ((0, 3), (3, 3), (6, 2), (8, 3), (11, 3), (14, 2)):
            growl.add(eight08(hz(root), PIECE.seconds(sixteenths * STEP) - 0.01, growl_rng, decay=0.8, drive=5.0),
                      bar * 4 + step * STEP)
    growl.audio = eq(growl.audio, highpass(60), lowpass(2500))
    loop.mix(growl, 0.2)

    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.25
    return loop


def trap_bass(midi, seconds, rng, slide_to=None, velocity=1.0):
    return eight08(hz(midi), seconds, rng, slide_to=slide_to, slide=0.08, decay=1.2, drive=2.8, presence=1.0,
                   velocity=velocity)


def render_boom():
    rng = PIECE.rng(6)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0]
        following = notes(PROGRESSION[(bar + 1) % 8][2])[0]
        # On the beat and off it, popping an octave up before the bar's middle, sliding into the next.
        for step, sixteenths, interval, slides in ((0, 3, 0, False), (3, 3, 0, False), (6, 2, 12, False),
                                                   (8, 4, 0, False), (12, 2, 0, False), (14, 2, 0, True)):
            target = hz(following) if slides and following != root else None
            bass.add(trap_bass(root + interval, PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target,
                               velocity=1.0 if step in (0, 8) else 0.85), bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(30), lowpass(3000))
    loop.mix(bass, 0.72)

    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.08
    return loop


def render_roll():
    rng = PIECE.rng(7)
    loop = PIECE.loop(4)

    hats = PIECE.loop(4)
    rolls = {(0, 12): ("3", 6, 1.0), (1, 12): ("32", 8, 1.0), (2, 4): ("3", 6, 1.1), (3, 8): ("32", 8, 1.2),
             (3, 12): ("32", 8, 1.4)}
    for bar in range(4):
        base = bar * 4
        step = 0
        while step < 16:
            roll = rolls.get((bar, step))
            if roll:
                kind, count, climb = roll
                for i in range(count):
                    along = i / (count - 1)
                    hats.add(trap_hat(rng, pitch=1 + (climb - 1) * along, velocity=0.3 + 0.4 * along),
                             base + (step + 4 * i / count) * STEP, pan=0.4)
                step += 4
                continue
            accent = 0.55 if step % 2 == 0 else 0.3
            hats.add(trap_hat(rng, velocity=accent * (1 + 0.08 * rng.standard_normal())), base + step * STEP, pan=0.4,
                     nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    loop.mix(hats, 4.8)

    for i in range(8):
        loop.add(snare(rng, tone_hz=230, seconds=0.1, velocity=0.45 + 0.06 * i), 15 + i * STEP / 2, pan=-0.2, gain=2.2)
    rising = swell(cymbal(rng, seconds=1.0, velocity=0.45))
    loop.add(rising, 16 - rising.shape[0] / PIECE.samples_per_beat, pan=0.3, gain=2.2)

    loop.audio = eq(loop.audio, highpass(150))
    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.15
    return loop


def howl(high, at, length=6):
    """A howl: a cry flung up onto a high note, held, and falling off it."""
    return [(at, high, length, AH)]


def ululate(upper, lower, at, count=6, hold=2):
    out = [(at + i, upper if i % 2 == 0 else lower, 1, OO) for i in range(count)]
    return out + [(at + count, lower if count % 2 == 0 else upper, hold, AH)]


CHOPS = [
    howl("F6", 0) + ululate("Bb5", "A5", 8),
    howl("Db6", 0, 4) + [(6, "Bb5", 2, OH)] + ululate("Bb5", "A5", 10, count=4),
    howl("Gb6", 0) + [(8, "F6", 2, OH), (10, "Db6", 2, AH), (12, "Bb5", 4, AH)],
    ululate("A5", "Gb5", 0, count=8, hold=4) + [(12, "Eb6", 4, AH)],
    howl("F6", 0) + ululate("Bb5", "A5", 8),
    howl("Eb6", 0, 4) + [(6, "Gb5", 2, OH)] + ululate("Bb5", "A5", 10, count=4),
    howl("Db6", 0) + [(8, "C6", 2, OH), (10, "Bb5", 2, AH), (12, "Gb5", 4, AH)],
    ululate("A5", "F5", 0, count=10, hold=6),
]


def render_chop():
    rng = PIECE.rng(8)
    loop = PIECE.loop(8)
    for bar, phrase in enumerate(CHOPS):
        for step, name, sixteenths, formants in phrase:
            longer = sixteenths > 1
            loop.add(chop(hz(notes(name)[0]), PIECE.seconds(sixteenths * STEP) * 0.9, rng, formants,
                          into=OO if longer and formants is AH else None, scoop=220 if longer else 0,
                          fall=500 if sixteenths >= 4 else (180 if longer else 0)),
                     bar * 4 + step * STEP, pan=0.4 if bar % 2 else -0.35, gain=0.4)
    loop.audio = eq(loop.audio, highpass(280), peak(2600, 2.0, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.3, 2400) * 0.25
    loop.audio += reverb(loop.audio, court(PIECE.rng(100))) * 0.3
    return loop


def main() -> int:
    write_stems(PIECE, {
        "bed": render_bed(),
        "pulse": render_pulse(),
        "drive": render_drive(),
        "lead": render_lead(),
        "boom": render_boom(),
        "roll": render_roll(),
        "chop": render_chop(),
        "fury": render_fury(),
    })
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
