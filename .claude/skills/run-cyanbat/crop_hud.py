# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Crop the HUD out of a CyanBat screenshot.

The HUD is the band across the top of the frame: score, combo and wave down the left, the stage
timer in the middle, and the level in the top right corner.

The game renders into a 480x320 framebuffer that is scaled up evenly to fit the window - with
bars beside it on a screen wider than 3:2, above and below it on one taller - so HUD text is small
and blurry in a full-size capture. This finds where the framebuffer landed and crops that band out
of it.

That assumes one of the display modes that keep the game's shape, Ambient bars (the default) or
Black bars. Under Stretch to fit screen the framebuffer fills the capture and this crop is off.

Run through uv so the Pillow dependency resolves itself:

    uv run .claude/skills/run-cyanbat/crop_hud.py <in.png> <out.png>
"""

import sys

from PIL import Image

FRAME_BUFFER_WIDTH = 480
FRAME_BUFFER_HEIGHT = 320

# Fractions of the framebuffer, matching the HUD's position in it: the full width, and down to
# the third line of the left column.
HUD_WIDTH_FRACTION = 1.0
HUD_HEIGHT_FRACTION = 0.21
# Skip the status bar on Android captures.
TOP_OFFSET_PX = 10


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    source, destination = sys.argv[1], sys.argv[2]
    with Image.open(source) as image:
        # Where the game drew it: the largest 3:2 box that fits, centered. See FrameFit.
        scale = min(image.width / FRAME_BUFFER_WIDTH, image.height / FRAME_BUFFER_HEIGHT)
        game_width = round(FRAME_BUFFER_WIDTH * scale)
        game_height = round(FRAME_BUFFER_HEIGHT * scale)
        left = (image.width - game_width) // 2
        top = (image.height - game_height) // 2 + TOP_OFFSET_PX
        width = int(game_width * HUD_WIDTH_FRACTION)
        height = int(game_height * HUD_HEIGHT_FRACTION)
        image.crop((left, top, left + width, top + height)).save(destination)

    print(destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
