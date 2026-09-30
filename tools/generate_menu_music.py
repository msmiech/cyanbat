# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the menu's music: one long loop, played for as long as the menu is open.

    uv run tools/generate_menu_music.py

The title screen is the dark before the flight: dark synths on a 16-bit console, a pulse in the
low ones like a heartbeat, a few haunting notes over it, an arpeggio that will not stop turning,
and a trap beat's 808s and hi-hats underneath once it gets going.

C minor at 70 BPM in 4/4. The eight-bar progression is a line cliché: the bass falls a half step at
a time under a minor chord that holds still, so the chord darkens bar by bar without moving -
minor, minor with a major seventh, minor seventh, half-diminished - until it gives way to the VI and
a dominant that pulls it back round:

    Cm | Cm/B | Cm/Bb | Am7b5 | Abmaj7 | Fm | G | G7

The arpeggio's inner voice falls with the bass. Thirty-two bars make four sections of eight, and
the loop builds and settles the way a heartbeat quickens and slows:

* the dark   - a pad and a choir holding still, the heartbeat, a few notes on a glass voice.
* the pulse  - the heartbeat quickens and brightens, the arpeggio fades in and opens, the theme.
* the beat   - the drum machine takes over: 808s sliding down the bass line, claps, hats rolling,
               the theme's answer reaching higher, and voices chopped into its motif.
* the ghost  - the beat at its height under whispered notes, then everything but the heartbeat
               falls away, back into the dark the loop starts in.
