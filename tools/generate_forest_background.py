# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the forest the second stage scrolls past: one long, seamlessly repeating strip.

    uv run tools/generate_forest_background.py

The same contract as the cave's `generate_background.py`, and for the same reasons:

* **Seamless.** Tiled end to end while it scrolls, so every feature is built from noise that is
  periodic over the width, and every tree, clump and blade of grass wraps its column index. The
  join is exact by construction, and checked at the end rather than trusted.
* **1440 wide**, three framebuffers, so the forest repeats every twelve seconds or so.
* **Dark, and darker than everything flying over it.** Nothing here goes above about a quarter
  brightness. The forest is green and cool so the warm hostiles keep the only warm hues on screen,
  exactly as the cave's blue-grey kept them.

Depth is layers again, back to front: a haze of distant trunks, then two layers of forest, then a
near canopy and undergrowth that frame the shot in near-black. Where the cave had stalactites the
forest has leaf clumps hanging off the canopy line; where it had stalagmites, grass and ferns.
The middle of the frame is where the game is played, so only trunks cross it - vertical, dim, and
far away, so they give the space depth without ever being mistaken for something to dodge.
"""

import pathlib
import random
from math import floor, pi, sin, sqrt

from PIL import Image

WIDTH = 1440
HEIGHT = 320

PALETTE = {
    "void0": (7, 14, 13),
    "void1": (9, 19, 17),
    "void2": (12, 25, 21),
    "haze": (15, 30, 25),
    "haze_lit": (19, 37, 30),
    "far": (21, 40, 31),
    "far_lit": (28, 51, 38),
    "mid": (16, 32, 24),
    "mid_lit": (25, 46, 32),
    "near": (8, 17, 12),
    "near_lit": (14, 29, 19),
    # Bioluminescent caps on the forest floor, in the cave's crystal role: the one saturated thing
    # in the backdrop, used a few dozen times, tying it to the bat's cyan.
    "glow": (40, 110, 104),
    "glow_hot": (96, 190, 172),
}

# Each layer: trunks crossing the frame, a canopy line hanging from the top with leaf clumps off
# it, and a ground line rising from the bottom with grass off it.
#
# As in the cave, the far layers reach deepest into the frame and the near layers hug its edges -
# painted back to front, a near layer that loomed largest would just cover the others.
LAYERS = (
    # name, trunks (count, half-width range), canopy (base, amplitude, harmonics, controls,
    # clumps, clump radius), ground (base, amplitude, harmonics, controls, blades, blade length)
    ("haze", (14, (3.0, 6.0)), (0.23, 0.05, (1, 2), 6, 22, (8.0, 16.0)), (0.20, 0.04, (1,), 5, 40, (5.0, 12.0))),
    ("far", (9, (5.0, 9.0)), (0.17, 0.05, (1, 3), 8, 34, (6.0, 13.0)), (0.155, 0.04, (2, 3), 9, 70, (4.0, 10.0))),
    ("mid", (6, (8.0, 13.0)), (0.12, 0.04, (2, 5), 13, 46, (5.0, 10.0)), (0.11, 0.035, (3, 5), 13, 110, (4.0, 9.0))),
    ("near", (3, (13.0, 18.0)), (0.085, 0.03, (3, 7), 21, 60, (6.0, 12.0)), (0.065, 0.03, (4, 7), 21, 170, (3.0, 8.0))),
)

SEED = 20260925
OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "forestBackground.png"


def wrapped_noise(control_points, rng):
    """Smooth noise exactly periodic over [WIDTH]; see the cave generator for why it must be."""
    points = [rng.random() for _ in range(control_points)]

    def at(x):
        t = x / WIDTH * control_points
        index = int(t) % control_points
        frac = t - int(t)
        a = points[index]
        b = points[(index + 1) % control_points]
        frac = frac * frac * (3.0 - 2.0 * frac)
        return a + (b - a) * frac

    return at


def ridge(base_fraction, amplitude, harmonics, control_points, rng):
    """A per-column depth for one edge of one layer, in pixels, periodic over the width."""
    noise = wrapped_noise(control_points, rng)
    phases = [rng.uniform(0.0, 2.0 * pi) for _ in harmonics]
    weights = [rng.uniform(0.5, 1.0) for _ in harmonics]
    total = sum(weights)

    depths = []
    for x in range(WIDTH):
        swell = sum(
            weight * sin(2.0 * pi * harmonic * x / WIDTH + phase)
            for harmonic, phase, weight in zip(harmonics, phases, weights)
        ) / total
        depth = base_fraction + amplitude * (0.62 * swell + 0.76 * (noise(x) - 0.5))
        depths.append(max(2.0, depth * HEIGHT))
    return depths


def wrapped_distance(a, b):
    d = abs(a - b) % WIDTH
    return min(d, WIDTH - d)


def clumps(line, count, radius_range, rng):
    """
    Leaf clumps as round bulges hanging below a canopy line.

    Each clump is a disc centered on the line itself, so what shows below the line is its lower
    half - a scalloped edge, which is what the underside of a canopy looks like from below.
    Combined with `max`, so two overlapping clumps make one bigger lobe rather than a spike.
    """
    extra = [0.0] * WIDTH
    for _ in range(count):
        center = rng.randrange(WIDTH)
        radius = rng.uniform(*radius_range)
        for offset in range(-int(radius) - 1, int(radius) + 2):
            if abs(offset) > radius:
                continue
            x = (center + offset) % WIDTH
            bulge = line[center] + sqrt(radius * radius - offset * offset) - line[x]
            extra[x] = max(extra[x], bulge)
    return extra


def blades(count, length_range, rng):
    """Grass and fern tips rising off a ground line: narrow, pointed, and wrapped at the seam."""
    extra = [0.0] * WIDTH
    for _ in range(count):
        center = rng.randrange(WIDTH)
        length = rng.uniform(*length_range)
        half_width = rng.uniform(1.0, 2.6)
        for offset in range(-int(half_width) - 1, int(half_width) + 2):
            if abs(offset) > half_width:
                continue
            taper = 1.0 - abs(offset) / half_width
            x = (center + offset) % WIDTH
            extra[x] = max(extra[x], length * taper)
    return extra


def trunks(count, half_width_range, rng):
    """
    Tree trunks as (center, half-width) pairs, spread evenly-ish round the strip.

    Evenly rather than purely at random, so no stretch of the stage is a blank wall or a picket
    fence: a jittered grid is the cheapest way to get "a forest" instead of "some trees".
    """
    spacing = WIDTH / count
    out = []
    for index in range(count):
        center = (index * spacing + rng.uniform(-0.35, 0.35) * spacing) % WIDTH
        out.append((center, rng.uniform(*half_width_range)))
    return out


def stamp_limb(pixels, start, end, r_start, r_end, color):
    """A tapering limb from [start] to [end], stamped as discs along its length and wrapped."""
    steps = int(max(abs(end[0] - start[0]), abs(end[1] - start[1]))) + 1
    for step in range(steps + 1):
        t = step / steps
        cx = start[0] + (end[0] - start[0]) * t
        cy = start[1] + (end[1] - start[1]) * t
        radius = r_start + (r_end - r_start) * t
        for dy in range(-int(radius) - 1, int(radius) + 2):
            for dx in range(-int(radius) - 1, int(radius) + 2):
                if dx * dx + dy * dy > radius * radius:
                    continue
                y = floor(cy + dy)
                if 0 <= y < HEIGHT:
                    pixels[floor(cx + dx) % WIDTH, y] = color


def main() -> int:
    rng = random.Random(SEED)
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()

    for y in range(HEIGHT):
        away = abs(y - HEIGHT / 2) / (HEIGHT / 2)
        tone = "void2" if away < 0.45 else ("void1" if away < 0.78 else "void0")
        for x in range(WIDTH):
            pixels[x, y] = PALETTE[tone]

    glow_spots = []

    for name, trunk_spec, canopy_spec, ground_spec in LAYERS:
        body = PALETTE[name]
        lit = PALETTE[f"{name}_lit"]

        # Trunks first, so the canopy and the ground of the same layer grow over their ends.
        for center, half_width in trunks(trunk_spec[0], trunk_spec[1], rng):
            # A slow sway down the height of the trunk. A dead-straight trunk reads as a pillar,
            # and a colonnade is a building, not a forest.
            sway = rng.uniform(1.0, 2.2) * half_width * 0.45
            sway_phase = rng.uniform(0.0, 2.0 * pi)

            def trunk_x(y):
                return center + sway * sin(2.0 * pi * y / (HEIGHT * 1.7) + sway_phase)

            # Branches reaching up into the canopy, one either side, drawn before the trunk so the
            # trunk covers their roots.
            for side in (-1.0, 1.0):
                start_y = rng.uniform(0.2, 0.34) * HEIGHT
                length = rng.uniform(4.0, 7.0) * half_width
                end = (trunk_x(start_y) + side * length, start_y - length * rng.uniform(0.6, 0.9))
                stamp_limb(pixels, (trunk_x(start_y), start_y), end, half_width * 0.45, 1.0, body)

            for y in range(HEIGHT):
                # A root flare over the bottom fifth, so a trunk stands on the ground instead of
                # running into it like a post.
                low = max(0.0, (y - HEIGHT * 0.78) / (HEIGHT * 0.22))
                width = half_width * (1.0 + 0.9 * low * low)
                middle = trunk_x(y)
                for offset in range(-int(width) - 1, int(width) + 2):
                    if abs(offset) > width:
                        continue
                    # Floored, not truncated: int() rounds toward zero, which puts a trunk that
                    # straddles column 0 a pixel out of place and breaks the seam.
                    x = floor(middle + offset) % WIDTH
                    # Lit down its left side, where the cave's rock is lit: one light for the game.
                    pixels[x, y] = lit if offset < -width + 2 else body

        c_base, c_amp, c_harm, c_ctrl, c_count, c_radius = canopy_spec
        canopy = ridge(c_base, c_amp, c_harm, c_ctrl, rng)
        hanging = clumps(canopy, c_count, c_radius, rng)
        canopy = [depth + hanging[x] for x, depth in enumerate(canopy)]

        g_base, g_amp, g_harm, g_ctrl, g_count, g_length = ground_spec
        ground = ridge(g_base, g_amp, g_harm, g_ctrl, rng)
        rising = blades(g_count, g_length, rng)
        ground_top = list(ground)
        ground = [depth + rising[x] for x, depth in enumerate(ground)]

        for x in range(WIDTH):
            top = int(canopy[x])
            for y in range(min(top, HEIGHT)):
                pixels[x, y] = lit if y >= top - 2 else body

            bottom = int(ground[x])
            for y in range(max(0, HEIGHT - bottom), HEIGHT):
                pixels[x, y] = lit if y < HEIGHT - bottom + 2 else body

        if name == "mid":
            glow_spots = [(x, HEIGHT - int(ground_top[x])) for x in range(0, WIDTH)]

    # Glowing caps along the mid layer's ground line, seeded so the forest is the same every run.
    scatter = random.Random(SEED + 1)
    for _ in range(WIDTH // 30):
        x, y = glow_spots[scatter.randrange(WIDTH)]
        size = scatter.randint(1, 2)
        for dx in range(-size, size + 1):
            pixels[(x + dx) % WIDTH, y - 1] = PALETTE["glow"]
        for dx in range(-size + 1, size):
            pixels[(x + dx) % WIDTH, y - 2] = PALETTE["glow_hot"]
        pixels[x % WIDTH, y] = PALETTE["glow"]

    image.save(OUTPUT)
    print(f"{OUTPUT} ({WIDTH}x{HEIGHT})")

    def column_step(left, right):
        return sum(
            sum(abs(a - b) for a, b in zip(image.getpixel((left, y)), image.getpixel((right, y))))
            for y in range(HEIGHT)
        ) / HEIGHT

    seam = column_step(WIDTH - 1, 0)
    interior = sum(column_step(x, x + 1) for x in range(0, WIDTH - 1, 7)) / len(range(0, WIDTH - 1, 7))
    print(f"mean |edge-to-edge| {seam:.2f} vs mean neighboring-column step {interior:.2f} (of 765)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
