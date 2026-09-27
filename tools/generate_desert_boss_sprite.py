# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the desert's boss: the Sand Wyrm, in parts, and the sand it throws up.

    uv run tools/generate_desert_boss_sprite.py

The cave's boss is one sprite drawn big and the Moth Queen one sprite drawn at her own size. The
Sand Wyrm is a body - a head and nine armored parts behind it, each its own entity, laid by the game
along the path the head has flown and turned to lie along it. So this sheet is not frames of an
animation but pieces of an animal, each in its own 48x48 frame:

    0-1  the head, jaws shut and jaws open, which the game alternates as it flies
    2    a great plate, for the three behind the head
    3    a middling plate
    4    a small plate
    5    the tail
    6-9  a spray of sand, four frames of it going up and coming down

The plates shrink toward the tail **inside** their frames rather than being drawn once and scaled,
so every part keeps the same pixel size; the game sets each part's hit box in from its frame by
how much of the frame it leaves empty.

Everything faces left, like every hostile, and each part reads at any angle it is turned to: the
plates are round with one spine on top, so a turned plate is still obviously the same plate. The
colors are the wyrmlings' own - crimson armor, a pale belly, and a throat and seams that glow like
something molten, which is what a thing fought only at night needs to be seen at all. The seams
glow along each plate's trailing edge, the edge left showing where the next plate forward overlaps
it, as armor does.

