# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the explosion: one fireball, eight frames of it.

    uv run tools/generate_explosion_sprites.py

What this replaces was not an explosion. `explosion.png` was 273x27 holding five unrelated pictures
- a red square, a yellow star, a grey lump of rock, and two white sparkles - at irregular offsets
that had been measured off some larger sheet. The game walked it on a 25 pixel stride from zero,
which landed on two slots that were **completely empty**, so what actually played when something
died was: blob, nothing, star, nothing, rocks. It strobed, and none of the five frames had anything
to do with the four around it.

This is a fireball that behaves like one:

* **It expands fast and then stops.** Most of the growth is over in the first three frames, which
  is what makes a blast read as a blast rather than as a balloon inflating.
* **It cools as it goes.** White-hot core, then yellow, amber, red, and finally a dark ember that
  fades out - one ramp, walked from the hot end to the cold end as the frames advance.
* **It hollows out.** From the middle of the animation the core opens into a ring, because that is
  what a fireball does once the hot gas has expanded past its own fuel.

The irregular edge is one angular noise reused by every frame rather than fresh noise per frame:
the blast keeps its shape as it grows, which is the difference between fire expanding and static
boiling.
"""

import pathlib
import random
import sys
from math import atan2, cos, hypot, pi, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME = 32
COUNT = 8
CENTER = (FRAME - 1) / 2.0
TAU = 2.0 * pi

# The fireball at its widest, in pixels. Just inside the frame, leaving room for the sparks to
# reach the edge without being clipped square by it.
MAX_RADIUS = 12.6

# Hot to cold, and the cold end fades out. Walking one ramp is what keeps the fireball reading as
# one substance cooling rather than as a sequence of differently colored blobs.
RAMP = ("w", "y", "o", "r", "d", "e1", "e2", "e3")

PALETTE = {
    ".": (0, 0, 0, 0),
    "w": (255, 250, 226, 255),
    "y": (255, 226, 118, 255),
    "o": (255, 164, 48, 255),
    "r": (228, 88, 32, 255),
    "d": (158, 46, 28, 255),
    "e1": (96, 30, 26, 226),
    "e2": (72, 28, 30, 150),
    "e3": (56, 26, 30, 78),
    # The debris thrown off the front of the blast.
    "s": (255, 240, 190, 255),
    "s2": (255, 176, 72, 210),
}

SEED = 20260916
OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "explosion.png"


def angular_noise(lobes, rng):
    """Smooth noise around a circle, periodic over a full turn so the blast's edge closes up."""
    points = [rng.uniform(0.0, 1.0) for _ in range(lobes)]

    def at(angle):
        t = (angle % TAU) / TAU * lobes
        index = int(t) % lobes
        frac = t - int(t)
        a = points[index]
        b = points[(index + 1) % lobes]
        frac = frac * frac * (3.0 - 2.0 * frac)
        return a + (b - a) * frac

    return at


def sparks(count, rng):
    """Fixed headings and speeds, so a spark flies straight out instead of wandering per frame."""
    return [
        (rng.uniform(0.0, TAU), rng.uniform(0.75, 1.45), rng.uniform(0.0, 0.22))
        for _ in range(count)
    ]


def render(index, edge, thrown):
    t = index / (COUNT - 1)
    grid = pa.blank(FRAME, FRAME)

    # Fast out of the gate and then easing off: three quarters of the growth is done by the third
    # frame. A linear expansion reads as inflation, not detonation.
    radius = MAX_RADIUS * (0.30 + 0.70 * (1.0 - (1.0 - t) ** 2))
    # The core opens from about halfway through, which is when the fireball becomes a ring.
    hollow = radius * max(0.0, (t - 0.42) / 0.58) * 0.82

    for y in range(FRAME):
        for x in range(FRAME):
            dx = x - CENTER
            dy = y - CENTER
            distance = hypot(dx, dy)
            angle = atan2(dy, dx)

            # The same lumpy outline at every size, so the blast keeps its identity as it grows.
            rim = radius * (0.66 + 0.54 * edge(angle))
            if distance > rim or distance < hollow:
                continue

            shell = 0.0 if rim <= hollow else (distance - hollow) / (rim - hollow)
            # Hot in the middle of the shell and cooling with time. The time term is what walks the
            # whole blast down the ramp frame by frame.
            heat = (1.0 - shell) * 0.72 + 0.30 - t * 0.86
            step = int((1.0 - max(0.0, min(1.0, heat))) * (len(RAMP) - 1))
            grid[y][x] = RAMP[max(0, min(len(RAMP) - 1, step))]

    # There was a shockwave ring here, and it had to go. A one pixel circle reads as line art at
    # any alpha faint enough not to shout, and the boss draws this artwork near three times over -
    # where that line became a brown hoop plainly drawn around the fire. The blast is stronger for
    # being only fire and debris.

    # Debris, thrown outward and cooling. Drawn last so a spark reads over the fireball it left.
    for angle, speed, delay in thrown:
        if t < delay:
            continue
        travel = MAX_RADIUS * (0.55 + 1.45 * (t - delay)) * speed
        x = int(round(CENTER + cos(angle) * travel))
        y = int(round(CENTER + sin(angle) * travel))
        if not (0 <= x < FRAME and 0 <= y < FRAME):
            continue
        grid[y][x] = "s" if t < 0.55 else "s2"

    return grid


def main() -> int:
    rng = random.Random(SEED)
    edge = angular_noise(9, rng)
    thrown = sparks(18, rng)

    grids = [render(index, edge, thrown) for index in range(COUNT)]
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {COUNT} frames of {FRAME}x{FRAME})")

    for index, grid in enumerate(grids):
        filled = sum(1 for row in grid for key in row if key != pa.TRANSPARENT)
        print(f"  frame {index}: {filled} pixels")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
