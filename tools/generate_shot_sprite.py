# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the player's shot sprite.

Kept as a script rather than a loose PNG so the pixel art stays editable: tweak the map below
and re-run, instead of reverse-engineering colours out of the image.

    uv run tools/generate_shot_sprite.py

The bolt points right, which is the only direction the player fires. Sizing is set against the
480x320 framebuffer, where the bat is 45x40 and an enemy is ~32x29 - a shot much bigger than
this reads as a projectile the size of its target.
"""

import pathlib

from PIL import Image

# . transparent   d dim tail   c cyan body   w white-hot tip
SPRITE = [
    "....dccw....",
    "..ddcccccw..",
    ".ddccccccccw",
    ".ddccccccccw",
    "..ddcccccw..",
    "....dccw....",
]

# Picked against the existing art: the bat is cyan and white, the cave background is dark and
# desaturated, so a cyan bolt reads clearly without introducing a new hue to the palette.
PALETTE = {
    ".": (0, 0, 0, 0),
    "d": (0, 140, 200, 180),
    "c": (0, 229, 255, 255),
    "w": (240, 255, 255, 255),
}

OUTPUT = pathlib.Path(__file__).resolve().parent.parent / "assets" / "shot.png"


def main() -> int:
    height = len(SPRITE)
    width = len(SPRITE[0])
    if any(len(row) != width for row in SPRITE):
        raise SystemExit("every row of SPRITE must be the same width")

    image = Image.new("RGBA", (width, height))
    for y, row in enumerate(SPRITE):
        for x, key in enumerate(row):
            image.putpixel((x, y), PALETTE[key])

    image.save(OUTPUT)
    print(f"{OUTPUT} ({width}x{height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
