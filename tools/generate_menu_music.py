# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the menu's music: one long loop, played for as long as the menu is open.

    uv run tools/generate_menu_music.py

The title screen is the bat's evening at home and the night it flies out into. It opens warm: a
marimba picking out major sevenths, muffled as if through a wall, with nothing under it. Then night
falls on the fifth bar - the bass, a slow trap beat in half time and a bell all land at once - and a
kalimba turns round each chord's root a half step either side, a high note holds over everything,
and a music box and a choir take the tune in turns. All of it played by a 16-bit console, with a big
dark hall round it.

In D, at 75 BPM in 4/4. The warm bars are D major, and their marimba's top line walks down to the
minor chord's F as the night comes in:

    Dmaj7 | Bm7 | Gmaj7 | A7

The night is D minor with the Phrygian's flat second leaning on it. Its eight-bar progression holds
the minor chord, steps up a half step to the flat II, falls to the flat VI, and climbs through the
III to a major V that pulls it home - to the minor chord again, or round the loop to the major one:

    Dm | Dm | Eb | Eb | Bb | Bb | F | A

The kalimba plays one turn over all of it - the root, a half step above, the root, a half step
below - so every chord gets the same chromatic shiver; over the Eb and the Bb the step above is
not even in the key. The high D holds through every change, the Eb's major seventh, the Bb's third
and the F's sixth, until the A bends it down to its C#.

Twenty-eight bars: the four warm ones and three passes of the night.

* home        - the marimba in the warm, its filter opening into the fifth bar.
* night       - the bass, the beat and the bell; the music box plays the theme.
* deep night  - the bell again. The choir sings a hymn under the theme, and the hats roll.
* before dawn - the theme's first half on a flute over the beat. Then the beat stops, a clock ticks,
                and the marimba comes back in over the last chords, into the warm the loop starts in.
