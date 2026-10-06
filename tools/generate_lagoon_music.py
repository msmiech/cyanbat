# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the lagoon's music, as the eight stems the game layers.

    uv run tools/generate_lagoon_music.py

The fourth stage is flown from night into noon across a bay of limestone islands toward a temple in
the water, and its music is a resort's lazy calm going wrong: a gong circle and a reed winding
through a scale with a half step at its root, over hand drums that never settle, tribal toms, and
voices whooping and ululating as if something in the jungle were watching. A trap beat rises under
it as the fight heats up, as in every stage. The notes are this script's own.

C as its home, in the mode F harmonic minor leaves it - C, D-flat, E, F, G, A-flat, B-flat - whose
half step from C up to D-flat is the unease in the whole piece: every phrase leans on it. 135 BPM in
4/4, its hand drums in sixteenths accented off the beat, where a resort band's would sit on it. The
eight-bar progression holds its home, slides up a half step and comes back the long way round:

    C | C | Db | Bbm | Ab+ | Fm | Db | C

The layers, bottom up:

* bed   - a gong circle turning over the chords in eighths, a dark string pad, a great gong.
* pulse - a bass held a bar at a time over a sub, finger cymbals ringing off the beat, a shaker.
* drive - tribal toms and hand drums in sixteenths, accented off the beat, claps on the backbeat,
          and a roll into the top.
* lead  - a reed with a wide vibrato, sixteen bars, a xylophone doubling it an octave up.
* boom  - 808s on the roots, sliding into the next bar's.
* roll  - hats in sixteenths leaning on the last of each beat, rolling, and a snare roll into the top.
          Four bars.
* chop  - a voice chopped into whoops - a low note leaping up an octave - and ululations on the half
          step, answering the reed.
