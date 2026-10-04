# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the victory's music: played once, as a stage's boss goes up in flames.

    uv run tools/generate_victory_music.py

Not a fanfare blown at the screen so much as the light breaking through. It opens in the air: a
choir and a pad climbing three chords, a glockenspiel running up a stair of arpeggios, a snare
roll and a cymbal swelling backwards into the drop. Then the drop: the choir opens to a major
chord, the 808s and a half-time trap beat come in, a synth pumps the chords, and a lead sings over
it all the way to the last chord. All of it played by a 16-bit console.

B at 120 BPM in 4/4. The climb is Phrygian - the minor tonic, the flat II a half step over it, the
flat VI - and the drop lands not on the minor chord the climb set up but on the major one, the
flat VI's D rising to a D#. After it the cadence so many video game victories make, from the flat
VI through the flat VII to the tonic:

    Bm C G | B . . . | G A | B

The track starts on its first chord. The drop lands at [LANDING_BEAT], three seconds in, which is the
moment the game puts its stage complete overlay up (STAGE_COMPLETE_DELAY_SECONDS): the music says
the stage is won as the screen does. The beat plays on under the overlay to the last chord at
[FINAL_BEAT], which rings out, and the track is rendered with room past its end and cut at
[END_BEAT] once it has died away.
"""

import numpy as np

from musicsynth import (
    AH, OO, Piece, clap, cymbal, dark, echo, eight08, eq, held, highpass, hz, kick, lowpass, mallet, notes,
    peak, pulse, reverb, reverb_ir, saw, snare, swell, trap_hat, vowel, write_track,
)

PIECE = Piece("victory", bpm=120, beats_per_bar=4, seed=20261002)

# Eight bars: four and a half of music and the rest room, so that nothing rings past the end of the
# loop it is rendered in and round onto its first beat.
BARS = 8

# Where the drop lands: the beat the game's overlay is timed to.
LANDING_BEAT = 6

# Where the last chord lands, two bars after the drop.
FINAL_BEAT = 14

# Where the track is cut: once the last chord has rung out.
END_BEAT = 22

# The chords, (beat, beats held, the choir's voicing, the bass's root). The climb has no bass; it
# arrives with the drop.
CLIMB = [
    (0, 2, "B2 F#3 B3 D4 F#4", None),
    (2, 2, "B2 G3 C4 E4 G4", None),
    (4, 2, "B2 G3 B3 D4 G4", None),
]
DROP = [
    (LANDING_BEAT, 4, "B2 F#3 B3 D#4 F#4 B4", "B1"),
    (10, 2, "G2 G3 B3 D4 G4", "G1"),
    (12, 2, "A2 A3 C#4 E4 A4", "A1"),
    (FINAL_BEAT, 6, "B2 F#3 B3 D#4 F#4 B4", "B1"),
]

# The synth's chords, pumped in a three-three-two of sixteenths: (beat, beats, voicing).
STABS = [(LANDING_BEAT, 4, "B3 D#4 F#4"), (10, 2, "B3 D4 G4"), (12, 2, "C#4 E4 A4")]
PUMP = (0, 3, 6, 8, 11, 14)

# The glockenspiel's stair, one arpeggio a chord, in eighths and then in sixteenths into the drop.
STAIR = [(0, 0.5, "B4 D5 F#5 B5"), (2, 0.5, "C5 E5 G5 C6"), (4, 0.25, "G4 B4 D5 G5 B5 D6 G6 B6")]

# The lead over the drop: (beat, note, beats held). It turns round the major third it lands on, and
# the climb's D comes back as the flat VI, so that the last three bars step up D, E, F#.
LEAD = [
    (LANDING_BEAT, "B5", 1), (7, "D#6", 0.5), (7.5, "C#6", 0.5), (8, "B5", 0.5), (8.5, "C#6", 0.5), (9, "D#6", 1),
    (10, "D6", 1), (11, "B5", 0.5), (11.5, "D6", 0.5),
    (12, "E6", 1), (13, "C#6", 0.5), (13.5, "E6", 0.5),
    (FINAL_BEAT, "F#6", 4),
]

# The kicks and snares of the half-time beat, in beats after the drop, the last chord's kick among them.
KICKS = (0, 0.75, 2.5, 4, 5.5, 6.75, FINAL_BEAT - LANDING_BEAT)
SNARES = (2, 6)


def hall(rng) -> np.ndarray:
    """A big bright hall, for a moment worth shouting about."""
    return reverb_ir(2.6, rng, predelay=0.035, brightness=0.6, early=8, early_spread=0.08)


def echoes(audio: np.ndarray, feedback: float) -> np.ndarray:
    """The console's echo, a dotted eighth on one side and a quarter on the other."""
    return echo(audio, PIECE.seconds(0.75), PIECE.seconds(1.0), feedback, 3000)


# --- Voices --------------------------------------------------------------------------------------