"""

import numpy as np
from scipy import ndimage

from musicsynth import (
    AH, OO, RENDER_RATE, TABLE_SIZE, Piece, bell, clap, cymbal, dark, echo, eight08, eq, filter_loop,
    harmonics, held, highpass, hz, kick, lowpass, mallet, noise, notes, peak, reverb, reverb_ir, saw,
    swell, trap_hat, vowel, wavetable, woodblock, write_track,
)

PIECE = Piece("menu", bpm=75, beats_per_bar=4, seed=20261002)

# One sixteenth, in beats.
STEP = 1 / 4

BARS = 28

# Where the night falls, where each pass of its progression after the first starts, in bars, and
# where the beat stops halfway through the last.
NIGHT_AT, DEEP_AT, DAWN_AT, STILL_AT = 4, 12, 20, 24

# The warm bars, before the night: the pad's voicing, the choir's open fifth, and the marimba's eight
# notes, played on [RIFF]. The marimba's top notes walk down E, C#, A, G, to the night's F.
HOME = [
    ("A3 C#4 F#4", "D3 A3", "D3 A3 F#4 C#5 D3 A3 F#4 E5"),
    ("A3 D4 F#4", "B2 F#3", "B2 F#3 D4 A4 B2 F#3 D4 C#5"),
    ("B3 D4 F#4", "G2 D3", "G2 D3 B3 F#4 G2 D3 B3 A4"),
    ("G3 C#4 E4", "A2 E3", "A2 E3 D4 G4 A2 E3 C#4 G4"),
]

# The night's chords: the sub's root, the pad's voicing, the choir's open fifth, the root the
# kalimba turns round, and the marimba's notes where it comes back in as the night ends - each
# chord with its major seventh, or the A with its flat one, to lead back into the warm.
PROGRESSION = [
    ("D2", "A3 D4 F4", "D3 A3", "D4", None),
    ("D2", "A3 D4 F4", "D3 A3", "D4", None),
    ("Eb2", "Bb3 Eb4 G4", "Eb3 Bb3", "Eb4", None),
    ("Eb2", "Bb3 Eb4 G4", "Eb3 Bb3", "Eb4", None),
    ("Bb1", "Bb3 D4 F4", "Bb2 F3", "Bb3", "Bb2 F3 D4 A4 Bb2 F3 D4 C5"),
    ("Bb1", "Bb3 D4 F4", "Bb2 F3", "Bb3", "Bb2 F3 D4 A4 Bb2 F3 D4 F4"),
    ("F2", "A3 C4 F4", "F3 C4", "F4", "F2 C3 A3 E4 F2 C3 A3 G4"),
    ("A1", "A3 C#4 E4", "A2 E3", "A3", "A2 E3 C#4 G4 A2 E3 C#4 E4"),
]

# The marimba's rhythm, in sixteenths, with how hard each note is struck: the low note on the one
# and again on the three, and the chord picked out round it, off the beat.
RIFF = [(0, 1.0), (2, 0.6), (3, 0.7), (6, 0.8), (8, 0.85), (10, 0.6), (11, 0.65), (14, 0.75)]

# The kalimba's turn, the same in every bar: (sixteenth, semitones from the root, velocity). The
# root, the half step above it, the root, the half step below, the root again, then up through the
# fifth to the octave and back.
TURN = [(0, 0, 1.0), (3, 1, 0.7), (4, 0, 0.85), (7, -1, 0.7), (8, 0, 0.9), (10, 7, 0.75), (12, 12, 0.8),
        (14, 7, 0.65)]

# Melodies bar by bar: (note, length in sixteenths), None for a rest. The theme, for the music box,
# reaches up a chord and turns round its top note the way the kalimba turns round the root.
THEME = [
    [(None, 4), ("A5", 2), ("D6", 2), ("F6", 6), ("E6", 2)],
    [("D6", 6), ("C#6", 2), ("D6", 4), ("A5", 4)],
    [(None, 4), ("Bb5", 2), ("Eb6", 2), ("G6", 6), ("F6", 2)],
    [("Eb6", 6), ("D6", 2), ("Eb6", 4), ("Bb5", 4)],
    [("F6", 6), ("Eb6", 2), ("D6", 4), ("C6", 2), ("Bb5", 2)],
    [("D6", 12), (None, 4)],
    [("C6", 4), ("A5", 4), ("F5", 4), ("A5", 2), ("C6", 2)],
    [("C#6", 8), ("D6", 2), ("C#6", 2), ("A5", 4)],
]

# The hymn the choir sings under it in the deep of the night: the theme's long notes, slowed to a
# walk and falling where the theme climbs.
HYMN = [
    [("F5", 8), ("E5", 4), ("D5", 4)],
    [("A5", 12), ("G5", 2), ("F5", 2)],
    [("G5", 8), ("F5", 4), ("Eb5", 4)],
    [("Bb5", 12), ("Ab5", 2), ("G5", 2)],
    [("F5", 8), ("D5", 4), ("F5", 4)],
    [("Bb5", 8), ("A5", 4), ("Bb5", 4)],
    [("C6", 8), ("A5", 4), ("F5", 4)],
    [("E5", 8), ("C#5", 4), ("E5", 4)],
]

# The music box once the beat has stopped, without its tune: (bar, beat, note, velocity), a note or
# two of the theme's top each chord, like the last stars.
STARS = [(STILL_AT, 1, "F6", 0.75), (STILL_AT, 3, "D6", 0.55), (STILL_AT + 2, 1, "C6", 0.75),
         (STILL_AT + 2, 3, "A5", 0.55), (STILL_AT + 3, 1, "C#6", 0.8), (STILL_AT + 3, 2.5, "E6", 0.55)]

# The kicks, in sixteenths, in a two-bar pattern: the claps hold beats two and four, and the kicks
# land everywhere else.
KICKS = ((0, 6, 10), (0, 3, 9, 14))


def chord_of(bar: int):
    """The night's chord in [bar], as a row of [PROGRESSION]."""
    return PROGRESSION[(bar - NIGHT_AT) % 8]