* fury  - for a streak gone supernova: toms in sixteenths, brass on the chords, a choir, gongs.
"""

import numpy as np

from musicsynth import (
    AH, OH, OO, Piece, bell, chop, clap, cymbal, dark, echo, eight08, eq, held, highpass, hz, jingles,
    kick, lowpass, mallet, membrane, notes, peak, pluck, reverb, reverb_ir, saturate, saw, shaker,
    snare, swell, trap_hat, vowel, write_stems,
)

PIECE = Piece("lagoon", bpm=135, beats_per_bar=4, seed=20261005)

# One sixteenth, in beats.
STEP = 1 / 4

# The pad's chord low to high, and the bass's root.
PROGRESSION = [
    ("C", notes("C4 E4 G4"), "C2"),
    ("C", notes("C4 E4 G4"), "C2"),
    ("Db", notes("Db4 F4 Ab4"), "Db2"),
    ("Bbm", notes("Bb3 Db4 F4"), "Bb1"),
    ("Ab+", notes("C4 E4 Ab4"), "Ab1"),
    ("Fm", notes("C4 F4 Ab4"), "F1"),
    ("Db", notes("Db4 F4 Ab4"), "Db2"),
    ("C", notes("C4 E4 G4"), "C2"),
]

# The gong circle's figure over each chord: which of the chord's tones, an octave up or not, on each
# eighth - turning round its chord and back, the way the gong circles in a temple band run.
CIRCLE = [(0, 0), (1, 0), (2, 0), (0, 12), (1, 12), (2, 0), (1, 0), (2, 12)]

# The reed, bar by bar: (note, length in sixteenths), None for a rest. A trailing "~" slides into
# the note from a half step below, the ornament the mode lives on.
TUNE = [
    [("G4~", 4), ("C5", 2), ("Db5", 2), ("E5", 6), ("Db5", 2)],
    [("C5", 4), ("E5", 2), ("F5", 2), ("G5~", 8)],
    [("Ab5", 4), ("G5", 2), ("F5", 2), ("Db5", 6), ("C5", 2)],
    [("Bb4~", 6), ("Db5", 2), ("F5", 4), ("E5", 4)],
    [("E5", 4), ("F5", 2), ("E5", 2), ("C5", 4), ("Ab4", 4)],
    [("F4~", 6), ("Ab4", 2), ("C5", 4), ("Db5", 2), ("C5", 2)],
    [("Db5", 4), ("F5", 4), ("Ab5", 4), ("G5", 2), ("F5", 2)],
    [("E5", 4), ("Db5", 4), ("C5~", 8)],
    [(None, 2), ("C6", 2), ("Bb5", 2), ("G5", 2), ("E5~", 8)],
    [("F5", 2), ("E5", 2), ("Db5", 2), ("E5", 2), ("G5~", 8)],
    [("Ab5~", 6), ("Bb5", 2), ("Ab5", 4), ("F5", 4)],
    [("Db6~", 6), ("C6", 2), ("Bb5", 4), ("F5", 4)],
    [("C6", 4), ("E5", 2), ("F5", 2), ("Ab5", 4), ("G5", 2), ("F5", 2)],
    [("Ab5~", 6), ("G5", 2), ("F5", 4), ("C5", 4)],
    [("Db5", 2), ("F5", 2), ("Ab5", 2), ("Db6", 2), ("C6", 4), ("Ab5", 4)],
    [("G5", 4), ("E5", 2), ("Db5", 2), ("C5~", 8)],
]

# The drums in sixteenths, accented off the beat: the low tom on one, the "a" of two and of four,
# and the "and" of the beat between; the high tom answering; hand slaps with the claps.
LOW_TOM = {0: 1.0, 3: 0.75, 6: 0.8, 8: 0.95, 11: 0.75, 14: 0.8}
HIGH_TOM = {2: 0.55, 7: 0.5, 10: 0.6, 15: 0.5}
SLAP = {4: 0.8, 12: 0.85, 13: 0.4}


def temple(rng) -> np.ndarray:
    """An open temple court by the water: a long, soft tail and a scatter of stone echoes."""
    return reverb_ir(2.8, rng, predelay=0.03, brightness=0.55, early=6, early_spread=0.08)


def khong(midi, velocity, rng, seconds=1.4):
    """A gong circle's bossed gong: a bright, ringing strike, its partials close to harmonic."""
    return bell(hz(midi), rng, seconds=seconds, ratios=(1.0, 2.01, 2.98, 4.07), velocity=velocity)


def ranat(midi, velocity, rng):
    """A xylophone of hardwood bars: a hard knock and a short, hollow ring."""
    return mallet(hz(midi), 0.6, rng, ratios=(1.0, 3.93, 9.2), levels=(1.0, 0.35, 0.1),
                  decays=(0.32, 0.07, 0.025), velocity=velocity, knock=0.18)


def gong(rng, f0=65.4, seconds=4.5, velocity=1.0):
    """The great gong that marks the top of the cycle."""
    return bell(f0, rng, seconds=seconds, ratios=(1.0, 1.52, 2.13, 2.71, 3.3, 4.1), velocity=velocity)


def ching(rng, open_=True, velocity=1.0):
    """Finger cymbals: let ring off the beat, or clapped together and damped on it."""
    return bell(2950, rng, seconds=0.85 if open_ else 0.06, ratios=(1.0, 1.48, 2.18, 3.07), velocity=velocity)


# A reed played in the nose: a strong second and third formant over the fundamental.
REED = ((700, 120, 1.0), (1350, 150, 0.8), (2650, 220, 0.35))


def reed(midi, seconds, rng, slide=False, glide_from=None, velocity=0.75):
    """A double reed with a wide, slow vibrato and a buzz in it, bending into its notes."""
    f0 = hz(midi)
    start = hz(midi - 1) if slide else glide_from
    return held(f0, seconds, rng, vowel(f0, REED), attack=0.03, decay=0.25, sustain=0.85, release=0.12,
                vibrato_hz=5.2, vibrato_cents=28 if seconds > 0.4 else 8, vibrato_delay=0.18,
                glide_from=start, glide=0.11 if slide else 0.04, breath=0.12, breath_band=(900, 5000),
                velocity=velocity)


