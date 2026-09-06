# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Crop the score/highscore/lives overlay out of a CyanBat screenshot.

The game renders into a 480x320 framebuffer that is stretched to the window, so HUD text is
small and blurry in a full-size capture. This crops the top-left corner where the HUD lives.

Run through uv so the Pillow dependency resolves itself:

    uv run .claude/skills/run-cyanbat/crop_hud.py <in.png> <out.png>
"""

import sys

from PIL import Image

# Fractions of the frame, matching the HUD's position in the 480x320 framebuffer.
HUD_WIDTH_FRACTION = 0.26
HUD_HEIGHT_FRACTION = 0.21
# Skip the status bar on Android captures.
TOP_OFFSET_PX = 10


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    source, destination = sys.argv[1], sys.argv[2]
    with Image.open(source) as image:
        width = int(image.width * HUD_WIDTH_FRACTION)
        height = int(image.height * HUD_HEIGHT_FRACTION)
        image.crop((0, TOP_OFFSET_PX, width, TOP_OFFSET_PX + height)).save(destination)

    print(destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