def ramp(bar: float, start: float, end: float, a: float, b: float) -> float:
    """[a] at bar [start], moving evenly to [b] at bar [end], and held flat either side."""
    along = min(max((bar - start) / max(end - start, 1), 0.0), 1.0)
    return a + (b - a) * along


def room(rng) -> np.ndarray:
    """A big, dark hall: long, soft at the top, its walls far off."""
    return reverb_ir(3.6, rng, predelay=0.04, brightness=0.3, early=6, early_spread=0.09)


def echoes(audio: np.ndarray, feedback: float) -> np.ndarray:
    """The console's echo, a dotted eighth on one side and a quarter on the other."""
    return echo(audio, PIECE.seconds(0.75), PIECE.seconds(1.0), feedback, 2200)


def lay_melody(loop, melody, first_bar, voice, rng, gain=1.0, pan=0.0, transpose=0):
    """Plays [melody] on [voice] from [first_bar] on, a bar of it to a bar, [transpose] semitones
    away from where it is written."""
    previous = None
    for offset, phrase in enumerate(melody):
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                midi = notes(name)[0] + transpose
                seconds = PIECE.seconds(sixteenths * STEP) * 0.96
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 else None
                loop.add(voice(midi, seconds, rng, glide), (first_bar + offset) * 4 + at * STEP, pan=pan,
                         gain=gain, nudge_seconds=0.004 * rng.standard_normal())
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {first_bar + offset + 1} of a melody is {at} sixteenths long"


def bar_curve(loop, value_at_bar) -> np.ndarray:
    """A control signal over the whole loop, sample by sample: [value_at_bar] read at every bar line
    and moved along evenly between them, round the end of the loop to its start."""
    points = np.array([value_at_bar(bar) for bar in range(BARS + 1)])
    return np.interp(np.arange(loop.length) / PIECE.at(PIECE.beats_per_bar), np.arange(BARS + 1), points)


def bar_steps(loop, value_at_bar, seconds: float) -> np.ndarray:
    """A control signal that holds [value_at_bar] for each whole bar and steps at the bar lines,
    taking [seconds] over each step and arriving on the downbeat rather than leaving from it."""
    per_bar = PIECE.at(PIECE.beats_per_bar)
    bar = (np.arange(loop.length) + seconds / 2 * RENDER_RATE) / per_bar
    steps = np.array([value_at_bar(b) for b in range(BARS)], dtype=float)[np.floor(bar).astype(int) % BARS]
    return ndimage.uniform_filter1d(steps, max(int(seconds * RENDER_RATE), 1), mode="wrap")


def endless(loop, pitch: np.ndarray, shape, rng, *, detune_cents=0.0, spread=0.0) -> np.ndarray:
    """A tone that never stops, following [pitch] (in hertz, sample by sample) through the loop.

    Its pitch is scaled by a hair so that each voice runs a whole number of cycles in one turn of the
    loop, and so meets itself at the join in phase; [drone] does the same for a tone that holds one
    note. Returns stereo.
    """
    table = wavetable(harmonics(float(np.min(pitch)), shape))
    voices = (-detune_cents, detune_cents) if detune_cents else (0.0,)
    out = np.zeros((2, loop.length))
    for v, cents in enumerate(voices):
        freq = pitch * 2 ** (cents / 1200)
        cycles = np.sum(freq) / RENDER_RATE
        freq = freq * round(cycles) / cycles
        position = ((rng.random() + np.cumsum(freq) / RENDER_RATE) % 1.0) * TABLE_SIZE
        index = position.astype(np.int64)
        frac = position - index
        tone = table[index] * (1 - frac) + table[index + 1] * frac
        pan = 0.0 if len(voices) == 1 else spread * (2 * v - 1)
        angle = (pan + 1) * np.pi / 4
        out[0] += tone * np.cos(angle) * np.sqrt(2.0) / len(voices)
        out[1] += tone * np.sin(angle) * np.sqrt(2.0) / len(voices)
    return out