"""

import numpy as np

from musicsynth import (
    AH, OH, OO, Piece, chop, clap, cymbal, dark, echo, eight08, eq, held, highpass, hz, kick, lowpass,
    notes, peak, pulse, reverb, reverb_ir, saw, swell, trap_hat, vowel, write_track,
)

PIECE = Piece("menu", bpm=70, beats_per_bar=4, seed=20261002)

# One sixteenth, in beats.
STEP = 1 / 4

BARS = 32

# Where each section after the first starts, in bars; the last one settles halfway through.
PULSE_AT, BEAT_AT, GHOST_AT, SETTLE_AT = 8, 16, 24, 28

# Each bar's chord: the arpeggio's eight notes, played twice a bar, and the bass's root. In the first
# five bars the fourth and sixth notes fall with the bass: C, B, B-flat, A, A-flat.
PROGRESSION = [
    ("Cm", notes("C4 Eb4 G4 C5 Eb5 C5 G4 Eb4"), "C2"),
    ("Cm/B", notes("C4 Eb4 G4 B4 Eb5 B4 G4 Eb4"), "B1"),
    ("Cm/Bb", notes("C4 Eb4 G4 Bb4 Eb5 Bb4 G4 Eb4"), "Bb1"),
    ("Am7b5", notes("C4 Eb4 G4 A4 Eb5 A4 G4 Eb4"), "A1"),
    ("Abmaj7", notes("C4 Eb4 G4 Ab4 Eb5 Ab4 G4 Eb4"), "Ab1"),
    ("Fm", notes("C4 F4 Ab4 C5 F5 C5 Ab4 F4"), "F1"),
    ("G", notes("B3 D4 G4 B4 D5 B4 G4 D4"), "G1"),
    ("G7", notes("B3 D4 F4 G4 B4 G4 F4 D4"), "G1"),
]

# The pad holds the minor triad over the falling bass, then follows the chords home.
PAD = [notes("G3 C4 Eb4")] * 5 + [notes("F3 Ab3 C4"), notes("G3 B3 D4"), notes("F3 B3 D4")]

# Melodies bar by bar: (note, length in sixteenths), None for a rest. The theme climbs a fifth and
# steps back down, and leans on whatever the falling bass has just made strange.
THEME = [
    [("C5", 4), ("G5", 8), ("F5", 2), ("Eb5", 2)],
    [("D5", 12), ("G4", 2), ("B4", 2)],
    [("C5", 4), ("Eb5", 4), ("G5", 6), ("Bb5", 2)],
    [("A5", 12), ("G5", 2), ("Eb5", 2)],
    [("G5", 8), ("C5", 4), ("Eb5", 2), ("G5", 2)],
    [("Ab5", 12), ("G5", 2), ("F5", 2)],
    [("G5", 8), ("D5", 4), ("B4", 4)],
    [("F5", 4), ("Eb5", 4), ("D5", 4), ("B4", 4)],
]

# The same tune thinned to its long notes, for the glass voice in the dark.
HAUNT = [
    [(None, 4), ("G5", 12)],
    [("D5", 16)],
    [(None, 8), ("G5", 8)],
    [("A5", 16)],
    [(None, 4), ("G5", 12)],
    [("Ab5", 16)],
    [(None, 8), ("G5", 8)],
    [("F5", 8), ("D5", 8)],
]

# The theme's answer, reaching higher, for once the beat has arrived.
ANSWER = [
    [("Eb5", 4), ("C6", 8), ("Bb5", 2), ("G5", 2)],
    [("G5", 10), ("Eb5", 2), ("D5", 2), ("Eb5", 2)],
    [("Eb5", 4), ("G5", 4), ("Bb5", 6), ("C6", 2)],
    [("C6", 8), ("Bb5", 2), ("A5", 2), ("G5", 4)],
    [("C6", 8), ("Eb6", 4), ("D6", 2), ("C6", 2)],
    [("Ab5", 8), ("C6", 4), ("Bb5", 2), ("Ab5", 2)],
    [("B5", 8), ("D6", 4), ("B5", 4)],
    [("D6", 4), ("C6", 2), ("B5", 2), ("G5", 8)],
]

# The chopped voices, by bar of the progression: (sixteenth, note, length in sixteenths, vowel). The
# theme's first four notes cut up and thrown about, and a stutter to turn the last bar over.
CHOPS = [
    [(0, "C5", 1, AH), (2, "G5", 2, AH), (6, "F5", 1, OH), (7, "Eb5", 1, OH), (10, "G5", 1, AH),
     (12, "C6", 2, AH)],
    [(0, "B4", 1, AH), (2, "G5", 2, AH), (6, "F5", 1, OH), (7, "Eb5", 1, OH), (10, "D5", 2, OH)],
    [(0, "Bb4", 1, AH), (2, "G5", 2, AH), (6, "F5", 1, OH), (7, "Eb5", 1, OH), (10, "G5", 1, AH),
     (12, "Bb5", 2, AH)],
    [(0, "A4", 1, AH), (2, "Eb5", 2, AH), (6, "G5", 1, OH), (8, "A5", 3, AH)],
    [(0, "C5", 1, AH), (2, "G5", 2, AH), (6, "F5", 1, OH), (7, "Eb5", 1, OH), (10, "G5", 1, AH),
     (12, "C6", 2, AH)],
    [(0, "C5", 1, AH), (2, "Ab5", 2, AH), (6, "G5", 1, OH), (7, "F5", 1, OH), (10, "C5", 2, OH)],
    [(0, "B4", 1, AH), (2, "G5", 2, AH), (6, "F5", 1, OH), (7, "D5", 1, OH), (10, "G5", 2, AH)],
    [(0, "D5", 1, AH), (1, "D5", 1, AH), (2, "D5", 1, AH), (3, "D5", 1, AH), (6, "F5", 1, OH),
     (8, "B4", 2, AH), (12, "G5", 1, AH), (13, "G5", 1, AH), (14, "B5", 2, AH)],
]


def ramp(bar: int, start: int, end: int, a: float, b: float) -> float:
    """[a] at bar [start], moving evenly to [b] at bar [end], and held flat either side."""
    along = min(max((bar - start) / max(end - start, 1), 0.0), 1.0)
    return a + (b - a) * along


def room(rng) -> np.ndarray:
    """A big, dark hall: long, soft at the top, its walls far off."""
    return reverb_ir(3.4, rng, predelay=0.035, brightness=0.35, early=6, early_spread=0.08)


def echoes(audio: np.ndarray, feedback: float) -> np.ndarray:
    """The console's echo, a dotted eighth on one side and a quarter on the other."""
    return echo(audio, PIECE.seconds(0.75), PIECE.seconds(1.0), feedback, 2400)


