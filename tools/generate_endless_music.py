# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the endless stage's own music, as the eight stems the game layers.

    uv run tools/generate_endless_music.py

The endless stage is flown on and on through every kind of country, and its theme is the one piece
of music in the game written for it alone: a Mediterranean dance number, grand and a little
unhinged. A soprano sings wordless leaps over a choir, mandolins tremble through the chords, and a
four-on-the-floor house beat pounds under it all, with the trap beat rising beneath as in every
stage. The notes are this script's own.

B-flat minor, the relative of D-flat major, at 126 BPM in 4/4. The eight-bar progression climbs
through the four chords a minor key's anthem is made of, then turns the last two bars toward the
harmonic minor: the major V, F with its A natural, pulls back to the top.

    Bbm | Gb | Db | Ab | Bbm | Gb | Ebm | F

The layers, bottom up:

* bed   - a choir holding the chords, mandolins trembling through them in sixteenths, a low string.
* pulse - a bass pumping on the off-beats over a sub, a shaker, a tambourine.
* drive - the house kit: a kick on every beat, claps and a snare on the backbeat, open hats off the
          beat, and a tom fill into the top.
* lead  - the soprano, sixteen bars, leaping up an octave into her long notes, a harp under her.
* boom  - 808s on the roots, sliding into the next bar's.
* roll  - hats in sixteenths, rolling, and a snare roll into the top. Four bars.
* chop  - a choir chopped into stabs, answering the soprano between her phrases.
* fury  - for a boss or a streak gone supernova: strings driving in sixteenths, brass, timpani,
          the choir an octave up, gongs.
"""

import numpy as np

from musicsynth import (
    AH, OH, Piece, chop, clap, cymbal, dark, echo, eight08, eq, hat, held, highpass, hz, jingles,
    kick, lowpass, membrane, notes, peak, pluck, reverb, reverb_ir, saturate, saw, shaker, snare,
    trap_hat, vowel, write_stems,
)

PIECE = Piece("endless", bpm=126, beats_per_bar=4, seed=20261012)

# One sixteenth, in beats.
STEP = 1 / 4

# The choir's chord low to high, and the bass's root.
PROGRESSION = [
    ("Bbm", notes("Bb3 Db4 F4"), "Bb1"),
    ("Gb", notes("Bb3 Db4 Gb4"), "Gb1"),
    ("Db", notes("Ab3 Db4 F4"), "Db2"),
    ("Ab", notes("Ab3 C4 Eb4"), "Ab1"),
    ("Bbm", notes("Bb3 Db4 F4"), "Bb1"),
    ("Gb", notes("Bb3 Db4 Gb4"), "Gb1"),
    ("Ebm", notes("Bb3 Eb4 Gb4"), "Eb2"),
    ("F", notes("A3 C4 F4"), "F1"),
]

# The soprano, bar by bar: (note, length in sixteenths), None for a rest. A trailing "^" leaps up
# into the note from the octave below, the way she takes her long notes; a trailing "~" scoops into
# it from a half step below.
TUNE = [
    [("F5^", 8), ("Db5", 4), ("Eb5", 4)],
    [("Db5", 6), ("Bb4", 2), ("Db5", 4), ("Gb5", 4)],
    [("F5", 8), ("Ab5", 4), ("F5", 4)],
    [("Eb5~", 12), (None, 4)],
    [("Bb5^", 8), ("Ab5", 2), ("Gb5", 2), ("F5", 4)],
    [("Gb5", 6), ("F5", 2), ("Db5", 8)],
    [("Eb5", 4), ("Gb5", 4), ("Bb5", 4), ("Db6", 4)],
    [("C6", 8), ("A5~", 8)],
    [("Db6^", 8), ("C6", 4), ("Bb5", 4)],
    [("Bb5", 6), ("Ab5", 2), ("Gb5", 4), ("Bb5", 4)],
    [("Ab5", 8), ("F5", 4), ("Ab5", 4)],
    [("C6", 8), ("Eb6~", 8)],
    [("F6^", 8), ("Eb6", 4), ("Db6", 4)],
    [("Db6", 6), ("Bb5", 2), ("Gb5", 8)],
    [("Gb5", 4), ("F5", 4), ("Eb5", 4), ("Gb5", 4)],
    [("A5^", 8), ("C6", 4), ("F5", 4)],
]

# The mandolins' turn through each chord, in sixteenths: which of its tones, up from the choir's
# octave, so the figure climbs and falls once a bar.
TREMOLO = [0, 1, 2, 3, 2, 1, 2, 3, 0, 1, 2, 3, 2, 3, 2, 1]


def palazzo(rng) -> np.ndarray:
    """A marble hall: a long, bright tail over a few strong reflections."""
    return reverb_ir(3.2, rng, predelay=0.035, brightness=0.7, early=7, early_spread=0.1)


# An open soprano "ah", its formants set higher than a speaking voice's, so the top of her range
# rings rather than hoots.
SOPRANO = ((920, 150, 1.0), (1350, 170, 0.5), (2900, 260, 0.32), (3900, 320, 0.14))


def soprano(midi, seconds, rng, leap=False, slide=False, glide_from=None, velocity=0.8):
    """A soprano singing out: a wide, late vibrato, and the long notes taken from below."""
    f0 = hz(midi)
    start = hz(midi - 12) if leap else hz(midi - 1) if slide else glide_from
    glide = 0.2 if leap else 0.09 if slide else 0.05
    return held(f0, seconds, rng, vowel(f0, SOPRANO), attack=0.05, decay=0.35, sustain=0.88, release=0.2,
                vibrato_hz=5.6, vibrato_cents=38 if seconds > 0.45 else 10, vibrato_delay=0.22,
                glide_from=start, glide=glide, voices=2, detune_cents=5, spread=0.15, breath=0.05,
                breath_band=(1500, 6000), velocity=velocity)


def mandolin(midi, velocity, rng):
    """A mandolin's doubled course picked once: bright, short, and a little out of tune with itself."""
    return pluck(hz(midi), 0.32, rng, decay=0.28, damping=0.55, pick=0.11, tilt=1.25, course_cents=7,
                 brightness_hz=5200, attack_noise=0.05, velocity=velocity)


