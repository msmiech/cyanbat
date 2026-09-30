# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the forest's music, as the eight stems the game layers.

    uv run tools/generate_forest_music.py

The second stage goes somewhere older and less friendly than the cave: a jungle, humid and close,
with something watching from it. The model is the music of a flooded jungle city in an old action
RPG - wooden drums, a drone that is almost a voice, a bamboo flute calling out and bending into its
notes - with a trap beat growing under the drums as the fight heats up: 808s riding the sway,
hi-hats rolling, voices chopped into a hook. The notes are this script's own.

D minor at 98 BPM in 4/4, darkened by the flat second of the Phrygian mode: the sixth bar sits on
E-flat, a half step over the tonic, which is where the forest turns threatening. The eight-bar
progression turns back through a suspended dominant:

    Dm | Bb | C | Dm | Dm | Eb | C | Asus4 A

Its rhythms lean on 3+3+2, three dotted eighths and a quarter, which is what makes it sway rather
than march. The layers, bottom up:

* bed   - a drone shifting between vowels, a kalimba figure in 3+3+2, and a dark pad. Eight bars.
* pulse - a round bass over a sub, a two-toned log drum, shakers.
* drive - djembe, surdo and clave, a drum machine's hats ticking eighths; toms tumbling into the
          turnaround.
* lead  - the bamboo flute: sixteen bars, a call and a higher answer, every long note bent up to.
* boom  - 808s on the 3+3+2, an octave's pop on the two, sliding into each bar's root; a clap on
          the backbeat.
* roll  - hats in sixteenths, rolling in thirty-seconds and triplets, and a snare roll and a cymbal
          swelling into the top. Four bars.
* chop  - a voice chopped into a hook on the 3+3+2, under the flute, stuttering into the turns.
* fury  - for the Moth Queen: war drums, low horns and growling 808s stabbing the tresillo, a
          choir. Eight bars.
