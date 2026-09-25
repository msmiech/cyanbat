# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the forest's obstacles: two hanging from the canopy, two standing on the floor.

    uv run tools/generate_forest_obstacle_sprites.py

The cave's obstacles are stalactites and stalagmites; the forest's are what hangs off a canopy
and what grows out of a forest floor:

* **A dead branch**, snapped and hanging, with a few leaves still on it.
* **A curtain of vines**, three strands with leaves along them.
* **A mossy stump**, with glowing caps growing out of its side.
* **A bramble**, a thicket bristling with thorns.

The cave's decision about brightness is kept, and for the reason it gives: an obstacle is a hazard
that costs the bat a third of its health, not scenery. So these are pale - lichen-grey bark and a
bright leaf green - against a backdrop that stays under a quarter brightness, and they are ringed
in near-black so the silhouette holds wherever it lands. The glowing caps play the cave's crystal
veins: a little of the bat's cyan, set into the scenery.

Sizes match the cave's four exactly. Obstacles take their collision box from the pixmap and the
generator anchors the bottom ones by their height, so the forest keeps the cave's footprint and
difficulty from scenery - what makes level 2 harder is its enemies, not bigger rocks.
"""

import pathlib
import random
import sys
from math import sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (8, 14, 10, 255),
    "O": (18, 30, 22, 255),
    # bark: pale lichen-grey, cool enough to stay out of the hostiles' warm range
    "r0": (84, 96, 86, 255),
    "r1": (122, 136, 120, 255),
    "r2": (164, 178, 158, 255),
    "r3": (206, 218, 196, 255),
    # leaves and moss
    "g0": (34, 90, 50, 255),
    "g1": (60, 138, 70, 255),
    "g2": (108, 190, 96, 255),
    "g3": (170, 230, 140, 255),
    # glowing caps, the forest's crystal
    "k0": (18, 66, 70, 255),
    "k1": (40, 150, 150, 255),
    "k2": (120, 230, 210, 255),
}

BARK_BANDS = ((0.26, "r3"), (0.55, "r2"), (0.82, "r1"), (1.01, "r0"))
LEAF_BANDS = ((0.3, "g3"), (0.6, "g2"), (0.85, "g1"), (1.01, "g0"))

OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"

SEED = 20260926


def leaf(cx, cy, length, angle_sign=1.0):
    """One leaf: a pointed oval, tilted a little so a row of them does not read as beads."""
    return pa.polygon([
        (cx - length * 0.5, cy),
        (cx - length * 0.1, cy - length * 0.32 * angle_sign),
        (cx + length * 0.5, cy + length * 0.1 * angle_sign),
        (cx - length * 0.1, cy + length * 0.26 * angle_sign),
    ])


def finish(grid, width, height, bark, leaves, extra_outline=()):
    pa.shade_bands_across(grid, bark, BARK_BANDS)
    pa.shade_bands(grid, leaves, LEAF_BANDS)
    pa.outline_against(grid, leaves - bark, bark, color="O")
    for subject, others in extra_outline:
        pa.outline_against(grid, subject, others, color="O")
    pa.outer_outline(grid, width, height)
    return grid


def branch(width, height, rng):
    """A snapped branch hanging from the canopy, forking once, with leaves at its ends."""
    grid = pa.blank(width, height)
    top = (width * 0.52, 0.0)
    knee = (width * 0.44, height * 0.46)
    tip = (width * 0.56, height * 0.9)
    fork_tip = (width * 0.14, height * 0.66)
    stub_tip = (width * 0.86, height * 0.3)

    wood = pa.union(
        pa.capsule(top, knee, width * 0.16, width * 0.11),
        pa.capsule(knee, tip, width * 0.11, width * 0.04),
        pa.capsule(knee, fork_tip, width * 0.08, width * 0.035),
        pa.capsule((width * 0.5, height * 0.18), stub_tip, width * 0.07, width * 0.04),
        # A torn crown where it came away from the tree.
        pa.ellipse(width * 0.52, 0.0, width * 0.3, height * 0.08),
    )
    bark = pa.rasterize(wood, width, height)

    foliage = []
    for (cx, cy), count in ((fork_tip, 3), (stub_tip, 2), ((width * 0.5, height * 0.7), 2)):
        for _ in range(count):
            foliage.append(leaf(
                cx + rng.uniform(-4, 4), cy + rng.uniform(-3, 5),
                rng.uniform(9, 12), rng.choice((-1.0, 1.0)),
            ))
    leaves = pa.rasterize(pa.union(*foliage), width, height)
    return finish(grid, width, height, bark - leaves, leaves)


def vines(width, height, rng):
    """Three strands of vine off a clump of canopy, each wavering and hung with leaves."""
    grid = pa.blank(width, height)
    strands = []
    foliage = [pa.ellipse(width * 0.5, 0.0, width * 0.55, height * 0.14)]
    for index, (x_fraction, length_fraction) in enumerate(((0.22, 0.78), (0.52, 1.0), (0.8, 0.66))):
        base_x = width * x_fraction
        length = (height - 3) * length_fraction
        phase = rng.uniform(0.0, 6.28)
        points = [
            (base_x + sin(phase + t * 0.35) * 3.2, t * length / 10.0)
            for t in range(11)
        ]
        for a, b in zip(points, points[1:]):
            strands.append(pa.capsule(a, b, 1.5, 1.4))
        for step in range(2, 11, 2):
            px, py = points[step]
            side = 1.0 if (step + index) % 4 == 0 else -1.0
            foliage.append(leaf(px + side * 3.5, py, rng.uniform(7, 9), side))
        # A heavier knot of leaves at the end of each strand, so it has a tip to read.
        foliage.append(pa.ellipse(points[-1][0], points[-1][1] - 1, 3.6, 3.2))

    stems = pa.rasterize(pa.union(*strands), width, height)
    leaves = pa.rasterize(pa.union(*foliage), width, height)
    grid = pa.blank(width, height)
    # Vines are green wood: banded in the leaf ramp's darker half so they sit behind the leaves.
    pa.shade_bands_across(grid, stems - leaves, ((0.5, "g1"), (1.01, "g0")))
    pa.shade_bands(grid, leaves, LEAF_BANDS)
    pa.outline_against(grid, stems - leaves, leaves, color="O")
    pa.outer_outline(grid, width, height)
    return grid


def stump(width, height, rng):
    """A broken stump on splayed roots, moss on the rim, glowing caps up one side."""
    grid = pa.blank(width, height)
    bottom = height - 1.0
    cx = width * 0.46
    half = width * 0.17
    # Straight-sided, with a jagged top where it broke: a cone reads as a rock, a cylinder as wood.
    body = pa.polygon([
        (cx - half * 1.1, bottom), (cx - half, height * 0.3),
        (cx - half * 0.5, height * 0.22), (cx - half * 0.1, height * 0.3),
        (cx + half * 0.3, height * 0.12), (cx + half * 0.7, height * 0.28),
        (cx + half, height * 0.24), (cx + half * 1.1, bottom),
    ])
    # Roots splayed well clear of the trunk, so they read as roots and not as a skirt.
    roots = pa.union(
        pa.capsule((cx - half * 0.8, height * 0.62), (cx - width * 0.44, bottom), 3.6, 1.8),
        pa.capsule((cx + half * 0.8, height * 0.6), (cx + width * 0.5, bottom), 3.6, 1.8),
        pa.capsule((cx + half * 0.3, height * 0.8), (cx + width * 0.28, bottom), 2.8, 1.6),
    )
    bark = pa.rasterize(pa.union(body, roots), width, height)

    # Grain down the trunk, a few darker lines, so it is bark and not stone.
    grain = set()
    for fx in (-0.55, 0.05, 0.6):
        x = cx + half * fx
        grain |= pa.rasterize(pa.capsule((x, height * 0.36), (x + 0.8, bottom - 2), 0.5, 0.5), width, height)

    moss = pa.rasterize(pa.union(
        pa.ellipse(cx - half * 0.4, height * 0.26, half * 0.9, height * 0.07),
        pa.ellipse(cx - width * 0.36, bottom, width * 0.1, height * 0.08),
        pa.ellipse(cx + width * 0.42, bottom, width * 0.09, height * 0.07),
    ), width, height)

    caps = set()
    stalks = set()
    for fx, fy, r in ((0.2, 0.5, 5.0), (0.24, 0.68, 3.8)):
        x, y = cx + half + width * fx * 0.3, height * fy
        caps |= pa.rasterize(pa.ellipse(x + r * 0.5, y, r, r * 0.55), width, height)
        stalks |= pa.rasterize(pa.capsule((cx + half - 1, y + 1), (x + r * 0.4, y + 1), 1.0, 1.0), width, height)

    finish(grid, width, height, bark - moss - caps, moss)
    for x, y in grain & bark - moss - caps:
        grid[y][x] = "r0"
    for x, y in stalks - caps:
        grid[y][x] = "k0"
    pa.shade_bands(grid, caps, ((0.4, "k2"), (1.01, "k1")))
    pa.outline_against(grid, caps, bark - caps, color="k0")
    return grid


def bramble(width, height, rng):
    """A low thicket of leafy lobes bristling with thorns."""
    grid = pa.blank(width, height)
    bottom = height - 1.0
    lobes = []
    for fraction, rx, ry in ((0.2, 0.2, 0.55), (0.45, 0.24, 0.85), (0.7, 0.22, 0.7), (0.88, 0.13, 0.45)):
        lobes.append(pa.ellipse(width * fraction, bottom, width * rx, height * ry))
    bush = pa.rasterize(pa.union(*lobes), width, height)

    thorns = []
    edge = sorted(p for p in bush if not all(n in bush for n in pa.neighbors(*p)) and p[1] < bottom - 3)
    for _ in range(18):
        x, y = edge[rng.randrange(len(edge))]
        dx = (x - width * 0.5) / width
        lean = dx * 6.0 + rng.uniform(-1.5, 1.5)
        thorns.append(pa.polygon([(x - 1.4, y + 1.0), (x + lean, y - rng.uniform(4.0, 6.5)), (x + 1.4, y + 1.0)]))
    spikes = pa.rasterize(pa.union(*thorns), width, height) - bush

    pa.shade_bands(grid, bush, LEAF_BANDS)

    # Leaf texture: scattered clusters in the tone below where they sit, with a lit pixel on top,
    # so the thicket reads as leaves rather than as one smooth hedge.
    darker = {"g3": "g2", "g2": "g1", "g1": "g0", "g0": "g0"}
    for _ in range(40):
        x, y = sorted(bush)[rng.randrange(len(bush))]
        cluster = pa.rasterize(leaf(x, y, rng.uniform(5, 7), rng.choice((-1.0, 1.0))), width, height) & bush
        for px, py in cluster:
            grid[py][px] = darker[grid[py][px]]
        if (x, y - 2) in bush:
            grid[y - 2][x] = "g3"

    # The thorns are pale, like the bark they grow from: dark thorns vanished against the forest.
    for x, y in spikes:
        grid[y][x] = "r2"
    pa.outer_outline(grid, width, height)
    return grid


# name, draw, width, height - sizes held at the cave's
OBSTACLES = (
    ("forestTopObstacle1.png", branch, 41, 46),
    ("forestTopObstacle2.png", vines, 38, 57),
    ("forestBottomObstacle1.png", stump, 76, 50),
    ("forestBottomObstacle2.png", bramble, 96, 54),
)


def main() -> int:
    rng = random.Random(SEED)
    for name, draw, width, height in OBSTACLES:
        grid = draw(width, height, rng)
        image = pa.save_single(OUT / name, grid, PALETTE)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
