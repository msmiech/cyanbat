# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Frame the main menu's title lettering in a gold rim and a warm glow.

    uv run tools/generate_title.py

The lettering itself, `title_lettering.png` next to this script, is the one piece of the game's art
drawn rather than generated, so it is kept here as a source and never edited: this reads it and
writes the framed title the menu shows. Re-running it is always safe - the output is never read
back as input, so the rim cannot end up wrapped around a rim.

Why a rim at all: the lettering is the same cyan as the waves rolling behind it on the menu, and
over the brightest of them it all but vanished. Gold is the one hue nowhere else on that screen.
Outwards from each stroke, in order:

* **The rim.** A few pixels of gold, lit from above - pale at the top of the letters, deep amber
  at their feet - so it reads as metal rather than as a flat sticker outline.
* **An edge.** One pixel of dark bronze around the rim. Gold on pale cyan is low contrast on its
  own; this is what keeps the silhouette crisp where the rim crosses the lightest part of a wave.
* **The glow.** A soft warm haze fading out past the edge, which lifts the title off the dark
  parts of the backdrop the way the rim lifts it off the light ones.

The rim is grown with a round pen rather than Pillow's square MaxFilter, and at 4x before being
scaled back down, so its outer edge is as smooth as the lettering's own antialiasing.
"""

import pathlib

from PIL import Image, ImageChops, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = pathlib.Path(__file__).resolve().parent / "title_lettering.png"
OUTPUT = ROOT / "game" / "src" / "commonMain" / "composeResources" / "drawable" / "title.png"

# In pixels of the lettering, which is 681x176.
RIM = 3
EDGE = 1
GLOW = 14
SUPERSAMPLE = 4

# Light catching the top of the rim, down to the shade along its foot.
RIM_GRADIENT = [(255, 246, 196), (255, 214, 82), (232, 168, 32), (176, 112, 12)]
EDGE_COLOR = (84, 52, 6)
GLOW_COLOR = (255, 190, 52)
# How far the blurred glow is pushed back up before it is used. A blur spreads the rim's coverage
# thin; without this the glow would be faint even where it meets the rim.
GLOW_GAIN = 1.5
GLOW_OPACITY = 0.85


def dilate(mask, radius):
    """Grow ``mask`` by a round pen of ``radius`` pixels."""
    grown = mask.copy()
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if (dx or dy) and dx * dx + dy * dy <= radius * radius:
                # offset() wraps around, which is harmless: the canvas is padded by more than any
                # radius used here, so only empty margin ever wraps.
                grown = ImageChops.lighter(grown, ImageChops.offset(mask, dx, dy))
    return grown


def vertical_gradient(size, stops, top, bottom):
    """``size`` filled with ``stops`` spread evenly from row ``top`` to row ``bottom``."""
    width, height = size
    column = Image.new("RGB", (1, height))
    for y in range(height):
        t = min(max((y - top) / (bottom - top), 0.0), 1.0) * (len(stops) - 1)
        i = min(int(t), len(stops) - 2)
        f = t - i
        column.putpixel(
            (0, y), tuple(round(a + (b - a) * f) for a, b in zip(stops[i], stops[i + 1]))
        )
    return column.resize(size)


def colored(size, color, mask):
    layer = Image.new("RGBA", size, color + (0,))
    layer.putalpha(mask)
    return layer


def main():
    lettering = Image.open(SOURCE).convert("RGBA")
    width, height = lettering.size
    pad = RIM + EDGE + GLOW + GLOW // 2
    size = (width + 2 * pad, height + 2 * pad)

    coverage = Image.new("L", size, 0)
    coverage.paste(lettering.getchannel("A"), (pad, pad))

    big = (size[0] * SUPERSAMPLE, size[1] * SUPERSAMPLE)
    rim_big = dilate(coverage.resize(big, Image.BICUBIC), RIM * SUPERSAMPLE)
    edge_big = dilate(rim_big, EDGE * SUPERSAMPLE)
    rim = rim_big.resize(size, Image.BOX)
    edge = edge_big.resize(size, Image.BOX)

    glow = edge.filter(ImageFilter.GaussianBlur(GLOW / 2.5))
    glow = glow.point(lambda v: round(min(255, v * GLOW_GAIN) * GLOW_OPACITY))

    framed = Image.new("RGBA", size, (0, 0, 0, 0))
    framed.alpha_composite(colored(size, GLOW_COLOR, glow))
    framed.alpha_composite(colored(size, EDGE_COLOR, edge))
    gold = vertical_gradient(size, RIM_GRADIENT, pad - RIM, pad + height + RIM).convert("RGBA")
    gold.putalpha(rim)
    framed.alpha_composite(gold)
    framed.alpha_composite(lettering, (pad, pad))

    framed.save(OUTPUT, optimize=True)
    print(f"{OUTPUT} ({size[0]}x{size[1]})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