def render_bed():
    rng = PIECE.rng(1)
    loop = PIECE.loop(8)

    circle = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for eighth, (tone, octave) in enumerate(CIRCLE):
            velocity = (0.9 if eighth % 2 == 0 else 0.65) * (1 + 0.07 * rng.standard_normal())
            circle.add(khong(chord[tone] + octave, velocity, rng), bar * 4 + eighth * 0.5,
                       pan=-0.35 + 0.1 * tone, nudge_seconds=0.005 * rng.standard_normal())
    circle.audio = eq(circle.audio, highpass(180), peak(1800, 2.0, 1.0))
    circle.audio += echo(circle.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.25, 3000) * 0.18
    loop.mix(circle, 0.95)

    pad = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for midi in chord:
            f0 = hz(midi - 12)
            pad.add(held(f0, PIECE.seconds(4) - 0.1, rng, dark(saw, f0, 1100), attack=0.6, decay=1.0,
                         sustain=0.85, release=0.9, voices=3, detune_cents=12, spread=0.8,
                         vibrato_hz=4.2, vibrato_cents=6), bar * 4, gain=0.27)
    pad.audio = eq(pad.audio, highpass(100))
    loop.mix(pad)

    gongs = PIECE.loop(8)
    gongs.add(gong(rng), 0, pan=0.1)
    gongs.add(gong(rng, f0=69.3, seconds=3.5, velocity=0.6), 16, pan=-0.1)
    gongs.audio = eq(gongs.audio, highpass(45))
    loop.mix(gongs, 0.8)

    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.3
    return loop


def tom(f0, velocity, rng, seconds=0.38):
    """A tribal floor tom, struck with a beater: a deep, bending body."""
    return membrane(f0, seconds, rng, bend=1.35, bend_seconds=0.025, thump=0.35, thump_hz=1600, velocity=velocity)


def slap(velocity, rng):
    """A hand drum slapped at its rim: high and dry."""
    return membrane(410, 0.07, rng, bend=1.05, modes=(1.0, 1.7, 2.45), mode_levels=(1.0, 0.55, 0.3),
                    thump=0.9, thump_hz=7000, velocity=velocity)


def render_pulse():
    rng = PIECE.rng(2)
    loop = PIECE.loop(8)

    # Held a bar at a time, as the music's whole floor, under a muffled pluck that ticks off the beat.
    bass = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        f0 = hz(notes(root_name)[0] + 12)
        bass.add(held(f0, PIECE.seconds(4) - 0.05, rng, dark(saw, f0, 700), attack=0.01, decay=0.4, sustain=0.75,
                      release=0.1, voices=2, detune_cents=6, spread=0.2), bar * 4)
        for step in (3, 6, 11, 14):
            bass.add(pluck(f0 * 2, PIECE.seconds(STEP), rng, decay=0.3, damping=0.7, pick=0.3, tilt=1.6,
                           brightness_hz=1500, attack_noise=0.02, velocity=0.5), bar * 4 + step * STEP, pan=0.2)
    bass.audio = eq(bass.audio, highpass(40), peak(140, 2.5, 1.0), lowpass(2400))
    loop.mix(bass, 0.42)

    # A sub an octave under it, held: the floor the 808s will stand on. Dice of its own.
    sub = PIECE.loop(8)
    sub_rng = PIECE.rng(20)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        sub.add(eight08(hz(notes(root_name)[0]), PIECE.seconds(4) - 0.03, sub_rng, punch=2, decay=2.0,
                        drive=1.3, click=0), bar * 4)
    loop.mix(sub, 0.22)

    # Finger cymbals: damped on the beat, let ring off it, the way a temple band keeps its time.
    hands = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for beat in range(4):
            hands.add(ching(rng, open_=beat % 2 == 1, velocity=0.5 if beat % 2 else 0.35), base + beat + (0.5 if beat % 2 else 0),
                      pan=0.45)
        for step in range(16):
            hands.add(shaker(rng, velocity=0.42 if step % 4 == 3 else 0.22), base + step * STEP, pan=-0.4,
                      nudge_seconds=0.003 * rng.standard_normal())
        for step, velocity in ((4, 0.55), (12, 0.6)):
            hands.add(slap(velocity, rng), base + step * STEP, pan=-0.15)
    loop.mix(hands, 0.5)

    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.18
    return loop