def harp(midi, velocity, rng):
    """A harp string: round and ringing."""
    return pluck(hz(midi), 1.4, rng, decay=0.9, damping=0.35, pick=0.45, tilt=1.9, brightness_hz=3000,
                 attack_noise=0.0, velocity=velocity)


def choir_chord(loop, rng, octave=0, gain=0.22, vowels=(AH, OH, AH)):
    """The choir holding each bar's chord, an ensemble of voices to each tone."""
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for i, midi in enumerate(chord):
            f0 = hz(midi + octave)
            loop.add(held(f0, PIECE.seconds(4) - 0.08, rng, vowel(f0, vowels[i % len(vowels)]), attack=0.35,
                          decay=0.8, sustain=0.9, release=0.45, voices=4, detune_cents=13, spread=0.8,
                          vibrato_hz=5.2, vibrato_cents=12, vibrato_delay=0.25), bar * 4, gain=gain)


def render_bed():
    rng = PIECE.rng(1)
    loop = PIECE.loop(8)

    choir = PIECE.loop(8)
    choir_chord(choir, rng)
    choir.audio = eq(choir.audio, highpass(140), peak(1100, 1.5, 1.0))
    loop.mix(choir, 1.7)

    strings = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        f0 = hz(notes(root_name)[0] + 12)
        strings.add(held(f0, PIECE.seconds(4) - 0.05, rng, dark(saw, f0, 900), attack=0.25, decay=0.6,
                         sustain=0.85, release=0.5, voices=3, detune_cents=9, spread=0.5,
                         vibrato_hz=4.8, vibrato_cents=7), bar * 4, gain=0.55)
    strings.audio = eq(strings.audio, highpass(55))
    loop.mix(strings)

    mandolins = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        tones = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[0] + 24]
        for step, tone in enumerate(TREMOLO):
            velocity = (0.75 if step % 4 == 0 else 0.5) * (1 + 0.08 * rng.standard_normal())
            mandolins.add(mandolin(tones[tone], velocity, rng), bar * 4 + step * STEP,
                          pan=0.35 if step % 2 else -0.3, nudge_seconds=0.004 * rng.standard_normal())
    mandolins.audio = eq(mandolins.audio, highpass(300), peak(2600, 2.0, 1.0))
    loop.mix(mandolins, 1.05)

    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.32
    return loop


