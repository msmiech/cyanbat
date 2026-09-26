# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the app's icons: Android's launcher icon, and the desktop builds' icons.

    uv run tools/generate_icons.py

Composed from the game's own art, the way the stage previews are, so the icon cannot drift away
from the game it opens: the bat is a frame of `assets/cyanBat.png`, the sky is the main menu's, and
the moon is lit in the title's gold. Re-run this after regenerating the bat sheet.

**The picture.** The bat crossing a full moon. The moon is there for contrast before anything else:
the bat is blue on a teal sky, and on its own the dark outline and the far wing sink into the
background until a launcher's grid shows a pale blob with ears. Against gold the whole silhouette
reads, down to a desktop's 16px. The gold is the title's rim: cyan inside gold is the first thing
the menu shows, so the icon and the game open on the same two colors.

Everything is drawn on one grid of 90x90 art pixels, which is an adaptive icon's 108dp layer at
1.2dp a pixel, so the bat keeps its in-game proportions against the moon at every size.

**Android.** An adaptive icon, `mipmap-anydpi/ic_launcher.xml`, whose three layers this writes as
vector drawables: the sky and the moon behind, the bat in front - so a launcher's parallax moves
the bat across the moon - and a monochrome glyph for themed icons. Vectors rather than PNGs per
density because a launcher draws the icon at whatever size its grid wants, and a bitmap scaled to
that goes soft. A vector redraws the pixel art as crisp squares at any size.