def render_drive():
    rng = PIECE.rng(3)
    loop = PIECE.loop(8)

    kit = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step, velocity in LOW_TOM.items():
            kit.add(tom(98, velocity * (1 + 0.06 * rng.standard_normal()), rng), base + step * STEP, pan=-0.2,
                    nudge_seconds=0.003 * rng.standard_normal())
        for step, velocity in HIGH_TOM.items():
            kit.add(tom(165, velocity, rng, seconds=0.25), base + step * STEP, pan=0.25)
        for step, velocity in SLAP.items():
            kit.add(slap(velocity * 1.3, rng), base + step * STEP, pan=0.05)
        # The hand drum filling the sixteenths between, quiet, so the pattern never stops moving.
        for step in range(16):
            if step not in LOW_TOM and step not in HIGH_TOM and step not in SLAP:
                kit.add(slap(0.4 + 0.12 * (step % 2), rng), base + step * STEP, pan=0.35,
                        nudge_seconds=0.004 * rng.standard_normal())
        for step in (4, 12):
            kit.add(clap(rng, velocity=0.75), base + step * STEP, pan=-0.05)
        for step, velocity in ((0, 1.0), (8, 0.85), (10, 0.6)):
            kit.add(kick(rng, low=48, high=140, seconds=0.2, velocity=velocity), base + step * STEP)
        if bar == 7:
            for i, step in enumerate(range(8, 16)):
                kit.add(tom(98 + 9 * (7 - i), 0.55 + 0.06 * i, rng, seconds=0.2), base + step * STEP, pan=-0.3 + 0.08 * i)
    kit.add(cymbal(rng, seconds=1.5, velocity=0.4), 0, pan=0.3)
    kit.audio = eq(saturate(kit.audio, 1.4), peak(3200, 3.0, 1.0))
    loop.mix(kit, 0.95)

    # A drum machine's snare stacked on the claps, which is what carries the backbeat through a
    # phone's speaker - the toms are mostly under what one can play.
    snares = PIECE.loop(8)
    snare_rng = PIECE.rng(30)
    for bar in range(8):
        for step in (4, 12):
            snares.add(snare(snare_rng, tone_hz=240, seconds=0.12, velocity=0.85), bar * 4 + step * STEP, pan=-0.05)
    loop.mix(snares, 0.62)

    # A tambourine's jingles on the eighths, leaning on the off-beats with the hand drums.
    tambourine = PIECE.loop(8)
    for bar in range(8):
        for eighth in range(8):
            tambourine.add(jingles(snare_rng, seconds=0.07, velocity=0.6 if eighth % 2 else 0.35, count=7),
                           bar * 4 + eighth * 0.5, pan=0.5)
    loop.mix(tambourine, 0.3)

    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.2
    return loop