def lay_melody(loop, melody, first_bar, voice, rng, gain=1.0, swell_to=None, pan=0.0):
    """Plays [melody] on [voice] from [first_bar] on, a bar of it to a bar: at [gain], or growing
    from there to [swell_to] by its last bar."""
    previous = None
    for offset, phrase in enumerate(melody):
        level = gain if swell_to is None else gain + (swell_to - gain) * offset / max(len(melody) - 1, 1)
        at = 0
        for name, sixteenths in phrase:
            if name is not None:
                midi = notes(name)[0]
                seconds = PIECE.seconds(sixteenths * STEP) * 0.96
                glide = hz(previous) if previous is not None and abs(midi - previous) <= 2 else None
                loop.add(voice(midi, seconds, rng, glide), (first_bar + offset) * 4 + at * STEP, pan=pan,
                         gain=level, nudge_seconds=0.005 * rng.standard_normal())
                previous = midi
            else:
                previous = None
            at += sixteenths
        assert at == 16, f"bar {first_bar + offset + 1} of a melody is {at} sixteenths long"


def octave_down(melody):
    return [[(name and name[:-1] + str(int(name[-1]) - 1), sixteenths) for name, sixteenths in phrase]
            for phrase in melody]


# --- Voices --------------------------------------------------------------------------------------


def glass(midi, seconds, rng, glide_from=None):
    """A soft, hollow tone that swells in: a sine and a little of its odd overtones, like a wet
    finger round the rim of a glass."""
    f0 = hz(midi)
    shape = lambda k: {1: 1.0, 3: 0.3, 5: 0.1}.get(k, 0.0)
    return held(f0, seconds, rng, shape, count=5, attack=0.12, decay=0.6, sustain=0.8, release=1.1,
                vibrato_hz=4.4, vibrato_cents=9, vibrato_delay=0.4, glide_from=glide_from, glide=0.1)


def lead(midi, seconds, rng, glide_from=None):
    """The theme's voice: a pulse wave a quarter on, rounded off, with a vibrato that comes in late."""
    f0 = hz(midi)
    return held(f0, seconds, rng, dark(pulse(0.25), f0, 2600), attack=0.02, decay=0.4, sustain=0.75,
                release=0.35, vibrato_hz=5.3, vibrato_cents=13, vibrato_delay=0.3,
                glide_from=glide_from, glide=0.06, voices=2, detune_cents=5, spread=0.3)


def whisper(midi, seconds, rng):
    """The ghost of a note: breath gathered round a pitch, with a thin whistle inside it, swelling
    in slowly."""
    f0 = hz(midi)
    return held(f0, seconds, rng, lambda k: 1.0 if k == 1 else 0.0, count=1, attack=0.9, decay=1.0,
                sustain=0.9, release=1.6, vibrato_hz=3.7, vibrato_cents=11, vibrato_delay=0.2,
                breath=1.0, breath_band=(f0 * 0.85, min(f0 * 1.8, 9000)), velocity=0.5)


def arp_note(midi, rng, cutoff, velocity):
    """One note of the arpeggio: a square struck short, as bright as the filter over it lets it be."""
    f0 = hz(midi)
    return held(f0, PIECE.seconds(STEP) * 0.9, rng, dark(pulse(0.5), f0, cutoff), attack=0.003,
                decay=0.12, sustain=0.35, release=0.09, velocity=velocity)


def heartbeat(root, rng, brightness, strong):
    """One half of a heartbeat: a thump at the chord's root, and over it a pulse of saw whose filter
    opens as the piece builds. The first half of each beat is [strong]; the second follows softer."""
    f0 = hz(root)
    thump = eight08(f0, 0.2 if strong else 0.14, rng, punch=6, decay=0.16, drive=1.4, click=0.05,
                    velocity=1.0 if strong else 0.7)
    body = held(f0 * 2, 0.16 if strong else 0.11, rng, dark(saw, f0 * 2, brightness), attack=0.004,
                decay=0.09, sustain=0.25, release=0.06, velocity=0.55 if strong else 0.4)
    out = np.zeros((2, max(thump.shape[0], body.shape[1])))
    out[:, :thump.shape[0]] += thump
    out[:, :body.shape[1]] += body
    return out