**Desktop.** The same picture on a rounded tile: `desktop/icons` holds the .ico, .icns and .png
the installers are built with, and `desktop/src/main/resources/icons` the sizes the running window
hands the OS for its title bar and task bar. Sizes that are a whole multiple of the art are scaled
by pixel replication; the rest are area-averaged from it, never resampled.
"""

import io
import pathlib
import struct
import sys

from PIL import Image, ImageDraw, ImageFilter

TOOLS = pathlib.Path(__file__).resolve().parent
ROOT = TOOLS.parent
sys.path.insert(0, str(TOOLS))

import generate_cyanbat_sprite as bat_sprite
import pixelart as pa
from generate_title import RIM_GRADIENT

DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"
PACKAGING = ROOT / "desktop" / "icons"
WINDOW_ICONS = ROOT / "desktop" / "src" / "main" / "resources" / "icons"

# The adaptive icon's 108dp layer, in art pixels. A launcher shows only the middle 72dp of it -
# rows and columns 15 to 75 here - and keeps the rest for parallax and for masks that bulge.
GRID = 90
VISIBLE = (15, 75)

# The main menu's sky, FlowBackground.SKY, from the top of the visible icon to its foot, in flat
# bands rather than a gradient: every other surface in the picture is pixel art.
SKY = [(0x0D, 0x4B, 0x5A), (0x07, 0x31, 0x3D), (0x03, 0x14, 0x1B)]
SKY_BANDS = 6

# Up and to the left of the middle, so the bat covers less of it: the lit rim shows past the far
# wing and the ears, which is where the silhouette most needs something bright behind it.
MOON_CENTER = (42, 42)
MOON_RADIUS = 19
# Where the diagonal from the lit rim to the shaded one crosses into each darker gold.
MOON_BANDS = (0.30, 0.55, 0.80)
# Only where the bat leaves the moon showing: along its lit upper left.
CRATERS = ((30.0, 33.5, 2.4), (36.5, 26.5, 1.5), (27.5, 44.5, 1.4))

# Two rings of the menu's wave light, FlowBackground.WAVE_LIGHT, laid over its sky: how far each
# reaches past the moon, and how strongly it lights the sky. Rings rather than a blur, for the same
# reason the sky is banded. The outer one just fits inside the tightest mask a launcher uses, a
# circle across the visible 72dp, so no mask cuts the glow short on one side.
WAVE_LIGHT = (0x2F, 0xC4, 0xD8)
HALO = ((6, 0.20), (3, 0.35))

# FlowBackground's sparkles, as the four-pointed star they flare into: a white heart and pale cyan
# points. Two sit where a round mask still shows them, two in corners only square ones keep. Each
# lies wholly inside or wholly outside the desktop's close crop, so none is cut in half by it.
SPARKLE = (0xFF, 0xFF, 0xFF)
SPARKLE_TINT = (0xBF, 0xF6, 0xFF)
SPARKLES = ((69, 30), (27, 66), (22, 21), (69, 69))

# Wings driving down and fully spread, the frame the stage previews use too: of the six it is the
# one that says "bat" soonest, with the far wing raised against the moon.
BAT_FRAME = 1
# Where the frame's top left sits on the grid, which centers the bat's own bounding box.
BAT_AT = (22, 28)

# The themed icon is a single color, so depth has to come from opacity: the far wing and the insides
# of the ears are half there, and the eye's white is cut out so the pupil sits in a hole. Everything
# else is solid. One level between cut out and solid, no more: see [translucent].
MONOCHROME_ALPHA = {"e": 0.0, "F": 0.5, "G": 0.5, "i": 0.5}
# The moon, reduced to its rim and kept a pixel clear of the bat so the two read apart.
MONOCHROME_RING = 2.5

# lint's VectorPath check flags any path longer than this as slow to draw, so longer ones are split.
MAX_PATH_DATA = 800

# Every size the desktop icons come in. Windows asks for 20 and 40 at 125% scaling, and a list that
# holds each size it asks for spares the JDK scaling a pixel-art icon down itself.
DESKTOP_SIZES = (16, 20, 24, 32, 40, 48, 64, 96, 128, 256)
LINUX_SIZE = 512
# The desktop tile: the whole moon, its halo and the sparkles, 64 art pixels a side so that 64, 128,
# 256 and 512 are exact multiples of it...
FULL = (13, 13, 77, 77)
# ...and a closer crop of the bat and the moon for the small sizes, which have no pixels to spare.
CLOSE = (20, 20, 68, 68)
# The corner radius as a fraction of the tile, which is macOS's; nothing else prescribes one.
CORNER = 0.2245

# Apple's grid for a macOS icon: the tile 832px of a 1024px canvas - 13 pixels to an art pixel, a
# hair under the 824 Apple draws - with a soft shadow falling below it.
MAC_CANVAS = 1024
MAC_TILE = 13 * 64
MAC_SHADOW = dict(offset=12, blur=14, opacity=0.3)
MAC_SIZES = (32, 64, 128, 256, 512)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def ramp(stops, t):
    """[stops] spread evenly over 0 to 1, read at [t]."""
    t = min(max(t, 0.0), 1.0) * (len(stops) - 1)
    i = min(int(t), len(stops) - 2)
    return lerp(stops[i], stops[i + 1], t - i)


def hex_color(rgb, alpha=1.0):
    if alpha >= 1.0:
        return "#{:02X}{:02X}{:02X}".format(*rgb)
    return "#{:02X}{:02X}{:02X}{:02X}".format(round(alpha * 255), *rgb)


# --- the picture ---------------------------------------------------------------------------------


def background():
    """The sky, the halo and the moon, as a grid of palette keys, and the palette."""
    palette = {}
    grid = pa.blank(GRID, GRID)
    top, bottom = VISIBLE
    band_height = (bottom - top) / SKY_BANDS
    for band in range(SKY_BANDS):
        palette[f"s{band}"] = ramp(SKY, (band + 0.5) / SKY_BANDS)
    for y in range(GRID):
        # The first and last bands run on to the layer's edges, which only parallax ever shows.
        band = min(max(int((y - top) // band_height), 0), SKY_BANDS - 1)
        for x in range(GRID):
            grid[y][x] = f"s{band}"

    cx, cy = MOON_CENTER
    for ring, (reach, strength) in enumerate(HALO):
        palette[f"h{ring}"] = lerp(SKY[0], WAVE_LIGHT, strength)
        radius = MOON_RADIUS + reach
        for x, y in pa.rasterize(pa.ellipse(cx, cy, radius, radius), GRID, GRID):
            grid[y][x] = f"h{ring}"

    for band, gold in enumerate(RIM_GRADIENT):
        palette[f"g{band}"] = gold
    moon = pa.rasterize(pa.ellipse(cx, cy, MOON_RADIUS, MOON_RADIUS), GRID, GRID)
    for x, y in moon:
        grid[y][x] = f"g{moon_band(x, y)}"
    # A crater is the gold one band darker than the ground it is pitted into.
    for crater in CRATERS:
        for x, y in pa.rasterize(pa.ellipse(*crater[:2], crater[2], crater[2]), GRID, GRID) & moon:
            grid[y][x] = f"g{min(moon_band(x, y) + 1, len(RIM_GRADIENT) - 1)}"

    palette["k"] = SPARKLE
    palette["t"] = SPARKLE_TINT
    for x, y in SPARKLES:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            grid[y + dy][x + dx] = "t"
        grid[y][x] = "k"
    return grid, palette


def moon_band(x, y):
    """Which of the golds a pixel of the moon is: banded across it, lit from the upper left."""
    cx, cy = MOON_CENTER
    reach = ((x + 0.5 - cx) + (y + 0.5 - cy)) / (2 ** 0.5 * MOON_RADIUS)
    depth = (reach + 1) / 2
    return sum(depth >= threshold for threshold in MOON_BANDS)


def foreground():
    """The bat, as a grid of the sprite generator's palette keys, read back off the sheet."""
    keys = {color: key for key, color in bat_sprite.PALETTE.items() if key != pa.TRANSPARENT}
    sheet = Image.open(ROOT / "assets" / "cyanBat.png").convert("RGBA")
    frame = sheet.crop((
        BAT_FRAME * bat_sprite.FRAME_WIDTH, 0,
        (BAT_FRAME + 1) * bat_sprite.FRAME_WIDTH, bat_sprite.FRAME_HEIGHT,
    ))
    grid = pa.blank(GRID, GRID)
    for y in range(frame.height):
        for x in range(frame.width):
            color = frame.getpixel((x, y))
            if color[3]:
                grid[BAT_AT[1] + y][BAT_AT[0] + x] = keys[color]
    return grid, {key: color[:3] for key, color in bat_sprite.PALETTE.items()}