"""

import numpy as np

from musicsynth import (
    AH, OH, OO, Piece, bell, chop, clap, cymbal, dark, drone, echo, eight08, eq, held, highpass, hz,
    jingles, kick, lowpass, mallet, membrane, notes, peak, pluck, reverb, reverb_ir, saturate, saw,
    shaker, snare, swell, trap_hat, vowel, woodblock, write_stems,
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

    # A sub under the bass's first note of the bar, the floor the 808s will stand on. Dice of its
    # own, so the bass and the drums play exactly as they did without it.
    sub = PIECE.loop(8)
    sub_rng = PIECE.rng(20)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        sub.add(eight08(hz(notes(root_name)[0]), PIECE.seconds(10 * STEP) - 0.05, sub_rng, punch=3, decay=0.9,
                        drive=1.3, click=0), bar * 4)
    loop.mix(sub, 0.26)

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

    # A drum machine's hats ticking eighths over the wooden drums.
    hats = PIECE.loop(8)
    hat_rng = PIECE.rng(30)
    for bar in range(8):
        for step in range(0, 16, 2):
            velocity = (0.6 if step % 4 == 0 else 0.42) * (1 + 0.08 * hat_rng.standard_normal())
            hats.add(trap_hat(hat_rng, velocity=velocity), bar * 4 + step * STEP, pan=0.35,
                     nudge_seconds=0.002 * hat_rng.standard_normal())
    loop.mix(hats, 2.2)

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

    # 808s driven until they growl, stabbing the tresillo with the horns: an octave over the boom's,
    # so the two do not pile up in the sub.
    growl = PIECE.loop(8)
    growl_rng = PIECE.rng(50)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 12
        for step, sixteenths in ((0, 3), (3, 3), (6, 2), (8, 3), (11, 3), (14, 2)):
            growl.add(eight08(hz(root), PIECE.seconds(sixteenths * STEP) - 0.01, growl_rng, decay=0.8, drive=5.0),
                      bar * 4 + step * STEP)
    growl.audio = eq(growl.audio, highpass(60), lowpass(2500))
    loop.mix(growl, 0.22)

    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.25
    return loop


def trap_bass(midi, seconds, rng, slide_to=None, velocity=1.0):
    """The boom's 808: long, sliding, and buzzing enough on top to come through a phone's speaker."""
    return eight08(hz(midi), seconds, rng, slide_to=slide_to, slide=0.1, decay=1.4, drive=2.6, presence=1.0,
                   velocity=velocity)


def render_boom():
    rng = PIECE.rng(6)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0]
        following = notes(PROGRESSION[(bar + 1) % 8][2])[0]
        # On the 3+3+2, the octave popping on the two, then held and slid into the next bar's root.
        for step, sixteenths, interval, slides in ((0, 3, 0, False), (3, 3, 0, False), (6, 2, 12, False),
                                                   (8, 6, 0, False), (14, 2, 0, True)):
            target = hz(following) if slides and following != root else None
            bass.add(trap_bass(root + interval, PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target,
                               velocity=1.0 if step in (0, 8) else 0.85), bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(30), lowpass(3000))
    loop.mix(bass, 0.75)

    kit = PIECE.loop(8)
    for bar in range(8):
        for step in (0, 6):
            kit.add(kick(rng, low=52, high=150, seconds=0.13, velocity=0.8), bar * 4 + step * STEP)
        for step in (4, 12):
            kit.add(clap(rng, velocity=0.85), bar * 4 + step * STEP, pan=0.05)
    loop.mix(kit, 0.5)

    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.08
    return loop


def render_roll():
    rng = PIECE.rng(7)
    loop = PIECE.loop(4)

    # The sixteenths between the drive's eighths, and rolls: thirty-seconds over the last beat of
    # the second bar, a triplet run in the third, and thirty-seconds climbing out of the fourth.
    hats = PIECE.loop(4)
    rolls = {(1, 12): ("32", 8, 1.0), (2, 8): ("3", 6, 1.0), (3, 12): ("32", 8, 1.3)}
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
                             base + (step + 4 * i / count) * STEP, pan=0.35)
                step += 4
                continue
            if step == 14 and bar % 2 == 0:
                hats.add(trap_hat(rng, open_=True, velocity=0.5), base + step * STEP, pan=0.35)
            elif step % 2:
                hats.add(trap_hat(rng, velocity=0.35 * (1 + 0.08 * rng.standard_normal())), base + step * STEP,
                         pan=0.35, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    loop.mix(hats, 8.0)

    # The snare rolls into the top of the loop, sixteenths and then thirty-seconds, with a cymbal
    # swelling under it.
    for i in range(4):
        loop.add(snare(rng, velocity=0.35 + 0.05 * i), 14 + i * STEP, pan=-0.2, gain=2.6)
    for i in range(8):
        loop.add(snare(rng, seconds=0.1, velocity=0.5 + 0.05 * i), 15 + i * STEP / 2, pan=-0.2, gain=2.6)
    rising = swell(cymbal(rng, seconds=1.2, velocity=0.45))
    loop.add(rising, 16 - rising.shape[0] / PIECE.samples_per_beat, pan=0.3, gain=2.6)

    loop.audio = eq(loop.audio, highpass(150))
    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.15
    return loop


# The chopped voice, bar by bar: (sixteenth, note, length in sixteenths, vowel). On the 3+3+2, in the
# octave under the flute, stuttering into the halfway point and into the turnaround.
CHOPS = [
    [(0, "D5", 2, AH), (3, "A4", 2, AH), (6, "F4", 2, OH), (8, "D4", 1, AH), (11, "F4", 2, AH), (14, "A4", 2, OH)],
    [(0, "D5", 2, AH), (3, "Bb4", 2, AH), (6, "F4", 2, OH), (8, "D4", 1, AH), (11, "F4", 2, AH), (14, "Bb4", 2, OH)],
    [(0, "E5", 2, AH), (3, "C5", 2, AH), (6, "G4", 2, OH), (8, "E4", 1, AH), (11, "G4", 2, AH), (14, "C5", 2, OH)],
    [(0, "F5", 2, AH), (3, "D5", 2, AH), (6, "A4", 2, OH), (8, "D5", 1, AH), (9, "D5", 1, AH), (10, "D5", 1, AH),
     (11, "F5", 2, AH), (14, "E5", 2, OH)],
    [(0, "D5", 2, AH), (3, "A4", 2, AH), (6, "F4", 2, OH), (8, "D4", 1, AH), (11, "F4", 2, AH), (14, "A4", 2, OH)],
    [(0, "Eb5", 2, AH), (3, "Bb4", 2, AH), (6, "G4", 2, OH), (8, "Eb4", 1, AH), (11, "G4", 2, AH), (14, "Bb4", 2, OH)],
    [(0, "E5", 2, AH), (3, "C5", 2, AH), (6, "G4", 2, OH), (8, "E4", 1, AH), (11, "G4", 2, AH), (14, "C5", 2, OH)],
    [(0, "D5", 2, AH), (3, "A4", 2, AH), (6, "E4", 2, OH), (8, "C#5", 1, AH), (9, "C#5", 1, AH), (10, "C#5", 1, AH),
     (11, "E5", 2, AH), (14, "A4", 2, OH)],
]


def render_chop():
    rng = PIECE.rng(8)
    loop = PIECE.loop(8)
    for bar, phrase in enumerate(CHOPS):
        for step, name, sixteenths, formants in phrase:
            longer = sixteenths > 1
            loop.add(chop(hz(notes(name)[0]), PIECE.seconds(sixteenths * STEP) * 0.85, rng, formants,
                          into=OH if longer and formants is AH else None, scoop=70 if longer else 0,
                          fall=180 if longer else 0),
                     bar * 4 + step * STEP, pan=0.4 if step % 3 else -0.3, gain=0.8)
    loop.audio = eq(loop.audio, highpass(220), peak(2800, 2.0, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.3, 2000) * 0.25
    loop.audio += reverb(loop.audio, jungle(PIECE.rng(100))) * 0.25
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