# --- Parts ---------------------------------------------------------------------------------------


def render_pads(rng):
    loop = PIECE.loop(BARS)
    for bar in range(BARS):
        chord = PAD[bar % 8]
        dark_section = bar < PULSE_AT or bar >= SETTLE_AT
        for midi in chord:
            f0 = hz(midi)
            loop.add(held(f0, PIECE.seconds(4) - 0.1, rng, dark(saw, f0, 900), attack=1.2, decay=1.2,
                          sustain=0.85, release=1.6, voices=3, detune_cents=11, spread=0.8,
                          vibrato_hz=3.9, vibrato_cents=6), bar * 4, gain=0.075 if dark_section else 0.064)
        # The choir only sings in the dark: the first section, and the half the loop settles into.
        if dark_section:
            for midi in chord[:2]:
                f0 = hz(midi)
                loop.add(held(f0, PIECE.seconds(4) - 0.05, rng, vowel(f0, OO), attack=1.4, decay=1.0,
                              sustain=0.9, release=1.8, voices=4, detune_cents=13, spread=0.9,
                              vibrato_hz=4.8, vibrato_cents=10, vibrato_delay=0.3), bar * 4, gain=0.05)
    loop.audio = eq(loop.audio, highpass(150), peak(2500, -3.0, 0.8))
    return loop


def render_heartbeat(rng):
    loop = PIECE.loop(BARS)
    for bar in range(BARS):
        root = notes(PROGRESSION[bar % 8][2])[0]
        if bar < PULSE_AT:
            level, brightness = ramp(bar, 0, PULSE_AT - 1, 0.45, 0.62), 260
        elif bar < BEAT_AT:
            level = ramp(bar, PULSE_AT, BEAT_AT - 1, 0.65, 1.1)
            brightness = ramp(bar, PULSE_AT, BEAT_AT - 1, 300, 1100)
        elif bar < SETTLE_AT:
            # Under the drum machine the heart is still there, but the 808s carry the low end.
            level, brightness = 0.5, 1100
        else:
            level = ramp(bar, SETTLE_AT, BARS - 1, 0.7, 0.45)
            brightness = ramp(bar, SETTLE_AT, BARS - 1, 800, 260)
        for beat in range(4):
            loop.add(heartbeat(root, rng, brightness, strong=True), bar * 4 + beat, gain=level)
            loop.add(heartbeat(root, rng, brightness, strong=False), bar * 4 + beat + 0.3, gain=level * 0.8)
    loop.audio = eq(loop.audio, highpass(35), peak(120, 2.0, 1.0), lowpass(2500))
    return loop


def render_arpeggio(rng):
    loop = PIECE.loop(BARS)
    for bar in range(PULSE_AT, BARS):
        _, figure, _ = PROGRESSION[bar % 8]
        if bar < BEAT_AT:
            cutoff = ramp(bar, PULSE_AT, BEAT_AT - 1, 450, 2400)
            level = ramp(bar, PULSE_AT, BEAT_AT - 1, 0.3, 0.85)
        elif bar < SETTLE_AT:
            cutoff, level = (3500 if bar >= GHOST_AT else 3000), 0.82
        else:
            # Closing back down and thinning out, to leave the dark that follows room.
            cutoff = ramp(bar, SETTLE_AT, BARS - 1, 2200, 450)
            level = ramp(bar, SETTLE_AT, BARS - 1, 0.65, 0.12)
        for step in range(16):
            accent = 1.0 if step % 4 == 0 else 0.72 if step % 2 == 0 else 0.6
            loop.add(arp_note(figure[step % 8], rng, cutoff, accent * level), bar * 4 + step * STEP,
                     pan=0.35 if step % 2 else -0.15, nudge_seconds=0.002 * rng.standard_normal())
    loop.audio = eq(loop.audio, highpass(180), peak(1500, 1.5, 1.0))
    loop.audio += echoes(loop.audio, 0.38) * 0.35
    return loop