def pixels_of(grid):
    return {
        (x, y) for y, row in enumerate(grid) for x, key in enumerate(row) if key != pa.TRANSPARENT
    }


def monochrome(bat):
    """The themed icon's glyph: how opaque each pixel of it is, 0 to 1."""
    alpha = {}
    silhouette = pixels_of(bat)
    for x, y in silhouette:
        alpha[(x, y)] = MONOCHROME_ALPHA.get(bat[y][x], 1.0)
    cx, cy = MOON_CENTER
    rim = pa.rasterize(pa.ellipse(cx, cy, MOON_RADIUS, MOON_RADIUS), GRID, GRID) - pa.rasterize(
        pa.ellipse(cx, cy, MOON_RADIUS - MONOCHROME_RING, MOON_RADIUS - MONOCHROME_RING), GRID, GRID
    )
    clearance = silhouette | {n for p in silhouette for n in pa.neighbors(*p)}
    for p in rim - clearance:
        alpha[p] = 1.0
    return alpha


def scene():
    """The whole picture, as an image one art pixel to the pixel."""
    image = Image.new("RGBA", (GRID, GRID))
    for grid, palette in (background(), foreground()):
        for x, y in pixels_of(grid):
            image.putpixel((x, y), palette[grid[y][x]] + (255,))
    return image


# --- Android: vector drawables -------------------------------------------------------------------

# The vector's own units: half an art pixel, so that a region can reach half a pixel past its edge.
UNITS = 2 * GRID


