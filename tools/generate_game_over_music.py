# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the game over's music: a lament the game plays once, from the moment the bat falls.

    uv run tools/generate_game_over_music.py

The run is over and the bat goes down to the underworld. The model is the sound of the ancient world
as a film would score it now - a double pipe keening over a lyre, bronze gongs, a choir of low men's
voices - with dark electronics snarling under it and a trap beat's 808s and hi-hats, all played by
a 16-bit console. The notes are this script's own.

C Phrygian at 60 BPM in 4/4: the minor mode with a flat second, the half step over the tonic that
makes a lament. The choir moves from an open fifth to the minor tonic, falls onto the flat second,
reaches down to the minor seventh, and comes home by the Phrygian cadence, the flat II stepping down
onto I:

    C5 | Cm | Db | Bbm | Db C | C5

The first bar leaves the middle of the range open, because the death sound is falling through it.
Then the pipe begins, the beat comes in under it and thickens as the lament climbs to its cry, and
drops out at the cadence, leaving the voices to resolve. A last gong rings the track out. It is
rendered with two bars of room past its end and cut once that gong has died away.
"""

import numpy as np

from musicsynth import (
    AH, OH, OO, RENDER_RATE, TOP_HZ, Piece, clap, cymbal, dark, echo, eight08, eq, held, highpass, hz,
    lowpass, noise, notes, one_shot_filter, peak, pluck, reverb, reverb_ir, saw, seconds_to_samples,
    swell, trap_hat, vowel, write_track,
)

PIECE = Piece("game_over", bpm=60, beats_per_bar=4, seed=20261003)

# One sixteenth, in beats.
STEP = 1 / 4

# Six bars of music and two of room, so that nothing rings past the end of the loop it is rendered
# in and round onto its first beat.
BARS = 8

# Where the track is cut: two bars after the last gong, which has died away by then.
END_BEAT = 28

# The choir, bar by bar: (beat, notes, vowel, beats held).
CHOIR = [
    (0, "C2 G2", OO, 4),
    (4, "C2 G2 Eb3", OH, 4),
    (8, "Db2 Ab2 F3", OH, 4),
    (12, "Bb1 F2 Db3", AH, 4),
    (16, "Db2 Ab2 F3", AH, 2),
    (18, "C2 G2 Eb3", OH, 2),
    (20, "C2 G2 C3", OO, 3),
]

# The lyre's eighths, by bar from the second: each bar's chord broken up and back down.
LYRE = [
    notes("C3 G3 C4 Eb4 G4 Eb4 C4 G3"),
    notes("Db3 Ab3 Db4 F4 Ab4 F4 Db4 Ab3"),
    notes("Bb2 F3 Bb3 Db4 F4 Db4 Bb3 F3"),
    notes("Db3 Ab3 Db4 F4 C3 G3 C4 G4"),
]

# The bass under it all, bar by bar from the first: the root, and where the last 808 slides to.
ROOTS = ["C2", "C2", "Db2", "Bb1", "Db2"]

# The pipe, bar by bar from the second: (note, length in sixteenths), None for a rest. A trailing "~"
# bends the note up from a half step below; a note a step or less from the one before glides from it.
LAMENT = [
    [(None, 2), ("G4~", 6), ("Ab4", 2), ("G4", 2), ("F4", 2), ("Eb4", 2)],
    [("F4~", 6), ("Eb4", 2), ("Db4", 4), ("C4", 2), ("Db4", 2)],
    [("F4", 2), ("Bb4~", 6), ("C5", 2), ("Db5~", 6)],
    [("C5", 4), ("Bb4", 2), ("Ab4", 2), ("G4~", 6), ("F4", 2)],
    [("Eb4", 2), ("Db4", 2), ("C4", 12)],
]

# The snarling electronics' sixteenths: 3+3+2, twice, the stabs that keep the lament uneasy.
STABS = (0, 3, 6, 8, 11, 14)


def underworld(rng) -> np.ndarray:
    """A vast dark hall of stone: long, dull at the top, echoing off walls a long way away."""
    return reverb_ir(3.0, rng, predelay=0.045, brightness=0.3, early=8, early_spread=0.11)


def gong(f0, rng, *, seconds=2.4, velocity=1.0):
    """A bronze gong: a low hum under a crowd of clashing partials, the higher ones blooming a moment
    after the strike, the way a gong's sound swells before it fades. Mono."""
    n = seconds_to_samples(seconds * 5)
    t = np.arange(n) / RENDER_RATE
    out = np.zeros(n)
    ratios = (1.0, 1.52, 2.0, 2.44, 2.98, 3.53, 4.08, 4.65, 5.26, 5.86, 6.51, 7.13, 8.56, 10.1, 12.2,
              14.9, 18.3, 22.7, 28.1, 34.9, 43.2, 53.6)
    for i, ratio in enumerate(ratios):
        f = f0 * ratio * (1 + 0.004 * (rng.random() - 0.5))
        if f >= TOP_HZ:
            break
        bloom = 0.015 + 0.035 * i
        env = (1 - np.exp(-t / bloom)) * np.exp(-t / (seconds / (1 + 0.12 * i)))
        beat = 1 + 0.0015 * (rng.random() - 0.5)
        ring = np.sin(2 * np.pi * f * t + rng.random() * 6.28) + 0.7 * np.sin(2 * np.pi * f * beat * t + rng.random() * 6.28)
        out += ring * env / (1 + 0.3 * i)
    # The padded mallet landing, before the metal has started to sing.
    k = seconds_to_samples(0.25)
    thud = one_shot_filter(noise(k, rng), lowpass(350)) * np.exp(-np.arange(k) / seconds_to_samples(0.05))
    out[:k] += 0.5 * np.max(np.abs(out)) * thud / max(np.max(np.abs(thud)), 1e-9)
    return out / max(np.max(np.abs(out)), 1e-9) * velocity


