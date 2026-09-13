# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the cave the game scrolls past: one long, seamlessly repeating strip.

    uv run tools/generate_background.py

What this replaces was `background.jpg`: 838x320 of blurry photographic mush carrying **42406
distinct colors**, in a game whose every other sprite is now flat pixel art in a palette of about
a dozen. It was also a JPEG, which is the wrong container for flat art twice over - it cannot store
hard edges without ringing, and it had already thrown away the edges it was given.

Three things this has to be:

* **Seamless.** The strip is tiled end to end while it scrolls, so column 0 has to continue from
  column W-1 exactly. That is not eyeballed here - every ridgeline is built from noise that is
  *periodic over the width by construction*, so the wrap is exact rather than nearly right.
* **Long.** 1440 rather than 838, which is three framebuffers rather than one and three quarters.
  The strip repeats roughly every 12 seconds at the scroll speed instead of every 7.
* **Dark, and darker than everything that flies over it.** The obstacles are pale limestone and
  the sprites are saturated; the cave has to stay out of their way. Nothing here goes above about a
  quarter brightness, and the palette is cool so the warm hostiles keep the only warm hue on screen.

Depth comes from three ridge layers rather than from a parallax the engine does not do: the far
wall is lightest and softest, the near wall is darkest and sharpest, and that ordering alone is
enough to read as distance.
"""

import pathlib
import random
from math import cos, pi, sin

from PIL import Image

WIDTH = 1440
HEIGHT = 320

# Cool and dark. The two crystal tones are the only saturated thing in here and they are used a few
# dozen times in 1440 columns - they tie the cave to the bat's cyan without lighting the place up.
PALETTE = {
    "void0": (10, 13, 22),
    "void1": (14, 18, 30),
    "void2": (19, 24, 39),
    "haze": (17, 21, 34),
    "haze_lit": (22, 27, 43),
    "far": (30, 37, 56),
    "far_lit": (40, 49, 71),
    "mid": (23, 29, 46),
    "mid_lit": (33, 41, 61),
    "near": (13, 17, 28),
    "near_lit": (21, 27, 42),
    "crystal": (38, 104, 126),
    "crystal_hot": (78, 170, 192),
}

# How far each layer's rock reaches in from the ceiling and the floor, as a fraction of the height.
#
# The far layer reaches *deepest* and the near layer hugs the frame edge, which looks backwards
# until you remember the draw order: back to front, each layer painting over the last. A near layer
# that loomed largest would simply cover the other two and the depth would be gone. Drawn this way
# the dark near rock frames the shot and the lighter wall recedes behind it.
#
# Teeth are what make it a cave rather than a landscape: stalactites hanging off the ceiling ridge
# and stalagmites rising off the floor one. The far layer carries the long ones, because those are
# the only ones with room to show past the layers in front.
LAYERS = (
    # name, ceiling, floor, amplitude, harmonics, controls, rim, teeth, length, half-width
    # The most distant wall, barely above the void it sits in. It reaches furthest into the middle
    # of the frame, where the game happens, and gets away with it precisely because there is almost
    # no contrast in it: it breaks up an otherwise dead band without competing with a single sprite.
    ("haze", 0.260, 0.245, 0.060, (1,), 5, 4, 6, (22.0, 38.0), (16.0, 32.0)),
    ("far", 0.215, 0.200, 0.055, (1, 2), 7, 3, 9, (16.0, 30.0), (9.0, 20.0)),
    ("mid", 0.155, 0.145, 0.045, (2, 3, 5), 13, 3, 16, (12.0, 22.0), (5.0, 12.0)),
    ("near", 0.095, 0.090, 0.035, (3, 5, 8), 21, 2, 26, (8.0, 17.0), (3.0, 7.0)),
)

SEED = 20260915
OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "background.png"


def wrapped_noise(control_points, rng):
    """
    Smooth noise that is exactly periodic over [WIDTH].

    The control points are a ring rather than a line - the last one interpolates back into the
    first - so a value read at x and at x + WIDTH is the same value, not merely a similar one. That
    exactness is the whole reason the strip can be tiled without a seam, and it is why this is
    generated rather than painted: a hand-drawn wall would be seamless only as far as the eye that
    drew it.
    """
    points = [rng.random() for _ in range(control_points)]

    def at(x):
        t = x / WIDTH * control_points
        index = int(t) % control_points
        frac = t - int(t)
        a = points[index]
        b = points[(index + 1) % control_points]
        # Smoothstep, so the ridges undulate instead of meeting at visible corners.
        frac = frac * frac * (3.0 - 2.0 * frac)
        return a + (b - a) * frac

    return at


def ridge(base_fraction, amplitude, harmonics, control_points, rng):
    """
    A per-column depth for one edge of one layer, in pixels.

    Integer harmonics of the full width, plus the wrapped noise: both repeat exactly over WIDTH, so
    their sum does too. The harmonics give the long swells a cave wall has and the noise breaks
    them up so the result does not read as a sine wave.
    """
    noise = wrapped_noise(control_points, rng)
    phases = [rng.uniform(0.0, 2.0 * pi) for _ in harmonics]
    weights = [rng.uniform(0.5, 1.0) for _ in harmonics]
    total = sum(weights)

    depths = []
    for x in range(WIDTH):
        swell = 0.0
        for harmonic, phase, weight in zip(harmonics, phases, weights):
            swell += weight * sin(2.0 * pi * harmonic * x / WIDTH + phase)
        swell /= total
        depth = base_fraction + amplitude * (0.62 * swell + 0.76 * (noise(x) - 0.5))
        depths.append(max(2.0, depth * HEIGHT))
    return depths


def teeth(count, length_range, half_width_range, rng):
    """
    A row of stalactites as an extra depth per column, to be added onto a layer's ridge.

    Built into their own array and combined with `max` rather than added as they are placed, so two
    teeth that overlap make one broad tooth instead of one twice as long.

    The column index wraps, which is all it takes for a tooth sitting on the seam to be drawn half
    at each end of the strip and to come back together when the strip is tiled.
    """
    extra = [0.0] * WIDTH
    for _ in range(count):
        center = rng.randrange(WIDTH)
        length = rng.uniform(*length_range)
        half_width = rng.uniform(*half_width_range)
        span = int(half_width) + 1
        for offset in range(-span, span + 1):
            if abs(offset) > half_width:
                continue
            # Squared, so a tooth tapers to a point instead of running out as a straight cone.
            taper = 1.0 - abs(offset) / half_width
            x = (center + offset) % WIDTH
            extra[x] = max(extra[x], length * taper * taper)
    return extra


def main() -> int:
    rng = random.Random(SEED)
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()

    # The void behind everything: a few flat bands rather than a smooth gradient, because a smooth
    # one would be the only place in the game with hundreds of colors in it.
    for y in range(HEIGHT):
        away = abs(y - HEIGHT / 2) / (HEIGHT / 2)
        tone = "void2" if away < 0.45 else ("void1" if away < 0.78 else "void0")
        band = PALETTE[tone]
        for x in range(WIDTH):
            pixels[x, y] = band

    for (
        name, ceiling_base, floor_base, amplitude, harmonics, controls, rim,
        tooth_count, tooth_length, tooth_half_width,
    ) in LAYERS:
        body = PALETTE[name]
        lit = PALETTE[f"{name}_lit"]
        ceiling = ridge(ceiling_base, amplitude, harmonics, controls, rng)
        floor = ridge(floor_base, amplitude, harmonics, controls, rng)

        hanging = teeth(tooth_count, tooth_length, tooth_half_width, rng)
        rising = teeth(tooth_count, tooth_length, tooth_half_width, rng)
        ceiling = [depth + hanging[x] for x, depth in enumerate(ceiling)]
        floor = [depth + rising[x] for x, depth in enumerate(floor)]

        for x in range(WIDTH):
            top = int(ceiling[x])
            for y in range(min(top, HEIGHT)):
                # A lit lip along the inner edge of the mass, so a layer reads as rock with a face
                # on it rather than as a slab of one color.
                pixels[x, y] = lit if y >= top - rim else body

            bottom = int(floor[x])
            for y in range(max(0, HEIGHT - bottom), HEIGHT):
                pixels[x, y] = lit if y < HEIGHT - bottom + rim else body

    # Crystal, in the same places the obstacles carry it and just as sparingly. Seeded from the same
    # generator, so the cave is the same cave every run.
    scatter = random.Random(SEED + 1)
    for _ in range(WIDTH // 26):
        cx = scatter.randrange(WIDTH)
        cy = scatter.choice(
            [scatter.randrange(6, int(HEIGHT * 0.30)), scatter.randrange(int(HEIGHT * 0.72), HEIGHT - 6)]
        )
        length = scatter.randint(2, 4)
        for step in range(-length, length + 1):
            y = cy + step
            if 0 <= y < HEIGHT:
                hot = abs(step) <= length // 2
                pixels[cx % WIDTH, y] = PALETTE["crystal_hot"] if hot else PALETTE["crystal"]
                if abs(step) < length:
                    pixels[(cx + 1) % WIDTH, y] = PALETTE["crystal"]

    image.save(OUTPUT)
    print(f"{OUTPUT} ({WIDTH}x{HEIGHT})")

    # The claim this file is built on, checked rather than asserted: the strip has to meet itself.
    # Measured against the average step between *any* two neighboring columns, because "zero" only
    # means something next to how much the picture normally changes from one column to the next.
    def column_step(left, right):
        return sum(
            sum(abs(a - b) for a, b in zip(image.getpixel((left, y)), image.getpixel((right, y))))
            for y in range(HEIGHT)
        ) / HEIGHT

    seam = column_step(WIDTH - 1, 0)
    interior = sum(column_step(x, x + 1) for x in range(0, WIDTH - 1, 7)) / len(
        range(0, WIDTH - 1, 7)
    )
    print(f"mean |edge-to-edge| {seam:.2f} vs mean neighboring-column step {interior:.2f} (of 765)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