def layered(owner, order):
    """
    [owner] - a map of pixel to key - as one region per key, in paint [order], in half pixels.

    Painted as they stand, two neighboring pixels of different colors would each get an antialiased
    edge where they meet, and wherever that edge fell between two device pixels the two partial
    coverages would leave a hairline of whatever is underneath - in the back layer, the wallpaper.
    So each region reaches half a pixel under every neighbor painted after it: every edge between
    two colors is then drawn over solid color, and only the picture's outer edge is ever blended
    with what is behind it.

    Half a pixel rather than a whole one, because two regions reaching under the same one-pixel
    line from either side must meet in its middle, not at the far edge of it where they would
    show. And not everything painted after, because then every region in between would have an
    edge there too - gold fringing a sparkle, in the back layer, for passing under it.
    """
    assert set(owner.values()) <= set(order), f"nowhere to paint {set(owner.values()) - set(order)}"
    rank = {key: i for i, key in enumerate(order)}
    halves = {
        (2 * x + dx, 2 * y + dy): rank[key]
        for (x, y), key in owner.items()
        for dx in (0, 1)
        for dy in (0, 1)
    }
    regions = []
    for i, key in enumerate(order):
        own = {half for half, r in halves.items() if r == i}
        if own:
            under = {n for half in own for n in pa.neighbors(*half) if halves.get(n, -1) > i}
            regions.append((key, own | under))
    return regions


def colored(grid, palette, order):
    """[grid]'s pixels as [layered] regions, each with its color."""
    owner = {(x, y): grid[y][x] for x, y in pixels_of(grid)}
    return [(hex_color(palette[key]), region) for key, region in layered(owner, order)]


def translucent(alpha):
    """
    An opacity map as [layered] regions of white, the translucent one painted before the solid.

    Only the tint's alpha reaches the screen, so the color is immaterial. There can be only one
    translucent level: its underlap is then painted over by solid, which hides it, where a second
    translucent level painted over it would compound with it into a visible seam.
    """
    owner = {p: a for p, a in alpha.items() if a > 0}
    levels = sorted(set(owner.values()))
    assert len([a for a in levels if a < 1]) <= 1, f"more than one translucent level: {levels}"
    return [(hex_color((0xFF, 0xFF, 0xFF), a), region) for a, region in layered(owner, levels)]


def outline_loops(pixels):
    """
    The boundary of a set of pixels, as closed loops of grid corners.

    Every edge runs with the region on its right - clockwise round the outside, counterclockwise
    round a hole - which is what the nonzero fill rule needs to leave the holes empty. Where two
    pixels touch only at a corner the loop turns right, keeping them apart.
    """
    edges = {}
    for x, y in sorted(pixels):
        if (x, y - 1) not in pixels:
            edges.setdefault((x, y), []).append((x + 1, y))
        if (x + 1, y) not in pixels:
            edges.setdefault((x + 1, y), []).append((x + 1, y + 1))
        if (x, y + 1) not in pixels:
            edges.setdefault((x + 1, y + 1), []).append((x, y + 1))
        if (x - 1, y) not in pixels:
            edges.setdefault((x, y + 1), []).append((x, y))

    loops = []
    while edges:
        start = min(edges)
        loop, here, heading = [start], start, None
        while True:
            options = edges[here]
            step = options[0]
            if heading is not None and len(options) > 1:
                right = (here[0] - heading[1], here[1] + heading[0])
                if right in options:
                    step = right
            options.remove(step)
            if not options:
                del edges[here]
            heading = (step[0] - here[0], step[1] - here[1])
            here = step
            if here == start:
                break
            loop.append(here)
        loops.append(loop)
    return loops


def path_data(pixels):
    """[pixels] as vector path data: one subpath per loop, straight runs merged into one line."""
    data = []
    for loop in outline_loops(pixels):
        corners = [
            point
            for i, point in enumerate(loop)
            if (loop[i - 1][0] == point[0]) != (point[0] == loop[(i + 1) % len(loop)][0])
        ]
        x, y = corners[0]
        data.append(f"M{x},{y}")
        for nx, ny in corners[1:]:
            data.append(f"h{nx - x}" if ny == y else f"v{ny - y}")
            x, y = nx, ny
        data.append("z")
    return "".join(data)


def connected(pixels):
    """[pixels] split into its four-connected pieces, in a fixed order."""
    left, pieces = set(pixels), []
    while left:
        seed = min(left)
        piece, frontier = {seed}, [seed]
        while frontier:
            x, y = frontier.pop()
            for n in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if n in left and n not in piece:
                    piece.add(n)
                    frontier.append(n)
        left -= piece
        pieces.append(piece)
    return pieces


