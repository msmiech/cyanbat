# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the desert's obstacles: four things standing in the sand, each drawn in four lights.

    uv run tools/generate_desert_obstacle_sprites.py

The cave has a ceiling to hang stalactites from and the jungle a canopy; the desert has an open
sky, so everything in its way stands on the ground:

* **A broken column**, fluted, the top of it snapped off.
* **An obelisk**, its cap still gilded, a line of carving down its face.
* **A hoodoo**, a slab of harder rock left balanced on a stem the wind has worn thin.
* **A ruined wall**, courses of dressed blocks crumbling away to one side.

The four keep the cave's four footprints exactly - the two its ceiling used, turned to stand up,
and the two its floor did - because an obstacle's size is its hit box: the desert is not made
harder by its scenery, but by what comes up out of the sand.

Every obstacle is drawn **four times, stacked top to bottom**: noon, the golden hour, sunset and
night, as the backdrop is (see `generate_desert_background.py`), and the game crossfades them in
step with the sky. They are stone rather than sand - greyer and paler than the dunes they stand in,
so they hold their shape against them - and ringed in the near-black every obstacle has, because
an obstacle is a hazard and has to read as one in any light. Lit from the right, by the sun the
player can see; at night the palette turns the light round to the moon, on the left.
"""

import pathlib
import random
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa
from PIL import Image

KEYFRAMES = 4

# One color per keyframe: noon, golden hour, sunset, night.
PALETTE = {
    "o": ((46, 30, 24), (40, 22, 20), (30, 12, 24), (8, 6, 14)),
    "O": ((96, 72, 54), (86, 50, 38), (60, 26, 42), (20, 14, 34)),
    # stone, from the face turned away from the sun to the face turned to it; at night the moon
    # is on the other side, so the ramp runs the other way
    "s0": ((150, 128, 104), (150, 98, 70), (92, 44, 62), (86, 74, 122)),
    "s1": ((186, 166, 136), (190, 134, 90), (126, 62, 72), (62, 52, 96)),
    "s2": ((216, 200, 170), (226, 170, 112), (176, 90, 82), (44, 36, 74)),
    "s3": ((238, 228, 204), (250, 206, 140), (228, 130, 94), (32, 26, 56)),
    # carving, cut into the stone
    "g": ((118, 96, 78), (122, 76, 56), (70, 32, 48), (24, 18, 42)),
    # the obelisk's gilded cap
    "a0": ((200, 146, 54), (220, 138, 46), (212, 96, 56), (70, 60, 104)),
    "a1": ((250, 214, 112), (255, 208, 100), (255, 170, 98), (128, 118, 170)),
    # sand drifted against the foot of it, in the near dunes' own colors
    "d0": ((208, 160, 94), (170, 106, 64), (92, 42, 60), (40, 28, 70)),
    "d1": ((228, 182, 108), (206, 140, 78), (124, 58, 70), (22, 14, 38)),
    "d2": ((242, 206, 132), (234, 170, 92), (172, 80, 74), (16, 10, 30)),
}

# Across each shape from its left edge to its right: the sun is on the right.
STONE_BANDS = ((0.22, "s0"), (0.5, "s1"), (0.8, "s2"), (1.01, "s3"))
DRIFT_BANDS = ((0.34, "d2"), (0.7, "d1"), (1.01, "d0"))

OUT = pathlib.Path(__file__).resolve().parent.parent / "assets"
SEED = 20260928


def paint(grid, pixels, key):
    for x, y in pixels:
        grid[y][x] = key


def drift(width, height, left, right, rise):
    """A mound of sand against the foot of it, highest in the middle, over the bottom rows."""
    return pa.polygon([
        (left, height + 1), (left + (right - left) * 0.25, height - rise * 0.8),
        ((left + right) / 2, height - rise), (left + (right - left) * 0.8, height - rise * 0.7),
        (right, height + 1),
    ])


def finish(grid, width, height, stone, sand, carving=frozenset()):
    pa.shade_bands_across(grid, stone, STONE_BANDS)
    paint(grid, carving & stone, "g")
    pa.shade_bands(grid, sand, DRIFT_BANDS)
    pa.outline_against(grid, stone - sand, sand, color="O")
    pa.outer_outline(grid, width, height)
    return grid


def broken_column():
    """41x46: a fluted column, two drums and the stump of a third, snapped off at a slant."""
    width, height = 41, 46
    grid = pa.blank(width, height)
    shaft = pa.rasterize(pa.polygon([
        (9, 46), (9, 14), (13, 9), (17, 12), (21, 5), (25, 10), (29, 7), (32, 12), (32, 46),
    ]), width, height)
    # A chunk of the capital fallen against its foot.
    fallen = pa.rasterize(pa.polygon([(27, 46), (29, 38), (38, 37), (40, 46)]), width, height)
    stone = shaft | fallen
    sand = pa.rasterize(drift(width, height, 1, 40, 8), width, height)
    stone -= sand
    # Flutes down the shaft and the joints between its drums.
    carving = set()
    for fx in (13, 17, 21, 25, 29):
        carving |= {(fx, y) for y in range(12, height) if (fx, y) in shaft}
    for jy in (24, 36):
        carving |= {(x, jy) for x in range(9, 33) if (x, jy) in shaft}
    return finish(grid, width, height, stone, sand, frozenset(carving))


def obelisk():
    """38x57: a tapering shaft, a gilded pyramidion, and a line of carving down the middle."""
    width, height = 38, 57
    grid = pa.blank(width, height)
    shaft = pa.rasterize(pa.polygon([(11, 57), (14, 11), (24, 11), (27, 57)]), width, height)
    cap = pa.rasterize(pa.polygon([(14, 11.5), (19, 2), (24, 11.5)]), width, height)
    sand = pa.rasterize(drift(width, height, 2, 36, 7), width, height)
    stone = shaft - sand
    carving = set()
    rng = random.Random(SEED)
    y = 16
    while y < 47:
        # A small glyph: a mark two or three pixels across, picked from a few shapes.
        glyph = (rng.choice(((0, 0), (-1, 0), (1, 0))), rng.choice(((0, 1), (1, 1), (-1, 1))))
        for dx, dy in glyph:
            carving.add((19 + dx, y + dy))
        carving.add((18, y + 2))
        carving.add((20, y + 2))
        y += 5
    pa.shade_bands_across(grid, cap - sand, ((0.5, "a0"), (1.01, "a1")))
    pa.outline_against(grid, cap, shaft - cap, color="O")
    return finish(grid, width, height, stone, sand, frozenset(carving))


def hoodoo():
    """76x50: a slab of cap rock balanced on a stem the wind has worn to a waist."""
    width, height = 76, 50
    grid = pa.blank(width, height)
    cap = pa.rasterize(pa.polygon([
        (4, 14), (10, 6), (26, 3), (48, 4), (66, 6), (73, 12), (70, 19), (52, 21), (30, 21), (10, 20),
    ]), width, height)
    stem = pa.rasterize(pa.polygon([
        (26, 20), (48, 20), (44, 28), (42, 34), (46, 42), (52, 50), (22, 50), (28, 42), (31, 34), (29, 27),
    ]), width, height)
    sand = pa.rasterize(drift(width, height, 12, 64, 7), width, height)
    stone = (cap | stem) - sand
    # The strata the wind has picked out: a line across the cap and a few across the stem.
    carving = {(x, 12) for x in range(8, 70)} | {(x, y) for y in (27, 33, 39) for x in range(20, 56)}
    finish(grid, width, height, stone, sand, frozenset(carving))
    # The cap is a different rock from the stem, and overhangs it: set apart with an inner outline.
    pa.outline_against(grid, cap - sand, stem - cap, color="O")
    return grid


def ruined_wall():
    """96x54: three courses of dressed blocks, the top two crumbling away to the right."""
    width, height = 96, 54
    grid = pa.blank(width, height)
    wall = pa.rasterize(pa.polygon([
        (4, 54), (4, 18), (8, 14), (44, 14), (48, 18), (54, 18), (58, 26), (68, 27), (72, 34),
        (84, 35), (90, 42), (92, 54),
    ]), width, height)
    # A cornice along what is left of the top.
    cornice = pa.rasterize(pa.polygon([(2, 12), (48, 12), (50, 17), (2, 17)]), width, height)
    sand = pa.rasterize(pa.union(
        drift(width, height, -4, 34, 9),
        drift(width, height, 50, 100, 12),
    ), width, height)
    stone = (wall | cornice) - sand
    carving = set()
    for row, y in enumerate(range(24, height, 9)):
        carving |= {(x, y) for x in range(0, width)}
        for x in range(8 + (row % 2) * 8, width, 16):
            carving |= {(x, yy) for yy in range(y + 1, y + 9)}
    carving |= {(x, 17) for x in range(2, 49)}
    return finish(grid, width, height, stone, sand, frozenset(carving))


def save_keyframes(path, grid):
    height = len(grid)
    width = len(grid[0])
    image = Image.new("RGBA", (width, height * KEYFRAMES), (0, 0, 0, 0))
    for frame in range(KEYFRAMES):
        for y, row in enumerate(grid):
            for x, key in enumerate(row):
                if key != pa.TRANSPARENT:
                    image.putpixel((x, frame * height + y), PALETTE[key][frame] + (255,))
    image.save(path)
    return image


def main() -> int:
    for index, build in enumerate((broken_column, obelisk, hoodoo, ruined_wall), start=1):
        path = OUT / f"desertObstacle{index}.png"
        image = save_keyframes(path, build())
        print(f"{path} ({image.width}x{image.height // KEYFRAMES}, {KEYFRAMES} keyframes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
