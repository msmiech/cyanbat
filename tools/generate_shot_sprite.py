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

Heavy Rounds makes the bat's bolts bigger, and `shotHeavy.png` draws its colorway again at each size,
a band of four frames per size, top to bottom, so that a bigger bolt is drawn rather than stretched.

The hit (`impact.png`) is laid out the same way, six frames a colorway: a star of light where the shot
struck, opening into a ring that breaks up as it spreads, with sparks thrown out past it. It is round,
so it needs no turning to match the way the shot came in, and it is a good deal smaller than a death's
blast: a hit is not a kill, and a tough enemy under fire would be lost behind effects the size of one.
"""

import pathlib
import random
from math import atan2, cos, floor, hypot, pi, sin, sqrt

from PIL import Image

# . transparent   f the tail's wisps   d dim tail   c body   h hot core   w white-hot tip and heart
#
# The bolt is drawn from its outline rather than from a pixel map, so that it can be drawn again at
# every size Heavy Rounds gives the bat's: stretched nearest-neighbor, a pixel of art came out one
# pixel wide here and two there, and the bolt went lumpy. Drawn at each size, it keeps the pixel
# density of the rest of the art, and a clean outline, at all of them.
#
# Its box is 24x12 at the base size, but the outline fills a column and a row less: a blit paints one
# column and one row short of its frame (`ComposeGraphics.drawPixmap`), and a nose drawn out to the
# last column was cut off there, to a straight line.
FRAME_WIDTH = 24
FRAME_HEIGHT = 12

# The outline, as a half-thickness along the bolt from the tail's end (0) to the nose (1), against the
# widest: a nose rounded as an ellipse from `nose_from` on, and a tail tapering back from it to `tip`,
# bowed outward by a `curve` under 1.
BOLT = dict(nose_from=0.6, tip=0.1, curve=0.7)
# The solid body inside it, from `BODY_FROM` along the bolt to the same nose; behind and around it is
# the dim tail. It has no tip of its own, so it ends in a curve the tail wraps round.
BODY_FROM = 0.22
BODY = dict(nose_from=0.6, tip=0.0, curve=0.5)
# How thick the white-hot rim round the nose is at its point, in pixels at the base size. It thins to
# nothing toward the top and the bottom, as light on a rounded nose does.
RIM = 2.0

FRAMES = 4

# The core at each of its three sizes, in pixels at the base size: the hot core's half-length and
# half-height, then the white heart's. Each is an ellipse whose front stays at `CORE_FRONT` of the way
# along, so the core swells toward the back of the bolt and the throb pushes into the body the ripple
# then carries off.
CORES = (
    (2.5, 1.0, 1.0, 1.0),  # small
    (3.5, 2.0, 2.0, 1.0),  # middling
    (4.5, 3.0, 3.0, 2.0),  # large
)
CORE_FRONT = 0.93

# Frame by frame: the core's size, and how far behind the nose the ripple has got, in pixels at the
# base size. The core is at its largest as the ripple leaves it, and the ripple is the rim set back,
# moving three pixels a frame, so it is never in two places at once.
CORE_SIZES = (2, 1, 0, 1)
RIPPLE_BACK = (6, 9, 12, 15)

# The rows of the tail's ragged end, counted down the base size's box, that trail a wisp a pixel
# further back, frame by frame; the others draw theirs in a pixel. It is the end of the tail
# flickering, as the end of a flame does. A bigger bolt maps its rows onto these.
TAIL_ROWS = (4, 5, 6, 7)
TAIL_WISPS = ((5, 6), (4, 7), (6,), (5, 6))

# The sizes Heavy Rounds draws the bat's bolts at, as `SHOT_SCALE_PER_LEVEL` and `MAX_SHOT_SIZE_LEVEL`
# in `CyanBatConstants` declare them: the box at each is the base box scaled and rounded, as the game
# rounds it. Only the bat's bolts grow, so only its colorway is drawn bigger, in `shotHeavy.png`.
SCALE_PER_LEVEL = 0.2
MAX_SIZE_LEVEL = 3

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


def profile(u, nose_from, tip, curve):
    """The half-thickness of an outline [u] of the way along it, against its widest."""
    if u >= 1:
        return 0.0
    if u > nose_from:
        along = (u - nose_from) / (1 - nose_from)
        return sqrt(1 - along * along)
    return tip + (1 - tip) * (max(u, 0.0) / nose_from) ** curve


def box(level):
    """The bolt's box at Heavy Rounds size [level], and its scale: rounded half up, as roundToInt is."""
    scale = 1 + level * SCALE_PER_LEVEL
    return floor(FRAME_WIDTH * scale + 0.5), floor(FRAME_HEIGHT * scale + 0.5), scale