def paths(pixels):
    """
    Path data for [pixels], in as many strings as it takes to keep each one under lint's limit.

    Separate pieces go into separate paths first. A piece too long on its own is cut in two across
    its rows, and the halves overlap by a whole art pixel: two paths meeting edge to edge would
    leave the same hairline between them that [layered] exists to prevent, where overlapping
    halves paint the seam solid at any size a launcher draws the icon.
    """
    data = path_data(pixels)
    if len(data) <= MAX_PATH_DATA:
        return [data]
    pieces = connected(pixels)
    if len(pieces) > 1:
        out = []
        for piece in pieces:
            for part in paths(piece):
                if out and len(out[-1]) + len(part) <= MAX_PATH_DATA:
                    out[-1] += part
                else:
                    out.append(part)
        return out
    rows = sorted({y for _, y in pixels})
    if len(rows) < 4:
        # Too thin to cut into two smaller overlapping halves - and too thin to ever be this long.
        return [data]
    cut = rows[(len(rows) - 1) // 2]
    return paths({p for p in pixels if p[1] <= cut + 1}) + paths({p for p in pixels if p[1] >= cut})


def write_vector(path, layers, what):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {what} Generated by tools/generate_icons.py: change the script and re-run it"
        " rather than editing this file. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        f'    android:viewportWidth="{UNITS}"',
        f'    android:viewportHeight="{UNITS}">',
    ]
    count = 0
    for color, pixels in layers:
        for data in paths(pixels):
            lines += [
                "    <path",
                f'        android:fillColor="{color}"',
                f'        android:pathData="{data}" />',
            ]
            count += 1
    lines.append("</vector>")
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write("\n".join(lines) + "\n")
    print(f"{path} ({count} paths)")


def write_android():
    sky, sky_palette = background()
    # Back to front: the sky from the top band down, the halo from the outside in, the moon from
    # its shaded side to its lit one, then the sparkles.
    sky_order = [f"s{band}" for band in range(SKY_BANDS)]
    sky_order += [f"h{ring}" for ring in range(len(HALO))]
    sky_order += [f"g{band}" for band in reversed(range(len(RIM_GRADIENT)))]
    sky_order += ["t", "k"]
    write_vector(
        DRAWABLES / "ic_launcher_background.xml",
        colored(sky, sky_palette, sky_order),
        "The launcher icon's back layer: the menu's sky and the moon.",
    )

    bat, bat_palette = foreground()
    # The outline first, since it rings everything else, then the palette's own order.
    bat_order = [key for key in bat_palette if key != pa.TRANSPARENT]
    write_vector(
        DRAWABLES / "ic_launcher_foreground.xml",
        colored(bat, bat_palette, bat_order),
        "The launcher icon's front layer: the bat, frame 1 of assets/cyanBat.png.",
    )
    write_vector(
        DRAWABLES / "ic_launcher_monochrome.xml",
        translucent(monochrome(bat)),
        "The launcher icon as a themed icon: the bat and the moon's rim, in opacity alone.",
    )


# --- desktop: bitmaps ----------------------------------------------------------------------------


def rounded_mask(size, supersample=8):
    """A rounded square's coverage, antialiased by drawing it large and averaging it down."""
    big = size * supersample
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=big * CORNER, fill=255)
    return mask.resize((size, size), Image.BOX)


