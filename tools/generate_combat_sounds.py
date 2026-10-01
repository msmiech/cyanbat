# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the sounds of the fight: what a hit, a kill and a broken rock sound like.

    uv run tools/generate_combat_sounds.py

The bat's gun and the aura's surge have scripts of their own; this one writes everything that
answers them - every effect that says a shot landed, something died, or something fired back:

* enemyShot.wav    - an enemy's volley. Low and buzzy where the bat's gun is a bright blip falling
                     away, because the palette rule holds for sound too: the bat is cool, everything
                     hostile is warm.
* shotHit.wav      - one of the bat's shots landing: a tick on top of a small thump. The quietest,
                     shortest thing here, because a fight is mostly this.
* shieldHit.wav    - a shot spent on a shield's bubble: a glassy ping instead of the thump, so a
                     player can hear that nothing got through.
* enemyDeath.wav   - something burning up: a crack, a roar that darkens as it dies, a thump under it
                     and a crackle of embers.
* rockShatter.wav  - a spire breaking: a crack and then stone clattering down, with a chip of crystal
                     in it. No roar - the shatter sheet has no fire in it, and nor does this.
* batHit.wav       - the bat taking a blow: a dull thump and a squeak.
* bossDeath.wav    - the boss going down: an enormous boom, a roar rolling on under it, and three
                     more blasts going off after it, at [AFTERSHOCKS], where the game lights its
                     wreck up again. They are over before the victory's fanfare comes in.

They share a palette and a level. Each is written near full scale and played at the volume its
`SoundEffect` gives it, which is where the balance between them and against the music is set.

Mono, 16-bit PCM at 22.05 kHz, like the gun and the aura's surge: `javax.sound.sampled` reads PCM
natively, and AGP leaves `.wav` uncompressed in the APK, which is what `AssetManager.openFd` needs to
hand SoundPool a file descriptor. Rendered at twice that and brought down at the end, so nothing
folds back from above the output's Nyquist frequency on the way, and seeded, so re-running the
script reproduces the files byte for byte (on the pinned numpy and scipy).
"""

import pathlib
import wave

import numpy as np
from scipy import signal

from musicsynth import RENDER_RATE, bandpass, highpass, lowpass, one_shot_filter, reverb_ir, seconds_to_samples

OUTPUT_RATE = 22050
ASSETS = pathlib.Path(__file__).resolve().parent.parent / "assets"

# Every effect is written to this peak. Near full scale, so the samples carry their full 16 bits;
# how loud each one plays is its SoundEffect's volume.
PEAK = 0.9

# When the boss's wreck goes off again after the first blast, in seconds. GameScreen bursts the
# wreck at the same moments (BOSS_AFTERSHOCK_SECONDS), so each of these blasts is seen as well as
# heard; change them together.
AFTERSHOCKS = (0.2, 0.42, 0.66)


# --- Pieces ------------------------------------------------------------------------------------


def silence(seconds: float) -> np.ndarray:
    return np.zeros(seconds_to_samples(seconds))


def decay(n: int, seconds: float) -> np.ndarray:
    """An exponential fall, to 1/e after [seconds]."""
    return np.exp(-np.arange(n) / RENDER_RATE / seconds)


def normalized(sound: np.ndarray) -> np.ndarray:
    return sound / max(np.max(np.abs(sound)), 1e-9)


def sweep(start_hz: float, end_hz: float, seconds: float, glide: float) -> np.ndarray:
    """A sine falling (or rising) exponentially from [start_hz] toward [end_hz], [glide] seconds to
    get most of the way there."""
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    freq = end_hz + (start_hz - end_hz) * np.exp(-t / glide)
    return np.sin(2 * np.pi * np.cumsum(freq) / RENDER_RATE)


def buzz(start_hz: float, end_hz: float, seconds: float, glide: float, harmonics: int) -> np.ndarray:
    """A saw built from [harmonics] partials, swept like [sweep]: band-limited, so it never aliases."""
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    freq = end_hz + (start_hz - end_hz) * np.exp(-t / glide)
    phase = 2 * np.pi * np.cumsum(freq) / RENDER_RATE
    out = np.zeros(n)
    for k in range(1, harmonics + 1):
        out += np.sin(k * phase) / k * (freq * k < 9000)
    return out


def burst(rng, seconds: float, band, fall: float) -> np.ndarray:
    """Noise in [band] (low, high), dying away over [fall] seconds."""
    n = seconds_to_samples(seconds)
    return normalized(one_shot_filter(rng.standard_normal(n), bandpass(*band))) * decay(n, fall)


def crackle(rng, seconds: float, per_second: float, fall: float) -> np.ndarray:
    """Embers popping: sparse clicks, thinning out over [fall] seconds."""
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    pops = (rng.random(n) < per_second / RENDER_RATE * np.exp(-t / fall)) * rng.uniform(0.3, 1.0, n)
    return one_shot_filter(pops * np.sign(rng.standard_normal(n)), bandpass(1200, 7000))


def falling_roar(rng, seconds: float, bands) -> np.ndarray:
    """Noise whose top dies before its bottom, so it darkens as it fades: a fireball cooling. Each
    band is (low, high, seconds to fall)."""
    n = seconds_to_samples(seconds)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    for low, high, fall in bands:
        out += normalized(one_shot_filter(noise, bandpass(low, high))) * decay(n, fall)
    return out


def boom(start_hz: float, end_hz: float, seconds: float, fall: float, drive: float = 2.0) -> np.ndarray:
    """The thump: a sine dropping in pitch, driven until it grows a little edge."""
    body = sweep(start_hz, end_hz, seconds, glide=seconds * 0.25) * decay(seconds_to_samples(seconds), fall)
    return np.tanh(drive * body) / np.tanh(drive)


def presence(sound: np.ndarray, drive: float = 6.0) -> np.ndarray:
    """A hard-driven copy of [sound] with its lows taken out, the buzz that lets a low boom through a
    phone's speaker, which reaches nowhere near the boom itself."""
    grit = np.tanh(drive * normalized(sound)) / np.tanh(drive)
    return one_shot_filter(grit, np.vstack((highpass(250), lowpass(3000))))