def render_melodies(rng):
    loop = PIECE.loop(BARS)
    lay_melody(loop, HAUNT, 0, glass, rng, gain=0.3, pan=-0.1)
    lay_melody(loop, THEME, PULSE_AT, lead, rng, gain=0.3, swell_to=0.46, pan=-0.05)
    lay_melody(loop, ANSWER, BEAT_AT, lead, rng, gain=0.32, pan=-0.05)
    # The answer's shadow on the glass, an octave down and further back.
    lay_melody(loop, octave_down(ANSWER), BEAT_AT, glass, rng, gain=0.18, pan=0.3)
    lay_melody(loop, THEME[:4], GHOST_AT, lead, rng, gain=0.34, pan=-0.05)
    lay_melody(loop, HAUNT[4:], SETTLE_AT, glass, rng, gain=0.3, pan=-0.1)
    loop.audio = eq(loop.audio, highpass(220), peak(2000, 1.0, 1.0))
    loop.audio += echoes(loop.audio, 0.33) * 0.3
    return loop


def render_whispers(rng):
    loop = PIECE.loop(BARS)
    # One breath in the dark, and then the ghost's chorus at the height of the piece.
    for bar, name in ((4, "G5"), (6, "Ab5")):
        loop.add(whisper(notes(name)[0], PIECE.seconds(8) - 0.3, rng), bar * 4, pan=-0.4, gain=0.22)
    for offset, names in enumerate(("G5 Eb6", "G5 D6", "Bb5 Eb6", "A5 C6")):
        for i, name in enumerate(names.split()):
            loop.add(whisper(notes(name)[0], PIECE.seconds(4) + 0.4, rng), (GHOST_AT + offset) * 4 + 0.5 * i,
                     pan=-0.6 + 1.2 * i, gain=0.3)
    loop.audio = eq(loop.audio, highpass(400))
    return loop


def trap_bass(midi, seconds, rng, slide_to=None, slide=0.16, decay=1.4, velocity=1.0):
    """The beat's 808: sliding down the bass line, and buzzing enough on top to come through a phone."""
    return eight08(hz(midi), seconds, rng, slide_to=slide_to, slide=slide, decay=decay, drive=2.6, presence=0.8,
                   velocity=velocity)


def render_808s(rng):
    loop = PIECE.loop(BARS)
    for bar in range(BEAT_AT, SETTLE_AT):
        root = notes(PROGRESSION[bar % 8][2])[0]
        following = notes(PROGRESSION[(bar + 1) % 8][2])[0]
        # Long on the one, a stab and an octave pop, then held and slid into the next bar's root.
        for step, sixteenths, interval, slides in ((0, 6, 0, False), (6, 2, 0, False), (8, 2, 12, False),
                                                   (10, 6, 0, True)):
            target = hz(following) if slides and following != root and bar + 1 < SETTLE_AT else None
            loop.add(trap_bass(root + interval, PIECE.seconds(sixteenths * STEP) - 0.01, rng, slide_to=target,
                               velocity=1.0 if step == 0 else 0.85), bar * 4 + step * STEP)
    # The beat stops as the ghost settles, and the last 808 dives an octave on its way out.
    root = notes(PROGRESSION[SETTLE_AT % 8][2])[0]
    loop.add(trap_bass(root, PIECE.seconds(4), rng, slide_to=hz(root - 12), slide=PIECE.seconds(3.5), decay=2.2,
                       velocity=0.9), SETTLE_AT * 4)
    loop.audio = eq(loop.audio, highpass(30), lowpass(3500))
    return loop


