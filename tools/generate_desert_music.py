# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the desert's music, as the eight stems the game layers.

    uv run tools/generate_desert_music.py

The last stage is flown from noon into night over a desert with something under the sand. The
model is the music of a desert palace's inner rooms in an old action RPG - a plucked lute, a
buzzing drone, goblet drums and finger cymbals, a reed flute winding through a scale with a gap in
it - with a trap beat rising under the drums as the fight heats up, 808s booming on the doums the
way ancient instruments and 808s meet in a film score today. As with the other two, the notes are
this script's own.

A in the Hijaz mode - A, B-flat, C-sharp, D, E, F, G - whose step and a half between the second
and third degrees is the sound of the whole piece. 105 BPM in 4/4, on the maqsum rhythm, the
backbone of so much music from the region: doum, tek, tek, doum, tek. Over a drone that never
leaves A, the eight-bar progression cadences the way the mode does, from the minor chord a tone
below:

    A | Bb | A | Gm | Dm | Bb | Gm | A

The layers, bottom up:

* bed   - a drone plucked in the tanpura's cycle, an oud on the maqsum, a low string pad.
* pulse - a soft darbuka playing the maqsum, a bass over a sub, a riq's jingles on the backbeat.
* drive - the full darbuka with its rolls, a frame drum, finger cymbals, hand claps with a drum
          machine's snare stacked on them.
* lead  - the ney: sixteen bars, the second half climbing to the octave, cadencing down the mode.
* boom  - 808s on the doums, an octave's pop on the second tek, sliding into each bar's root.
* roll  - hats in sixteenths over the riq, rolling in thirty-seconds and triplets, and a snare roll
          and a cymbal swelling into the top. Four bars.
* chop  - a voice chopped into a hook on the maqsum, scooping into its notes as the ney does, and
          stuttering into the turns.
* fury  - for the Sand Wyrm: low strings sawing an ostinato, big frame drums, brass, growling 808s,
          a choir.