def lay(into: np.ndarray, sound: np.ndarray, at: float, gain: float = 1.0):
    """Mixes [sound] into [into], [at] seconds in, cutting off whatever would run past its end.

    Every piece is faded out over the last part of its length on the way in. A decay is cut off
    wherever its piece ends, and one cut short of silence is a click in the middle of the effect.
    """
    start = seconds_to_samples(at) if at > 0 else 0
    take = min(sound.shape[0], into.shape[0] - start)
    fade = max(seconds_to_samples(0.005), sound.shape[0] // 6)
    ended = sound.copy()
    ended[-fade:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, fade)))
    into[start:start + take] += ended[:take] * gain


def finish(sound: np.ndarray, release: float = 0.02) -> np.ndarray:
    """A moment's fade at each end, so a sound retriggered over itself never starts or stops on a
    click, and the whole of it written to [PEAK]."""
    out = sound.copy()
    a = seconds_to_samples(0.0008)
    r = seconds_to_samples(release)
    out[:a] *= np.linspace(0, 1, a, endpoint=False)
    out[-r:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, r)))
    return normalized(out) * PEAK


# --- The effects -------------------------------------------------------------------------------


def enemy_shot(rng) -> np.ndarray:
    """A warm spit of plasma: a dark buzz falling an octave and a half, with a puff of air on it.

    Softer at the front than the bat's gun, and darker all the way through, so the two guns can
    never be mistaken for one another - and so a screen full of enemies firing sits under the music
    rather than over it.
    """
    seconds = 0.15
    tone = buzz(470, 170, seconds, glide=0.045, harmonics=12)
    tone = one_shot_filter(tone, lowpass(2400))
    n = tone.shape[0]
    t = np.arange(n) / RENDER_RATE
    env = np.clip(t / 0.003, 0, 1) * decay(n, 0.045)
    air = burst(rng, seconds, (300, 1800), 0.025)
    return finish(normalized(tone) * env + 0.35 * air)


def shot_hit(rng) -> np.ndarray:
    """A shot going in: a tick of grit, a small thump under it, and a zap of the shot's own cool tone.

    Over in under a tenth of a second, because at full tilt a fight lands several of these a second
    and anything with a tail would smear them into a drone.
    """
    out = silence(0.09)
    lay(out, burst(rng, 0.02, (2200, 8000), 0.002), 0, 0.9)
    lay(out, sweep(420, 140, 0.09, glide=0.02) * decay(seconds_to_samples(0.09), 0.022), 0, 1.0)
    zap = np.sign(sweep(1300, 650, 0.05, glide=0.015)) * decay(seconds_to_samples(0.05), 0.01)
    lay(out, one_shot_filter(zap, lowpass(5000)), 0, 0.22)
    return finish(out, release=0.01)