def render_pulse():
    rng = PIECE.rng(2)
    loop = PIECE.loop(8)

    # The house bass, pumping on the off-beats: short, so the kick has the downbeat to itself.
    bass = PIECE.loop(8)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        f0 = hz(notes(root_name)[0] + 12)
        for step in (2, 6, 10, 14):
            bass.add(held(f0, PIECE.seconds(STEP * 1.6), rng, dark(saw, f0, 1200), attack=0.004, decay=0.12,
                          sustain=0.6, release=0.05, voices=2, detune_cents=7, spread=0.2),
                     bar * 4 + step * STEP)
        # A pick an octave up on the last sixteenth, leading into the next beat.
        bass.add(pluck(f0 * 2, PIECE.seconds(STEP), rng, decay=0.25, damping=0.7, pick=0.3, tilt=1.6,
                       brightness_hz=1800, velocity=0.45), bar * 4 + 15 * STEP, pan=0.2)
    bass.audio = eq(bass.audio, highpass(40), peak(160, 2.5, 1.0), lowpass(2600))
    loop.mix(bass, 0.48)

    sub = PIECE.loop(8)
    sub_rng = PIECE.rng(20)
    for bar, (_, _, root_name) in enumerate(PROGRESSION):
        sub.add(eight08(hz(notes(root_name)[0]), PIECE.seconds(4) - 0.03, sub_rng, punch=2, decay=2.0,
                        drive=1.3, click=0), bar * 4)
    loop.mix(sub, 0.2)

    hands = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step in range(16):
            hands.add(shaker(rng, velocity=0.45 if step % 4 == 2 else 0.22), base + step * STEP, pan=-0.4,
                      nudge_seconds=0.003 * rng.standard_normal())
        for eighth in range(8):
            hands.add(jingles(rng, seconds=0.08, velocity=0.6 if eighth % 2 else 0.3, count=7),
                      base + eighth * 0.5, pan=0.45)
    loop.mix(hands, 0.42)

    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.14
    return loop


def tom(f0, velocity, rng, seconds=0.3):
    """A concert tom: a deep, bending body."""
    return membrane(f0, seconds, rng, bend=1.3, bend_seconds=0.02, thump=0.3, thump_hz=1800, velocity=velocity)


def render_drive():
    rng = PIECE.rng(3)
    loop = PIECE.loop(8)

    kit = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        # Four on the floor.
        for beat in range(4):
            kit.add(kick(rng, low=50, high=160, seconds=0.26, velocity=1.0 if beat % 2 == 0 else 0.92),
                    base + beat)
        for step in (4, 12):
            kit.add(clap(rng, velocity=0.85), base + step * STEP, pan=-0.05)
        # Open hats on the off-beats, ticking closed ones between.
        for step in range(16):
            if step % 4 == 2:
                kit.add(hat(rng, open_=True, velocity=0.62), base + step * STEP, pan=0.3)
            elif step % 2 == 1:
                kit.add(hat(rng, velocity=0.3 + 0.08 * (step % 4 == 3)), base + step * STEP, pan=0.4,
                        nudge_seconds=0.002 * rng.standard_normal())
        if bar == 7:
            for i, step in enumerate(range(8, 16)):
                kit.add(tom(150 - 9 * i, 0.55 + 0.05 * i, rng), base + step * STEP, pan=0.3 - 0.08 * i)
    kit.add(cymbal(rng, seconds=1.6, velocity=0.45), 0, pan=-0.3)
    kit.audio = eq(saturate(kit.audio, 1.3), peak(3500, 2.5, 1.0))
    loop.mix(kit, 0.76)

    # A drum machine's snare stacked on the claps, which carries the backbeat through a phone.
    snares = PIECE.loop(8)
    snare_rng = PIECE.rng(30)
    for bar in range(8):
        for step in (4, 12):
            snares.add(snare(snare_rng, tone_hz=230, seconds=0.13, velocity=0.85), bar * 4 + step * STEP, pan=-0.05)
    loop.mix(snares, 0.48)

    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.16
    return loop