def render_lead():
    rng = PIECE.rng(4)
    loop = PIECE.loop(16)
    bars = PIECE.loop(16)
    previous = None
    for bar, phrase in enumerate(TUNE):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                slide = name.endswith("~")
                midi = notes(name.rstrip("~"))[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.95
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 and not slide else None
                loop.add(reed(midi, seconds, rng, slide=slide, glide_from=glide), bar * 4 + at * STEP,
                         pan=-0.1, nudge_seconds=0.006 * rng.standard_normal())
                # The xylophone doubles it an octave up, rolling its longer notes.
                strikes = max(1, sixteenths // 2) if sixteenths >= 4 else 1
                for k in range(strikes):
                    bars.add(ranat(midi + 12, 0.75 if k == 0 else 0.45, rng), bar * 4 + (at + 2 * k) * STEP, pan=0.3)
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {bar + 1} of the tune is {at} sixteenths long"
    loop.audio = eq(loop.audio, highpass(260), peak(1400, 2.0, 1.0))
    bars.audio = eq(bars.audio, highpass(400))
    loop.mix(bars, 0.32)
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(1.5), 0.3, 2600) * 0.2
    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.3
    return loop


def brass(midi, seconds, rng):
    """Brass stabbed short and loud: a bright saw ensemble, driven."""
    f0 = hz(midi)
    tone = held(f0, seconds, rng, dark(saw, f0, 1900), attack=0.015, decay=0.18, sustain=0.65, release=0.1,
                voices=3, detune_cents=8, spread=0.5, vibrato_hz=5, vibrato_cents=4)
    return saturate(tone, 1.4)


def render_fury():
    rng = PIECE.rng(5)
    loop = PIECE.loop(8)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step in range(16):
            velocity = (1.0 if step in LOW_TOM else 0.55) * (1 + 0.05 * rng.standard_normal())
            drums.add(tom(78 if step in LOW_TOM else 120, velocity, rng, seconds=0.5 if step in LOW_TOM else 0.25),
                      base + step * STEP, pan=-0.15 if step % 2 else 0.15)
    drums.add(gong(rng, f0=58.3, seconds=4.0), 0)
    drums.add(gong(rng, f0=61.7, seconds=3.0, velocity=0.7), 16)
    drums.audio = eq(saturate(drums.audio, 1.5), highpass(45))
    loop.mix(drums, 0.5)

    stabs = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for step, sixteenths in ((0, 3), (6, 2), (11, 3)):
            for midi in chord:
                stabs.add(brass(midi, PIECE.seconds(sixteenths * STEP) * 0.85, rng), bar * 4 + step * STEP, pan=0.25)
    stabs.audio = eq(stabs.audio, highpass(120), peak(1300, 2.5, 1.0), lowpass(5000))
    loop.mix(stabs, 0.34)

    choir = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for i, midi in enumerate(chord):
            f0 = hz(midi + 12)
            choir.add(held(f0, PIECE.seconds(4) - 0.1, rng, vowel(f0, AH if i != 1 else OH), attack=0.3,
                           decay=0.8, sustain=0.9, release=0.5, voices=4, detune_cents=14, spread=0.8,
                           vibrato_hz=5.4, vibrato_cents=14, vibrato_delay=0.2), bar * 4, gain=0.24)
    loop.mix(choir)

    growl = PIECE.loop(8)
    growl_rng = PIECE.rng(50)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 12
        for step, sixteenths in ((0, 3), (3, 3), (6, 2), (8, 3), (11, 3), (14, 2)):
            growl.add(eight08(hz(root), PIECE.seconds(sixteenths * STEP) - 0.01, growl_rng, decay=0.8, drive=5.0),
                      bar * 4 + step * STEP)
    growl.audio = eq(growl.audio, highpass(60), lowpass(2500))
    loop.mix(growl, 0.2)

    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.25
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
        # Long on one, popping an octave up on the "a" of two, back on the root with the toms, and
        # sliding into the next bar's.
        for step, sixteenths, interval, slides in ((0, 6, 0, False), (6, 2, 12, False), (8, 3, 0, False),
                                                   (11, 3, 0, False), (14, 2, 0, True)):
            target = hz(following) if slides and following != root else None
            bass.add(trap_bass(root + interval, PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target,
                               velocity=1.0 if step in (0, 8) else 0.85), bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(30), lowpass(3000))
    loop.mix(bass, 0.75)

    kicks = PIECE.loop(8)
    for bar in range(8):
        for step in (0, 8, 10):
            kicks.add(kick(rng, low=52, high=150, seconds=0.13, velocity=0.8 if step != 10 else 0.6), bar * 4 + step * STEP)
    loop.mix(kicks, 0.45)

    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.08
    return loop


def render_roll():
    rng = PIECE.rng(7)
    loop = PIECE.loop(4)

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
                accent = 0.55 if step % 4 == 3 else 0.32
                hats.add(trap_hat(rng, velocity=accent * (1 + 0.08 * rng.standard_normal())), base + step * STEP,
                         pan=0.4, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    loop.mix(hats, 5.8)

    for i in range(4):
        loop.add(snare(rng, tone_hz=220, velocity=0.35 + 0.05 * i), 14 + i * STEP, pan=-0.2, gain=2.4)
    for i in range(8):
        loop.add(snare(rng, tone_hz=220, seconds=0.1, velocity=0.5 + 0.05 * i), 15 + i * STEP / 2, pan=-0.2, gain=2.4)
    rising = swell(cymbal(rng, seconds=1.2, velocity=0.45))
    loop.add(rising, 16 - rising.shape[0] / PIECE.samples_per_beat, pan=0.3, gain=2.4)

    loop.audio = eq(loop.audio, highpass(150))
    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.15
    return loop


def whoop(low, high, at, length=4):
    """A whoop: a short low note, and a leap up to a long high one, scooped into and falling off."""
    return [(at, low, 1, OO), (at + 1, high, length, AH)]


def ululate(upper, lower, at, count=5, hold=3):
    """An ululation: the voice flicking between two notes a half step or so apart, then holding."""
    out = [(at + i, upper if i % 2 == 0 else lower, 1, OO) for i in range(count)]
    return out + [(at + count, lower if count % 2 == 0 else upper, hold, AH)]


# The voice, bar by bar: (sixteenth, note, length in sixteenths, vowel). Whoops on the odd bars,
# ululations on the half step on the even ones, each answered by a falling pair.
CHOPS = [
    whoop("G4", "C6", 0) + [(10, "Bb5", 2, OH), (12, "G5", 3, AH)],
    ululate("Db6", "C6", 0) + [(10, "G5", 2, OH), (13, "E5", 2, AH)],
    whoop("Ab4", "Db6", 0) + [(10, "C6", 2, OH), (12, "Ab5", 3, AH)],
    ululate("Db6", "Bb5", 0) + [(10, "F5", 2, OH), (13, "Db5", 2, AH)],
    whoop("E4", "C6", 0) + [(10, "E5", 2, OH), (12, "Ab5", 3, AH)],
    ululate("C6", "Ab5", 0) + [(10, "F5", 2, OH), (13, "C5", 2, AH)],
    whoop("Ab4", "Ab5", 0, length=2) + [(4, "Db6", 4, AH), (12, "C6", 2, OH), (14, "Bb5", 2, AH)],
    ululate("Db6", "C6", 0, count=7, hold=5) + [(14, "G5", 2, OH)],
]


def render_chop():
    rng = PIECE.rng(8)
    loop = PIECE.loop(8)
    for bar, phrase in enumerate(CHOPS):
        for step, name, sixteenths, formants in phrase:
            longer = sixteenths > 1
            loop.add(chop(hz(notes(name)[0]), PIECE.seconds(sixteenths * STEP) * 0.85, rng, formants,
                          into=OH if longer and formants is AH else None, scoop=180 if longer else 0,
                          fall=260 if longer else 0),
                     bar * 4 + step * STEP, pan=0.4 if bar % 2 else -0.35, gain=0.52)
    loop.audio = eq(loop.audio, highpass(260), peak(2600, 2.0, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.32, 2400) * 0.28
    loop.audio += reverb(loop.audio, temple(PIECE.rng(100))) * 0.3
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