def render_kit(rng):
    loop = PIECE.loop(BARS)
    for bar in range(BEAT_AT, SETTLE_AT):
        base = bar * 4
        index = bar - BEAT_AT
        for step in (0, 6, 10):
            loop.add(kick(rng, low=55, high=140, seconds=0.12, velocity=0.5), base + step * STEP)
        for step in (4, 12):
            loop.add(clap(rng, velocity=0.9), base + step * STEP, pan=0.05)
        # Straight sixteenths, except where the bar rolls: thirty-seconds into every other bar, a
        # triplet run in the sixth, and a roll that climbs into each change of section. Each roll
        # is (kind, hits, how far its pitch climbs), and takes a beat, or two for sixteen hits.
        rolls = {}
        if index % 2 == 1:
            rolls[12] = ("32", 8, 1.0)
        if index % 8 == 5:
            rolls[8] = ("3", 6, 1.0)
        if bar in (GHOST_AT - 1, SETTLE_AT - 1):
            rolls[8] = ("32", 16, 1.35)
        step = 0
        while step < 16:
            if step in rolls:
                kind, count, climb = rolls[step]
                span = 4 if kind == "3" else count // 2
                for i in range(count):
                    along = i / max(count - 1, 1)
                    loop.add(trap_hat(rng, pitch=1.0 + (climb - 1.0) * along, velocity=0.35 + 0.35 * along),
                             base + (step + span * i / count) * STEP, pan=0.3)
                step += span
                continue
            if step == 14 and index % 2 == 0:
                loop.add(trap_hat(rng, open_=True, velocity=0.45), base + step * STEP, pan=0.3)
            else:
                accent = 0.62 if step % 4 == 0 else 0.45 if step % 2 == 0 else 0.33
                loop.add(trap_hat(rng, velocity=accent * (1 + 0.08 * rng.standard_normal())), base + step * STEP,
                         pan=0.3, nudge_seconds=0.002 * rng.standard_normal())
            step += 1
    # A cymbal swelling into the beat and into the ghost, and one crashing on each.
    for bar in (BEAT_AT, GHOST_AT):
        rising = swell(cymbal(rng, seconds=1.4, velocity=0.5))
        loop.add(rising, bar * 4 - rising.shape[0] / PIECE.samples_per_beat, pan=-0.3)
        loop.add(cymbal(rng, seconds=1.8, velocity=0.4), bar * 4, pan=0.25)
    loop.audio = eq(loop.audio, highpass(60))
    return loop


def render_chops(rng):
    loop = PIECE.loop(BARS)
    for bar in range(BEAT_AT + 4, SETTLE_AT):
        for step, name, sixteenths, formants in CHOPS[bar % 8]:
            loop.add(chop(hz(notes(name)[0]), PIECE.seconds(sixteenths * STEP) * 0.85, rng, formants,
                          into=OH if formants is AH and sixteenths > 1 else None,
                          scoop=60 if sixteenths > 1 else 0, fall=250 if sixteenths > 2 else 0),
                     bar * 4 + step * STEP, pan=0.45 if step % 4 else -0.35, gain=0.5)
    loop.audio = eq(loop.audio, highpass(300), peak(3000, 2.0, 1.0))
    loop.audio += echoes(loop.audio, 0.3) * 0.3
    return loop


def render():
    loop = PIECE.loop(BARS)
    for part, gain in ((render_pads(PIECE.rng(1)), 1.0), (render_arpeggio(PIECE.rng(3)), 0.75),
                       (render_melodies(PIECE.rng(4)), 1.0), (render_whispers(PIECE.rng(5)), 1.0),
                       (render_chops(PIECE.rng(8)), 1.0)):
        loop.mix(part, gain)
    loop.audio += reverb(loop.audio, room(PIECE.rng(100))) * 0.42

    # The low end and the drums get much less of the hall, so the 808s and the heartbeat stay tight.
    dry = PIECE.loop(BARS)
    for part, gain in ((render_heartbeat(PIECE.rng(2)), 0.9), (render_808s(PIECE.rng(6)), 0.5),
                       (render_kit(PIECE.rng(7)), 0.55)):
        dry.mix(part, gain)
    dry.audio += reverb(dry.audio, room(PIECE.rng(100))) * 0.12
    loop.audio += dry.audio
    return loop


def main() -> int:
    write_track(PIECE, render(), loudness_db=-16.5)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