def render_lead():
    rng = PIECE.rng(4)
    loop = PIECE.loop(16)
    strings = PIECE.loop(16)
    previous = None
    for bar, phrase in enumerate(TUNE):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                leap = name.endswith("^")
                slide = name.endswith("~")
                midi = notes(name.rstrip("^~"))[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.96
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 and not (leap or slide) else None
                loop.add(soprano(midi, seconds, rng, leap=leap, slide=slide, glide_from=glide), bar * 4 + at * STEP,
                         pan=0.05, nudge_seconds=0.005 * rng.standard_normal())
                # The harp an octave under her, struck on each note and again through the long ones,
                # so the tune still reads on a phone's speaker.
                strikes = max(1, sixteenths // 4)
                for k in range(strikes):
                    strings.add(harp(midi - 12, 0.7 if k == 0 else 0.45, rng), bar * 4 + (at + 4 * k) * STEP, pan=-0.3)
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {bar + 1} of the tune is {at} sixteenths long"
    loop.audio = eq(loop.audio, highpass(300), peak(2900, 2.0, 1.0))
    strings.audio = eq(strings.audio, highpass(180))
    loop.mix(strings, 0.38)
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(1.5), 0.3, 3200) * 0.2
    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.38
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
        for step, sixteenths, interval, slides in ((0, 6, 0, False), (6, 2, 12, False), (8, 4, 0, False),
                                                   (12, 2, 7, False), (14, 2, 0, True)):
            target = hz(following) if slides and following != root else None
            bass.add(trap_bass(root + interval, PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target,
                               velocity=1.0 if step in (0, 8) else 0.85), bar * 4 + step * STEP)
    bass.audio = eq(bass.audio, highpass(30), lowpass(3000))
    loop.mix(bass, 0.6)

    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.06
    return loop


def render_roll():
    rng = PIECE.rng(7)
    loop = PIECE.loop(4)

    hats = PIECE.loop(4)
    rolls = {(1, 12): (8, 1.0), (2, 4): (6, 1.0), (3, 8): (12, 1.4)}
    for bar in range(4):
        base = bar * 4
        step = 0
        while step < 16:
            roll = rolls.get((bar, step))
            if roll:
                count, climb = roll
                span = 8 if count > 8 else 4
                for i in range(count):
                    along = i / (count - 1)
                    hats.add(trap_hat(rng, pitch=1 + (climb - 1) * along, velocity=0.3 + 0.45 * along),
                             base + (step + span * i / count) * STEP, pan=0.4)
                step += span
                continue
            accent = 0.55 if step % 4 == 2 else 0.3
            hats.add(trap_hat(rng, velocity=accent * (1 + 0.08 * rng.standard_normal())), base + step * STEP,
                     pan=0.4, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    loop.mix(hats, 5.6)

    # A snare roll into the top of the loop, swelling over its last two beats.
    rolls_ = PIECE.loop(4)
    snare_rng = PIECE.rng(70)
    for i in range(16):
        along = i / 15
        rolls_.add(snare(snare_rng, tone_hz=250, seconds=0.07, velocity=0.25 + 0.6 * along),
                   14 + 2 * i / 16, pan=-0.1)
    loop.mix(rolls_, 0.6)

    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.08
    return loop


# The chopped choir, bar by bar: (sixteenth, which chord tone, an octave up or not, length in
# sixteenths, vowel), placed in the gaps between the soprano's phrases.
STABS = [(3, 2, 12, 1, AH), (6, 0, 12, 2, OH), (10, 1, 12, 1, AH), (11, 2, 12, 1, AH), (14, 0, 24, 2, OH)]


def render_chop():
    rng = PIECE.rng(8)
    loop = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for step, tone, octave, sixteenths, formants in STABS:
            if bar % 4 == 3 and step >= 10:
                continue  # Room for the soprano's long notes at the end of each phrase.
            longer = sixteenths > 1
            loop.add(chop(hz(chord[tone] + octave), PIECE.seconds(sixteenths * STEP) * 0.8, rng, formants,
                          into=OH if longer and formants is AH else None, scoop=140 if longer else 0,
                          fall=220 if longer else 0),
                     bar * 4 + step * STEP, pan=0.4 if step % 2 else -0.35, gain=0.7)
    loop.audio = eq(loop.audio, highpass(260), peak(2600, 2.0, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(0.5), 0.3, 2600) * 0.26
    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.3
    return loop


def brass(midi, seconds, rng):
    """Brass stabbed short and loud: a bright saw ensemble, driven."""
    f0 = hz(midi)
    tone = held(f0, seconds, rng, dark(saw, f0, 2000), attack=0.012, decay=0.18, sustain=0.65, release=0.1,
                voices=3, detune_cents=8, spread=0.5, vibrato_hz=5, vibrato_cents=4)
    return saturate(tone, 1.4)


def render_fury():
    rng = PIECE.rng(5)
    loop = PIECE.loop(8)

    # The strings driving in sixteenths on the root and its fifth, the engine of the whole fury.
    strings = PIECE.loop(8)
    for bar, (_, chord, root_name) in enumerate(PROGRESSION):
        root = notes(root_name)[0] + 24
        for step in range(16):
            midi = root + (7 if step % 4 == 2 else 12 if step % 8 == 7 else 0)
            f0 = hz(midi)
            strings.add(held(f0, PIECE.seconds(STEP) * 0.7, rng, dark(saw, f0, 2600), attack=0.006, decay=0.06,
                             sustain=0.5, release=0.04, voices=3, detune_cents=10, spread=0.6),
                        bar * 4 + step * STEP, gain=0.9 if step % 4 == 0 else 0.6)
    strings.audio = eq(strings.audio, highpass(110), peak(1800, 2.0, 1.0))
    loop.mix(strings, 0.3)

    stabs = PIECE.loop(8)
    for bar, (_, chord, _) in enumerate(PROGRESSION):
        for step, sixteenths in ((0, 3), (3, 3), (6, 2), (10, 2), (12, 4)):
            for midi in chord:
                stabs.add(brass(midi, PIECE.seconds(sixteenths * STEP) * 0.85, rng), bar * 4 + step * STEP, pan=0.25)
    stabs.audio = eq(stabs.audio, highpass(120), peak(1300, 2.5, 1.0), lowpass(5200))
    loop.mix(stabs, 0.3)

    choir = PIECE.loop(8)
    choir_chord(choir, rng, octave=12, gain=0.2)
    loop.mix(choir)

    drums = PIECE.loop(8)
    for bar in range(8):
        base = bar * 4
        for step in (0, 3, 6, 8, 11, 14):
            drums.add(tom(70 if step in (0, 8) else 95, 1.0 if step in (0, 8) else 0.75, rng, seconds=0.55),
                      base + step * STEP, pan=-0.15 if step % 2 else 0.15)
    drums.add(cymbal(rng, seconds=2.4, velocity=0.6), 0, pan=0.3)
    drums.add(cymbal(rng, seconds=2.0, velocity=0.45), 16, pan=-0.3)
    drums.audio = eq(saturate(drums.audio, 1.5), highpass(45))
    loop.mix(drums, 0.5)

    loop.audio += reverb(loop.audio, palazzo(PIECE.rng(100))) * 0.24
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
