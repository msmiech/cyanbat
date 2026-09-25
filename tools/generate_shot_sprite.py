# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the shot sheet: one bolt, in every colorway that fires it.

Kept as a script rather than a loose PNG so the pixel art stays editable: tweak the map below
and re-run, instead of reverse-engineering colors out of the image.

    uv run tools/generate_shot_sprite.py

The bolt points right, which is the only direction the *player* fires; enemy shots travel left and
are turned by `FacingSystem`, so one drawing serves both.

Seven colorways, laid out left to right and addressed the way the enemy sheet is. The player's is
cyan, the next three are the cave's enemy palettes from `generate_enemy_sprites.py` - violet, amber
and crimson - and the last three are the forest's shooters - so a shot is the same color as
whatever fired it. That matters more than it sounds:
the screen can hold the bat's shots and the boss's at once, travelling in opposite directions, and
before this they were the same cyan bolt. Which ones were dangerous had to be worked out from
which way they were moving.
"""

import pathlib

from PIL import Image

# . transparent   d dim tail   c cyan body   w white-hot tip
#
# Drawn at 24x12 rather than scaling a smaller map up: a nearest-neighbor 2x would give the
# bolt 2x2 pixel blocks, visibly coarser than the bat and the enemies it flies past. Same pixel
# density as the rest of the art, just a bigger object.
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

# One ramp per colorway: a part-transparent tail, a solid body, and a hot tip. The tip stays near
# white in every one of them - that is the part which reads as "this is moving fast" - so the hue
# lives in the body and the tail, where there is enough of it to carry a color.
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
    # ZIGZAG, and so the boss: crimson. The forest's beetle fires these too - it is crimson.
    {"d": (140, 30, 50, 180), "c": (220, 66, 80, 255), "w": (255, 222, 216, 255)},
    # The forest's own, from `generate_forest_enemy_sprites.py` and `generate_forest_boss_sprite.py`.
    # SPITTER: magenta, the pod's color.
    {"d": (130, 30, 120, 180), "c": (228, 90, 204, 255), "w": (255, 226, 248, 255)},
    # WISP: flame orange.
    {"d": (170, 56, 20, 180), "c": (252, 140, 40, 255), "w": (255, 244, 200, 255)},
    # the Moth Queen: rose, and a touch paler than the spitter's so the two read apart mid-fight.
    {"d": (150, 50, 96, 180), "c": (240, 128, 170, 255), "w": (255, 236, 244, 255)},
)

TRANSPARENT = (0, 0, 0, 0)

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "shot.png"


def main() -> int:
    height = len(SPRITE)
    width = len(SPRITE[0])
    if any(len(row) != width for row in SPRITE):
        raise SystemExit("every row of SPRITE must be the same width")

    sheet = Image.new("RGBA", (width * len(COLORWAYS), height), TRANSPARENT)
    for index, ramp in enumerate(COLORWAYS):
        for y, row in enumerate(SPRITE):
            for x, key in enumerate(row):
                color = TRANSPARENT if key == "." else ramp[key]
                sheet.putpixel((index * width + x, y), color)

    sheet.save(OUTPUT)
    print(f"{OUTPUT} ({sheet.width}x{sheet.height}, {len(COLORWAYS)} colorways of {width}x{height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