def shield_hit(rng) -> np.ndarray:
    """A shot glancing off a bubble: a glassy ping of a few clashing partials, ringing a moment."""
    seconds = 0.28
    n = seconds_to_samples(seconds)
    t = np.arange(n) / RENDER_RATE
    out = np.zeros(n)
    for ratio, level, fall in ((1.0, 1.0, 0.07), (2.32, 0.55, 0.045), (4.25, 0.3, 0.025), (5.4, 0.15, 0.015)):
        f = 1750 * ratio
        # A hair of wobble on each, as a thin shell rings: it beats rather than sitting still.
        wobble = 1 + 0.004 * np.sin(2 * np.pi * 23 * t + rng.random() * 6.28)
        out += level * np.sin(2 * np.pi * np.cumsum(f * wobble) / RENDER_RATE) * decay(n, fall) * (f < 10000)
    lay(out, burst(rng, 0.01, (3000, 9000), 0.0015), 0, 0.5)
    return finish(out)


def enemy_death(rng) -> np.ndarray:
    """Something burning up: a crack, a roar whose top dies first, a thump and a few embers."""
    out = silence(0.5)
    lay(out, burst(rng, 0.03, (1500, 9000), 0.004), 0, 0.8)
    roar = falling_roar(rng, 0.5, ((60, 400, 0.14), (400, 1500, 0.08), (1500, 6000, 0.035)))
    lay(out, np.tanh(1.8 * roar) / np.tanh(1.8), 0.002, 0.8)
    thump = boom(150, 48, 0.4, fall=0.09, drive=2.2)
    lay(out, thump, 0, 0.9)
    lay(out, presence(thump), 0, 0.3)
    lay(out, crackle(rng, 0.45, per_second=60, fall=0.12), 0.03, 0.35)
    return finish(out, release=0.05)


def rock_shatter(rng) -> np.ndarray:
    """A spire breaking: a hard crack, then stones clattering down, the odd chip of crystal among
    them, and a little dust. A clack is a short burst of noise with a ring of stone in it."""
    seconds = 0.6
    out = silence(seconds)
    lay(out, burst(rng, 0.03, (1500, 9500), 0.003), 0, 1.0)
    lay(out, sweep(900, 520, 0.04, glide=0.01) * decay(seconds_to_samples(0.04), 0.008), 0, 0.5)
    lay(out, boom(120, 60, 0.25, fall=0.05, drive=1.5), 0, 0.55)

    # The pieces landing: thick at first, then fewer and quieter as the last of them fall.
    at = 0.025
    while at < 0.48:
        level = 0.75 * np.exp(-at / 0.2) * rng.uniform(0.5, 1.0)
        center = rng.uniform(900, 3200)
        clack = burst(rng, 0.03, (center * 0.6, center * 1.5), rng.uniform(0.004, 0.009))
        ring = np.sin(2 * np.pi * center * 1.3 * np.arange(clack.shape[0]) / RENDER_RATE) * decay(clack.shape[0], 0.006)
        lay(out, clack + 0.4 * ring, at, level)
        at += rng.uniform(0.012, 0.035) * (1 + at * 4)
    # Crystal, which the spires carry veins of, and which rings where limestone only clacks.
    for at, f in ((0.06, 3700), (0.17, 4400)):
        chip = np.sin(2 * np.pi * f * np.arange(seconds_to_samples(0.12)) / RENDER_RATE)
        lay(out, chip * decay(chip.shape[0], 0.03), at, 0.18)

    lay(out, burst(rng, seconds, (200, 1600), 0.14), 0.01, 0.2)
    return finish(out, release=0.06)


