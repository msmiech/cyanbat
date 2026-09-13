# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the cave's obstacles: two stalactites and two stalagmite clusters.

    uv run tools/generate_obstacle_sprites.py

What this replaces was not pixel art at all. The four files were soft, blurry blobs carrying 734,
1638, 2290 and 1433 distinct colors in sprites no bigger than 96x54 - photographic mush that had
been scaled down and keyed out, sitting in a game whose bat is drawn in fourteen flat tones. They
were the single loudest thing breaking the look.

Three decisions shape the replacements:

* **Cool and desaturated.** The bat is cyan, the hostiles are warm, and these are grey-blue rock.
  Scenery has to be the thing the player's eye skips over on the way to what matters, and the one
  way to lose a colour war is to enter it.
* **Chipped, not smooth.** Each silhouette is built from a jagged edge walked down the spire with
  seeded offsets, so the rock reads as broken stone rather than as a cone. The seed is fixed, so
  the four are always the same four.
* **Crystal, sparingly.** A few cyan veins per rock, which is the one place the cave and the bat
  share a colour - enough to tie the scenery to the rest of the palette without making a lump of
  rock look like something to shoot.

Sizes are held at exactly what the old files were. Obstacles take their collision box straight from
the pixmap, and bottom ones are anchored by their own height, so a different size here is a
different game - this is a redraw, not a rebalance.
"""

import pathlib
import random
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

# Pale limestone, not dark rock, and this is the one number here worth arguing with. The first
# pass reasoned that scenery should recede and drew these in the greys of the cave wall - and over
# the actual background they all but disappeared. An obstacle is not scenery: it is a hazard that
# takes a third of the bat's health on contact, and a hazard the player cannot see is not a
# difficulty, it is a cheat. The artwork this replaces was near-white for exactly this reason.
#
# So: cool enough to stay out of the warm hostiles' territory, light enough to read instantly
# against a dark cave, and ringed in near-black so the silhouette holds wherever it lands.
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (9, 13, 22, 255),
    "r0": (92, 106, 132, 255),
    "r1": (132, 148, 172, 255),
    "r2": (172, 188, 208, 255),
    "r3": (214, 228, 240, 255),
    # The veins, darkened to suit: a pale rock needs a deeper cyan cut into it, where the dark one
    # wanted a bright one. Two tones is enough for a thing four pixels wide.
    "k0": (18, 66, 88, 255),
    "k1": (36, 138, 168, 255),
    "k2": (92, 206, 230, 255),
}

ROCK_BANDS = ((0.26, "r3"), (0.55, "r2"), (0.82, "r1"), (1.01, "r0"))
VEIN_BANDS = ((0.45, "k2"), (1.01, "k1"))

OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"

# One seed for the whole file, so the four rocks never shuffle between runs.
SEED = 20260914


def spire(width, height, hanging, base_fraction, rng, jag=0.16, steps=9):
    """
    One tapered spire, flush with the edge it grows out of and chipped down both flanks.

    [hanging] picks which edge that is: a stalactite is anchored to the top of its frame because
    that is where the ceiling is, and a stalagmite to the bottom. The taper is walked in [steps]
    rather than drawn as a straight line so each flank comes out as a run of facets - a smooth
    cone reads as a traffic bollard, not as stone.
    """
    half = width * base_fraction / 2.0
    center = width / 2.0
    left, right = [], []

    for step in range(steps + 1):
        t = step / steps
        # Squared, so the rock keeps its bulk near the ceiling and runs out quickly at the tip,
        # which is the shape a drip actually leaves behind.
        span = half * (1.0 - t * t * 0.94)
        wobble = jag * width
        lx = center - span + rng.uniform(-wobble, wobble) * (1.0 - t) * 0.9
        rx = center + span + rng.uniform(-wobble, wobble) * (1.0 - t) * 0.9
        y = t * (height - 1) if hanging else (height - 1) - t * (height - 1)
        left.append((min(lx, center - 0.6), y))
        right.append((max(rx, center + 0.6), y))

    # Down one flank and back up the other; the open end is the edge it is anchored to.
    return pa.polygon(left + list(reversed(right)))


def veins(shape_pixels, width, height, rng, count):
    """A few slivers of crystal, seeded inside the rock and clipped to it."""
    body = sorted(shape_pixels)
    if not body:
        return set()

    out = set()
    for _ in range(count):
        cx, cy = body[rng.randrange(len(body))]
        length = rng.uniform(2.6, 5.2)
        lean = rng.uniform(-0.7, 0.7)
        sliver = pa.polygon([
            (cx, cy - length),
            (cx + 1.5 + lean, cy),
            (cx, cy + length * 0.55),
            (cx - 1.5 + lean, cy),
        ])
        out |= pa.rasterize(sliver, width, height) & shape_pixels
    return out


def render(width, height, hanging, base_fraction, vein_count, lumps, rng):
    grid = pa.blank(width, height)

    shape = spire(width, height, hanging, base_fraction, rng)
    # Extra masses low on the rock, which is what turns a single spike into the cluster the wider
    # sprites need. Rooted on the anchored edge so they read as part of the same formation.
    #
    # Wide and flat rather than round, and overlapping the spire rather than beside it. Round lumps
    # sitting clear of it read as balls set down next to a rock; flattened and merged, they read as
    # the rubble a formation is standing in.
    for fraction, radius in lumps:
        cx = width * fraction
        cy = 0.0 if hanging else float(height - 1)
        shape = pa.union(
            shape,
            pa.ellipse(cx, cy, radius * width * 0.72, radius * height * 0.52),
        )

    rock = pa.rasterize(shape, width, height)
    pa.shade_bands_across(grid, rock, ROCK_BANDS)

    crystal = veins(rock, width, height, rng, vein_count)
    pa.shade_bands_across(grid, crystal, VEIN_BANDS)
    # A dark lip where a vein meets the rock, so it reads as set into the stone rather than
    # painted onto it.
    pa.outline_against(grid, crystal, rock - crystal, color="k0")

    pa.outer_outline(grid, width, height)
    return grid


# width, height, hanging, base fraction, veins, extra lumps as (x fraction, radius)
ROCKS = (
    ("topObstacle1.png", 41, 46, True, 0.94, 3, ()),
    ("topObstacle2.png", 38, 57, True, 0.88, 4, ()),
    ("bottomObstacle1.png", 76, 50, False, 0.56, 4, ((0.26, 0.34), (0.74, 0.30))),
    ("bottomObstacle2.png", 96, 54, False, 0.48, 5, ((0.24, 0.32), (0.70, 0.36), (0.88, 0.24))),
)


def main() -> int:
    rng = random.Random(SEED)
    for name, width, height, hanging, base, vein_count, lumps in ROCKS:
        grid = render(width, height, hanging, base, vein_count, lumps, rng)
        image = pa.save_single(OUT / name, grid, PALETTE)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