# --- Voices --------------------------------------------------------------------------------------


def marimba(midi, rng, velocity):
    """A marimba played with soft mallets: a wooden bar's note, and the overtones its carving tunes
    two octaves and two octaves and a third over it, both dying fast. Mono."""
    return mallet(hz(midi), 1.2, rng, ratios=(1.0, 3.93, 9.2), levels=(1.0, 0.12, 0.035), decays=(0.6, 0.12, 0.04),
                  velocity=velocity, knock=0.06)


def kalimba(midi, rng, velocity):
    """A thumb piano: a tine whose fundamental rings, and the bright overtone near its sixth harmonic
    that makes it a tine and not a string, dying fast. Mono."""
    return mallet(hz(midi), 1.4, rng, ratios=(1.0, 2.0, 5.95), levels=(1.0, 0.1, 0.24), decays=(0.85, 0.3, 0.07),
                  velocity=velocity, knock=0.12)


def music_box(midi, seconds, rng, glide_from=None, velocity=1.0):
    """A music box's comb: a tooth plucked and left to ring, with its clamped bar's overtone at six
    and a quarter times the note, which is what makes it sound small and far away. Mono."""
    return mallet(hz(midi), max(1.6, seconds + 0.6), rng, ratios=(1.0, 2.0, 6.27), levels=(1.0, 0.07, 0.2),
                  decays=(1.3, 0.35, 0.1), velocity=velocity, knock=0.06)


def flute(midi, seconds, rng, glide_from=None):
    """A soft flute: mostly its fundamental, with the air of the player's breath around it."""
    f0 = hz(midi)
    shape = lambda k: {1: 1.0, 2: 0.3, 3: 0.12, 4: 0.04}.get(k, 0.0)
    return held(f0, seconds, rng, shape, count=4, attack=0.06, decay=0.5, sustain=0.8, release=0.35,
                vibrato_hz=4.8, vibrato_cents=12, vibrato_delay=0.25, glide_from=glide_from, glide=0.06,
                breath=0.3, breath_band=(f0, min(f0 * 3, 9000)))


def singer(midi, seconds, rng, glide_from=None):
    """The choir in the deep of the night: four voices on an open "ah", leaning into each note."""
    f0 = hz(midi)
    return held(f0, seconds, rng, vowel(f0, AH), attack=0.25, decay=0.8, sustain=0.85, release=0.9, voices=4,
                detune_cents=12, spread=0.7, vibrato_hz=4.9, vibrato_cents=14, vibrato_delay=0.35,
                glide_from=glide_from, glide=0.12)


# --- Parts ---------------------------------------------------------------------------------------


def render_pads(rng):
    loop = PIECE.loop(BARS)
    for bar in range(BARS):
        chord, fifth = HOME[bar][:2] if bar < NIGHT_AT else chord_of(bar)[1:3]
        quiet = bar < NIGHT_AT or bar >= STILL_AT
        for midi in notes(chord):
            f0 = hz(midi)
            loop.add(held(f0, PIECE.seconds(4) - 0.1, rng, dark(saw, f0, 850), attack=1.0, decay=1.2,
                          sustain=0.85, release=1.6, voices=3, detune_cents=10, spread=0.8,
                          vibrato_hz=3.7, vibrato_cents=6), bar * 4, gain=0.07 if quiet else 0.055)
        # The choir holds the open fifth under it all.
        for midi in notes(fifth):
            f0 = hz(midi)
            loop.add(held(f0, PIECE.seconds(4) - 0.05, rng, vowel(f0, OO), attack=1.2, decay=1.0,
                          sustain=0.9, release=1.8, voices=4, detune_cents=13, spread=0.9,
                          vibrato_hz=4.6, vibrato_cents=9, vibrato_delay=0.3), bar * 4, gain=0.06)
    loop.audio = eq(loop.audio, highpass(120), peak(2500, -3.0, 0.8))
    return loop