def choir(midi, seconds, rng, formants, attack, velocity=1.0):
    f0 = hz(midi)
    return held(f0, seconds, rng, vowel(f0, formants), attack=attack, decay=0.8, sustain=0.85, release=1.0,
                voices=4, detune_cents=11, spread=0.8, vibrato_hz=5.0, vibrato_cents=12, vibrato_delay=0.3,
                velocity=velocity)


def pad(midi, seconds, rng, attack):
    f0 = hz(midi)
    return held(f0, seconds, rng, dark(saw, f0, 1400), attack=attack, decay=1.0, sustain=0.85, release=0.8,
                voices=3, detune_cents=9, spread=0.7, vibrato_hz=3.8, vibrato_cents=6)


def stab(midi, rng, velocity):
    """One pump of the synth's chords: two sawtooths a few cents apart, struck short and let go."""
    f0 = hz(midi)
    return held(f0, PIECE.seconds(0.25) * 0.7, rng, dark(saw, f0, 2600), attack=0.004, decay=0.07, sustain=0.45,
                release=0.05, voices=2, detune_cents=8, spread=0.5, velocity=velocity)


def lead(midi, seconds, rng, glide_from=None):
    """The lead: a pulse wave a third on, bright, with a scoop up into its note and a vibrato that
    comes in late."""
    f0 = hz(midi)
    return held(f0, seconds, rng, dark(pulse(0.33), f0, 4200), attack=0.012, decay=0.35, sustain=0.8,
                release=0.25, vibrato_hz=5.6, vibrato_cents=15 if seconds > 0.4 else 0, vibrato_delay=0.25,
                glide_from=glide_from if glide_from is not None else hz(midi - 0.7), glide=0.05, voices=2,
                detune_cents=6, spread=0.3)


def glockenspiel(midi, rng, velocity=1.0):
    return mallet(hz(midi), 1.6, rng, ratios=(1.0, 2.76, 5.4), levels=(1.0, 0.28, 0.1),
                  decays=(0.8, 0.25, 0.1), velocity=velocity, knock=0.12)


def boom(midi, seconds, rng, **shape):
    """The 808s: driven, with the buzz that carries them through a phone's speaker."""
    return eight08(hz(midi), seconds, rng, drive=3.0, presence=0.8, **shape)


# --- Parts ---------------------------------------------------------------------------------------


def render_choir(rng):
    loop = PIECE.loop(BARS)
    # The climb on a soft "oo", each chord swelling up out of the one before; the drop opens it to
    # "ah".
    for i, (beat, beats, voicing, _) in enumerate(CLIMB):
        for midi in notes(voicing):
            loop.add(choir(midi, PIECE.seconds(beats) + 0.1, rng, OO, attack=0.5, velocity=1.0 + 0.25 * i), beat)
    for beat, beats, voicing, _ in DROP:
        for midi in notes(voicing):
            loop.add(choir(midi, PIECE.seconds(beats) - 0.05, rng, AH, attack=0.04), beat)
    loop.audio = eq(loop.audio, highpass(110), peak(900, 1.5, 1.0), lowpass(5500))
    return loop


def render_pad(rng):
    loop = PIECE.loop(BARS)
    for i, (beat, beats, voicing, _) in enumerate(CLIMB):
        for midi in notes(voicing)[1:]:
            loop.add(pad(midi, PIECE.seconds(beats) + 0.05, rng, attack=0.6), beat, gain=1.0 + 0.4 * i)
    loop.audio = eq(loop.audio, highpass(150), lowpass(4000))
    return loop


def render_stabs(rng):
    loop = PIECE.loop(BARS)
    for beat, beats, voicing in STABS:
        for step in PUMP:
            if step >= beats * 4:
                break
            accent = 1.0 if step in (0, 8) else 0.75
            for i, midi in enumerate(notes(voicing)):
                loop.add(stab(midi, rng, accent), beat + step / 4, pan=-0.3 + 0.3 * i)
    loop.audio = eq(loop.audio, highpass(200), peak(1800, 2.0, 1.0))
    loop.audio += echoes(loop.audio, 0.3) * 0.25
    return loop


def render_lead(rng):
    loop = PIECE.loop(BARS)
    previous = None
    for beat, name, beats in LEAD:
        midi = notes(name)[0]
        glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 else None
        loop.add(lead(midi, PIECE.seconds(beats) * 0.94, rng, glide), beat, pan=0.05,
                 nudge_seconds=0.003 * rng.standard_normal())
        previous = midi
    loop.audio = eq(loop.audio, highpass(250), peak(2000, 1.5, 1.0))
    loop.audio += echoes(loop.audio, 0.3) * 0.3
    return loop