def choir(midi, seconds, rng, formants):
    """Low men's voices, several to a part, so the part is a crowd rather than a singer."""
    f0 = hz(midi)
    return held(f0, seconds, rng, vowel(f0, formants), attack=0.9, decay=1.0, sustain=0.9, release=1.6,
                voices=4, detune_cents=11, spread=0.75, vibrato_hz=4.6, vibrato_cents=10, vibrato_delay=0.4)


def pipe(midi, seconds, rng, bend=False, glide_from=None, velocity=1.0):
    """One reed of the double pipe: nasal and buzzing, loud in the middle of its range, its two
    tongues never quite together."""
    f0 = hz(midi)

    def shape(k):
        f = k * f0
        return (1 / k ** 0.55) * (1 + 1.8 * np.exp(-((f - 1400) / 600) ** 2)) / (1 + (f / 4200) ** 2)

    start = hz(midi - 1) if bend else glide_from
    return held(f0, seconds, rng, shape, attack=0.05, decay=0.3, sustain=0.85, release=0.18,
                vibrato_hz=5.4, vibrato_cents=18 if seconds > 0.6 else 6, vibrato_delay=0.35,
                glide_from=start, glide=0.14 if bend else 0.05, breath=0.08, breath_band=(1500, 5000),
                voices=2, detune_cents=6, spread=0.25, velocity=velocity)


def lyre(midi, velocity, rng):
    """Gut strings plucked by hand: a harp's round tone, dying away in a couple of seconds."""
    f0 = hz(midi)
    return pluck(f0, 2.6, rng, decay=1.5 * (262 / f0) ** 0.3, damping=0.3, pick=0.22, tilt=1.1,
                 brightness_hz=5500, attack_noise=0.05, velocity=velocity)


def snarl(midi, seconds, rng, velocity):
    """The electronics: a saw bass struck short and driven until it snarls. Stereo."""
    f0 = hz(midi)
    tone = held(f0, seconds, rng, dark(saw, f0, 1600), attack=0.002, decay=0.07, sustain=0.4, release=0.03,
                voices=2, detune_cents=9, spread=0.35)
    return np.tanh(3.5 * tone) / np.tanh(3.5) * velocity


