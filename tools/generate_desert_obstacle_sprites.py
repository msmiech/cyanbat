# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the desert's obstacles: four things standing in the sand, each drawn in four lights.

    uv run tools/generate_desert_obstacle_sprites.py

The cave has a ceiling to hang stalactites from and the forest a canopy; the desert has an open
sky, so everything in its way stands on the ground. What is built is Aztec, the ruins of the same
people whose temples stand on the horizon (see `generate_desert_background.py`):

* **A sun stone**, the great carved calendar disc, stood on its edge with a bite out of its rim.
* **A stone warrior**, one of the pillars carved as a figure that held up a temple's roof.
* **A hoodoo**, a slab of harder rock left balanced on a stem the wind has worn thin.
* **A ruined platform**, the front of a temple's base, broken away to one side.

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
import sys
from math import atan2, degrees, hypot, radians

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
    # gilding: the sun's face on the stone, the warrior's breastplate
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


def rect(left, top, right, bottom):
    """Pixels [left, right) by [top, bottom), for the squared-off parts of carved stone."""
    return {(x, y) for y in range(top, bottom) for x in range(left, right)}


def sun_stone():
    """
    41x46: a great carved disc stood on its edge and half sunk in the sand - a ring of the sun's
    rays round a ring of day signs, round the sun's gilded face - with a bite out of its rim.
    """
    width, height = 41, 46
    grid = pa.blank(width, height)
    cx, cy, radius = 20.5, 21.5, 20.0
    disc = pa.rasterize(pa.ellipse(cx, cy, radius, radius), width, height)
    chipped = pa.rasterize(pa.polygon([
        (25, -1), (42, -1), (42, 16), (37, 15), (35, 10), (30, 8), (27, 4),
    ]), width, height)
    sand = pa.rasterize(drift(width, height, -1, 42, 7), width, height)
    stone = disc - chipped - sand

    def polar(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        return hypot(dx, dy), degrees(atan2(dy, dx)) % 360

    def across(distance, angle, step, phase=0.0):
        """How many pixels round the ring [angle] is from the nearest multiple of [step] degrees."""
        return radians(abs((angle - phase + step / 2) % step - step / 2)) * distance

    outer, middle, inner = 17.6, 13.2, 9.0
    carving = set()
    face = set()
    for x, y in disc:
        distance, angle = polar(x, y)
        # The rings between the bands, one pixel wide each.
        if any(abs(distance - ring) < 0.55 for ring in (outer, middle, inner)):
            carving.add((x, y))
        # The rays: eight points between the outer two rings, aimed outward, their bases meeting.
        elif middle < distance < outer:
            half = (outer - distance) / (outer - middle) * middle * 0.39
            if abs(across(distance, angle, 45, 22.5) - half) < 0.6:
                carving.add((x, y))
        # The day signs: twenty boxes round the middle band.
        elif inner < distance < middle:
            if across(distance, angle, 18) < 0.5:
                carving.add((x, y))
        elif distance < 5.6:
            face.add((x, y))
        # The four arms of the sign of movement, on the diagonals round the face.
        elif distance < 8.0 and across(distance, angle, 90, 45) < 1.4:
            carving.add((x, y))
    grid = finish(grid, width, height, stone, sand, frozenset(carving))
    gold = face - sand
    pa.shade_bands_across(grid, gold, ((0.5, "a0"), (1.01, "a1")))
    # The face: two slits of eyes, and a tongue out of the mouth, down over its chin.
    paint(grid, rect(17, 20, 19, 21) | rect(22, 20, 24, 21) | rect(19, 23, 22, 24) | rect(20, 24, 21, 27), "O")
    pa.outline_against(grid, gold, stone - gold, color="O")
    return grid


def stone_warrior():
    """
    38x57: a warrior carved as a pillar, one of the figures a temple's roof once stood on - a crown
    of feathers, a square face, ear spools, a gilded breastplate, and a belt over a kilt.
    """
    width, height = 38, 57
    grid = pa.blank(width, height)
    # The crown: five upright feathers with rounded tips, over a band.
    feathers = set()
    for left in (9, 13, 17, 21, 25):
        feathers |= pa.rasterize(pa.union(
            pa.polygon([(left, 13), (left, 4), (left + 4, 4), (left + 4, 13)]),
            pa.ellipse(left + 2, 4, 2, 2.6),
        ), width, height)
    band = rect(8, 12, 30, 16)
    face = rect(11, 16, 27, 28)
    spools = rect(7, 17, 11, 23) | rect(27, 17, 31, 23)
    torso = pa.rasterize(pa.polygon([(7, 28), (31, 28), (30, 46), (8, 46)]), width, height)
    legs = rect(9, 46, 29, 57)
    sand = pa.rasterize(drift(width, height, 1, 37, 7), width, height)
    stone = (feathers | band | face | spools | torso | legs) - sand
    # The breastplate is a butterfly: a broad wing and a smaller one below it, either side of its
    # body.
    wings = [
        [(11.5, 29.5), (18, 32), (18, 35), (13, 35)],
        [(14, 36.5), (18, 36.5), (17, 40), (14.5, 39.5)],
    ]
    breastplate = pa.rasterize(pa.union(*(
        pa.polygon([(38 - x, y) for x, y in wing] if mirrored else wing) for wing in wings for mirrored in (False, True)
    )), width, height) | rect(18, 31, 20, 39)
    carving = set()
    # The gaps between the feathers, and a row of discs along the band.
    for x in (13, 17, 21, 25):
        carving |= {(x, y) for y in range(5, 12)}
    carving |= {(x, 14) for x in range(9, 29) if x % 3 == 1}
    # The face: brow, eyes, nose and mouth, cut in straight lines.
    carving |= rect(13, 18, 25, 19) | rect(14, 20, 17, 22) | rect(21, 20, 24, 22) | rect(19, 19, 20, 24)
    carving |= rect(15, 25, 23, 26)
    carving |= {(9, 20), (28, 20)}
    # Arms carved down the sides of the body, and the belt.
    carving |= {(10, y) for y in range(30, 44)} | {(27, y) for y in range(30, 44)}
    carving |= {(x, 41) for x in range(8, 31)} | {(x, 46) for x in range(9, 29)}
    carving |= {(19, y) for y in range(47, 57)}
    grid = finish(grid, width, height, stone, sand, frozenset(carving - breastplate))
    gold = breastplate - sand
    pa.shade_bands_across(grid, gold, ((0.5, "a0"), (1.01, "a1")))
    pa.outline_against(grid, stone - gold, gold, color="O")
    return grid


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


# One unit of the step fret along a platform's frieze, cut into the stone: a square hook, and a
# line stepping down from it.
FRET = (
    "ggggggg....",
    "g.....g....",
    "g.ggg.g....",
    "g.g.g.g....",
    "g...g.g....",
    "ggggg.gg...",
    ".......gg..",
    "........gg.",
)


def ruined_platform():
    """
    96x54: the front of a temple platform - a sloping base, an upright panel framed over it with a
    step fret along it, a cornice, and stepped crenellations along the top - broken away to the
    right.
    """
    width, height = 96, 54
    grid = pa.blank(width, height)
    base = pa.rasterize(pa.polygon([
        (3, 54), (6, 37), (60, 37), (62, 33), (70, 34), (74, 39), (82, 40), (88, 45), (92, 54),
    ]), width, height)
    panel = pa.rasterize(pa.polygon([
        (2, 37), (2, 20), (55, 20), (57, 24), (60, 26), (61, 33), (59, 37),
    ]), width, height)
    cornice = rect(1, 17, 56, 20)
    merlons = set()
    for left in (3, 13, 23, 33, 43):
        merlons |= rect(left, 13, left + 7, 17)
        if left < 43:
            merlons |= rect(left + 1, 10, left + 6, 13) | rect(left + 2, 7, left + 5, 10)
        else:
            # The last has broken off above its first step.
            merlons |= rect(left + 1, 11, left + 4, 13)
    sand = pa.rasterize(pa.union(
        drift(width, height, -4, 34, 9),
        drift(width, height, 50, 100, 12),
    ), width, height)
    stone = (base | panel | cornice | merlons) - sand
    carving = set()
    # Where the panel stands over the base and the cornice over the panel, and the panel's frame.
    carving |= {(x, 37) for x in range(2, 62)} | {(x, 20) for x in range(1, 56)}
    carving |= {(x, y) for x, y in rect(5, 23, 60, 35) if y in (23, 34) or x == 5}
    for left in range(7, 58, 11):
        for row, line in enumerate(FRET):
            for column, mark in enumerate(line):
                if mark == "g":
                    carving.add((left + column, 25 + row))
    carving = {p for p in carving if p in panel or p[1] in (20, 37)}
    # Courses of the base, with the joints between its blocks staggered.
    for row, y in enumerate((42, 47)):
        carving |= {(x, y) for x in range(0, width)}
        for x in range(10 + (row % 2) * 7, width, 14):
            carving |= {(x, yy) for yy in range(y + 1, y + 5)}
    carving |= {(x, 52) for x in range(0, width)}
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
    for index, build in enumerate((sun_stone, stone_warrior, hoodoo, ruined_platform), start=1):
        path = OUT / f"desertObstacle{index}.png"
        image = save_keyframes(path, build())
        print(f"{path} ({image.width}x{image.height // KEYFRAMES}, {KEYFRAMES} keyframes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