def render_bells(rng):
    loop = PIECE.loop(BARS)
    for beat, step, chord in STAIR:
        for i, midi in enumerate(notes(chord)):
            loop.add(glockenspiel(midi, rng, velocity=0.4 + 0.25 * (beat + i * step) / LANDING_BEAT),
                     beat + i * step, pan=0.4 - 0.1 * (i % 2))
    # The lead's notes struck on the glockenspiel as well, where they land on a beat; and a sparkle
    # thrown up over the last chord.
    for beat, name, _ in LEAD:
        if beat == int(beat):
            loop.add(glockenspiel(notes(name)[0], rng, velocity=0.6), beat, pan=0.4)
    for i, name in enumerate(("B5", "D#6", "F#6", "B6", "F#6", "D#6", "F#6", "B6")):
        loop.add(glockenspiel(notes(name)[0], rng, velocity=0.85 if i in (0, 3, 7) else 0.6),
                 FINAL_BEAT + 0.5 + i * 0.25, pan=0.4)
    loop.audio = eq(loop.audio, highpass(900))
    loop.audio += echoes(loop.audio, 0.35) * 0.3
    return loop


def render_kit(rng):
    loop = PIECE.loop(BARS)
    # The climb: a hat ticking eighths that quicken to sixteenths, a snare roll swelling into the drop,
    # and a cymbal growing into it backwards. All of it kept down, so that the drop is a drop: the
    # roll stops a sixteenth short of it, and the cymbal is all that is left to land.
    for i in range(8):
        loop.add(trap_hat(rng, velocity=0.2 + 0.02 * i), i * 0.5, pan=0.35)
    for i in range(7):
        loop.add(trap_hat(rng, pitch=1 + 0.02 * i, velocity=0.25 + 0.03 * i), 4 + i * 0.25, pan=0.35)
    for i in range(14):
        along = i / 13
        loop.add(snare(rng, velocity=0.1 + 0.35 * along ** 2), 4 + i / 8, pan=0.1)
    rising = swell(cymbal(rng, seconds=1.6, velocity=0.45))
    loop.add(rising, LANDING_BEAT - rising.shape[0] / PIECE.samples_per_beat, pan=-0.3)

    # The drop and the half-time beat after it.
    for beat in KICKS:
        loop.add(kick(rng, low=50, high=170, seconds=0.25, velocity=1.0 if beat % 4 == 0 else 0.8), LANDING_BEAT + beat)
    for beat in SNARES:
        loop.add(snare(rng, velocity=0.85), LANDING_BEAT + beat, pan=0.1)
        loop.add(clap(rng, velocity=0.7), LANDING_BEAT + beat, pan=-0.1)
    for eighth in range(16):
        beat = LANDING_BEAT + eighth * 0.5
        if beat >= FINAL_BEAT - 1:
            break
        loop.add(trap_hat(rng, open_=eighth == 7, velocity=0.45 if eighth % 2 == 0 else 0.32), beat, pan=0.35)
        # A sixteenth now and then, as a trap beat's hats skip.
        if eighth in (2, 5, 9):
            loop.add(trap_hat(rng, velocity=0.25), beat + 0.25, pan=0.35)
    # Into the last chord the hats roll in thirty-seconds and climb.
    for i in range(8):
        along = i / 7
        loop.add(trap_hat(rng, pitch=1 + 0.25 * along, velocity=0.3 + 0.4 * along), FINAL_BEAT - 1 + i / 8, pan=0.35)

    for beat in (LANDING_BEAT, FINAL_BEAT):
        loop.add(cymbal(rng, seconds=2.6, velocity=1.0), beat, pan=-0.3)
        loop.add(cymbal(rng, seconds=2.2, velocity=0.6), beat, pan=0.4)
        loop.add(clap(rng, velocity=0.8), beat)
    loop.audio = eq(loop.audio, highpass(45))
    return loop


def render_808s(rng):
    loop = PIECE.loop(BARS)
    for beat, beats, _, root in DROP:
        long = beat == FINAL_BEAT
        loop.add(boom(notes(root)[0], PIECE.seconds(beats) - 0.02, rng, decay=1.8 if long else 1.1, punch=10),
                 beat)
    # An octave's pop on the off-beat, to keep the first bar of the drop moving.
    loop.add(boom(notes("B2")[0], PIECE.seconds(0.5), rng, decay=0.4, punch=7, velocity=0.6), LANDING_BEAT + 3.5)
    loop.audio = eq(loop.audio, highpass(30), lowpass(3000))
    return loop


def render():
    loop = PIECE.loop(BARS)
    for part, gain in ((render_choir(PIECE.rng(1)), 0.12), (render_pad(PIECE.rng(2)), 0.06),
                       (render_stabs(PIECE.rng(3)), 0.28), (render_lead(PIECE.rng(4)), 0.3),
                       (render_bells(PIECE.rng(5)), 0.16)):
        loop.mix(part, gain)
    loop.audio += reverb(loop.audio, hall(PIECE.rng(100))) * 0.4

    dry = PIECE.loop(BARS)
    for part, gain in ((render_kit(PIECE.rng(6)), 0.4), (render_808s(PIECE.rng(7)), 0.34)):
        dry.mix(part, gain)
    dry.audio += reverb(dry.audio, hall(PIECE.rng(100))) * 0.15
    loop.audio += dry.audio
    return loop


def main() -> int:
    write_track(PIECE, render(), loudness_db=-15.0, end_beat=END_BEAT, crest_db=14.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