def render_marimba(rng):
    """The warm bars' marimba, and its return as the night ends. It plays through a wall - a low-pass
    that opens over the bar before the night falls, so that the night comes in bright."""
    loop = PIECE.loop(BARS)
    for bar in range(BARS):
        if bar < NIGHT_AT:
            riff, level = HOME[bar][2], 1.0
        elif bar >= STILL_AT:
            riff, level = chord_of(bar)[4], ramp(bar, STILL_AT, BARS - 1, 0.55, 0.85)
        else:
            continue
        for (step, velocity), midi in zip(RIFF, notes(riff)):
            loop.add(marimba(midi, rng, velocity * level), bar * 4 + step * STEP,
                     pan=0.25 if midi >= notes("C4")[0] else -0.15, nudge_seconds=0.004 * rng.standard_normal())
    muffled = eq(loop.audio, highpass(90), lowpass(900))
    clear = eq(loop.audio, highpass(90), peak(1500, 1.5, 1.0), lowpass(6000))
    opening = bar_curve(loop, lambda bar: 1.0 if bar == NIGHT_AT else 0.0) ** 2
    loop.audio = muffled * (1 - opening) + clear * opening
    loop.audio += echoes(loop.audio, 0.3) * 0.2
    return loop


def render_sub(rng):
    """The floor of the night: one sine that never stops, sliding from each root to the next, with a
    driven copy of itself on top, high-passed - the buzz a trap mix puts on its bass so that a phone's
    speaker can hint at a note it is too small to play. It arrives with the night, on an 808 of its
    own, and leaves with it."""
    loop = PIECE.loop(BARS)
    d2 = notes("D2")[0]
    semitones = bar_steps(loop, lambda bar: notes(chord_of(bar)[0])[0] - d2 if bar >= NIGHT_AT else 0, 0.22)
    sine = endless(loop, hz(d2) * 2 ** (semitones / 12), lambda k: 1.0 if k == 1 else 0.0, rng)[0]
    level = bar_steps(loop, lambda bar: 0.0 if bar < NIGHT_AT else 0.75 if bar >= STILL_AT else 1.0, 0.03)
    grit = filter_loop(np.tanh(6.0 * sine) / np.tanh(6.0), np.vstack((highpass(220), lowpass(2200))))
    tone = (sine + 0.3 * grit) * level
    loop.audio = np.vstack((tone, tone))
    for bar in (NIGHT_AT, DEEP_AT):
        loop.add(eight08(hz(d2), PIECE.seconds(3), rng, punch=10, decay=1.0, drive=2.6, presence=0.8) * 0.9,
                 bar * 4)
    return loop


def render_pedal(rng):
    """The high D, held through the night as one tone that is never struck again, bending down to the
    C# under each A and back. It rises as the night falls and sinks back into the warm."""
    loop = PIECE.loop(BARS)
    d5 = notes("D5")[0]
    semitones = bar_steps(loop, lambda bar: -1 if bar >= NIGHT_AT and (bar - NIGHT_AT) % 8 == 7 else 0, 0.35)
    t = np.arange(loop.length) / RENDER_RATE
    vibrato = 7 * np.sin(2 * np.pi * 4.1 * t) / 1200
    pitch = hz(d5) * 2 ** (semitones / 12 + vibrato)
    shape = lambda k: {1: 1.0, 2: 0.22, 3: 0.12}.get(k, 0.0)
    tone = endless(loop, pitch, shape, rng, detune_cents=4, spread=0.3)
    air = filter_loop(noise(loop.length, rng), np.vstack((highpass(hz(d5) * 0.9), lowpass(hz(d5) * 2.4))))
    tone += 0.25 * air / (np.std(air) * 4)

    def level(bar):
        if bar <= NIGHT_AT:
            return 0.0
        if bar < STILL_AT:
            return ramp(bar, NIGHT_AT, NIGHT_AT + 2, 0.0, 1.0)
        return ramp(bar, STILL_AT, BARS, 1.0, 0.0)
    loop.audio = tone * bar_curve(loop, level) ** 1.5
    loop.audio = eq(loop.audio, highpass(400), lowpass(5000))
    return loop


