# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the power-up an elite drops when it is shot down: a silver plus, spinning about its
upright axis, eight frames of half a turn.

    uv run tools/generate_power_up_drop_sprite.py

The plus is a solid, a thick plate cut in the shape of one, and each frame is that solid turned and
traced, so its edge shows as it swings round and it narrows to a sliver side on. Half a turn is the
whole loop: its back is drawn as its front, so the frame after the last is the first again.

Silver, a cool steel going to white, because it is the bat's to take, and the bat's things are cool
where everything hostile is warm. Polished: the light falls from the top left, as on everything the
bat owns, so the face brightens as it turns that way and dulls turning off, and every edge facing
the light is caught bright and every edge turned away is dark. A streak of reflected light slides
across the face the other way from its turn, as if the reflection held still while the metal turned
under it, and as the plus faces the player square on it flashes: a four-pointed glint at its top
left corner. Ringed in the near-black outline the bat wears, so it reads over the desert's pale noon
as well as in the cave.

Eight 19x19 frames, laid left to right: `POWER_UP_DROP_FRAME` and `POWER_UP_DROP_FRAME_COUNT` in
the game.
"""

import pathlib
from math import cos, hypot, pi, sin

from pixelart import SUBSAMPLES, TRANSPARENT, blank, outer_outline, save_sheet

FRAME = 19
FRAMES = 8
CENTER = FRAME / 2

# The plus, in pixels: how far each arm reaches from the middle, half the width of an arm, and half
# the plate's thickness, which is all there is of it side on.
REACH = 7.5
HALF_ARM = 2.5
HALF_DEPTH = 1.5

# The two bars the plus is made of, as boxes (x, y and z half extents) around its middle.
BARS = ((HALF_ARM, REACH, HALF_DEPTH), (REACH, HALF_ARM, HALF_DEPTH))

# Where the light comes from, as seen: up and to the left, and toward the player. x runs right, y
# down and z out of the screen.
LIGHT = (-1.0, -1.0, 1.4)

PALETTE = {
    "o": (12, 18, 36, 255),
    "1": (58, 66, 88, 255),
    "2": (96, 106, 128, 255),
    "3": (138, 148, 168, 255),
    "4": (180, 188, 204, 255),
    "5": (220, 226, 238, 255),
    "6": (255, 255, 255, 255),
}

# The steel from dark to bright, as steps a shade is rounded to: never blended.
RAMP = "123456"

# The face's light: what it shows turned away from the light, what turning toward it adds, and how
# much darker its bottom right corner is than its top left.
FACE_BASE = 0.22
FACE_LIT = 0.5
FACE_FALLOFF = 0.16

# The plate's edge, which is never polished bright: its darkest and how much the light lifts it.
EDGE_BASE = 0.1
EDGE_LIT = 0.55

# The streak of reflection: its middle and its sides as a share of the face's diagonal, and how
# far it slides over a quarter of a turn.
STREAK_CORE = 0.07
STREAK_HALF_WIDTH = 0.2
STREAK_TRAVEL = 1.3

# How much an edge of the silhouette turned to the light is caught by it, and one turned away loses.
BEVEL = 0.2

# The glint's arms, by frame: long as the plus faces the player square on, short a frame either
# side of that, and none further round.
GLINT_ARMS = {0: 2, 1: 1, FRAMES - 1: 1}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "powerUpDrop.png"


def normalized(v):
    length = hypot(*v)
    return tuple(c / length for c in v)


def turned(v, angle):
    """[v] turned by [angle] about the upright axis, the way the plus turns."""
    x, y, z = v
    return (x * cos(angle) + z * sin(angle), y, -x * sin(angle) + z * cos(angle))


def trace(x, y, angle):
    """
    Where a ray straight into the screen at ([x], [y]) first meets the plus turned by [angle]:
    the face it meets, as its outward normal in the plus's own terms, and the point it meets it at,
    likewise. None for a ray that misses.
    """
    # Into the plus's own terms, where its bars are boxes along the axes.
    origin = turned((x - CENTER, y - CENTER, 100.0), -angle)
    direction = turned((0.0, 0.0, -1.0), -angle)
    nearest = None
    for extent in BARS:
        near, far, normal = -1e9, 1e9, None
        for axis in range(3):
            o, d, e = origin[axis], direction[axis], extent[axis]
            if abs(d) < 1e-9:
                if abs(o) > e:
                    break
                continue
            t1, t2 = (-e - o) / d, (e - o) / d
            sign = -1.0
            if t1 > t2:
                t1, t2 = t2, t1
                sign = 1.0
            if t1 > near:
                near = t1
                normal = tuple(sign if a == axis else 0.0 for a in range(3))
            far = min(far, t2)
        else:
            if near <= far and (nearest is None or near < nearest[0]):
                nearest = (near, normal)
    if nearest is None:
        return None
    t, normal = nearest
    point = tuple(origin[a] + direction[a] * t for a in range(3))
    return normal, point


def brightness(normal, point, angle, facing):
    """
    How bright a point of the plus is, 0..1 and past it, before it is rounded to a step of the
    steel. [facing] is how far the face toward the player has turned from square on, up to a quarter
    turn either way.
    """
    seen = turned(normal, angle)
    lit = max(0.0, sum(a * b for a, b in zip(seen, normalized(LIGHT))))
    if normal[2] == 0.0:
        return EDGE_BASE + EDGE_LIT * lit

    # The face, back or front, painted as seen from its own side, so the back turning round to face
    # the player looks as the front did and the loop has no seam.
    across = point[0] if normal[2] > 0 else -point[0]
    diagonal = (across + point[1]) / (2 * REACH)
    level = FACE_BASE + FACE_LIT * lit - FACE_FALLOFF * diagonal
    off_streak = abs(diagonal + STREAK_TRAVEL * sin(facing))
    if off_streak < STREAK_CORE:
        return 1.0
    if off_streak < STREAK_HALF_WIDTH:
        return max(level + 0.3, 0.8)
    return level


def step(level):
    """A brightness as a step of the steel."""
    index = round(level * (len(RAMP) - 1))
    return RAMP[max(0, min(len(RAMP) - 1, index))]


def frame(index):
    angle = pi * index / FRAMES
    # The face toward the player, and how far it has turned from square on: the front for the first
    # quarter turn, then the back.
    facing = angle if angle <= pi / 2 else angle - pi
    levels = {}
    for py in range(FRAME):
        for px in range(FRAME):
            hits = [
                trace(px + (sx + 0.5) / SUBSAMPLES, py + (sy + 0.5) / SUBSAMPLES, angle)
                for sy in range(SUBSAMPLES)
                for sx in range(SUBSAMPLES)
            ]
            landed = [hit for hit in hits if hit is not None]
            if len(landed) * 2 < len(hits):
                continue
            # Shaded where the middle of the pixel lands, so an edge between two faces falls where
            # the faces meet.
            normal, point = hits[len(hits) // 2] or landed[0]
            levels[(px, py)] = brightness(normal, point, angle, facing)

    grid = blank(FRAME, FRAME)
    for (x, y), level in levels.items():
        # The silhouette's own edge, as if rounded over: caught by the light on its top and left,
        # in shadow on its bottom and right. Short of the streak, which is already as bright as
        # anything gets.
        if level < 1.0:
            if (x - 1, y) not in levels or (x, y - 1) not in levels:
                level += BEVEL
            elif (x + 1, y) not in levels or (x, y + 1) not in levels:
                level -= BEVEL
        grid[y][x] = step(level)
    outer_outline(grid, FRAME, FRAME)
    glint(grid, index, levels)
    return grid


def glint(grid, index, levels):
    """
    The flash as the plus faces the player: a four-pointed star on its top left corner, the first
    pixel of its top row. Over the outline rather than ringed by it, since it is light.
    """
    arms = GLINT_ARMS.get(index)
    if arms is None:
        return
    gy = min(y for _, y in levels)
    gx = min(x for x, y in levels if y == gy)
    grid[gy][gx] = "6"
    for reach in range(1, arms + 1):
        for dx, dy in ((reach, 0), (-reach, 0), (0, reach), (0, -reach)):
            x, y = gx + dx, gy + dy
            if 0 <= x < FRAME and 0 <= y < FRAME:
                # The steel's brightest but one at the tips, so the star tapers.
                grid[y][x] = "6" if reach < arms else "5"


def main() -> int:
    sheet = save_sheet(OUTPUT, [frame(i) for i in range(FRAMES)], PALETTE, FRAME, FRAME)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {FRAMES} frames of {FRAME}x{FRAME})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
