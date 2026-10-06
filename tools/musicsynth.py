"""Shared machinery for the music generators in this directory.

The stage music is generated rather than recorded, for the same reason the art is: a piece nobody
can regenerate is a piece nobody can rearrange. The scripts next to this one are the scores -
which notes, on which instrument, when - and this is the band and the studio.

A stage's piece is cut into stems, one per layer of the game's layered music, and every stem is a
loop. The menu's theme is one loop, and the game over's lament one track the game plays once. That
shapes everything here:

* **Everything is circular.** A note that rings past the end of its loop carries on at the start,
  and so do the reverb and the echo; filters are applied as their steady-state response to a signal
  that repeats forever. A stem is rendered as one turn of an endless loop, so the join is not a
  join at all.
* **Everything is on the grid.** A loop is a whole number of bars, and a bar a whole number of
  output frames, so the game can land a layer on a beat to the sample.
* **Everything is deterministic.** Randomness comes from seeded generators, so re-running an
  unchanged score reproduces its stems byte for byte (on the pinned numpy and scipy).

The sound aims at a 16-bit console rather than at a studio: instruments are built from a few
partials or a single-cycle wave, the output is 22.05 kHz, and the stems are stored as IMA ADPCM,
four bits a sample, a close cousin of what the SNES kept its instruments in. What is not retro is
the room: a proper reverb, because that is what makes a few partials sound like a place.
"""

import pathlib
import struct

import numpy as np
from scipy import ndimage, signal

# Rendered at twice the output rate and brought down at the end, so nothing folds back from above
# the output's Nyquist frequency on the way.
RENDER_RATE = 44100
OUTPUT_RATE = 22050

# Partials above this are not rendered: the output cannot carry them.
TOP_HZ = 10000.0

MUSIC_DIR = pathlib.Path(__file__).resolve().parent.parent / "assets" / "music"

# The layers every stage's music is cut into, bottom up: the game's MusicLayer, and the order the
# stems are handed to its mixer.
LAYERS = ("bed", "pulse", "drive", "lead", "boom", "roll", "chop", "fury")

# The game's mixer passes everything under this share of full scale straight through and rounds
# off what goes over it (StemMixer's CLIP_KNEE).
MIXER_KNEE = 0.9

# How much of the full mix, every layer up, may go past the knee: one sample in a thousand. The
# loudest moments of a mix are a few peaks of different stems landing together, and leveling the
# whole set so that even those stay under the knee costs every other moment a couple of decibels -
# more with every layer the ladder grows. Every layer is only up at the height of a fight, a boss or
# a streak gone supernova, where a peak the mixer rounds off is lost in the noise.
PAST_KNEE = 1e-3

NOTE_NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note(name: str) -> int:
    """A note name such as "E2", "F#3" or "Bb4" as a MIDI number; middle C is C4."""
    pitch = NOTE_NAMES[name[0]]
    rest = name[1:]
    while rest and rest[0] in "#b":
        pitch += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + pitch


def notes(names: str) -> list[int]:
    """Several note names, space separated."""
    return [note(n) for n in names.split()]


def hz(midi: float) -> float:
    """The frequency of MIDI note [midi], in equal temperament at A440."""
    return 440.0 * 2.0 ** ((midi - 69.0) / 12.0)


# --- The grid ------------------------------------------------------------------------------------