def upscaled(art, size):
    """
    [art] at [size]: replicated to the first whole multiple at or past it, then averaged down.

    The average is over the area each output pixel covers, so the art's squares keep their hard
    edges wherever they line up with the output and are blended only where they straddle it.
    """
    factor = -(-size // art.width)
    big = art.resize((art.width * factor, art.height * factor), Image.NEAREST)
    return big if big.width == size else big.resize((size, size), Image.BOX)


def tile(picture, size):
    """The icon on its rounded tile, filling a [size] square."""
    image = upscaled(picture.crop(FULL if size >= 64 else CLOSE), size)
    image.putalpha(rounded_mask(size))
    return image


def mac_icon(picture):
    """The .icns master: the tile on Apple's grid, with its shadow."""
    inset = (MAC_CANVAS - MAC_TILE) // 2
    shape = rounded_mask(MAC_TILE)
    shadow = Image.new("L", (MAC_CANVAS, MAC_CANVAS), 0)
    shadow.paste(shape, (inset, inset + MAC_SHADOW["offset"]))
    shadow = shadow.filter(ImageFilter.GaussianBlur(MAC_SHADOW["blur"]))
    shadow = shadow.point(lambda v: round(v * MAC_SHADOW["opacity"]))
    icon = Image.new("RGBA", (MAC_CANVAS, MAC_CANVAS), (0, 0, 0, 0))
    icon.putalpha(shadow)
    face = upscaled(picture.crop(FULL), MAC_TILE)
    face.putalpha(shape)
    icon.alpha_composite(face, (inset, inset))
    return icon


def write_ico(path, images):
    """
    A Windows .ico of [images]: 32-bit bitmaps up to 128px, and a PNG at 256.

    The layout Windows' own tools write, and the one the Compose plugin's default icon has, which
    every Compose app's installer is built with. Pillow would write a PNG at every size; Windows
    reads those too, but the bitmaps are what everything that handles an icon is sure to read.
    """
    entries = []
    for image in sorted(images, key=lambda i: i.width):
        size = image.width
        if size >= 256:
            data = io.BytesIO()
            image.save(data, format="PNG")
            entries.append((size, data.getvalue()))
            continue
        # A bitmap header with the height doubled, for the image and its mask stacked; then the
        # pixels as BGRA, bottom row first; then a one-bit mask of the fully transparent pixels,
        # each row padded to four bytes, which Windows ignores in favor of alpha but expects. Under
        # the mask the color is black, which is what a reader that goes by the mask assumes.
        header = struct.pack(
            "<IiiHHIIiiII", 40, size, 2 * size, 1, 32, 0, size * size * 4, 0, 0, 0, 0
        )
        seen = image.getchannel("A").point(lambda a: 255 if a else 0)
        clean = Image.composite(image, Image.new("RGBA", image.size), seen)
        pixels = clean.transpose(Image.Transpose.FLIP_TOP_BOTTOM).tobytes("raw", "BGRA")
        stride = (size + 31) // 32 * 4
        alpha = image.getchannel("A").load()
        mask = bytearray(stride * size)
        for y in range(size):
            row = (size - 1 - y) * stride
            for x in range(size):
                if alpha[x, y] == 0:
                    mask[row + x // 8] |= 0x80 >> (x % 8)
        entries.append((size, header + pixels + bytes(mask)))

    directory = struct.pack("<HHH", 0, 1, len(entries))
    offset = len(directory) + 16 * len(entries)
    for size, data in entries:
        side = size if size < 256 else 0
        directory += struct.pack("<BBBBHHII", side, side, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    path.write_bytes(directory + b"".join(data for _, data in entries))


def write_desktop():
    picture = scene()
    PACKAGING.mkdir(parents=True, exist_ok=True)
    WINDOW_ICONS.mkdir(parents=True, exist_ok=True)

    tiles = {size: tile(picture, size) for size in DESKTOP_SIZES}
    for size, image in tiles.items():
        image.save(WINDOW_ICONS / f"cyanbat_{size}.png")
    print(f"{WINDOW_ICONS} ({len(tiles)} sizes, {min(tiles)} to {max(tiles)}px)")

    ico = PACKAGING / "cyanbat.ico"
    write_ico(ico, tiles.values())
    print(f"{ico} ({len(tiles)} sizes)")

    png = PACKAGING / "cyanbat.png"
    tile(picture, LINUX_SIZE).save(png)
    print(f"{png} ({LINUX_SIZE}x{LINUX_SIZE})")

    master = mac_icon(picture)
    icns = PACKAGING / "cyanbat.icns"
    smaller = [master.resize((size, size), Image.BOX) for size in MAC_SIZES]
    master.save(icns, format="ICNS", append_images=smaller)
    print(f"{icns} ({MAC_CANVAS}px master)")


def main() -> int:
    write_android()
    write_desktop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
