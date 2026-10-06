# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the shots: one bolt, four frames of it, in every colorway that fires it - and the hit each
one leaves where it is spent, in the same colorways.

Kept as a script rather than a loose PNG so the pixel art stays editable: tweak the maps below
and re-run, instead of reverse-engineering colors out of the image.

    uv run tools/generate_shot_sprite.py

The bolt points right, which is the only direction the *player* fires; enemy shots travel left and
are turned by `FacingSystem`, so one drawing serves both.

It burns as it flies. Its core throbs, and each throb sends a ripple back down its body - the shape of
its own nose, set further back every frame, brightening the body and then the tail as it passes -
while the tail's ragged end flickers. The outline never moves, so the bolt keeps its shape at any
speed and only the light inside it runs; four frames loop, as the game plays them.

Fourteen colorways, laid out left to right and addressed the way the enemy sheet is: each one's four
frames side by side, then the next one's. The player's is cyan, the next three are the cave's enemy
palettes from `generate_enemy_sprites.py` - violet, amber and crimson - the next three are the
jungle's shooters, the next is the desert's boss, the next five are the elites', one per
`ElitePalette`, and the last is the lagoon's boss - so a shot is the same color as whatever fired it.
That matters more than it sounds: the screen can hold the bat's shots and the boss's at once,
travelling in opposite directions, and before this they were the same cyan bolt. Which ones were
dangerous had to be worked out from which way they were moving.