class Piece:
    """The grid one piece is written on: its tempo, its bars, and its dice.

    Times in a score are in beats from the top of the loop. A beat is whatever the game's grid
    calls one - in 12/8 that is a dotted quarter - and it has to come out as a whole number of
    output frames, or the game's beat would drift against the music's.
    """

    def __init__(self, name: str, bpm: float, beats_per_bar: int, seed: int):
        frames_per_beat = OUTPUT_RATE * 60 / bpm
        if not float(frames_per_beat).is_integer():
            raise ValueError(f"{bpm} BPM is {frames_per_beat} frames a beat; pick a tempo that divides")
        self.name = name
        self.bpm = bpm
        self.beats_per_bar = beats_per_bar
        self.seed = seed
        self.samples_per_beat = int(frames_per_beat) * (RENDER_RATE // OUTPUT_RATE)

    def at(self, beat: float) -> float:
        """Where [beat] falls, in render samples."""
        return beat * self.samples_per_beat

    def seconds(self, beats: float) -> float:
        """[beats] of this piece, in seconds."""
        return beats * 60.0 / self.bpm

    def rng(self, salt: int) -> np.random.Generator:
        """Dice of their own for one part, so changing one part leaves the others' rolls alone."""
        return np.random.default_rng([self.seed, salt])

    def loop(self, bars: int) -> "Loop":
        """A silent loop [bars] bars long, to render parts into."""
        return Loop(self, bars)


class Loop:
    """One turn of an endless stereo loop, [bars] long, that sounds are laid into."""

    def __init__(self, piece: Piece, bars: int):
        self.piece = piece
        self.bars = bars
        self.beats = bars * piece.beats_per_bar
        self.length = int(round(piece.at(self.beats)))
        self.audio = np.zeros((2, self.length))

    def add(self, sound: np.ndarray, beat: float, pan: float = 0.0, gain: float = 1.0,
            nudge_seconds: float = 0.0):
        """Lays [sound] in at [beat], wrapping whatever runs past the end round to the start.

        A mono sound is placed at [pan], -1 left to 1 right, at constant power; a stereo one keeps
        its own image and [pan] only leans it.
        """
        if sound.ndim == 1:
            angle = (pan + 1.0) * np.pi / 4.0
            stereo = np.vstack((sound * np.cos(angle), sound * np.sin(angle))) * np.sqrt(2.0)
        else:
            lean = np.array([[min(1.0, 1.0 - pan)], [min(1.0, 1.0 + pan)]])
            stereo = sound * lean
        start = int(round(self.piece.at(beat) + nudge_seconds * RENDER_RATE)) % self.length
        remaining = stereo * gain
        position = start
        while remaining.shape[1] > 0:
            take = min(self.length - position, remaining.shape[1])
            self.audio[:, position:position + take] += remaining[:, :take]
            remaining = remaining[:, take:]
            position = 0

    def mix(self, other: "Loop", gain: float = 1.0):
        """Adds another loop of the same piece, repeated to fill this one if it is shorter."""
        if self.length % other.length:
            raise ValueError("a loop can only take one whose length divides its own")
        self.audio += np.tile(other.audio, self.length // other.length) * gain


# --- Filters, applied to a loop as the steady state of a signal that repeats forever --------------


def _response(sos: np.ndarray, length: int) -> np.ndarray:
    """[sos]'s response at each bin of a real FFT [length] samples long."""
    radians = 2.0 * np.pi * np.fft.rfftfreq(length)
    _, response = signal.sosfreqz(sos, worN=radians)
    return response


def filter_loop(audio: np.ndarray, sos: np.ndarray) -> np.ndarray:
    """Runs [audio] through [sos] as if it had been looping forever: no start-up, no seam."""
    length = audio.shape[-1]
    return np.fft.irfft(np.fft.rfft(audio) * _response(sos, length), n=length)


def lowpass(cutoff: float, order: int = 2) -> np.ndarray:
    """A Butterworth low-pass at [cutoff] Hz, as second-order sections."""
    return signal.butter(order, cutoff, "lowpass", fs=RENDER_RATE, output="sos")


def highpass(cutoff: float, order: int = 2) -> np.ndarray:
    """A Butterworth high-pass at [cutoff] Hz, as second-order sections."""
    return signal.butter(order, cutoff, "highpass", fs=RENDER_RATE, output="sos")


def bandpass(low: float, high: float, order: int = 2) -> np.ndarray:
    """A Butterworth band-pass from [low] to [high] Hz, as second-order sections."""
    return signal.butter(order, [low, high], "bandpass", fs=RENDER_RATE, output="sos")


def peak(center: float, gain_db: float, q: float = 1.0) -> np.ndarray:
    """A peaking EQ, from the Audio EQ Cookbook."""
    a = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * center / RENDER_RATE
    alpha = np.sin(w0) / (2 * q)
    b = [1 + alpha * a, -2 * np.cos(w0), 1 - alpha * a]
    den = [1 + alpha / a, -2 * np.cos(w0), 1 - alpha / a]
    return np.array([[b[0] / den[0], b[1] / den[0], b[2] / den[0], 1.0, den[1] / den[0], den[2] / den[0]]])


def eq(audio: np.ndarray, *bands: np.ndarray) -> np.ndarray:
    """Several filters in series, applied to a loop."""
    return filter_loop(audio, np.vstack(bands))


def one_shot_filter(sound: np.ndarray, sos: np.ndarray) -> np.ndarray:
    """Filters a single sound, from silence, the ordinary way."""
    return signal.sosfilt(sos, sound)


# --- Space -------------------------------------------------------------------------------------


def reverb_ir(seconds: float, rng: np.random.Generator, *, predelay: float = 0.02,
              brightness: float = 0.5, early: int = 6, early_spread: float = 0.06) -> np.ndarray:
    """A stereo room: decaying noise whose treble dies first, after a few early reflections.

    [seconds] is the time for the low mids to die away by 60 dB. [brightness] sets how much longer
    the treble lasts, from 0 (a heavy, damped room) to 1 (a bright hall).
    """
    length = int((seconds * 1.1 + predelay) * RENDER_RATE)
    t = np.arange(length) / RENDER_RATE
    noise = rng.standard_normal((2, length))
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(length, 1 / RENDER_RATE)
    tail = np.zeros((2, length))
    # Each band decays at its own rate: treble dies first, which is what makes it a room.
    bands = ((0, 250, 1.15), (250, 1500, 1.0), (1500, 5000, 0.45 + 0.4 * brightness),
             (5000, RENDER_RATE, 0.25 + 0.4 * brightness))
    for low, high, factor in bands:
        mask = (freqs >= low) & (freqs < high)
        band = np.fft.irfft(spectrum * mask, n=length)
        tail += band * np.exp(-6.9 * t / (seconds * factor))
    onset = np.clip(t / 0.012, 0, 1)
    ir = tail * onset
    for i in range(early):
        at = int((0.008 + rng.random() * early_spread) * RENDER_RATE)
        ir[0, at] += (rng.random() * 2 - 1) * 1.5
        ir[1, at + int(rng.random() * 40)] += (rng.random() * 2 - 1) * 1.5
    ir = np.hstack((np.zeros((2, int(predelay * RENDER_RATE))), ir))
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def reverb(audio: np.ndarray, ir: np.ndarray) -> np.ndarray:
    """The wet signal of [audio] through [ir], each side through its own side of the room."""
    length = audio.shape[-1]
    if ir.shape[-1] > length:
        raise ValueError("a room longer than the loop would hear itself twice")
    padded = np.zeros((2, length))
    padded[:, :ir.shape[-1]] = ir
    if audio.ndim == 1:
        audio = np.vstack((audio, audio))
    return np.fft.irfft(np.fft.rfft(audio) * np.fft.rfft(padded), n=length)


def echo(audio: np.ndarray, seconds_left: float, seconds_right: float, feedback: float,
         damp_hz: float) -> np.ndarray:
    """The wet signal of a feedback echo, each repeat a little duller, the way the SNES's was.

    The two sides repeat at different times, which is what spreads a mono part across the field.
    Worked in closed form - the infinite sum of repeats is a geometric series - so the echo of the
    end of the loop lands at its start, and never runs out.
    """
    length = audio.shape[-1]
    freqs = np.fft.rfftfreq(length, 1 / RENDER_RATE)
    damp = 1 / (1 + 1j * freqs / damp_hz)
    out = np.zeros((2, length))
    spectrum = np.fft.rfft(audio)
    for side, seconds in enumerate((seconds_left, seconds_right)):
        delay = np.exp(-2j * np.pi * freqs * seconds)
        response = damp * delay / (1 - feedback * damp * delay)
        out[side] = np.fft.irfft(spectrum[side] * response, n=length)
    return out


def tame(audio: np.ndarray, crest_db: float = 10.0, ratio: float = 4.0, hold: float = 0.03) -> np.ndarray:
    """Compresses whatever peaks more than [crest_db] over the loop's own RMS, [ratio] to one.

    A look-ahead design, and circular like everything else: the gain is worked out from the
    loudest sample within [hold] either side, widened, then smoothed over half that, so the gain is
    already down by the time a peak arrives and nothing overshoots. Both sides move together, so
    the stereo image does not wander when something loud lands on one side.
    """
    level = np.max(np.abs(audio), axis=0)
    threshold = np.sqrt(np.mean(audio ** 2)) * 10 ** (crest_db / 20)
    size = seconds_to_samples(hold)
    peaks = ndimage.maximum_filter1d(level, size, mode="wrap")
    gain = np.minimum(1.0, (threshold / np.maximum(peaks, 1e-9)) ** (1 - 1 / ratio))
    gain = ndimage.minimum_filter1d(gain, size, mode="wrap")
    gain = ndimage.uniform_filter1d(gain, size // 2, mode="wrap")
    return audio * gain


def saturate(audio: np.ndarray, drive: float) -> np.ndarray:
    """Rounds peaks off the way an overdriven preamp does; below the drive, nearly transparent."""
    return np.tanh(audio * drive) / drive


# --- Envelopes and oscillators -----------------------------------------------------------------


def seconds_to_samples(seconds: float) -> int:
    """[seconds] in samples at [RENDER_RATE], never fewer than one."""
    return max(1, int(round(seconds * RENDER_RATE)))


def adsr(total: float, attack: float, decay: float, sustain: float, release: float,
         curve: float = 1.0) -> np.ndarray:
    """An envelope held [total] seconds before its release, with a linear attack."""
    held = seconds_to_samples(total)
    rel = seconds_to_samples(release)
    t = np.arange(held + rel) / RENDER_RATE
    env = np.empty_like(t)
    rise = np.clip(t / max(attack, 1e-4), 0, 1) ** curve
    fall = sustain + (1 - sustain) * np.exp(-np.maximum(t - attack, 0) / max(decay, 1e-4))
    env[:] = np.where(t < attack, rise, fall)
    level_at_release = env[held - 1]
    tail = np.arange(rel) / rel
    env[held:] = level_at_release * 0.5 * (1 + np.cos(np.pi * tail))
    return env


def fade_edges(sound: np.ndarray, attack: float = 0.001, release: float = 0.015) -> np.ndarray:
    """A short ramp at each end, so no sound starts or stops on a click."""
    a = min(seconds_to_samples(attack), sound.shape[-1] // 2)
    r = min(seconds_to_samples(release), sound.shape[-1] // 2)
    out = sound.copy()
    out[..., :a] *= np.linspace(0, 1, a, endpoint=False)
    out[..., -r:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, r)))
    return out


_TABLES: dict = {}
TABLE_SIZE = 4096


def wavetable(amplitudes: tuple) -> np.ndarray:
    """One cycle built from harmonic amplitudes, cached. Band-limited by construction."""
    table = _TABLES.get(amplitudes)
    if table is None:
        x = np.arange(TABLE_SIZE) / TABLE_SIZE
        table = np.zeros(TABLE_SIZE)
        for k, amp in enumerate(amplitudes, start=1):
            if amp:
                table += amp * np.sin(2 * np.pi * k * x)
        peak_level = np.max(np.abs(table))
        table = table / peak_level if peak_level else table
        table = np.append(table, table[0])
        _TABLES[amplitudes] = table
    return table


def harmonics(f0: float, shape, count: int | None = None) -> tuple:
    """Harmonic amplitudes for a tone at [f0], stopping below [TOP_HZ]: [shape] maps k to one."""
    top = int(TOP_HZ / max(f0, 1.0))
    if count is not None:
        top = min(top, count)
    return tuple(round(float(shape(k)), 6) for k in range(1, max(top, 1) + 1))


def read_table(table: np.ndarray, freq: np.ndarray, phase: float = 0.0) -> np.ndarray:
    """Plays [table] at a frequency that may move, sample by sample."""
    cycles = phase + np.cumsum(freq) / RENDER_RATE
    position = (cycles % 1.0) * TABLE_SIZE
    index = position.astype(np.int64)
    frac = position - index
    return table[index] * (1 - frac) + table[index + 1] * frac


def pitch_curve(f0: float, length: int, *, vibrato_hz: float = 0.0, vibrato_cents: float = 0.0,
                vibrato_delay: float = 0.0, glide_from: float | None = None, glide: float = 0.06,
                drift_cents: float = 0.0, rng: np.random.Generator | None = None) -> np.ndarray:
    """The frequency of a held note over time: a glide in, then a vibrato that fades up."""
    t = np.arange(length) / RENDER_RATE
    cents = np.zeros(length)
    if glide_from is not None:
        start = 1200 * np.log2(glide_from / f0)
        cents += start * np.exp(-t / max(glide / 3, 1e-4))
    if vibrato_cents:
        depth = vibrato_cents * np.clip((t - vibrato_delay) / 0.35, 0, 1)
        offset = rng.random() * 2 * np.pi if rng is not None else 0.0
        cents += depth * np.sin(2 * np.pi * vibrato_hz * t + offset)
    if drift_cents and rng is not None:
        cents += drift_cents * (rng.random() * 2 - 1)
    return f0 * 2 ** (cents / 1200)


def noise(length: int, rng: np.random.Generator) -> np.ndarray:
    """[length] samples of white noise."""
    return rng.standard_normal(length)


# --- Instruments -------------------------------------------------------------------------------


def pluck(f0: float, seconds: float, rng: np.random.Generator, *, decay: float = 1.5,
          damping: float = 0.45, pick: float = 0.18, tilt: float = 1.5, inharmonicity: float = 0.0,
          course_cents: float = 0.0, attack_noise: float = 0.04, brightness_hz: float = 4000.0,
          velocity: float = 1.0) -> np.ndarray:
    """A plucked string, as the sum of its partials, each dying away faster than the one below.

    [pick] is where along the string it was plucked, which notches out the partials that have a
    node there; [tilt] how fast the partials fall away; [damping] how much faster each one dies
    than the one under it. [course_cents] adds a second string tuned that far away, as a lute's
    doubled courses have. Softer playing is darker as well as quieter.
    """
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    out = np.zeros(n)
    soft = 0.55 + 0.45 * velocity
    for k in range(1, 64):
        fk = f0 * k * np.sqrt(1 + inharmonicity * k * k)
        if fk > TOP_HZ:
            break
        amp = abs(np.sin(np.pi * k * pick)) / k ** tilt
        amp /= 1 + (fk / (brightness_hz * soft)) ** 2
        tau = decay / (1 + damping * (k - 1))
        env = np.exp(-t / tau)
        partial = np.sin(2 * np.pi * fk * t)
        if course_cents:
            partial = 0.5 * (partial + np.sin(2 * np.pi * fk * 2 ** (course_cents / 1200) * t + 0.7 * k))
        out += amp * env * partial
    out /= max(np.max(np.abs(out)), 1e-9)
    if attack_noise:
        click = seconds_to_samples(0.006)
        burst = noise(click, rng) * np.exp(-np.arange(click) / (click / 4))
        burst = one_shot_filter(burst, bandpass(1500, 6000))
        out[:click] += attack_noise * burst / max(np.max(np.abs(burst)), 1e-9)
    return fade_edges(out * velocity, attack=0.0008, release=0.03)


def mallet(f0: float, seconds: float, rng: np.random.Generator, ratios, levels, decays,
           velocity: float = 1.0, knock: float = 0.1) -> np.ndarray:
    """A struck bar or tine: a few partials at fixed ratios, each with its own decay."""
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    out = np.zeros(n)
    for ratio, level, tau in zip(ratios, levels, decays):
        f = f0 * ratio
        if f < TOP_HZ:
            out += level * np.exp(-t / tau) * np.sin(2 * np.pi * f * t)
    if knock:
        k = seconds_to_samples(0.004)
        burst = one_shot_filter(noise(k, rng), bandpass(800, 5000)) * np.exp(-np.arange(k) / (k / 3))
        out[:k] += knock * burst / max(np.max(np.abs(burst)), 1e-9)
    out /= max(np.max(np.abs(out)), 1e-9)
    return fade_edges(out * velocity, attack=0.0005, release=0.02)


def held(f0: float, seconds: float, rng: np.random.Generator, shape, *, attack: float = 0.02,
         decay: float = 0.3, sustain: float = 0.8, release: float = 0.2, vibrato_hz: float = 5.0,
         vibrato_cents: float = 0.0, vibrato_delay: float = 0.3, glide_from: float | None = None,
         glide: float = 0.08, voices: int = 1, detune_cents: float = 0.0, spread: float = 0.0,
         breath: float = 0.0, breath_band=(600, 3000), velocity: float = 1.0,
         count: int | None = None) -> np.ndarray:
    """A held tone from a single-cycle wave: winds, strings, voices, pads.

    [shape] maps harmonic number to amplitude. Several [voices] detuned against each other make an
    ensemble, spread across the field by [spread]; [breath] mixes in band-limited noise that follows
    the envelope, for the air in a flute. Returns stereo.
    """
    env = adsr(seconds, attack, decay, sustain, release)
    n = env.shape[0]
    table = wavetable(harmonics(f0 * 1.03, shape, count))
    out = np.zeros((2, n))
    for v in range(voices):
        cents = 0.0 if voices == 1 else detune_cents * (2 * v / (voices - 1) - 1)
        freq = pitch_curve(f0 * 2 ** (cents / 1200), n, vibrato_hz=vibrato_hz * (1 + 0.07 * v),
                           vibrato_cents=vibrato_cents, vibrato_delay=vibrato_delay,
                           glide_from=glide_from, glide=glide, rng=rng)
        tone = read_table(table, freq, phase=rng.random())
        pan = 0.0 if voices == 1 else spread * (2 * v / (voices - 1) - 1)
        angle = (pan + 1) * np.pi / 4
        out[0] += tone * np.cos(angle)
        out[1] += tone * np.sin(angle)
    out *= np.sqrt(2.0) / voices
    if breath:
        air = one_shot_filter(noise(n, rng), bandpass(*breath_band))
        air /= max(np.std(air), 1e-9) * 4
        # The air is loudest as the note speaks, and settles under it.
        chiff = 1 + 2.5 * np.exp(-np.arange(n) / seconds_to_samples(0.05))
        out += breath * air * chiff
    return out * env * velocity


def drone(f0: float, length: int, rng: np.random.Generator, shape, *, voices: int = 1,
          detune_cents: float = 0.0, spread: float = 0.0, sway: float = 0.0, sway_cycles: int = 1,
          count: int | None = None) -> np.ndarray:
    """A tone that never stops: exactly [length] samples of it, with no start and no end.

    Every frequency is nudged, by a hair, to fit a whole number of cycles into the loop, so the
    last sample runs into the first as if the loop were one long note. A held note laid across the
    join instead would meet its own attack there, out of phase, and dip. [sway] fades each voice
    up and down [sway_cycles] times a loop, out of step with the others, so the ensemble breathes.
    Returns stereo.
    """
    table = wavetable(harmonics(f0, shape, count))
    out = np.zeros((2, length))
    for v in range(voices):
        cents = 0.0 if voices == 1 else detune_cents * (2 * v / (voices - 1) - 1)
        cycles = max(1, round(f0 * 2 ** (cents / 1200) * length / RENDER_RATE))
        phase = rng.random()
        position = ((phase + cycles * np.arange(length) / length) % 1.0) * TABLE_SIZE
        index = position.astype(np.int64)
        frac = position - index
        tone = table[index] * (1 - frac) + table[index + 1] * frac
        if sway:
            tone *= 1 - sway * 0.5 * (1 + np.sin(2 * np.pi * sway_cycles * np.arange(length) / length
                                                  + 2 * np.pi * v / max(voices, 1)))
        pan = 0.0 if voices == 1 else spread * (2 * v / (voices - 1) - 1)
        angle = (pan + 1) * np.pi / 4
        out[0] += tone * np.cos(angle)
        out[1] += tone * np.sin(angle)
    return out * np.sqrt(2.0) / voices


# Harmonic recipes, as functions of the harmonic number.
def saw(k):
    """A sawtooth's harmonic [k]: every harmonic, falling as 1/k."""
    return 1.0 / k


def square(k):
    """A square wave's harmonic [k]: odd harmonics only, falling as 1/k."""
    return 1.0 / k if k % 2 else 0.0


def triangle(k):
    """A triangle wave's harmonic [k]: odd harmonics only, falling as 1/k^2 and alternating in sign."""
    return (1.0 / (k * k)) * (1 if (k // 2) % 2 == 0 else -1) if k % 2 else 0.0


def pulse(duty):
    """The harmonics of a pulse wave high for [duty] of each cycle."""
    return lambda k: np.sin(np.pi * k * duty) / k


def dark(shape, f0, cutoff_hz, order=4):
    """[shape] with its harmonics rolled off above [cutoff_hz], the way a low-pass would."""
    return lambda k: shape(k) / np.sqrt(1 + (k * f0 / cutoff_hz) ** order)


def vowel(f0, formants):
    """A voice: a saw whose harmonics are shaped by resonances at the [formants], (hz, width, gain)."""
    def shape(k):
        f = k * f0
        gain = sum(g / (1 + ((f - center) / width) ** 2) for center, width, g in formants)
        return gain / k ** 0.4
    return shape


AH = ((730, 90, 1.0), (1090, 110, 0.55), (2440, 160, 0.22), (3400, 250, 0.1))
OH = ((500, 80, 1.0), (830, 100, 0.5), (2500, 160, 0.15))
OO = ((320, 70, 1.0), (800, 100, 0.3), (2300, 160, 0.08))


# --- Percussion --------------------------------------------------------------------------------


def _decay(n: int, seconds: float) -> np.ndarray:
    """[n] samples of exponential decay, falling by a factor of e every [seconds]."""
    return np.exp(-np.arange(n) / RENDER_RATE / seconds)


def _normalized(sound: np.ndarray) -> np.ndarray:
    """[sound] scaled so its loudest sample is at full scale."""
    return sound / max(np.max(np.abs(sound)), 1e-9)


def membrane(f0: float, seconds: float, rng: np.random.Generator, *, bend: float = 1.5,
             bend_seconds: float = 0.03, modes=(1.0, 1.59, 2.14, 2.3), mode_levels=(1.0, 0.3, 0.18, 0.1),
             thump: float = 0.15, thump_hz: float = 2000.0, velocity: float = 1.0) -> np.ndarray:
    """A drumhead: a pitched body that falls from [bend] times its note, its upper modes dying
    first, and a thump of noise where the stick or hand lands."""
    n = seconds_to_samples(seconds * 1.5)
    t = np.arange(n) / RENDER_RATE
    sweep = 1 + (bend - 1) * np.exp(-t / bend_seconds)
    out = np.zeros(n)
    for mode, level in zip(modes, mode_levels):
        phase = 2 * np.pi * np.cumsum(f0 * mode * sweep) / RENDER_RATE
        out += level * np.sin(phase) * _decay(n, seconds / mode ** 0.8)
    if thump:
        k = seconds_to_samples(0.02)
        hit = one_shot_filter(noise(k, rng), lowpass(thump_hz)) * _decay(k, 0.005)
        out[:k] += thump * _normalized(hit)
    return fade_edges(_normalized(out) * velocity, attack=0.0003)


def kick(rng, *, low=46.0, high=150.0, seconds=0.32, velocity=1.0):
    """A kick drum: a sine swept down from [high] to [low] Hz, with a click on the front. Mono."""
    n = seconds_to_samples(seconds * 2)
    t = np.arange(n) / RENDER_RATE
    freq = low + (high - low) * np.exp(-t / 0.028)
    body = np.sin(2 * np.pi * np.cumsum(freq) / RENDER_RATE) * _decay(n, seconds)
    k = seconds_to_samples(0.004)
    body[:k] += 0.25 * _normalized(one_shot_filter(noise(k, rng), highpass(2500)))
    return fade_edges(_normalized(saturate(body, 1.6)) * velocity, attack=0.0002)


def snare(rng, *, tone_hz=190.0, seconds=0.17, brush=0.0, velocity=1.0):
    """A snare: a two-tone shell under a band-passed rattle, which [brush] softens into a sweep. Mono."""
    n = seconds_to_samples(seconds * 2)
    t = np.arange(n) / RENDER_RATE
    tone = (np.sin(2 * np.pi * tone_hz * t) + 0.5 * np.sin(2 * np.pi * tone_hz * 1.72 * t)) * _decay(n, 0.06)
    rattle = one_shot_filter(noise(n, rng), bandpass(1400, 8500)) * _decay(n, seconds)
    if brush:
        rattle *= np.clip(t / (0.012 * brush), 0, 1)
    out = (1 - brush * 0.6) * 0.6 * _normalized(tone) + _normalized(rattle)
    return fade_edges(_normalized(out) * velocity, attack=0.0003)


def hat(rng, *, open_=False, velocity=1.0):
    """A hi-hat of high-passed noise, closed or [open_]. Mono."""
    seconds = 0.22 if open_ else 0.035
    n = seconds_to_samples(seconds * 2.5)
    metal = one_shot_filter(noise(n, rng), highpass(6500, order=4)) * _decay(n, seconds)
    return fade_edges(_normalized(metal) * velocity, attack=0.0002)


def shaker(rng, *, seconds=0.05, velocity=1.0):
    """A shaker: band-passed noise with a soft attack. Mono."""
    n = seconds_to_samples(seconds * 3)
    t = np.arange(n) / RENDER_RATE
    grains = one_shot_filter(noise(n, rng), bandpass(3500, 9000))
    env = np.clip(t / 0.008, 0, 1) * _decay(n, seconds)
    return fade_edges(_normalized(grains * env) * velocity, attack=0.001)


def jingles(rng, *, seconds=0.16, velocity=1.0, count=9):
    """A tambourine's or a riq's zils: a cluster of high, inharmonic rings over a hiss."""
    n = seconds_to_samples(seconds * 2.5)
    t = np.arange(n) / RENDER_RATE
    out = np.zeros(n)
    for _ in range(count):
        f = 4200 + rng.random() * 5200
        out += np.sin(2 * np.pi * f * t + rng.random() * 6.28) * _decay(n, seconds * (0.5 + rng.random()))
    hiss = one_shot_filter(noise(n, rng), highpass(5000)) * _decay(n, seconds * 0.4)
    return fade_edges(_normalized(out + 1.2 * _normalized(hiss)) * velocity, attack=0.0005)


def bell(f0: float, rng, *, seconds=1.4, ratios=(1.0, 1.47, 2.09, 2.56, 3.21), velocity=1.0):
    """Finger cymbals, a gong, a temple bell: inharmonic partials, the lowest lasting longest."""
    n = seconds_to_samples(seconds * 1.3)
    t = np.arange(n) / RENDER_RATE
    out = np.zeros(n)
    for i, ratio in enumerate(ratios):
        f = f0 * ratio
        if f >= TOP_HZ:
            continue
        beat = 1 + 0.0015 * (rng.random() - 0.5)
        ring = np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * f * beat * t + 1.0)
        out += ring * _decay(n, seconds / (1 + 0.5 * i)) / (1 + 0.3 * i)
    return fade_edges(_normalized(out) * velocity, attack=0.0004)


def clap(rng, *, velocity=1.0):
    """A hand clap: three quick bursts of band-passed noise and a short tail. Mono."""
    n = seconds_to_samples(0.25)
    t = np.arange(n) / RENDER_RATE
    env = np.zeros(n)
    for at in (0.0, 0.011, 0.02):
        env += (t >= at) * np.exp(-np.maximum(t - at, 0) / 0.006)
    env += (t >= 0.028) * np.exp(-np.maximum(t - 0.028, 0) / 0.07)
    body = one_shot_filter(noise(n, rng), bandpass(900, 3200)) * env
    return fade_edges(_normalized(body) * velocity, attack=0.0002)


def woodblock(f0: float, rng, *, seconds=0.05, velocity=1.0):
    """A woodblock at [f0]: a sine and a shorter inharmonic partial over a click. Mono."""
    n = seconds_to_samples(seconds * 3)
    t = np.arange(n) / RENDER_RATE
    out = np.sin(2 * np.pi * f0 * t) * _decay(n, seconds) + 0.4 * np.sin(2 * np.pi * f0 * 2.74 * t) * _decay(n, seconds / 3)
    k = seconds_to_samples(0.002)
    out[:k] += 0.3 * _normalized(noise(k, rng))
    return fade_edges(_normalized(out) * velocity, attack=0.0002)


def cymbal(rng, *, seconds=1.6, velocity=1.0):
    """A crash, for the downbeat a section lands on."""
    n = seconds_to_samples(seconds * 1.5)
    t = np.arange(n) / RENDER_RATE
    wash = one_shot_filter(noise(n, rng), bandpass(3000, 9500)) * _decay(n, seconds)
    ring = sum(np.sin(2 * np.pi * (3100 + 700 * i + 400 * rng.random()) * t) for i in range(6)) * _decay(n, seconds * 0.5)
    return fade_edges(_normalized(_normalized(wash) + 0.15 * _normalized(ring)) * velocity, attack=0.0005)


def swell(sound: np.ndarray, release: float = 0.01) -> np.ndarray:
    """[sound] played backwards: a cymbal or a voice that grows out of nothing and stops dead.
    Lay it in so that it ends where it should land, [len] before the beat it leads into."""
    return fade_edges(sound[..., ::-1], attack=0.002, release=release)


# --- The trap kit -------------------------------------------------------------------------------
#
# What a drum machine brings to the older instruments above: the 808 played as a bass and sliding
# between its notes, hi-hats ticking and rolling faster than any hand could, and voices chopped out
# of a sung line and played as an instrument. Used over the stages' own instruments rather than in
# place of them.


# The six square waves a TR-808 mixes for its cymbal and hi-hats, in hertz: clashing on purpose,
# since what is left of them after a band-pass is metal.
_METAL_HZ = (205.3, 304.4, 369.6, 522.7, 540.0, 800.0)


def eight08(f0: float, seconds: float, rng: np.random.Generator, *, slide_to: float | None = None,
            slide: float = 0.12, punch: float = 9.0, decay: float = 1.6, drive: float = 2.5,
            click: float = 0.15, presence: float = 0.0, velocity: float = 1.0) -> np.ndarray:
    """An 808: the drum machine's long kick, tuned and played as a bass. Mono.

    It lands [punch] semitones sharp and falls onto its note within a few hundredths of a second,
    which is the thump; rings on, dying away over [decay]; and is driven through a soft clipper
    until it grows some overtones. With [slide_to] it glides into that frequency over its last
    [slide] seconds - the slide trap bass lines are made of. [seconds] is how long it is held.

    The note itself is lower than a phone's speaker reaches, and so are the overtones a soft clip
    grows, so an 808 meant to be heard there rides [presence] of a hard-driven copy of itself with
    its lows taken out: the buzz a trap mix puts on its 808s so that they come through small
    speakers.
    """
    held_for = seconds_to_samples(seconds)
    n = held_for + seconds_to_samples(0.015)
    t = np.arange(n) / RENDER_RATE
    semitones = punch * np.exp(-t / 0.018)
    if slide_to is not None:
        begin = max(0, held_for - seconds_to_samples(slide))
        along = np.clip((np.arange(n) - begin) / max(held_for - begin, 1), 0.0, 1.0)
        semitones = semitones + 12 * np.log2(slide_to / f0) * (0.5 - 0.5 * np.cos(np.pi * along))
    body = np.sin(2 * np.pi * np.cumsum(f0 * 2 ** (semitones / 12)) / RENDER_RATE)
    level = np.exp(-t / decay)
    level[held_for:] *= 0.5 * (1 + np.cos(np.pi * np.arange(n - held_for) / (n - held_for)))
    tone = np.tanh(drive * body * level) / np.tanh(drive)
    if presence:
        grit = np.tanh(8.0 * body * level) / np.tanh(8.0)
        tone = tone + presence * one_shot_filter(grit, np.vstack((highpass(250), lowpass(2500))))
    if click:
        k = seconds_to_samples(0.003)
        tick = one_shot_filter(noise(k, rng), bandpass(1000, 5000)) * np.exp(-np.arange(k) / (k / 3))
        tone[:k] += click * _normalized(tick)
    return fade_edges(tone * velocity, attack=0.0015, release=0.004)


def trap_hat(rng: np.random.Generator, *, open_: bool = False, pitch: float = 1.0,
             velocity: float = 1.0) -> np.ndarray:
    """A drum machine's hi-hat: the six squares of [_METAL_HZ] and a little hiss, band-passed down to
    their fizz. Tighter than [hat], the tick trap hats roll in; [pitch] moves the whole cluster, for
    a roll that climbs. Mono."""
    seconds = 0.16 if open_ else 0.022
    n = seconds_to_samples(seconds * 4 + 0.01)
    metal = np.zeros(n)
    for f in _METAL_HZ:
        f *= pitch
        metal += read_table(wavetable(harmonics(f, square)), np.full(n, f), phase=rng.random())
    metal = one_shot_filter(metal, bandpass(5000, 9800))
    hiss = one_shot_filter(noise(n, rng), bandpass(5500, 9800))
    body = (_normalized(metal) + 0.6 * _normalized(hiss)) * _decay(n, seconds)
    return fade_edges(_normalized(body) * velocity, attack=0.0002)


def chop(f0: float, seconds: float, rng: np.random.Generator, formants=AH, *, into=None,
         scoop: float = 0.0, fall: float = 0.0, air: float = 0.1, velocity: float = 1.0) -> np.ndarray:
    """A slice of a sung vowel, the way a sampler chops one out of a vocal: it starts and stops dead
    instead of breathing in and out. Mono.

    It can [scoop] up into its note from that many cents below, [fall] off it by that many cents as
    it ends, and turn [into] a second vowel on the way.
    """
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    cents = -scoop * np.exp(-t / 0.035)
    if fall:
        tail = min(0.09, seconds * 0.5)
        cents = cents - fall * np.clip((t - (seconds - tail)) / tail, 0, 1) ** 2
    freq = f0 * 2 ** (cents / 1200)
    phase = rng.random()
    voice = read_table(wavetable(harmonics(f0, vowel(f0, formants))), freq, phase)
    if into is not None:
        other = read_table(wavetable(harmonics(f0, vowel(f0, into))), freq, phase)
        morph = t / seconds
        voice = voice * (1 - morph) + other * morph
    if air:
        breath = one_shot_filter(noise(n, rng), bandpass(1200, 5000))
        voice = voice + air * breath / max(np.std(breath), 1e-9) * 0.25
    body = voice * (0.8 + 0.2 * np.exp(-t / 0.05))
    return fade_edges(body * velocity, attack=0.002, release=0.012)


# --- Writing it out ----------------------------------------------------------------------------


def to_output_rate(audio: np.ndarray) -> np.ndarray:
    """Halves the sample rate of a loop, circularly, rolling the top end off on the way.

    The roll-off starts well below the new Nyquist frequency, and it is gentle on purpose: the
    16-bit consoles' output was soft at the top, and some of what makes them sound like themselves
    is exactly that.
    """
    length = audio.shape[-1]
    half = length // 2
    spectrum = np.fft.rfft(audio)[:, :half // 2 + 1]
    freqs = np.fft.rfftfreq(length, 1 / RENDER_RATE)[:half // 2 + 1]
    taper = np.where(freqs < 7500, 1.0, np.cos(np.clip((freqs - 7500) / 3500, 0, 1) * np.pi / 2) ** 2)
    return np.fft.irfft(spectrum * taper, n=half) * (half / length)


_STEP_TABLE = (
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55,
    60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
    337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411,
    1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
    5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500,
    20350, 22385, 24623, 27086, 29794, 32767,
)
_INDEX_TABLE = (-1, -1, -1, -1, 2, 4, 6, 8)

# 256 bytes a channel for every 11.025 kHz, which is the block Microsoft's own encoder picks.
BLOCK_ALIGN = 1024
FRAMES_PER_BLOCK = (BLOCK_ALIGN - 4 * 2) * 8 // (4 * 2) + 1


# How much of each sample's quantization error is carried into the next sample's target. ADPCM's
# error is white, and the music is not: most of its energy is low, so plain encoding leaves a hiss
# over the top octave that nothing masks. Carrying the error forward shapes the noise to rise in
# the bass and fall in the treble, under the music rather than above it.
NOISE_SHAPING = 0.8


def _encode(samples, predictor: int, index: int, shaping: float = NOISE_SHAPING):
    """IMA ADPCM nibbles for [samples], carrying on from [predictor] and [index]."""
    steps = _STEP_TABLE
    moves = _INDEX_TABLE
    out = []
    append = out.append
    error = 0.0
    for sample in samples:
        target = sample + shaping * error
        step = steps[index]
        diff = int(round(target)) - predictor
        if diff < 0:
            nibble = 8
            diff = -diff
        else:
            nibble = 0
        change = step >> 3
        if diff >= step:
            nibble |= 4
            diff -= step
            change += step
        part = step >> 1
        if diff >= part:
            nibble |= 2
            diff -= part
            change += part
        part = step >> 2
        if diff >= part:
            nibble |= 1
            change += part
        predictor = predictor - change if nibble & 8 else predictor + change
        if predictor > 32767:
            predictor = 32767
        elif predictor < -32768:
            predictor = -32768
        error = predictor - target
        index += moves[nibble & 7]
        if index < 0:
            index = 0
        elif index > 88:
            index = 88
        append(nibble)
    return out, predictor, index


def write_ima_adpcm(path: pathlib.Path, audio: np.ndarray):
    """Writes a stereo loop at [OUTPUT_RATE] as an IMA ADPCM WAV, with its exact length in `fact`."""
    pcm = np.clip(np.round(audio * 32767), -32768, 32767).astype(np.int64)
    frames = pcm.shape[1]
    channels = [pcm[0].tolist(), pcm[1].tolist()]
    index = [0, 0]
    data = bytearray()
    for first in range(0, frames, FRAMES_PER_BLOCK):
        header = bytearray()
        bodies = []
        for ch in range(2):
            block = channels[ch][first:first + FRAMES_PER_BLOCK]
            # A short last block is padded out with its own last sample, which costs nothing to
            # encode; the length in `fact` is what says where the audio really ends.
            block += [block[-1]] * (FRAMES_PER_BLOCK - len(block))
            header += struct.pack("<hBB", block[0], index[ch], 0)
            nibbles, _, index[ch] = _encode(block[1:], block[0], index[ch])
            bodies.append(nibbles)
        data += header
        for group in range(0, FRAMES_PER_BLOCK - 1, 8):
            for ch in range(2):
                n = bodies[ch][group:group + 8]
                data += bytes((n[i] | (n[i + 1] << 4)) for i in range(0, 8, 2))
    byte_rate = OUTPUT_RATE * BLOCK_ALIGN // FRAMES_PER_BLOCK
    fmt = struct.pack("<HHIIHHHH", 0x0011, 2, OUTPUT_RATE, byte_rate, BLOCK_ALIGN, 4, 2, FRAMES_PER_BLOCK)
    body = b"WAVE" + b"fmt " + struct.pack("<I", len(fmt)) + fmt
    body += b"fact" + struct.pack("<II", 4, frames)
    body += b"data" + struct.pack("<I", len(data)) + bytes(data)
    path.write_bytes(b"RIFF" + struct.pack("<I", len(body)) + body)


def decode_ima_adpcm(path: pathlib.Path) -> np.ndarray:
    """Reads back what [write_ima_adpcm] wrote, for measuring what the game will actually hear."""
    raw = path.read_bytes()
    frames = struct.unpack_from("<I", raw, raw.index(b"fact") + 8)[0]
    start = raw.index(b"data") + 8
    out = np.zeros((2, frames))
    position = 0
    offset = start
    while position < frames:
        block = raw[offset:offset + BLOCK_ALIGN]
        offset += BLOCK_ALIGN
        count = min(FRAMES_PER_BLOCK, frames - position)
        predictor = list(struct.unpack_from("<hh", bytes(block[0:2] + block[4:6])))
        index = [block[2], block[6]]
        decoded = [[predictor[0]], [predictor[1]]]
        p = 8
        while len(decoded[1]) < FRAMES_PER_BLOCK:
            for ch in range(2):
                for byte in block[p:p + 4]:
                    for nibble in (byte & 15, byte >> 4):
                        step = _STEP_TABLE[index[ch]]
                        diff = step >> 3
                        if nibble & 4:
                            diff += step
                        if nibble & 2:
                            diff += step >> 1
                        if nibble & 1:
                            diff += step >> 2
                        predictor[ch] += -diff if nibble & 8 else diff
                        predictor[ch] = max(-32768, min(32767, predictor[ch]))
                        index[ch] = max(0, min(88, index[ch] + _INDEX_TABLE[nibble & 7]))
                        decoded[ch].append(predictor[ch])
                p += 4
        out[:, position:position + count] = np.array([decoded[0][:count], decoded[1][:count]]) / 32768
        position += count
    return out


def _db(x: float) -> str:
    """[x] in decibels, formatted for a report."""
    return f"{20 * np.log10(max(x, 1e-9)):6.1f} dB"


def loudness(audio: np.ndarray) -> float:
    """How loud a loop at [OUTPUT_RATE] sounds, in decibels: its level through the K-weighting of
    ITU-R BS.1770, near enough - a high-pass under 38 Hz and a four-decibel lift over 1.7 kHz.

    Not its RMS, because a track heavy with 808s puts most of its energy where ears hear least, and
    one leveled by its RMS comes out sounding quieter than its neighbors.
    """
    freqs = np.fft.rfftfreq(audio.shape[-1], 1 / OUTPUT_RATE)
    ratio = freqs / 38.13
    under = ratio ** 2 / np.sqrt((1 - ratio ** 2) ** 2 + (ratio / 0.5003) ** 2)
    lift = np.sqrt((1 + (10 ** (4 / 20) * freqs / 1682.0) ** 2) / (1 + (freqs / 1682.0) ** 2))
    weighted = np.fft.irfft(np.fft.rfft(audio) * under * lift, n=audio.shape[-1])
    return 10 * np.log10(max(np.mean(weighted ** 2), 1e-18))


def write_stems(piece: Piece, stems: dict[str, Loop]):
    """Brings a piece's stems down to the output rate, levels them as a set, and writes them.

    Leveled as a set, not one by one: the balance between the layers is part of the music, so all
    of them are scaled by the one factor that brings the full mix up to the mixer's knee; see
    [PAST_KNEE].
    """
    if tuple(stems) != LAYERS:
        raise ValueError(f"stems must be {LAYERS}, in order")
    longest = max(loop.length for loop in stems.values())
    for layer, loop in stems.items():
        if longest % loop.length:
            raise ValueError(f"{layer} is {loop.bars} bars, which does not divide the longest stem")

    # Nothing under 30 Hz reaches a listener on anything the game runs on, and it would take
    # headroom from everything that does. The peaks are tamed stem by stem for the same reason: the
    # set is leveled by the full mix's loudest moment, and a drum hit that stands far above the
    # rest would keep everything else quiet.
    outputs = {
        layer: to_output_rate(tame(filter_loop(loop.audio, highpass(30))))
        for layer, loop in stems.items()
    }
    full = sum(np.tile(audio, longest // 2 // audio.shape[1]) for audio in outputs.values())
    scale = MIXER_KNEE / np.quantile(np.abs(full), 1 - PAST_KNEE)

    MUSIC_DIR.mkdir(parents=True, exist_ok=True)
    print(f"{piece.name}: {piece.bpm} BPM, {piece.beats_per_bar} beats a bar, scaled by {scale:.3f}")
    for layer, audio in outputs.items():
        path = MUSIC_DIR / f"{piece.name}_{layer}.wav"
        write_ima_adpcm(path, audio * scale)
        rms = np.sqrt(np.mean((audio * scale) ** 2))
        print(f"  {path.name:22} {stems[layer].bars:3} bars  {audio.shape[1] / OUTPUT_RATE:6.2f} s"
              f"  rms {_db(rms)}  peak {_db(np.max(np.abs(audio * scale)))}"
              f"  {path.stat().st_size / 1024:7.0f} KiB")
    rms = np.sqrt(np.mean((full * scale) ** 2))
    print(f"  full mix rms {_db(rms)}, peak {np.max(np.abs(full * scale)):.2f} of full scale")


def write_track(piece: Piece, loop: Loop, *, loudness_db: float, end_beat: float | None = None,
                crest_db: float = 10.0):
    """Brings a piece that is not layered - the menu's, the game over's - down to the output rate,
    levels it, and writes it as `music/<piece name>.wav`.

    Leveled to a [loudness] rather than to the mixer's knee, because it plays on its own: to
    [loudness_db] over its whole length, set against the stages' mixes so that the menu is no louder
    than a fight. Its peaks still have to clear the knee, and the level comes down if they would not.

    A track the game plays once rather than loops is cut at [end_beat], once its last sound has rung
    out, with a moment's fade so it cannot end on a click. Everything here is circular, so the loop
    it was rendered in needs room past that beat: whatever rang on past the loop's end would come
    round again at its start.

    Peaks standing more than [crest_db] over the loop's RMS are tamed first; see [tame]. A track that
    is quiet for a while and then drops needs more than the default, or the drop is tamed back down
    to the quiet part's level - the more so since the room past its end counts toward the RMS.
    """
    audio = to_output_rate(tame(filter_loop(loop.audio, highpass(30)), crest_db=crest_db))
    if end_beat is not None:
        end = int(round(piece.at(end_beat))) * OUTPUT_RATE // RENDER_RATE
        fade = int(0.3 * OUTPUT_RATE)
        audio = audio[:, :end].copy()
        audio[:, -fade:] *= np.cos(np.linspace(0, np.pi / 2, fade)) ** 2
    scale = 10 ** ((loudness_db - loudness(audio)) / 20)
    loudest = np.quantile(np.abs(audio * scale), 1 - PAST_KNEE)
    if loudest > MIXER_KNEE:
        print(f"  {piece.name}: held {_db(MIXER_KNEE / loudest).strip()} under {loudness_db} dB, to clear the knee")
        scale *= MIXER_KNEE / loudest

    MUSIC_DIR.mkdir(parents=True, exist_ok=True)
    path = MUSIC_DIR / f"{piece.name}.wav"
    write_ima_adpcm(path, audio * scale)
    rms = np.sqrt(np.mean((audio * scale) ** 2))
    print(f"{piece.name}: {piece.bpm} BPM, {piece.beats_per_bar} beats a bar"
          f"{', played once' if end_beat is not None else ''}")
    print(f"  {path.name:22} {audio.shape[1] / OUTPUT_RATE:6.2f} s  loudness {loudness(audio * scale):5.1f} dB"
          f"  rms {_db(rms)}  peak {_db(np.max(np.abs(audio * scale)))}  {path.stat().st_size / 1024:7.0f} KiB")