The sand is a pale, moonlit grey: the wyrm is only ever fought at night.
"""

import pathlib
import random
import sys
from math import cos, pi, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME = 48
FRAMES = 10

PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (18, 6, 12, 255),
    "O": (46, 14, 24, 255),
    # armor: crimson, darkest underneath
    "r0": (76, 16, 26, 255),
    "r1": (136, 30, 36, 255),
    "r2": (196, 62, 48, 255),
    "r3": (240, 120, 72, 255),
    "r4": (255, 182, 128, 255),
    # the belly, pale as bone
    "b0": (190, 138, 106, 255),
    "b1": (236, 198, 156, 255),
    # molten: the throat and the seams
    "g0": (230, 100, 24, 255),
    "g1": (255, 170, 48, 255),
    "g2": (255, 238, 150, 255),
    # teeth and horns
    "t0": (200, 182, 150, 255),
    "t1": (250, 240, 214, 255),
    # the eye: cold, like every hostile's
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
    # moonlit sand
    "d0": (104, 90, 136, 255),
    "d1": (150, 134, 176, 255),
    "d2": (198, 184, 220, 255),
}

ARMOR = ((0.18, "r4"), (0.38, "r3"), (0.62, "r2"), (0.86, "r1"), (1.01, "r0"))

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "desertBoss.png"
SEED = 20260929


def raster(shape):
    return pa.rasterize(shape, FRAME, FRAME)


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def head(open_jaws):
    """
    A wedge of armor, broad at the back and blunt at the front, with a crest of three horns swept
    back over it and at the front a great round maw ringed with teeth and lit from inside. Two
    tusks curve forward round the maw, closing over it or spread from it; the game alternates the
    two frames as the wyrm flies, so it is always working its jaws.
    """
    grid = pa.blank(FRAME, FRAME)
    cy = 26.0

    # The crest first, so the skull covers its roots.
    horns = set()
    for base_x, tip, width in ((17.0, (25.0, 1.0), 3.4), (26.0, (36.0, 2.0), 3.0), (34.0, (45.0, 6.0), 2.6)):
        horns |= raster(pa.polygon([(base_x - width, 15.0), tip, (base_x + width, 14.0)]))
    pa.shade_bands_across(grid, horns, ((0.5, "t1"), (1.01, "t0")))

    skull = raster(pa.union(
        pa.polygon([(7.0, 15.0), (30.0, 10.0), (44.0, 13.0), (47.0, 26.0), (44.0, 40.0), (30.0, 43.0), (7.0, 38.0)]),
        pa.ellipse(9.0, cy, 7.0, 11.5),
    ))
    pa.shade_bands(grid, skull, ARMOR)
    belly = {(x, y) for x, y in skull if y > 36}
    pa.shade_bands(grid, belly, ((0.5, "b1"), (1.01, "b0")))
    # Plates across the skull, and the collar where the head meets the body, glowing at its seam.
    for px in (21, 31):
        paint(grid, raster(pa.capsule((px + 1, 11), (px - 1, 42), 0.55, 0.55)) & skull, "r0")
    paint(grid, raster(pa.capsule((41, 13), (40, 40), 0.6, 0.6)) & skull, "g1")
    paint(grid, raster(pa.capsule((42.5, 14), (41.5, 39), 0.5, 0.5)) & skull, "g0")

    # The maw: a dark ring of teeth round a molten throat.
    gape = 9.0 if open_jaws else 7.0
    maw = raster(pa.ellipse(8.0, cy, gape * 0.72, gape))
    paint(grid, maw, "p")
    paint(grid, raster(pa.ellipse(9.0, cy, gape * 0.48, gape * 0.66)), "g0")
    paint(grid, raster(pa.ellipse(9.6, cy, gape * 0.28, gape * 0.4)), "g1")
    paint(grid, raster(pa.ellipse(10.0, cy, gape * 0.12, gape * 0.18)), "g2")
    for step in range(12):
        angle = step * 2.0 * pi / 12.0
        tx = 8.0 + cos(angle) * gape * 0.72
        ty = cy + sin(angle) * gape
        paint(grid, raster(pa.ellipse(tx, ty, 1.0, 1.0)), "t1")

    # The tusks, curving forward round the maw from above and below.
    reach = 2.0 if open_jaws else 6.0
    tusks = raster(pa.union(
        pa.capsule((14.0, 14.0), (3.0, 11.0), 2.4, 1.4),
        pa.capsule((3.0, 11.0), (0.8, 11.0 + reach), 1.4, 0.7),
        pa.capsule((14.0, 38.0), (3.0, 41.0), 2.4, 1.4),
        pa.capsule((3.0, 41.0), (0.8, 41.0 - reach), 1.4, 0.7),
    )) - maw
    pa.shade_bands_across(grid, tusks, ((0.5, "t1"), (1.01, "t0")))

    pa.outline_against(grid, horns - skull, skull, color="O")
    pa.outline_against(grid, tusks, skull | maw, color="O")
    # Cold eyes, narrowed, set back along the side of the head.
    paint(grid, {(19, 20), (20, 20), (21, 20), (20, 19)}, "e")
    paint(grid, {(19, 21)}, "p")
    paint(grid, {(25, 19), (26, 19)}, "e")
    pa.outer_outline(grid, FRAME, FRAME)
    return grid


def plate(size):
    """
    One armored segment: a round plate [size] across, a spine on its back, a pale belly, and a
    molten seam along its trailing edge where the next plate forward does not cover it.
    """
    grid = pa.blank(FRAME, FRAME)
    radius = size / 2.0
    cx, cy = 24.0, 25.0
    body = raster(pa.ellipse(cx, cy, radius, radius * 0.9))
    spine = raster(pa.polygon([
        (cx - radius * 0.45, cy - radius * 0.7), (cx + radius * 0.35, cy - radius * 0.9 - radius * 0.55),
        (cx + radius * 0.35, cy - radius * 0.72),
    ])) - body
    pa.shade_bands_across(grid, spine, ((0.5, "t1"), (1.01, "t0")))
    pa.shade_bands(grid, body, ARMOR)
    belly = {(x, y) for x, y in body if y > cy + radius * 0.45}
    pa.shade_bands(grid, belly, ((0.5, "b1"), (1.01, "b0")))
    # Ridges across the plate, and the seam glowing at its back.
    paint(grid, raster(pa.capsule((cx - radius * 0.2, cy - radius), (cx - radius * 0.3, cy + radius), 0.5, 0.5)) & body, "r1")
    seam = raster(pa.ellipse(cx + radius * 0.55, cy, radius * 0.5, radius * 0.85)) & body
    seam -= raster(pa.ellipse(cx + radius * 0.35, cy, radius * 0.5, radius * 0.85))
    paint(grid, seam, "g1")
    paint(grid, {(x, y) for x, y in seam if (x + 1, y) not in seam}, "g0")
    pa.outline_against(grid, spine, body, color="O")
    pa.outer_outline(grid, FRAME, FRAME)
    return grid


def tail():
    """The end of it: a last small plate drawn out into a hooked point."""
    grid = pa.blank(FRAME, FRAME)
    cx, cy = 20.0, 25.0
    body = raster(pa.union(
        pa.ellipse(cx, cy, 10.0, 9.0),
        pa.polygon([(cx + 2, cy - 7), (cx + 20, cy - 2), (cx + 24, cy - 7), (cx + 22, cy + 1), (cx + 2, cy + 8)]),
    ))
    pa.shade_bands(grid, body, ARMOR)
    belly = {(x, y) for x, y in body if y > cy + 4}
    pa.shade_bands(grid, belly, ((0.5, "b1"), (1.01, "b0")))
    paint(grid, raster(pa.capsule((cx + 6, cy - 6), (cx + 5, cy + 7), 0.5, 0.5)) & body, "g1")
    paint(grid, raster(pa.capsule((cx + 13, cy - 4), (cx + 12, cy + 4), 0.5, 0.5)) & body, "r0")
    pa.outer_outline(grid, FRAME, FRAME)
    return grid


def plume(frame, rng):
    """
    Sand thrown up out of the ground, standing on the bottom of its frame: a mound bursting, a
    column of sand going up, the column spreading at its top, and the last of it coming down. Solid
    clumps carry the shape and loose grains fly off round them, scattered from a seed so the spray
    is the same spray on every run.
    """
    grid = pa.blank(FRAME, FRAME)
    # Per frame: the mound, then the clumps as (x offset, height above the ground, radius).
    mound = ((11.0, 7.0), (13.0, 8.0), (18.0, 7.0), (21.0, 4.0))[frame]
    clumps = (
        ((0, 10, 5.5), (-3, 17, 3.5), (2, 22, 2.5)),
        ((0, 10, 7.0), (-2, 20, 6.0), (2, 29, 5.0), (-1, 37, 4.0)),
        ((0, 8, 6.0), (-7, 22, 5.0), (7, 24, 5.0), (-12, 30, 3.5), (12, 31, 3.5), (0, 30, 4.5)),
        ((-15, 10, 3.0), (14, 12, 3.0), (-6, 18, 2.5), (8, 16, 2.5)),
    )[frame]
    grains = (14, 30, 36, 26)[frame]
    spread = (9.0, 12.0, 20.0, 22.0)[frame]
    lift = (26.0, 44.0, 40.0, 28.0)[frame]

    shape = [pa.ellipse(24.0, FRAME, mound[0], mound[1])]
    shape += [pa.ellipse(24.0 + dx, FRAME - up, r * 1.1, r) for dx, up, r in clumps]
    sand = raster(pa.union(*shape))
    pa.shade_bands(grid, sand, ((0.3, "d2"), (0.7, "d1"), (1.01, "d0")))
    pa.outer_outline(grid, FRAME, FRAME, color="O")

    for _ in range(grains):
        t = rng.random()
        x = 24.0 + (rng.random() * 2.0 - 1.0) * spread * (0.3 + 0.7 * t)
        y = FRAME - 3 - lift * (1.0 - t * t) * (0.5 + 0.5 * rng.random())
        xi, yi = int(x), int(y)
        if 0 <= xi < FRAME and 0 <= yi < FRAME - 1 and grid[yi][xi] == ".":
            grid[yi][xi] = rng.choice(("d1", "d2"))
    return grid


def main() -> int:
    rng = random.Random(SEED)
    grids = [head(False), head(True), plate(40), plate(34), plate(28), tail()]
    grids += [plume(frame, rng) for frame in range(4)]
    assert len(grids) == FRAMES
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {FRAMES} frames of {FRAME})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