def bat_hit(rng) -> np.ndarray:
    """The bat taking a blow: a dull smack, and a squeak - a bat's, high and sliding down."""
    seconds = 0.3
    out = silence(seconds)
    lay(out, burst(rng, 0.03, (300, 2500), 0.008), 0, 0.7)
    lay(out, sweep(230, 85, 0.2, glide=0.04) * decay(seconds_to_samples(0.2), 0.045), 0, 1.0)

    n = seconds_to_samples(0.11)
    t = np.arange(n) / RENDER_RATE
    # Sliding down from 3.2 kHz, with a flutter on it, and gone in a tenth of a second.
    freq = 1700 + 1500 * np.exp(-t / 0.05) + 90 * np.sin(2 * np.pi * 38 * t)
    squeak = np.sin(2 * np.pi * np.cumsum(freq) / RENDER_RATE)
    squeak += 0.25 * np.sin(2 * 2 * np.pi * np.cumsum(freq) / RENDER_RATE)
    env = np.clip(t / 0.008, 0, 1) * np.clip((0.11 - t) / 0.03, 0, 1)
    lay(out, squeak * env, 0.018, 0.4)
    return finish(out, release=0.03)


def boss_death(rng) -> np.ndarray:
    """The boss going down: one enormous boom, a roar that rolls on and darkens under it, embers,
    and three more blasts at [AFTERSHOCKS] as the wreck goes up again - each smaller than the last -
    all of it in a big space that gives it back."""
    seconds = 3.6
    out = silence(seconds)

    lay(out, burst(rng, 0.06, (800, 9500), 0.012), 0, 1.0)
    thump = boom(110, 30, 1.6, fall=0.55, drive=2.5)
    lay(out, thump, 0, 1.0)
    lay(out, presence(thump), 0, 0.35)

    roar = falling_roar(rng, 2.6, ((35, 300, 0.9), (300, 1200, 0.45), (1200, 4000, 0.22), (4000, 9000, 0.08)))
    # A slow churn through the roar, so it rolls rather than hisses.
    t = np.arange(roar.shape[0]) / RENDER_RATE
    roar *= 1 + 0.35 * np.sin(2 * np.pi * 5.5 * t + 1.0) * np.exp(-t / 0.8)
    lay(out, np.tanh(1.6 * normalized(roar)) / np.tanh(1.6), 0.004, 0.75)
    lay(out, crackle(rng, 1.9, per_second=90, fall=0.6), 0.04, 0.4)

    for i, at in enumerate(AFTERSHOCKS):
        size = 0.75 * 0.82 ** i
        lay(out, burst(rng, 0.03, (1200, 8000), 0.006), at, size * 0.8)
        lay(out, falling_roar(rng, 0.6, ((60, 400, 0.18), (400, 1500, 0.09), (1500, 6000, 0.04))), at, size * 0.6)
        shock = boom(130 - 12 * i, 42, 0.5, fall=0.12, drive=2.2)
        lay(out, shock, at, size)
        lay(out, presence(shock), at, size * 0.3)

    # A big hall around it: the stage the boss was fought in, giving the blast back. Damped, so the
    # tail is a rumble rather than a wash of hiss.
    room = reverb_ir(2.2, rng, predelay=0.03, brightness=0.2, early=6, early_spread=0.08)[0]
    wet = signal.fftconvolve(out, room)[:out.shape[0]]
    out = out + 0.22 * normalized(wet) * np.max(np.abs(out))
    return finish(out, release=0.6)


# --- Writing it out ----------------------------------------------------------------------------


EFFECTS = {
    "enemyShot.wav": (enemy_shot, 1),
    "shotHit.wav": (shot_hit, 2),
    "shieldHit.wav": (shield_hit, 3),
    "enemyDeath.wav": (enemy_death, 4),
    "rockShatter.wav": (rock_shatter, 5),
    "batHit.wav": (bat_hit, 6),
    "bossDeath.wav": (boss_death, 7),
}

SEED = 20261001


def write(path: pathlib.Path, sound: np.ndarray):
    """Brings [sound] down to the output rate and writes it as 16-bit mono PCM."""
    down = signal.resample_poly(sound, OUTPUT_RATE, RENDER_RATE)
    pcm = np.clip(np.round(down * 32767), -32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(OUTPUT_RATE)
        out.writeframes(pcm.tobytes())
    print(f"  {path.name:18} {down.shape[0] / OUTPUT_RATE:5.2f} s  peak {np.max(np.abs(down)):.2f}")


def main() -> int:
    for name, (make, salt) in EFFECTS.items():
        write(ASSETS / name, make(np.random.default_rng([SEED, salt])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