The hit (`impact.png`) is laid out the same way, six frames a colorway: a star of light where the shot
struck, opening into a ring that breaks up as it spreads, with sparks thrown out past it. It is round,
so it needs no turning to match the way the shot came in, and it is a good deal smaller than a death's
blast: a hit is not a kill, and a tough enemy under fire would be lost behind effects the size of one.
"""

import pathlib
import random
from math import atan2, cos, hypot, pi, sin

from PIL import Image

# . transparent   f the tail's wisps   d dim tail   c body   h hot core   w white-hot tip and heart
#
# Drawn at 24x12 rather than scaling a smaller map up: a nearest-neighbor 2x would give the
# bolt 2x2 pixel blocks, visibly coarser than the bat and the enemies it flies past. Same pixel
# density as the rest of the art, just a bigger object. This is the outline every frame keeps; the
# frames differ only in what burns inside it.
SPRITE = [
    "...........dcccw........",
    "........dddccccccww.....",
    ".....ddddccccccccccww...",
    "...ddddddccccccccccccww.",
    ".ddddddcccccccccccccccww",
    "dddddddcccccccccccccccww",
    "dddddddcccccccccccccccww",
    ".ddddddcccccccccccccccww",
    "...ddddddccccccccccccww.",
    ".....ddddccccccccccww...",
    "........dddccccccww.....",
    "...........dcccw........",
]

FRAMES = 4

# The core at each of its three sizes, as runs of (row, first column, last column): the hot core, and
# the white heart inside it. It swells toward the back of the bolt, so the throb pushes into the body
# the ripple then carries off.
CORES = (
    # small
    ([(5, 16, 20), (6, 16, 20)],
     [(5, 18, 19), (6, 18, 19)]),
    # middling
    ([(4, 16, 20), (5, 14, 21), (6, 14, 21), (7, 16, 20)],
     [(5, 17, 20), (6, 17, 20)]),
    # large
    ([(3, 17, 19), (4, 14, 21), (5, 12, 21), (6, 12, 21), (7, 14, 21), (8, 17, 19)],
     [(4, 18, 19), (5, 15, 20), (6, 15, 20), (7, 18, 19)]),
)

# Frame by frame: the core's size, and how far behind the nose the ripple has got. The core is at its
# largest as the ripple leaves it, and the ripple is two pixels thick and moves three a frame, so it is
# never in two places at once.
CORE_SIZES = (2, 1, 0, 1)
RIPPLE_BACK = (6, 9, 12, 15)

# The rows of the tail's ragged end that trail a wisp a pixel further back, frame by frame; the others
# draw theirs in a pixel. It is the end of the tail flickering, as the end of a flame does.
TAIL_ROWS = (4, 5, 6, 7)
TAIL_WISPS = ((5, 6), (4, 7), (6,), (5, 6))

# One ramp per colorway: a part-transparent tail, a solid body, and a hot tip. The tip stays near
# white in every one of them - that is the part which reads as "this is moving fast" - so the hue
# lives in the body and the tail, where there is enough of it to carry a color. The hot core and the
# tail's wisps are worked out from these (see `palette`), so each colorway stays one ramp.
#
# The order is the order `EntityFactory` addresses them in: the player first, then the three enemy
# types in the order the enemy sheet lays them out.
COLORWAYS = (
    # player: cyan, the bat's own color
    {"d": (0, 140, 200, 180), "c": (0, 229, 255, 255), "w": (240, 255, 255, 255)},
    # SCOUT: violet
    {"d": (96, 40, 150, 180), "c": (176, 92, 232, 255), "w": (244, 222, 255, 255)},
    # SINE: amber
    {"d": (150, 78, 20, 180), "c": (232, 148, 44, 255), "w": (255, 240, 206, 255)},
    # ZIGZAG, and so the boss: crimson. The jungle's beetle fires these too - it is crimson.
    {"d": (140, 30, 50, 180), "c": (220, 66, 80, 255), "w": (255, 222, 216, 255)},
    # The jungle's own, from `generate_jungle_enemy_sprites.py` and `generate_jungle_boss_sprite.py`.
    # SPITTER: magenta, the pod's color.
    {"d": (130, 30, 120, 180), "c": (228, 90, 204, 255), "w": (255, 226, 248, 255)},
    # WISP: flame orange.
    {"d": (170, 56, 20, 180), "c": (252, 140, 40, 255), "w": (255, 244, 200, 255)},
    # the Moth Queen: rose, and a touch paler than the spitter's so the two read apart mid-fight.
    {"d": (150, 50, 96, 180), "c": (240, 128, 170, 255), "w": (255, 236, 244, 255)},
    # The desert's, from `generate_desert_boss_sprite.py`. The Sand Wyrm: molten gold, the glow in
    # its throat. The desert's other shooters fire in colorways already here - the djinn in the
    # spitter's magenta, the scarab in crimson, an armed hawk in flame - because each of those is
    # the color of the thing firing it.
    {"d": (186, 104, 20, 180), "c": (255, 204, 64, 255), "w": (255, 250, 226, 255)},
    # The elites', one per `ElitePalette` in the order it declares them. An elite of any species can
    # wear any of them, so each body is exactly its palette's rim - the outside of its glow - and a
    # bolt is the color of the light around whatever fired it rather than of the creature inside.
    # SCARLET
    {"d": (150, 24, 40, 180), "c": (255, 56, 72, 255), "w": (255, 226, 228, 255)},
    # EMBER
    {"d": (160, 62, 14, 180), "c": (255, 122, 30, 255), "w": (255, 238, 214, 255)},
    # VENOM
    {"d": (40, 130, 24, 180), "c": (116, 238, 60, 255), "w": (236, 255, 226, 255)},
    # ULTRAVIOLET
    {"d": (84, 36, 160, 180), "c": (164, 88, 255, 255), "w": (240, 228, 255, 255)},
    # FUCHSIA
    {"d": (150, 24, 112, 180), "c": (255, 64, 196, 255), "w": (255, 226, 246, 255)},
    # The lagoon's, from `generate_lagoon_boss_sprite.py`. The Naga: neon pink, the light in its hood -
    # hotter and less violet than the fuchsia elite's, so the boss's spit and an elite's bolt read
    # apart. The lagoon's other shooters fire in colorways already here - the crab in crimson, the
    # puffer in amber, an armed krait in flame - each the color of the thing firing it.
    {"d": (160, 20, 80, 180), "c": (255, 61, 142, 255), "w": (255, 224, 238, 255)},
)

# How opaque a wisp is: the tail's own color, but as faint again as the tail.
WISP_ALPHA = 96

# --- the hit -------------------------------------------------------------------------------------

# Odd, so the star has a middle pixel to be drawn around.
IMPACT_FRAME = 21
IMPACT_CENTER = IMPACT_FRAME // 2
TAU = 2.0 * pi

# Frame by frame, from the flash to the last sparks:
#   core     the white heart, as a radius out from the middle (a diamond, the shape a star's middle
#            takes on the grid)
#   halo     the hot core around it
#   rays     the star's four long points: how far they reach, the key of their tips and of the rest
#   diagonal its four short points: how far they reach, and their key
#   ring     the ring the flash opens into: its radius, key, and how broken up it is, as 0..1
#   sparks   how far out the sparks have flown, and their key
#
# The star is all there is for the first two frames and gone by the third: a hit is an instant, and
# what is left after it is light spreading out and going. It is big from the first frame, which lands
# under the number the hit knocks off and the white of the struck creature's flash, and has to reach
# out past both to be seen at all.
IMPACT = (
    dict(core=(1.5, "w"), halo=(3.0, "h"), rays=(7.5, "w", "h"), diagonal=(4.6, "h"), ring=None, sparks=None),
    dict(core=(2.0, "w"), halo=(3.6, "h"), rays=(8.5, "w", "h"), diagonal=(5.8, "c"), ring=None, sparks=(4.6, "w")),
    dict(core=(0.8, "w"), halo=(2.0, "h"), rays=None, diagonal=None, ring=(5.4, "c", 0.15), sparks=(6.8, "h")),
    dict(core=None, halo=(0.8, "c"), rays=None, diagonal=None, ring=(7.3, "c", 0.35), sparks=(8.3, "c")),
    dict(core=None, halo=None, rays=None, diagonal=None, ring=(8.7, "d", 0.5), sparks=(9.4, "d")),
    dict(core=None, halo=None, rays=None, diagonal=None, ring=None, sparks=(10.2, "f")),
)

# How many sparks a hit throws, and how far each flies against the others, so they do not all land
# on the ring. Their headings are spread evenly round and then jittered, so none of them bunch up.
SPARK_COUNT = 9
SPARK_REACH = (0.75, 1.2)
# How many arcs the ring is broken into as it spreads, each with a strength of its own: an arc goes
# once the ring's own `broken` passes it.
RING_ARCS = 23

SEED = 20261005

ASSETS = pathlib.Path(__file__).resolve().parent.parent / "assets"
TRANSPARENT = (0, 0, 0, 0)


def palette(ramp):
    """A colorway's whole palette: its ramp, the hot core between its body and its tip, and wisps."""
    hot = tuple((a + b + 1) // 2 for a, b in zip(ramp["c"][:3], ramp["w"][:3])) + (255,)
    return {**ramp, "h": hot, "f": ramp["d"][:3] + (WISP_ALPHA,)}


def bolt(frame):
    grid = [list(row) for row in SPRITE]

    # The ripple: the nose's outline, set back. On the body it is the hot core's color, and on the
    # tail it lifts the tail to the body's.
    back = RIPPLE_BACK[frame]
    for y, row in enumerate(SPRITE):
        nose = row.index("w")
        for x in (nose - back, nose - back - 1):
            if 0 <= x < len(row):
                if grid[y][x] == "c":
                    grid[y][x] = "h"
                elif grid[y][x] == "d":
                    grid[y][x] = "c"

    # The core over it, since the ripple comes out of the core.
    hot, white = CORES[CORE_SIZES[frame]]
    for runs, key in ((hot, "h"), (white, "w")):
        for y, first, last in runs:
            for x in range(first, last + 1):
                grid[y][x] = key

    # The end of the tail: a wisp a pixel further back on some rows, and drawn in a pixel on the rest.
    for y in TAIL_ROWS:
        end = next(x for x, key in enumerate(grid[y]) if key != ".")
        if y in TAIL_WISPS[frame]:
            grid[y][end] = "f"
        else:
            grid[y][end] = "."
            if grid[y][end + 1] == "d":
                grid[y][end + 1] = "f"
    return grid


def impact(spec, sparks, arcs):
    size = IMPACT_FRAME
    middle = IMPACT_CENTER
    grid = [["." for _ in range(size)] for _ in range(size)]

    def put(x, y, key):
        if 0 <= x < size and 0 <= y < size:
            grid[y][x] = key

    if spec["ring"]:
        radius, key, broken = spec["ring"]
        for y in range(size):
            for x in range(size):
                if abs(hypot(x - middle, y - middle) - radius) > 0.5:
                    continue
                arc = int((atan2(y - middle, x - middle) % TAU) / TAU * len(arcs)) % len(arcs)
                if arcs[arc] >= broken:
                    grid[y][x] = key

    if spec["rays"]:
        reach, tip, rest = spec["rays"]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for step in range(1, int(reach) + 1):
                put(middle + dx * step, middle + dy * step, tip if step > reach - 2 else rest)

    if spec["diagonal"]:
        reach, key = spec["diagonal"]
        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            for step in range(1, int(reach / 1.414) + 1):
                put(middle + dx * step, middle + dy * step, key)

    for part in ("halo", "core"):
        if spec[part]:
            radius, key = spec[part]
            for y in range(size):
                for x in range(size):
                    if abs(x - middle) + abs(y - middle) <= radius:
                        grid[y][x] = key

    # Last, so a spark reads over the ring it is flying past. A bright one draws a streak behind it.
    if spec["sparks"]:
        flown, key = spec["sparks"]
        for heading, reach in sparks:
            distance = flown * reach
            put(round(middle + cos(heading) * distance), round(middle + sin(heading) * distance), key)
            if key in ("w", "h"):
                behind = distance - 1.2
                put(round(middle + cos(heading) * behind), round(middle + sin(heading) * behind), "c")
    return grid


def save(name, grids, width, height):
    """Every colorway of [grids], each one's frames side by side and then the next colorway's."""
    sheet = Image.new("RGBA", (width * len(grids) * len(COLORWAYS), height), TRANSPARENT)
    for index, ramp in enumerate(COLORWAYS):
        colors = palette(ramp)
        for frame, grid in enumerate(grids):
            left = (index * len(grids) + frame) * width
            for y, row in enumerate(grid):
                for x, key in enumerate(row):
                    if key != ".":
                        sheet.putpixel((left + x, y), colors[key])
    path = ASSETS / name
    sheet.save(path)
    print(f"{path} ({sheet.width}x{sheet.height}, {len(COLORWAYS)} colorways of {len(grids)} frames of {width}x{height})")


def main() -> int:
    width = len(SPRITE[0])
    if any(len(row) != width for row in SPRITE):
        raise SystemExit("every row of SPRITE must be the same width")
    save("shot.png", [bolt(frame) for frame in range(FRAMES)], width, len(SPRITE))

    rng = random.Random(SEED)
    sparks = [
        (index * TAU / SPARK_COUNT + rng.uniform(-0.25, 0.25), rng.uniform(*SPARK_REACH))
        for index in range(SPARK_COUNT)
    ]
    arcs = [rng.random() for _ in range(RING_ARCS)]
    save("impact.png", [impact(spec, sparks, arcs) for spec in IMPACT], IMPACT_FRAME, IMPACT_FRAME)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