def bolt(frame, level=0):
    frame_width, frame_height, scale = box(level)
    # The outline fills the box less its last column and row, which the blit never paints.
    width, height = frame_width - 1, frame_height - 1
    middle = height / 2
    nose_from = BOLT["nose_from"] * width
    nose_length = width - nose_from
    rim = RIM * scale

    def across(y):
        return abs(y + 0.5 - middle) / middle

    def inside(x, y):
        return across(y) <= profile((x + 0.5) / width, **BOLT)

    def in_body(x, y):
        along = ((x + 0.5) / width - BODY_FROM) / (1 - BODY_FROM)
        return along > 0 and across(y) <= profile(along, **BODY)

    def on_rim(x, y, back=0.0):
        """Whether ([x], [y]) is on the nose's rim, or on the rim set [back] pixels behind it."""
        ahead = x + 0.5 + back - nose_from
        if ahead <= 0 or across(y) >= 1:
            return False
        # The row's share of the nose, which the rim's thickness shrinks with.
        share = sqrt(1 - across(y) ** 2)
        return (nose_length - rim) * share < ahead <= nose_length * share

    grid = [["." for _ in range(frame_width)] for _ in range(frame_height)]
    for y in range(height):
        for x in range(width):
            if inside(x, y):
                grid[y][x] = "w" if on_rim(x, y) else "c" if in_body(x, y) else "d"

    # The ripple: the nose's rim, set back. On the body it is the hot core's color, and on the tail
    # it lifts the tail to the body's.
    back = RIPPLE_BACK[frame] * scale
    for y in range(height):
        for x in range(width):
            if on_rim(x, y, back):
                if grid[y][x] == "c":
                    grid[y][x] = "h"
                elif grid[y][x] == "d":
                    grid[y][x] = "c"

    # The core over it, since the ripple comes out of the core; inside the rim, which stays white.
    front = CORE_FRONT * width
    hot_length, hot_height, white_length, white_height = CORES[CORE_SIZES[frame]]
    for y in range(height):
        for x in range(width):
            if grid[y][x] in ".w":
                continue
            down = (y + 0.5 - middle) / scale
            for length, half, key in ((hot_length, hot_height, "h"), (white_length, white_height, "w")):
                along = (x + 0.5 - front) / scale + length
                if (along / length) ** 2 + (down / half) ** 2 <= 1:
                    grid[y][x] = key

    # The end of the tail: a wisp a pixel further back on some rows, and drawn in a pixel on the rest.
    for y in range(height):
        row = floor((y + 0.5 - middle) / scale + FRAME_HEIGHT / 2)
        if row not in TAIL_ROWS:
            continue
        end = next(x for x, key in enumerate(grid[y]) if key != ".")
        if row in TAIL_WISPS[frame]:
            grid[y][max(end - 1, 0)] = "f"
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


def save_heavy(name):
    """The bat's bolt at every size past the base one: a band of its frames per size, top to bottom,
    each frame as wide as its box."""
    bands = [(box(level), [bolt(frame, level) for frame in range(FRAMES)])
             for level in range(1, MAX_SIZE_LEVEL + 1)]
    widest = max(width for (width, _, _), _ in bands)
    tall = sum(height for (_, height, _), _ in bands)
    sheet = Image.new("RGBA", (widest * FRAMES, tall), TRANSPARENT)
    colors = palette(COLORWAYS[0])
    top = 0
    for (width, height, _), grids in bands:
        for frame, grid in enumerate(grids):
            for y, row in enumerate(grid):
                for x, key in enumerate(row):
                    if key != ".":
                        sheet.putpixel((frame * width + x, top + y), colors[key])
        top += height
    path = ASSETS / name
    sheet.save(path)
    print(f"{path} ({sheet.width}x{sheet.height}, {len(bands)} sizes of {FRAMES} frames)")


def main() -> int:
    save("shot.png", [bolt(frame) for frame in range(FRAMES)], FRAME_WIDTH, FRAME_HEIGHT)
    save_heavy("shotHeavy.png")

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