def render_kalimba(rng):
    loop = PIECE.loop(BARS)
    for bar in range(NIGHT_AT, BARS):
        root = notes(chord_of(bar)[3])[0]
        if bar < STILL_AT:
            level = 0.85 if DEEP_AT <= bar < DAWN_AT else 0.8
        else:
            # Thinning out under the marimba as the warm comes back.
            level = ramp(bar, STILL_AT, BARS - 1, 0.6, 0.2)
        for step, interval, velocity in TURN:
            loop.add(kalimba(root + interval, rng, velocity * level), bar * 4 + step * STEP,
                     pan=-0.3 if step % 4 else 0.1, nudge_seconds=0.003 * rng.standard_normal())
    loop.audio = eq(loop.audio, highpass(150), peak(1200, 1.5, 1.0))
    loop.audio += echoes(loop.audio, 0.35) * 0.3
    return loop


def render_music_box(rng):
    loop = PIECE.loop(BARS)
    for bar, beat, name, velocity in STARS:
        loop.add(music_box(notes(name)[0], 1.0, rng, velocity=velocity), bar * 4 + beat, pan=0.35)
    lay_melody(loop, THEME, NIGHT_AT, music_box, rng, gain=1.0, pan=0.2)
    # Further off under the hymn, in the deep of the night.
    lay_melody(loop, THEME, DEEP_AT, music_box, rng, gain=0.55, pan=0.4)
    loop.audio = eq(loop.audio, highpass(400), peak(3000, -2.0, 1.0))
    loop.audio += echoes(loop.audio, 0.4) * 0.35
    return loop


def render_voices(rng):
    loop = PIECE.loop(BARS)
    lay_melody(loop, HYMN, DEEP_AT, singer, rng, gain=0.9, pan=-0.1)
    # The low half of the choir, an octave under.
    lay_melody(loop, HYMN, DEEP_AT, singer, rng, gain=0.6, pan=0.15, transpose=-12)
    lay_melody(loop, THEME[:4], DAWN_AT, flute, rng, gain=0.75, pan=-0.15, transpose=-12)
    loop.audio = eq(loop.audio, highpass(180), peak(2200, 1.0, 1.0))
    return loop


def render_bell(rng):
    loop = PIECE.loop(BARS)
    # A church bell on D, its hum an octave under and its tierce a minor third over, tolling as the
    # night falls and again in the deep of it.
    for bar in (NIGHT_AT, DEEP_AT):
        loop.add(bell(hz(notes("D3")[0]), rng, seconds=5.0, ratios=(0.5, 1.0, 1.19, 1.5, 2.0, 2.52, 3.0)),
                 bar * 4, pan=-0.2)
    loop.audio = eq(loop.audio, highpass(60), lowpass(4000))
    return loop


def render_clock(rng):
    loop = PIECE.loop(BARS)
    # A clock ticking in the dark once the beat has stopped.
    for bar in range(STILL_AT, BARS):
        for beat in range(4):
            tick = woodblock(2600 if beat % 2 == 0 else 2100, rng, seconds=0.025,
                             velocity=0.5 * ramp(bar, STILL_AT, BARS - 1, 0.6, 1.0))
            loop.add(tick, bar * 4 + beat, pan=0.5 if beat % 2 == 0 else 0.35)
    loop.audio = eq(loop.audio, highpass(800))
    return loop


