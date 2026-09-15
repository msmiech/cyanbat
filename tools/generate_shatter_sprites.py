# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the obstacle's destruction: rock breaking, in seven frames.

    uv run tools/generate_shatter_sprites.py

Obstacles used the explosion. They are pale limestone spires with cyan crystal in them, and shooting
one set it on fire - a rock going up in a white-hot fireball, cooling through amber to an ember.
Nothing in the cave burns, and the blast said the obstacle had been *detonated* rather than broken.

So this is the other thing a hit can mean, and it is built from the opposite parts:

* **No heat anywhere.** The palette is the obstacles' own limestone ramp plus dust. Fire is what
  happens to enemies; stone is what happens to stone, and keeping the two on separate palettes is
  the whole point of the sheet existing.
* **Chunks, not a fireball.** Angular fragments thrown out on fixed headings, tumbling as they go
  and pulled down as they slow - so the debris falls rather than dissolving in place.
* **Dust behind them.** It bursts wider than the chunks, then thins out and settles. It is what
  fills the hole the spire left; the chunks alone read as confetti.

A few fragments are crystal rather than rock, because the obstacles carry veins of it and a break
should show what the thing was made of.
"""

import pathlib
import random
import sys
from math import atan2, cos, hypot, pi, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME = 40
COUNT = 7
CENTER = (FRAME - 1) / 2.0
TAU = 2.0 * pi

# The obstacles' own tones, so a fragment is plainly a piece of the thing that was standing there,
# plus three dust tones that thin out to nothing.
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (9, 13, 22, 255),
    "r3": (214, 228, 240, 255),
    "r2": (172, 188, 208, 255),
    "r1": (132, 148, 172, 255),
    "r0": (92, 106, 132, 255),
    "k": (92, 206, 230, 255),
    "k0": (36, 138, 168, 255),
    # Pale, because what a limestone spire throws up is pale. The first pass used the darker end
    # of the rock ramp and the cloud read as a hole punched in the cave rather than as dust.
    "d0": (176, 190, 212, 100),
    "d1": (146, 160, 184, 66),
    "d2": (118, 132, 156, 36),
}

ROCK_BANDS = ((0.34, "r3"), (0.62, "r2"), (0.86, "r1"), (1.01, "r0"))

# How far the dust reaches at its widest, and how far a chunk can be thrown. The dust goes further:
# it is the part that says how big the thing was, and the chunks read as coming out of it.
DUST_RADIUS = 18.0
CHUNK_REACH = 17.0

CHUNKS = 11
SEED = 20260917
OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "shatter.png"


def angular_noise(lobes, rng):
    """Smooth noise around a circle, periodic over a full turn so the dust cloud closes up."""
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


def fragments(rng):
    """
    The pieces, fixed up front: heading, speed, size, how fast each tumbles, and what it is made of.

    Decided once and read by every frame, so a chunk flies a straight line and spins at its own
    rate instead of being re-scattered each time the sheet advances.
    """
    out = []
    for index in range(CHUNKS):
        # Spread around the circle rather than fully at random, so no quarter of the break comes
        # out empty - eleven pieces is few enough that clumping would show.
        angle = (index / CHUNKS) * TAU + rng.uniform(-0.22, 0.22)
        out.append(
            dict(
                angle=angle,
                speed=rng.uniform(0.55, 1.0),
                size=rng.uniform(1.6, 3.4),
                spin=rng.uniform(-5.0, 5.0),
                # Two or three of the eleven, so a break shows the veins the rock was carrying.
                crystal=index % 4 == 1,
                facets=rng.randint(4, 5),
            )
        )
    return out


def chunk_shape(cx, cy, size, turn, facets, rng_points):
    """One fragment: a small angular blob, turned by however far it has tumbled."""
    points = []
    for index in range(facets):
        angle = turn + (index / facets) * TAU
        radius = size * rng_points[index % len(rng_points)]
        points.append((cx + cos(angle) * radius, cy + sin(angle) * radius))
    return pa.polygon(points)


def render(index, dust_edge, pieces, facet_radii):
    t = index / (COUNT - 1)
    grid = pa.blank(FRAME, FRAME)

    # Dust first, behind everything. Out fast, then thinning: by the last frame it is the faintest
    # tone the palette has and about to be gone.
    spread = DUST_RADIUS * (0.34 + 0.66 * (1.0 - (1.0 - t) ** 2))
    thinning = ("d0", "d0", "d0", "d1", "d1", "d2", "d2")[index]
    for y in range(FRAME):
        for x in range(FRAME):
            dx = x - CENTER
            dy = y - CENTER
            rim = spread * (0.70 + 0.46 * dust_edge(atan2(dy, dx)))
            distance = hypot(dx, dy)
            if distance > rim:
                continue
            # Hollow from the middle out, so the cloud becomes a ring of settling dust rather than
            # a disc that simply fades.
            if t > 0.45 and distance < rim * (t - 0.45) * 1.5:
                continue
            grid[y][x] = thinning

    for piece in pieces:
        # Out fast and slowing, which is what air does to a thrown rock. The drop is squared, so
        # gravity is barely there at first and has taken over by the end.
        travel = CHUNK_REACH * piece["speed"] * (1.0 - (1.0 - t) ** 2)
        fall = 7.0 * t * t
        cx = CENTER + cos(piece["angle"]) * travel
        cy = CENTER + sin(piece["angle"]) * travel + fall

        # Pieces shrink as they go: partly distance, mostly so the frame empties out by the end
        # instead of ending on eleven rocks parked at the edge.
        size = piece["size"] * (1.0 - 0.45 * t)
        if size < 0.7:
            continue

        shape = chunk_shape(cx, cy, size, piece["spin"] * t, piece["facets"], facet_radii)
        pixels = pa.rasterize(shape, FRAME, FRAME)
        if piece["crystal"]:
            for x, y in pixels:
                grid[y][x] = "k" if t < 0.5 else "k0"
        else:
            pa.shade_bands(grid, pixels, ROCK_BANDS)

    return grid


def main() -> int:
    rng = random.Random(SEED)
    dust_edge = angular_noise(8, rng)
    pieces = fragments(rng)
    # One set of facet radii shared by every chunk, so they are all cut from the same rock.
    facet_radii = [rng.uniform(0.62, 1.0) for _ in range(5)]

    grids = [render(index, dust_edge, pieces, facet_radii) for index in range(COUNT)]
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {COUNT} frames of {FRAME}x{FRAME})")

    for index, grid in enumerate(grids):
        filled = sum(1 for row in grid for key in row if key != pa.TRANSPARENT)
        print(f"  frame {index}: {filled} pixels")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
