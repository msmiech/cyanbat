# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the enemy sheet: three hostiles, four frames of wingbeat each.

    uv run tools/generate_enemy_sprites.py

The sheet it replaces was three unrelated creatures - one violet, one brown, one grey - carrying
stray magenta pixels from whatever key color the original art was cut against, and flapping on two
frames while the bat beside them flapped on six. Nothing about them said which was the player and
which was the thing trying to kill him.

Two rules fix that, and they are the whole design:

* **The player is cool, everything hostile is warm.** The bat is cyan and deep blue; these are
  violet, amber and crimson. Over a dark blue-grey cave that reads instantly, at a glance, at the
  size these are actually seen - which is the only test a 32-pixel sprite has to pass.
* **One body, three tempers.** All three are the same drone: a hooded shell, a single lit eye, a
  pair of swept wings. They differ in hue, in how sharp the shell is and in how far the wings
  sweep, so a new wave reads as *more of the same enemy, angrier* rather than as a different game.

Types are indexed the way `EntityFactory.srcXOf` addresses them: 0 SCOUT, 1 SINE, 2 ZIGZAG. Type 2
is also what the boss is drawn from, so it is the one that has to survive being scaled up.
"""

import pathlib
import sys
from math import cos, radians, sin

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import pixelart as pa

FRAME_WIDTH = 32
FRAME_HEIGHT = 29
FRAME_COUNT = 4
TYPE_COUNT = 3

# Four frames at the interval EntityFactory uses puts the cycle at about the 0.4s the bat's six
# take, so the two animate at the same rate rather than one of them looking slowed down.
#
# A full beat, not a ping-pong: down, bottom, up, top. Sweep is how far back the wings are thrown,
# in degrees above horizontal; span is how far they extend, which closes on the upstroke the way
# the bat's does.
#
# The floor is 32 rather than the 14 it started at. Below about 30 the pair lies flat along the
# hull and is swallowed by it, so the wings appeared to blink out of existence for a quarter of
# every beat - which reads as a glitch, not as a wingbeat.
BEAT = (
    # sweep  span
    (58.0, 0.96),   # driving down
    (32.0, 1.00),   # bottom of the stroke, fully out
    (50.0, 0.80),   # recovering, drawn in
    (70.0, 0.70),   # top of the stroke
)

# Hull shape and palette per type. The shell gets sharper and the wings longer as the types get
# harder, so the last one an early wave meets already looks like the thing the boss will be.
# Sized to fill the frame, which is not the same as sized to taste. The collision box an enemy
# actually occupies is 28x29 - a constant in EnemyGenerator, not the artwork - and the first pass
# drew creatures about 20x20 inside it. They played bigger than they looked, which is the wrong way
# round for a hazard: a shot that visibly missed still connected.
#
# The hull grew more than the wings. Hull is what the eye measures an enemy by, and wing tips are
# what runs out of frame first - at the top of the beat they are already within a pixel of the
# ceiling of a 29-row frame.
TYPES = (
    # SCOUT: small, blunt, swept right back. Reads as fast, which is what it is.
    dict(key="v", hull=(8.7, 5.5), wing=16.0, spike=2.4, tilt=6.0),
    # SINE: rounder and broader, the weaver.
    dict(key="a", hull=(9.5, 6.0), wing=17.0, spike=3.1, tilt=0.0),
    # ZIGZAG, and the boss: heaviest and most angular.
    dict(key="c", hull=(10.1, 6.4), wing=18.0, spike=4.4, tilt=-5.0),
)

# Three ramps of four tones each, plus the shared parts. Same structure every time - back, body,
# lit edge, and a hot rim - so the three read as one species in three tempers rather than as three
# different pieces of art.
PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (14, 8, 26, 255),      # outline, near-black and slightly violet
    "O": (22, 14, 38, 255),     # inner outline, where a wing crosses the hull
    # violet
    "v0": (58, 26, 96, 255),
    "v1": (104, 44, 158, 255),
    "v2": (156, 78, 212, 255),
    "v3": (208, 150, 245, 255),
    # amber
    "a0": (92, 44, 14, 255),
    "a1": (154, 82, 22, 255),
    "a2": (214, 132, 38, 255),
    "a3": (248, 198, 110, 255),
    # crimson
    "c0": (84, 18, 34, 255),
    "c1": (146, 32, 52, 255),
    "c2": (202, 58, 72, 255),
    "c3": (246, 132, 128, 255),
    # the eye, shared: the one cold thing on a warm body, so it reads as a lens rather than a pupil
    "e": (226, 252, 255, 255),
    "p": (16, 10, 28, 255),
}

# Wings are the type's own darkest two tones, so they sit behind the hull without a separate ramp.
WING_BANDS = ((0.5, "{k}0"), (1.01, "{k}1"))
HULL_BANDS = ((0.22, "{k}0"), (0.52, "{k}1"), (0.80, "{k}2"), (1.01, "{k}3"))

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "enemies.png"

# The hostiles fly left, so the front of the body is the left edge and the wings trail right.
NOSE_X = 3.0
CENTER_Y = 14.0


def banded(bands, key):
    return tuple((threshold, material.format(k=key)) for threshold, material in bands)


def wing(root, hull_back, sweep_degrees, span, length, up):
    """
    One wing as a swept blade, thrown back and either up or down from [root].

    A triangle rather than the bat's fingered membrane: at 32 pixels a scalloped trailing edge is
    two pixels of noise, and a clean swept blade is what still reads as a wing when the boss draws
    the same artwork three times over.

    The tip is reached mostly vertically and the trailing corner sits behind [hull_back], because
    on a 32x29 frame the free space is above and below the body - the first pass rooted the wings
    inside the hull and swept them along it, and they were invisible behind their own animal.
    """
    angle = radians(sweep_degrees) * (1.0 if up else -1.0)
    reach = length * span
    # The vertical reach is held back to 0.88 of the full swing. Once the hulls grew, a blade at
    # the top of its stroke ran past row 0 and came back with its tip cut square by the edge of the
    # frame - and a clipped wing reads as a drawing mistake, not as a big enemy.
    rise = reach * sin(angle) * 0.82
    tip = (root[0] + reach * cos(angle) * 0.45, root[1] - rise)
    trail = (hull_back + reach * 0.42, root[1] - rise * 0.18)
    return pa.polygon([root, tip, trail])


def render(type_index, frame_index):
    spec = TYPES[type_index]
    key = spec["key"]
    sweep, span = BEAT[frame_index]
    grid = pa.blank(FRAME_WIDTH, FRAME_HEIGHT)

    hull_rx, hull_ry = spec["hull"]
    body_x = NOSE_X + hull_rx - 1.0
    # The hull rides its own wingbeat, a pixel either way, so the whole thing breathes rather than
    # the wings moving against a body nailed to the grid.
    bob = (1 if frame_index in (2, 3) else 0) - (1 if frame_index == 1 else 0)
    center = (body_x, CENTER_Y + bob)

    root = (center[0] + hull_rx * 0.10, center[1] - hull_ry * 0.30)
    hull_back = center[0] + hull_rx

    # Wings first: behind the hull, so a downstroke passes under the body instead of over the eye.
    wings = set()
    for up in (True, False):
        wings |= pa.rasterize(
            wing(root, hull_back, sweep + spec["tilt"], span, spec["wing"], up),
            FRAME_WIDTH,
            FRAME_HEIGHT,
        )
    pa.shade_bands(grid, wings, banded(WING_BANDS, key))

    # The hull: a shell with a spike off the back, pointed the way it travels.
    shell = pa.ellipse(center[0], center[1], hull_rx, hull_ry)
    spike = pa.polygon([
        (center[0] + hull_rx * 0.5, center[1] - spec["spike"]),
        (center[0] + hull_rx + spec["spike"] * 1.5, center[1]),
        (center[0] + hull_rx * 0.5, center[1] + spec["spike"]),
    ])
    # A jaw under the nose, which is the half of the silhouette that says which way it is facing.
    jaw = pa.polygon([
        (center[0] - hull_rx * 0.95, center[1] + hull_ry * 0.1),
        (center[0] - hull_rx * 0.2, center[1] + hull_ry * 0.55),
        (center[0] - hull_rx * 0.1, center[1] + hull_ry * 1.05),
        (center[0] - hull_rx * 0.8, center[1] + hull_ry * 0.85),
    ])
    hull = pa.rasterize(pa.union(shell, spike, jaw), FRAME_WIDTH, FRAME_HEIGHT)
    pa.shade_bands(grid, hull, banded(HULL_BANDS, key))

    # The hull silhouetted against its own wings, so the two do not melt together at this size.
    pa.outline_against(grid, wings - hull, hull, color="O")

    # The eye last and cold, which is the one place the player's eye lands.
    eye_x = center[0] - hull_rx * 0.42
    for x, y in pa.rasterize(pa.ellipse(eye_x, center[1] - 0.6, 2.6, 2.0), FRAME_WIDTH, FRAME_HEIGHT):
        grid[y][x] = "e"
    for x, y in pa.rasterize(pa.ellipse(eye_x - 0.7, center[1] - 0.5, 1.2, 1.1), FRAME_WIDTH, FRAME_HEIGHT):
        grid[y][x] = "p"

    pa.outer_outline(grid, FRAME_WIDTH, FRAME_HEIGHT)
    return grid


def main() -> int:
    # Laid out type by type, each type's frames contiguous, which is what srcXOf plus the
    # animation's frame stride walks.
    grids = [
        render(type_index, frame_index)
        for type_index in range(TYPE_COUNT)
        for frame_index in range(FRAME_COUNT)
    ]
    sheet = pa.save_sheet(OUTPUT, grids, PALETTE, FRAME_WIDTH, FRAME_HEIGHT)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {TYPE_COUNT} types x {FRAME_COUNT} frames)")
    print(f"strip offsets: {[i * FRAME_WIDTH * FRAME_COUNT for i in range(TYPE_COUNT)]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
