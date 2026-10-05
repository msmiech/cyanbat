# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the orb Guardian Orb sends circling the bat: six frames of one sphere of light, with a
glint going round its rim.

    uv run tools/generate_orb_sprite.py

The bat's own colors, because it is the bat's: cyan going to white where the light catches it, and
the near-black outline every sprite in the game is ringed in, so it reads over the pale noon of the
desert as well as over the cave. Lit from the top left like everything else the bat owns.

The sphere stays put from frame to frame; what moves is a four-pointed glint, a sixth of the way
round its rim each frame, clockwise - the way the orbs go round the bat - so a ring of them reads as
spinning even where it is standing still against the scenery. Six 14x14 frames, laid left to right:
`ORB_FRAME` and `ORB_FRAME_COUNT` in the game.
"""

import pathlib
from math import cos, hypot, pi, sin

from pixelart import blank, ellipse, outer_outline, rasterize, save_sheet

FRAME = 14
FRAMES = 6
CENTER = FRAME / 2

# The sphere, inside the outline that rings it, and where the light on it is brightest.
RADIUS = 4.6
LIGHT = (CENTER - 2, CENTER - 2)

# How far out from the middle the glint goes round, and where it starts: up and to the right, across
# the sphere from the highlight, which it passes over on its way back round.
GLINT_DISTANCE = 5.1
GLINT_START = -pi / 3

PALETTE = {
    "o": (8, 26, 52, 255),
    "d": (0, 112, 168, 255),
    "c": (0, 206, 240, 255),
    "l": (140, 240, 255, 255),
    "w": (236, 255, 255, 255),
    "g": (255, 255, 255, 255),
}

# Distance from the light, in pixels, out to which each tone holds: a white heart where the light
# lands, a pale rim round it, the body's cyan, and a deep cyan on the side turned away.
SHADES = ((1.0, "w"), (2.5, "l"), (5.0, "c"), (99.0, "d"))

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "orb.png"


def sphere():
    grid = blank(FRAME, FRAME)
    for x, y in rasterize(ellipse(CENTER, CENTER, RADIUS, RADIUS), FRAME, FRAME):
        away = hypot(x + 0.5 - LIGHT[0], y + 0.5 - LIGHT[1])
        grid[y][x] = next(tone for reach, tone in SHADES if away <= reach)
    outer_outline(grid, FRAME, FRAME)
    return grid


def frame(index):
    grid = sphere()
    # Clockwise as the frame is drawn: y runs down, so a growing angle goes right, then down.
    angle = GLINT_START + 2 * pi * index / FRAMES
    gx = round(CENTER - 0.5 + GLINT_DISTANCE * cos(angle))
    gy = round(CENTER - 0.5 + GLINT_DISTANCE * sin(angle))
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        x, y = gx + dx, gy + dy
        if 0 <= x < FRAME and 0 <= y < FRAME:
            grid[y][x] = "g"
    return grid


def main() -> int:
    sheet = save_sheet(OUTPUT, [frame(i) for i in range(FRAMES)], PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {FRAMES} frames of {FRAME}x{FRAME})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