"""

import numpy as np

from musicsynth import (
    AH, OH, RENDER_RATE, Piece, bandpass, bell, chop, clap, cymbal, dark, echo, eight08, eq, held,
    highpass, hz, jingles, kick, lowpass, membrane, notes, one_shot_filter, peak, pluck, reverb,
    reverb_ir, saturate, saw, snare, swell, trap_hat, vowel, write_stems,
)

PIECE = Piece("desert", bpm=105, beats_per_bar=4, seed=20261001)

# One sixteenth, in beats.
STEP = 1 / 4

# The pad's chord low to high, the bass's root, and the oud's six notes on the maqsum's strokes.
PROGRESSION = [
    ("A", notes("A3 C#4 E4"), "A1", notes("A2 A3 C#4 E3 D4 C#4")),
    ("Bb", notes("Bb3 D4 F4"), "Bb1", notes("Bb2 Bb3 D4 F3 F4 D4")),
    ("A", notes("A3 C#4 E4"), "A1", notes("A2 A3 C#4 E3 Bb3 A3")),
    ("Gm", notes("G3 Bb3 D4"), "G1", notes("G2 G3 Bb3 D3 D4 Bb3")),
    ("Dm", notes("A3 D4 F4"), "D2", notes("D3 A3 D4 F3 E4 D4")),
    ("Bb", notes("Bb3 D4 F4"), "Bb1", notes("Bb2 F3 Bb3 D3 F4 D4")),
    ("Gm", notes("G3 Bb3 D4"), "G1", notes("G2 D3 G3 Bb3 C#4 D4")),
    ("A", notes("A3 C#4 E4"), "A1", notes("A2 E3 A3 C#4 Bb3 A3")),
]

# The maqsum in sixteenths: doum on one and three, teks around them. The oud plucks on the same
# strokes, plus one to turn the bar over.
MAQSUM = {0: "D", 2: "T", 6: "T", 8: "D", 12: "T"}
OUD_STEPS = [0, 2, 6, 8, 12, 14]

# The ney, bar by bar: (note, length in sixteenths), None for a rest. A trailing "~" slides into
# the note from a half step below, the ornament the mode lives on.
TUNE = [
    [("A4~", 6), ("Bb4", 2), ("C#5", 4), ("D5", 2), ("C#5", 2)],
    [("D5~", 8), ("F5", 4), ("D5", 2), ("Bb4", 2)],
    [("C#5~", 6), ("Bb4", 2), ("A4", 8)],
    [(None, 2), ("G4", 2), ("Bb4", 2), ("D5", 2), ("G5~", 8)],
    [("F5~", 6), ("E5", 2), ("D5", 4), ("A4", 4)],
    [("Bb4", 4), ("D5", 4), ("F5~", 6), ("E5", 2)],
    [("D5~", 6), ("Bb4", 2), ("G4", 4), ("Bb4", 2), ("C#5", 2)],
    [("D5", 2), ("C#5", 2), ("Bb4", 4), ("A4~", 8)],
    [("E5~", 4), ("F5", 2), ("E5", 2), ("C#5", 4), ("E5", 4)],
    [("F5~", 6), ("G5", 2), ("F5", 4), ("D5", 4)],
    [("E5", 4), ("A5~", 8), ("G5", 2), ("F5", 2)],
    [("G5~", 6), ("F5", 2), ("D5", 4), ("Bb4", 4)],
    [("A5~", 8), ("G5", 2), ("F5", 2), ("E5", 4)],
    [("F5", 4), ("D5", 4), ("F5", 2), ("G5", 2), ("F5", 4)],
    [("G5~", 6), ("F5", 2), ("E5", 2), ("D5", 2), ("C#5", 2), ("D5", 2)],
    [("E5", 4), ("D5", 2), ("C#5", 2), ("Bb4", 4), ("A4~", 4)],
]


def hall(rng) -> np.ndarray:
    """Tiled walls round an open courtyard: bright, spacious, a clear set of early echoes."""
    return reverb_ir(2.4, rng, predelay=0.025, brightness=0.6, early=7, early_spread=0.07)


def oud(midi, velocity, rng, seconds=1.2):
    """A fretless lute with doubled strings, struck with a quill: dark body, bright attack."""
    f0 = hz(midi)
    return pluck(f0, seconds, rng, decay=0.9 * (147 / f0) ** 0.3, damping=0.5, pick=0.14, tilt=1.25,
                 course_cents=4, brightness_hz=4200, attack_noise=0.08, velocity=velocity)


def tanpura(midi, rng):
    """A long-necked drone lute: every partial rings for seconds, and the doubled strings shimmer."""
    return pluck(hz(midi), 4.5, rng, decay=3.2, damping=0.1, pick=0.1, tilt=1.0, course_cents=3,
                 brightness_hz=5000, attack_noise=0.02, velocity=0.6)


def render_bed():
    rng = PIECE.rng(1)
    loop = PIECE.loop(8)

    drone = PIECE.loop(8)
    for bar in range(8):
        for beat, name in enumerate(("E3", "A3", "A3", "A2")):
            drone.add(tanpura(notes(name)[0], rng), bar * 4 + beat, pan=-0.4 + 0.25 * beat,
                      nudge_seconds=0.01 * rng.standard_normal())
    drone.audio = eq(drone.audio, highpass(90), peak(2500, 3.0, 1.5))
    loop.mix(drone, 0.55)

    lute = PIECE.loop(8)
    for bar, (_, _, _, tones) in enumerate(PROGRESSION):
        for step, midi in zip(OUD_STEPS, tones):
            velocity = (0.95 if step in (0, 8) else 0.75) * (1 + 0.08 * rng.standard_normal())
            lute.add(oud(midi, velocity, rng), bar * 4 + step * STEP, pan=0.25,
                     nudge_seconds=0.004 * rng.standard_normal())
    lute.audio = eq(lute.audio, highpass(80), peak(200, 2.0, 1.0), peak(3200, 2.0, 1.2))
    lute.audio += echo(lute.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.22, 2500) * 0.2
    loop.mix(lute, 1.1)

    pad = PIECE.loop(8)
    for bar, (_, chord, _, _) in enumerate(PROGRESSION):
        for midi in chord:
            f0 = hz(midi - 12)
            pad.add(held(f0, PIECE.seconds(4) - 0.15, rng, dark(saw, f0, 1200), attack=0.5, decay=1.0,
                         sustain=0.85, release=0.9, voices=3, detune_cents=10, spread=0.7,
                         vibrato_hz=4.5, vibrato_cents=6), bar * 4, gain=0.19)
    pad.audio = eq(pad.audio, highpass(110))
    loop.mix(pad)

    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.3
    return loop


def darbuka(stroke, velocity, rng):
    """The goblet drum: doum in the middle of the head, tek and ka ringing at its rim."""
    if stroke == "D":
        return membrane(105, 0.3, rng, bend=1.2, modes=(1.0, 1.59, 2.14), mode_levels=(1.0, 0.3, 0.15),
                        thump=0.3, thump_hz=1200, velocity=velocity)
    ring = 780 if stroke == "T" else 700
    return membrane(ring, 0.06, rng, bend=1.01, modes=(1.0, 1.72, 2.4), mode_levels=(1.0, 0.6, 0.3),
                    thump=0.8, thump_hz=8000, velocity=velocity * (1.0 if stroke == "T" else 0.7))


def render_pulse():
    rng = PIECE.rng(2)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    for bar, (_, _, root_name, _) in enumerate(PROGRESSION):
        root = notes(root_name)[0]
        # On the doums, and a step up to lean into the next bar, the way the mode leans.
        for step, interval, sixteenths, velocity in ((0, 0, 5, 1.0), (6, 0, 2, 0.7), (8, 0, 4, 0.9),
                                                     (14, 1, 2, 0.6)):
            f0 = hz(root + interval + 12)
            bass.add(pluck(f0, PIECE.seconds(sixteenths * STEP) + 0.15, rng, decay=0.8, damping=0.55,
                           pick=0.25, tilt=1.45, brightness_hz=2000, attack_noise=0.03, velocity=velocity),
                     bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(40), peak(150, 2.5, 1.0), peak(700, 1.5, 1.0), lowpass(3000))
    loop.mix(bass, 0.55)

    # A sub an octave under the bass, on the doums: the floor the 808s will stand on. Dice of its
    # own, so the bass and the drums play exactly as they did without it.
    sub = PIECE.loop(8)
    sub_rng = PIECE.rng(20)
    for bar, (_, _, root_name, _) in enumerate(PROGRESSION):
        for step, sixteenths in ((0, 5), (8, 4)):
            sub.add(eight08(hz(notes(root_name)[0]), PIECE.seconds(sixteenths * STEP) - 0.03, sub_rng, punch=3,
                            decay=0.8, drive=1.3, click=0), bar * 4 + step * STEP)
    loop.mix(sub, 0.26)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, stroke in MAQSUM.items():
            velocity = (0.9 if stroke == "D" else 0.6) * (1 + 0.07 * rng.standard_normal())
            drums.add(darbuka(stroke, velocity, rng), base + step * STEP, pan=-0.2,
                      nudge_seconds=0.003 * rng.standard_normal())
        for step in (4, 12):
            drums.add(jingles(rng, seconds=0.13, velocity=0.45), base + step * STEP, pan=0.5)
    loop.mix(drums, 0.47)

    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.2
    return loop


def bendir(velocity, rng):
    """A frame drum with a snare stretched under its skin: a low boom with a buzz on it."""
    boom = membrane(84, 0.45, rng, bend=1.25, thump=0.25, thump_hz=900)
    n = boom.shape[0]
    buzz = one_shot_filter(rng.standard_normal(n), bandpass(1800, 6500)) * np.exp(-np.arange(n) / (0.12 * RENDER_RATE))
    return (boom + 0.25 * buzz / np.max(np.abs(buzz))) * velocity


def render_drive():
    rng = PIECE.rng(3)
    loop = PIECE.loop(8)

    kit = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        # The full maqsum, with the gaps filled by ka strokes, and a roll into every other bar.
        for step in range(16):
            stroke = MAQSUM.get(step, "K")
            if stroke == "K" and step % 2 and bar % 2 == 0 and step < 13:
                continue
            velocity = {"D": 1.0, "T": 0.75, "K": 0.4}[stroke] * (1 + 0.08 * rng.standard_normal())
            kit.add(darbuka(stroke, velocity, rng), base + step * STEP, pan=-0.2,
                    nudge_seconds=0.003 * rng.standard_normal())
        for step, velocity in ((0, 1.0), (8, 0.85), (11, 0.6)):
            kit.add(bendir(velocity, rng), base + step * STEP, pan=0.2)
        for step, velocity in ((0, 0.6), (4, 0.45), (6, 0.4), (8, 0.55), (12, 0.45), (14, 0.4)):
            kit.add(bell(2750, rng, seconds=0.9, ratios=(1.0, 1.41, 2.17, 2.9), velocity=velocity),
                    base + step * STEP, pan=0.6 if step % 8 else -0.6)
        for step in (4, 12):
            kit.add(clap(rng, velocity=0.7), base + step * STEP, pan=0.1)
        for step in range(16):
            kit.add(jingles(rng, seconds=0.06, velocity=0.3 if step % 4 else 0.45, count=6),
                    base + step * STEP, pan=0.45, nudge_seconds=0.002 * rng.standard_normal())
        if bar == 7:
            for i, step in enumerate(range(10, 16)):
                kit.add(darbuka("T", 0.6 + 0.07 * i, rng), base + step * STEP, pan=-0.2)
    kit.add(cymbal(rng, seconds=1.4, velocity=0.4), 0, pan=0.3)
    kit.audio = saturate(kit.audio, 1.5)
    loop.mix(kit, 1.03)

    # A drum machine's snare stacked on the claps.
    snares = PIECE.loop(8)
    snare_rng = PIECE.rng(30)
    for bar in range(8):
        for step in (4, 12):
            snares.add(snare(snare_rng, tone_hz=230, seconds=0.12, velocity=0.8), bar * 4 + step * STEP, pan=-0.05)
    loop.mix(snares, 0.3)

    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.2
    return loop


def ney(midi, seconds, rng, slide=False, glide_from=None, velocity=0.72):
    """The reed flute: breathier than any other wind here, with a hollow, slightly nasal tone."""
    f0 = hz(midi)
    shape = lambda k: (1.0, 0.3, 0.16, 0.08, 0.05, 0.02)[k - 1] if k <= 6 else 0.0
    start = hz(midi - 1) if slide else glide_from
    return held(f0, seconds, rng, shape, count=6, attack=0.06, decay=0.35, sustain=0.82, release=0.15,
                vibrato_hz=5.6, vibrato_cents=20 if seconds > 0.5 else 6, vibrato_delay=0.3,
                glide_from=start, glide=0.12 if slide else 0.04, breath=0.2, breath_band=(800, 5500),
                velocity=velocity)


def render_lead():
    rng = PIECE.rng(4)
    loop = PIECE.loop(16)
    previous = None
    for bar, phrase in enumerate(TUNE):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                slide = name.endswith("~")
                midi = notes(name.rstrip("~"))[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.95
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 and not slide else None
                loop.add(ney(midi, seconds, rng, slide=slide, glide_from=glide), bar * 4 + at * STEP,
                         pan=-0.1, nudge_seconds=0.007 * rng.standard_normal())
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {bar + 1} of the tune is {at} sixteenths long"
    loop.audio = eq(loop.audio, highpass(250), peak(1500, 2.0, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(1.5), 0.3, 2400) * 0.22
    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.32
    return loop


def strings(midi, seconds, rng, velocity=1.0):
    """Low strings bowed short and hard: a saw ensemble, bright at the attack."""
    f0 = hz(midi)
    body = held(f0, seconds, rng, dark(saw, f0, 2600), attack=0.01, decay=0.12, sustain=0.5, release=0.06,
                voices=3, detune_cents=9, spread=0.6)
    return saturate(body * velocity, 1.3)


def render_fury():
    rng = PIECE.rng(5)
    loop = PIECE.loop(8)

    # The chase: sixteenths on the root, kicking up a half step and back, the mode's own neighbor.
    ostinato = PIECE.loop(8)
    figure = (0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 3, 1, 0, -2)
    for bar, (_, _, root_name, _) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 12
        for step, offset in enumerate(figure):
            velocity = (1.0 if step % 4 == 0 else 0.7) * (1 + 0.05 * rng.standard_normal())
            ostinato.add(strings(root + offset, PIECE.seconds(STEP) * 0.8, rng, velocity),
                         bar * 4 + step * STEP, pan=-0.3)
    ostinato.audio = eq(ostinato.audio, highpass(70), peak(900, 2.0, 1.0), lowpass(5000))
    loop.mix(ostinato, 0.5)

    brass = PIECE.loop(8)
    for bar, (_, chord, _, _) in enumerate(PROGRESSION):
        for step, sixteenths in ((0, 3), (6, 2), (8, 6)):
            for midi in chord:
                f0 = hz(midi)
                tone = held(f0, PIECE.seconds(sixteenths * STEP) * 0.85, rng, dark(saw, f0, 1800),
                            attack=0.02, decay=0.2, sustain=0.7, release=0.12, voices=3, detune_cents=7,
                            spread=0.5, vibrato_hz=5, vibrato_cents=4)
                brass.add(saturate(tone, 1.4), bar * 4 + step * STEP, pan=0.3)
    brass.audio = eq(brass.audio, highpass(120), peak(1300, 2.5, 1.0), lowpass(4800))
    loop.mix(brass, 0.37)

    choir = PIECE.loop(8)
    for bar, (_, chord, _, _) in enumerate(PROGRESSION):
        for i, midi in enumerate(chord):
            f0 = hz(midi + 12)
            choir.add(held(f0, PIECE.seconds(4) - 0.1, rng, vowel(f0, AH if i != 1 else OH), attack=0.3,
                           decay=0.8, sustain=0.9, release=0.5, voices=4, detune_cents=14, spread=0.8,
                           vibrato_hz=5.4, vibrato_cents=14, vibrato_delay=0.2), bar * 4, gain=0.24)
    loop.mix(choir)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, velocity in ((0, 1.0), (3, 0.7), (6, 0.85), (8, 0.95), (11, 0.7), (14, 0.85), (15, 0.6)):
            drums.add(membrane(76, 0.7, rng, bend=1.5, bend_seconds=0.035, thump=0.45, thump_hz=1500,
                               velocity=velocity), base + step * STEP, pan=-0.1)
    drums.add(bell(96, rng, seconds=3.5, ratios=(1.0, 1.52, 2.13, 2.71, 3.3, 4.1), velocity=0.8), 0)
    drums.audio = eq(saturate(drums.audio, 1.5), highpass(45))
    loop.mix(drums, 0.55)

    # 808s driven until they growl, with the frame drums: an octave over the boom's, so the two do
    # not pile up in the sub.
    growl = PIECE.loop(8)
    growl_rng = PIECE.rng(50)
    for bar, (_, _, root_name, _) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 12
        for step, sixteenths in ((0, 3), (6, 2), (8, 3), (11, 3), (14, 2)):
            growl.add(eight08(hz(root), PIECE.seconds(sixteenths * STEP) - 0.01, growl_rng, decay=0.8, drive=5.0),
                      bar * 4 + step * STEP)
    growl.audio = eq(growl.audio, highpass(60), lowpass(2500))
    loop.mix(growl, 0.22)

    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.25
    return loop


def trap_bass(midi, seconds, rng, slide_to=None, velocity=1.0):
    """The boom's 808: long, sliding, and buzzing enough on top to come through a phone's speaker."""
    return eight08(hz(midi), seconds, rng, slide_to=slide_to, slide=0.1, decay=1.4, drive=2.6, presence=1.0,
                   velocity=velocity)


def render_boom():
    rng = PIECE.rng(6)
    loop = PIECE.loop(8)

    bass = PIECE.loop(8)
    for bar, (_, _, root_name, _) in enumerate(PROGRESSION):
        root = notes(root_name)[0]
        following = notes(PROGRESSION[(bar + 1) % 8][2])[0]
        # Long on each doum, the octave popping on the second tek, and slid into the next bar's root.
        for step, sixteenths, interval, slides in ((0, 6, 0, False), (6, 2, 12, False), (8, 3, 0, False),
                                                   (11, 3, 0, False), (14, 2, 0, True)):
            target = hz(following) if slides and following != root else None
            bass.add(trap_bass(root + interval, PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target,
                               velocity=1.0 if step in (0, 8) else 0.85), bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(30), lowpass(3000))
    loop.mix(bass, 0.75)

    kicks = PIECE.loop(8)
    for bar in range(8):
        for step in (0, 8, 11):
            kicks.add(kick(rng, low=52, high=150, seconds=0.13, velocity=0.8 if step != 11 else 0.6),
                      bar * 4 + step * STEP)
    loop.mix(kicks, 0.45)

    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.08
    return loop


def render_roll():
    rng = PIECE.rng(7)
    loop = PIECE.loop(4)

    # Sixteenths leaning on the maqsum's strokes, and rolls: thirty-seconds over the last beat of the
    # second bar, a triplet run in the third, and thirty-seconds climbing out of the fourth.
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
                             base + (step + 4 * i / count) * STEP, pan=0.4)
                step += 4
                continue
            if step == 14 and bar % 2 == 0:
                hats.add(trap_hat(rng, open_=True, velocity=0.5), base + step * STEP, pan=0.4)
            else:
                accent = 0.55 if step in MAQSUM else 0.32
                hats.add(trap_hat(rng, velocity=accent * (1 + 0.08 * rng.standard_normal())), base + step * STEP,
                         pan=0.4, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    loop.mix(hats, 6.7)

    # The snare rolls into the top of the loop, sixteenths and then thirty-seconds, with a cymbal
    # swelling under it.
    for i in range(4):
        loop.add(snare(rng, tone_hz=230, velocity=0.35 + 0.05 * i), 14 + i * STEP, pan=-0.2, gain=2.4)
    for i in range(8):
        loop.add(snare(rng, tone_hz=230, seconds=0.1, velocity=0.5 + 0.05 * i), 15 + i * STEP / 2, pan=-0.2,
                 gain=2.4)
    rising = swell(cymbal(rng, seconds=1.2, velocity=0.45))
    loop.add(rising, 16 - rising.shape[0] / PIECE.samples_per_beat, pan=0.3, gain=2.4)

    loop.audio = eq(loop.audio, highpass(150))
    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.15
    return loop


# The chopped voice, bar by bar: (sixteenth, note, length in sixteenths, vowel). On the maqsum, under
# the ney, every longer note scooped into from below; stuttering into the halfway point and the top.
CHOPS = [
    [(0, "A4", 2, AH), (3, "C#5", 1, AH), (6, "E5", 2, OH), (8, "D5", 1, AH), (10, "C#5", 2, AH),
     (12, "Bb4", 2, OH), (14, "A4", 2, AH)],
    [(0, "Bb4", 2, AH), (3, "D5", 1, AH), (6, "F5", 2, OH), (8, "E5", 1, AH), (10, "D5", 2, AH),
     (12, "C#5", 2, OH), (14, "D5", 2, AH)],
    [(0, "A4", 2, AH), (3, "C#5", 1, AH), (6, "E5", 2, OH), (8, "F5", 1, AH), (10, "E5", 2, AH),
     (12, "D5", 1, OH), (13, "C#5", 1, OH), (14, "A4", 2, AH)],
    [(0, "G4", 2, AH), (3, "Bb4", 1, AH), (6, "D5", 2, OH), (8, "D5", 1, AH), (9, "D5", 1, AH), (10, "D5", 1, AH),
     (11, "D5", 1, AH), (12, "C#5", 2, OH), (14, "Bb4", 2, AH)],
    [(0, "D5", 2, AH), (3, "F5", 1, AH), (6, "E5", 2, OH), (8, "F5", 1, AH), (10, "E5", 2, AH),
     (12, "D5", 2, OH), (14, "A4", 2, AH)],
    [(0, "D5", 2, AH), (3, "F5", 1, AH), (6, "D5", 2, OH), (8, "Bb4", 1, AH), (10, "C#5", 2, AH),
     (12, "D5", 2, OH), (14, "F5", 2, AH)],
    [(0, "G4", 2, AH), (3, "Bb4", 1, AH), (6, "D5", 2, OH), (8, "C#5", 1, AH), (10, "Bb4", 2, AH),
     (12, "A4", 2, OH), (14, "G4", 2, AH)],
    [(0, "A4", 2, AH), (3, "Bb4", 1, AH), (6, "C#5", 2, OH), (8, "E5", 1, AH), (9, "E5", 1, AH), (10, "E5", 1, AH),
     (11, "E5", 1, AH), (12, "D5", 2, OH), (14, "C#5", 2, AH)],
]


def render_chop():
    rng = PIECE.rng(8)
    loop = PIECE.loop(8)
    for bar, phrase in enumerate(CHOPS):
        for step, name, sixteenths, formants in phrase:
            longer = sixteenths > 1
            loop.add(chop(hz(notes(name)[0]), PIECE.seconds(sixteenths * STEP) * 0.85, rng, formants,
                          into=OH if longer and formants is AH else None, scoop=100 if longer else 0,
                          fall=150 if longer else 0),
                     bar * 4 + step * STEP, pan=0.4 if step % 4 else -0.3, gain=0.63)
    loop.audio = eq(loop.audio, highpass(220), peak(2800, 2.0, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.3, 2400) * 0.25
    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.25
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