def render_kit(rng):
    loop = PIECE.loop(BARS)
    for bar in range(NIGHT_AT, STILL_AT):
        base = bar * 4
        index = bar - NIGHT_AT
        deep = DEEP_AT <= bar < DAWN_AT
        for step in KICKS[index % 2]:
            loop.add(kick(rng, low=52, high=170, seconds=0.2, velocity=1.0 if step == 0 else 0.8), base + step * STEP)
        for step in (4, 12):
            loop.add(clap(rng, velocity=0.85), base + step * STEP, pan=0.05)
        # Straight sixteenths, except where the bar rolls: thirty-seconds into every other bar, more
        # often in the deep of the night, a triplet run now and then, and a roll that climbs into
        # each change. Each roll is (kind, hits, how far its pitch climbs), and takes a beat.
        rolls = {}
        if index % 2 == 1 or (deep and index % 4 == 2):
            rolls[12] = ("32", 8, 1.0)
        if index % 8 == 5:
            rolls[8] = ("3", 6, 1.0)
        if bar in (DEEP_AT - 1, DAWN_AT - 1, STILL_AT - 1):
            rolls[12] = ("32", 8, 1.3)
        step = 0
        while step < 16:
            if step in rolls:
                kind, count, climb = rolls[step]
                for i in range(count):
                    along = i / max(count - 1, 1)
                    loop.add(trap_hat(rng, pitch=1.0 + (climb - 1.0) * along, velocity=0.3 + 0.35 * along),
                             base + (step + 4 * i / count) * STEP, pan=0.3)
                step += 4
                continue
            if step == 14 and index % 4 == 0:
                loop.add(trap_hat(rng, open_=True, velocity=0.4), base + step * STEP, pan=0.3)
            else:
                accent = 0.6 if step % 4 == 0 else 0.42 if step % 2 == 0 else 0.3
                loop.add(trap_hat(rng, velocity=accent * (1 + 0.08 * rng.standard_normal())), base + step * STEP,
                         pan=0.3, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    # A cymbal swelling into the night and into the deep of it, and one crashing on each; and a last
    # kick and crash as the beat stops.
    for bar in (NIGHT_AT, DEEP_AT):
        rising = swell(cymbal(rng, seconds=1.6, velocity=0.5))
        loop.add(rising, bar * 4 - rising.shape[0] / PIECE.samples_per_beat, pan=-0.3)
        loop.add(cymbal(rng, seconds=2.0, velocity=0.4), bar * 4, pan=0.25)
    loop.add(kick(rng, low=52, high=170, seconds=0.3), STILL_AT * 4)
    loop.add(cymbal(rng, seconds=2.6, velocity=0.4), STILL_AT * 4, pan=0.25)
    loop.audio = eq(loop.audio, highpass(40))
    return loop


def render():
    loop = PIECE.loop(BARS)
    for part, gain in ((render_pads(PIECE.rng(1)), 1.0), (render_marimba(PIECE.rng(10)), 0.3),
                       (render_pedal(PIECE.rng(3)), 0.11), (render_kalimba(PIECE.rng(4)), 0.25),
                       (render_music_box(PIECE.rng(5)), 0.22), (render_voices(PIECE.rng(6)), 0.3),
                       (render_bell(PIECE.rng(7)), 0.6)):
        loop.mix(part, gain)
    loop.audio += reverb(loop.audio, room(PIECE.rng(100))) * 0.45

    # The low end and the drums get much less of the hall, so the sub and the kicks stay tight.
    dry = PIECE.loop(BARS)
    for part, gain in ((render_sub(PIECE.rng(2)), 0.28), (render_kit(PIECE.rng(8)), 0.5),
                       (render_clock(PIECE.rng(9)), 0.35)):
        dry.mix(part, gain)
    dry.audio += reverb(dry.audio, room(PIECE.rng(100))) * 0.12
    loop.audio += dry.audio
    return loop


def main() -> int:
    write_track(PIECE, render(), loudness_db=-16.5)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
