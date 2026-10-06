# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Take the line of English out of the game over artwork, leaving GAME OVER and the wreck behind it.

    uv run tools/generate_game_over.py

The artwork, `game_over_art.png` next to this script, is hand-drawn, so it is kept here as a source
and never edited: this reads it and writes `assets/gameover.png`, the picture the game over screen
shows. GAME OVER stays as it was drawn, in every language. The line under it, "Touch the screen for
main menu", was drawn into the picture too, which left it English whatever the player's language;
the screen now draws that line itself, as text in the player's language, where this takes it out.

What is under the line is the faint debris of the wreck, flat patches of four dark blues, and
nothing of the line is any of them: the line is white and grey, so every pixel of it with any red
or green in it. Each one is filled from the art around it, a ring at a time from the line's edges
inwards, with whichever of the blues most of its filled neighbors already have, so the patches
close up over where the letters were with their own edges.
"""

import pathlib
from collections import Counter

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = pathlib.Path(__file__).resolve().parent / "game_over_art.png"
OUTPUT = ROOT / "assets" / "gameover.png"

# The rows the line is drawn in, with a margin; GAME OVER ends well above them, on row 179.
LINE_ROWS = range(200, 250)


def is_line(pixel):
    """Whether ``pixel`` is part of the line: the art behind it is blue and nothing else."""
    red, green, _, _ = pixel
    return red > 0 or green > 0


def main():
    art = Image.open(SOURCE).convert("RGBA")
    width, height = art.size
    px = art.load()

    holes = {(x, y) for y in LINE_ROWS for x in range(width) if is_line(px[x, y])}
    while holes:
        filled = {}
        for x, y in sorted(holes, key=lambda p: (p[1], p[0])):
            around = Counter(
                px[x + dx, y + dy]
                for dy in (-1, 0, 1)
                for dx in (-1, 0, 1)
                if (dx or dy)
                and 0 <= x + dx < width
                and 0 <= y + dy < height
                and (x + dx, y + dy) not in holes
            )
            if around:
                # The commonest neighbor, and of two as common the darker, so a tie is settled
                # the same way every run.
                filled[(x, y)] = max(around.items(), key=lambda kv: (kv[1], -kv[0][2]))[0]
        for point, color in filled.items():
            px[point] = color
        holes -= filled.keys()

    art.save(OUTPUT)
    print(f"wrote {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