# --- Parts ---------------------------------------------------------------------------------------


def render_choir(rng):
    loop = PIECE.loop(BARS)
    for beat, names, formants, beats in CHOIR:
        for midi in notes(names):
            loop.add(choir(midi, PIECE.seconds(beats) - 0.05, rng, formants), beat, gain=0.12)
    # The voices' own fundamentals are left to the 808: a bass singer's lowest notes are heard by
    # their overtones anyway.
    loop.audio = eq(loop.audio, highpass(95), peak(300, 2.0, 1.0), lowpass(3500))
    return loop


def render_pipes(rng):
    loop = PIECE.loop(BARS)
    previous = None
    for bar, phrase in enumerate(LAMENT, start=1):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                bend = name.endswith("~")
                midi = notes(name.rstrip("~"))[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.96
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 and not bend else None
                loop.add(pipe(midi, seconds, rng, bend=bend, glide_from=glide), bar * 4 + at * STEP, pan=-0.1,
                         nudge_seconds=0.006 * rng.standard_normal())
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {bar + 1} of the lament is {at} sixteenths long"
    # The second pipe holds the tonic under the tune, the way the double pipe's drone reed does.
    loop.add(pipe(notes("C4")[0], PIECE.seconds(16) - 0.3, rng, velocity=0.3), 4.5, pan=0.2)
    loop.audio = eq(loop.audio, highpass(200), peak(1400, 1.5, 1.0))
    loop.audio += echo(loop.audio, PIECE.seconds(0.75), PIECE.seconds(1.0), 0.25, 2000) * 0.2
    return loop


def render_lyre(rng):
    loop = PIECE.loop(BARS)
    for bar, tones in enumerate(LYRE, start=1):
        for eighth, midi in enumerate(tones):
            velocity = (0.9 if eighth % 4 == 0 else 0.7) * (1 + 0.08 * rng.standard_normal())
            loop.add(lyre(midi, velocity, rng), bar * 4 + eighth * 0.5, pan=0.35,
                     nudge_seconds=0.005 * rng.standard_normal())
    # The last chord, strummed low to high as the final gong lands.
    for i, midi in enumerate(notes("C3 G3 C4 G4 C5")):
        loop.add(lyre(midi, 0.85, rng), 20, pan=0.35, nudge_seconds=0.03 * i)
    loop.audio = eq(loop.audio, highpass(110), peak(2500, 2.0, 1.0))
    return loop


def render_gongs(rng):
    loop = PIECE.loop(BARS)
    for beat, velocity, seconds in ((0, 1.0, 2.6), (12, 0.55, 2.0), (20, 0.95, 1.5)):
        loop.add(gong(hz(notes("C2")[0]), rng, seconds=seconds, velocity=velocity), beat, pan=-0.15)
    rising = swell(cymbal(rng, seconds=1.6, velocity=0.45))
    loop.add(rising, 20 - rising.shape[0] / PIECE.samples_per_beat, pan=0.3)
    loop.audio = eq(loop.audio, highpass(70), lowpass(6000))
    return loop


def render_808s(rng):
    loop = PIECE.loop(BARS)
    loop.add(eight08(hz(notes("C2")[0]), PIECE.seconds(3.8), rng, decay=2.2, drive=3.0, punch=10), 0)
    for bar in range(1, 5):
        root = notes(ROOTS[bar])[0]
        following = notes(ROOTS[bar + 1])[0] if bar + 1 < len(ROOTS) else root
        if bar == 4:
            # The cadence: the flat second held, slid down onto the tonic, and carried on there
            # without a fresh thump.
            tonic = hz(notes("C2")[0])
            loop.add(eight08(hz(root), PIECE.seconds(2), rng, slide_to=tonic, slide=0.3, decay=1.8, drive=3.0),
                     bar * 4)
            loop.add(eight08(tonic, PIECE.seconds(2) - 0.05, rng, punch=0, click=0, decay=1.2, drive=3.0,
                             velocity=0.6), bar * 4 + 2)
            continue
        for step, sixteenths, slides in ((0, 6, False), (6, 2, False), (8, 4, False), (12, 4, True)):
            target = hz(following) if slides and following != root else None
            loop.add(eight08(hz(root), PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target, slide=0.2,
                             decay=1.5, drive=3.0, velocity=1.0 if step == 0 else 0.8), bar * 4 + step * STEP)
    loop.add(eight08(hz(notes("C2")[0]), PIECE.seconds(2.5), rng, decay=0.9, drive=3.0, punch=10), 20)
    loop.audio = eq(loop.audio, highpass(30), lowpass(3000))
    return loop


def render_snarl(rng):
    loop = PIECE.loop(BARS)
    # Creeping in under the first bar's second half, then stabbing 3+3+2 until the cadence.
    for i, step in enumerate(range(8, 16)):
        loop.add(snarl(notes("C3")[0], PIECE.seconds(STEP) * 0.7, rng, 0.1 + 0.05 * i), step * STEP, pan=-0.3)
    for bar in range(1, 5):
        root = notes(ROOTS[bar])[0] + 12
        for step in range(16):
            if bar == 4 and step >= 8:
                break
            accented = step in STABS
            velocity = (0.9 if accented else 0.3) * (1 + 0.06 * rng.standard_normal())
            loop.add(snarl(root, PIECE.seconds(STEP) * (0.8 if accented else 0.45), rng, velocity),
                     bar * 4 + step * STEP, pan=-0.3 if step % 2 else 0.3)
    loop.audio = eq(loop.audio, highpass(90), peak(900, 2.0, 1.0), lowpass(3200))
    return loop


def render_kit(rng):
    loop = PIECE.loop(BARS)
    for bar in range(1, 5):
        base = bar * 4
        last = 8 if bar == 4 else 16
        # Eighths in the first bar of the beat, sixteenths once the lament climbs.
        per_beat = 2 if bar == 1 else 4
        rolls = {2: (12, 8, "32"), 3: (8, 6, "3")}.get(bar)
        step = 0
        while step < last:
            if rolls and step == rolls[0]:
                _, count, kind = rolls
                span = 4
                for i in range(count):
                    along = i / (count - 1)
                    loop.add(trap_hat(rng, pitch=1 + 0.2 * along, velocity=0.3 + 0.35 * along),
                             base + (step + span * i / count) * STEP, pan=0.4)
                step += span
                continue
            if step % (4 // per_beat) == 0:
                open_ = bar == 3 and step == 14
                accent = 0.55 if step % 4 == 0 else 0.4
                loop.add(trap_hat(rng, open_=open_, velocity=accent * (1 + 0.08 * rng.standard_normal())),
                         base + step * STEP, pan=0.4, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
        if bar < 4:
            loop.add(clap(rng, velocity=0.9), base + 2, pan=-0.05)
        if bar == 3:
            loop.add(clap(rng, velocity=0.4), base + 15 * STEP, pan=-0.05)
    loop.audio = eq(loop.audio, highpass(70))
    return loop


def render():
    loop = PIECE.loop(BARS)
    for part, gain in ((render_choir(PIECE.rng(1)), 1.0), (render_pipes(PIECE.rng(2)), 0.42),
                       (render_lyre(PIECE.rng(3)), 0.38), (render_gongs(PIECE.rng(4)), 0.55)):
        loop.mix(part, gain)
    loop.audio += reverb(loop.audio, underworld(PIECE.rng(100))) * 0.4

    dry = PIECE.loop(BARS)
    for part, gain in ((render_808s(PIECE.rng(5)), 0.32), (render_snarl(PIECE.rng(6)), 0.22),
                       (render_kit(PIECE.rng(7)), 0.45)):
        dry.mix(part, gain)
    dry.audio += reverb(dry.audio, underworld(PIECE.rng(100))) * 0.14
    loop.audio += dry.audio
    return loop


def main() -> int:
    write_track(PIECE, render(), loudness_db=-15.0, end_beat=END_BEAT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
