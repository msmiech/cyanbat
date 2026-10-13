# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the pictures the steering dialog asks its question with: one per way a finger can fly
the bat, drag or stick.

    uv run tools/generate_steering_previews.py

Each is the bottom of a frame of the jungle, the stage a first run is flown in, with a hand flying
the bat. Drag: a finger on the bat, which has come along the dotted way the finger went. Stick: a
thumb on the stick at the bottom left, pushed up and to the right, and the bat flying off that way
on the far side of the screen from it - which is the point of the stick, that the thumb is not over
the bat.

Composed from the game's own art, as the stage select's previews are: the jungle's strip, a frame of
`assets/cyanBat.png`, the bat's bolt. The stick is drawn as `GameScreen.drawStick` draws it, at its
place and size in the frame and in its colors, so keep the numbers here in step with `STICK_*` in
`CyanBatConstants`. The hands are drawn here: a pale, cool glove of a hand, as a game's tutorial
draws one, ringed in the outline everything else wears.

640x320 each, the frame's width and its bottom 320 rows: the dialog sets the two side by side at 2:1.
Written to the shared Compose resources, where the menu picks them up on every platform.
"""

import pathlib

from PIL import Image, ImageDraw

from pixelart import blank, capsule, ellipse, outer_outline, rasterize, union, TRANSPARENT

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
OUT = ROOT / "game" / "src" / "commonMain" / "composeResources" / "drawable"

WIDTH, HEIGHT = 640, 320
# The picture is the frame's bottom 320 rows; a frame's y is this much further down than the picture's.
FRAME_TOP = 360 - HEIGHT

BAT_W = 45
WOUND_ROWS = 3
SHOT_W = 24
SHOT_FRAMES = 4

# The stick, as the game has it: `STICK_*` in CyanBatConstants.
STICK_RADIUS = 48
STICK_KNOB_RADIUS = 20
STICK_HOME = (16 + STICK_RADIUS, 360 - 16 - STICK_RADIUS - FRAME_TOP)
STICK_BASE = (0x00, 0x00, 0x00, 0x46)
STICK_SHADOW = (0x00, 0x00, 0x00, 0x70)
STICK_RIM_HELD = (0xC8, 0xF8, 0xFF, 0xD0)
STICK_KNOB_HELD = (0x00, 0xD8, 0xF8, 0xC0)

# The glove: lit from the top left, as everything of the bat's is, and cool, as the bat is.
HAND = {
    "l": (0xF4, 0xF7, 0xFC, 255),
    "m": (0xD2, 0xDB, 0xE8, 255),
    "d": (0xA2, 0xB0, 0xC6, 255),
    "o": (0x16, 0x1A, 0x26, 255),
}

# Where the finger has drawn the bat from, and the trail it leaves; the way it is going on.
TRAIL = (0xE8, 0xF8, 0xFF)
TOUCH_RING = (0xC8, 0xF8, 0xFF)


def load(name):
    return Image.open(ASSETS / name).convert("RGBA")


def bat():
    sheet = load("cyanBat.png")
    return sheet.crop((BAT_W, 0, 2 * BAT_W, sheet.height // WOUND_ROWS))


def bolt():
    sheet = load("shot.png")
    return sheet.crop((0, 0, SHOT_W, sheet.height))


def jungle():
    """The jungle's strip where its ruins show, its bottom 320 rows, dimmed a little under the hands."""
    strip = load("jungleBackground.png")
    scene = strip.crop((330, FRAME_TOP, 330 + WIDTH, FRAME_TOP + HEIGHT))
    scene.alpha_composite(Image.new("RGBA", scene.size, (8, 12, 24, 70)))
    return scene


def faded(sprite, alpha):
    """[sprite] see-through at [alpha], 0..1."""
    out = sprite.copy()
    out.putalpha(out.getchannel("A").point(lambda a: round(a * alpha)))
    return out


def overlay(scene, draw):
    """Draws [draw] onto a layer of its own and lays it over [scene], so translucency blends."""
    layer = Image.new("RGBA", scene.size, (0, 0, 0, 0))
    draw(ImageDraw.Draw(layer))
    scene.alpha_composite(layer)


def circle(draw, cx, cy, radius, fill=None, rim=None):
    """A circle the way the game rasterizes one: an odd number of pixels across, square on its center."""
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    draw.ellipse(box, fill=fill, outline=rim)


def stick(scene, knob):
    """The stick at its place, a thumb on it pushing the knob to [knob], as `drawStick` draws it held."""
    cx, cy = STICK_HOME
    overlay(scene, lambda d: circle(d, cx, cy, STICK_RADIUS, STICK_BASE, STICK_SHADOW))
    overlay(scene, lambda d: circle(d, cx, cy, STICK_RADIUS - 1, None, STICK_RIM_HELD))
    overlay(scene, lambda d: circle(d, knob[0], knob[1], STICK_KNOB_RADIUS, STICK_KNOB_HELD, STICK_RIM_HELD))


def dotted(scene, points, color, every=7, size=2):
    """Dots every [every] pixels along the polyline through [points], fading in toward its end."""
    path = []
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        steps = max(1, round(((bx - ax) ** 2 + (by - ay) ** 2) ** 0.5))
        for i in range(steps):
            t = i / steps
            path.append((ax + (bx - ax) * t, ay + (by - ay) * t))
    dots = path[::every]

    def draw(d):
        for i, (x, y) in enumerate(dots):
            alpha = round(90 + 150 * i / max(1, len(dots) - 1))
            x, y = round(x), round(y)
            d.rectangle((x, y, x + size - 1, y + size - 1), fill=color + (alpha,))

    overlay(scene, draw)


def glove(shape, width, height):
    """
    One part of a hand - a finger, the fist - rasterized as the glove in a [width] by [height] box:
    lit from the top left, so its bottom right edge is shaded two bands deep and it reads as round,
    and ringed in the outline. A hand is its parts laid over one another, each with its own ring,
    which is what keeps a finger apart from the fist it rests on.
    """
    pixels = rasterize(shape, width, height)
    grid = blank(width, height)
    for x, y in pixels:
        if (x + 1, y) not in pixels or (x, y + 1) not in pixels or (x + 1, y + 1) not in pixels:
            grid[y][x] = "d"
        elif (x + 3, y) not in pixels or (x, y + 3) not in pixels or (x + 2, y + 2) not in pixels:
            grid[y][x] = "m"
        else:
            grid[y][x] = "l"
    outer_outline(grid, width, height)
    return painted(grid)


def nail(shape, width, height):
    """A fingernail, pale and ringed in the glove's shadow rather than the outline: it is on the thumb."""
    pixels = rasterize(shape, width, height)
    grid = blank(width, height)
    for x, y in pixels:
        inner = all(n in pixels for n in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
        grid[y][x] = "l" if inner else "d"
    return painted(grid)


def painted(grid):
    image = Image.new("RGBA", (len(grid[0]), len(grid)), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, key in enumerate(row):
            if key != TRANSPARENT:
                image.putpixel((x, y), HAND[key])
    return image


def layered(width, height, *parts):
    """The parts of a hand laid over one another, the first at the back."""
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for part in parts:
        image.alpha_composite(part)
    return image


# The pointing hand's box, and where in it the fingertip is. Tall enough to run off the picture.
POINTER_W, POINTER_H = 92, 200
POINTER_TIP = (26, 4)


def pointing_hand():
    """
    A hand from below, its forefinger up and the rest curled in a fist under it, which runs off the
    bottom of its box: the classic pointer, as a tutorial shows a tap.
    """
    w, h = POINTER_W, POINTER_H
    fist = glove(union(ellipse(52, 104, 38, 42), capsule((50, 130), (52, 240), 31, 31)), w, h)
    # The three fingers curled into it, each a knuckle on its top edge, stepping down to the little
    # finger.
    curled = [glove(ellipse(cx, cy, 11, 12), w, h) for cx, cy in ((44, 70), (63, 73), (80, 79))]
    thumb = glove(capsule((20, 128), (16, 94), 12, 11), w, h)
    finger = glove(capsule((26, 15), (26, 78), 11, 12), w, h)
    return layered(w, h, fist, *reversed(curled), thumb, finger)


# The thumb's box, and where in it the pad presses down. Big enough to run off the picture's corner.
THUMB_W, THUMB_H = 170, 170
THUMB_TIP = (128, 42)


def thumb_hand():
    """
    A left thumb from the bottom left corner, seen from above, its nail showing: the pad pressed
    on the stick's knob, the rest of it running off the box's bottom left, as off the screen's.
    """
    w, h = THUMB_W, THUMB_H
    tip = THUMB_TIP
    base = (tip[0] - 160, tip[1] + 160)
    thumb = glove(capsule(tip, base, 15, 26), w, h)
    # The nail on its back, a little behind the tip, along the thumb's length.
    length = ((base[0] - tip[0]) ** 2 + (base[1] - tip[1]) ** 2) ** 0.5
    along = ((base[0] - tip[0]) / length, (base[1] - tip[1]) / length)
    nail_from = (tip[0] + along[0] * 8, tip[1] + along[1] * 8)
    nail_to = (tip[0] + along[0] * 22, tip[1] + along[1] * 22)
    return layered(w, h, thumb, nail(capsule(nail_from, nail_to, 7, 8), w, h))


def lay(scene, hand, at):
    """[hand] over [scene] at [at], which may hang off its edges, as a hand does off the screen's."""
    scene.paste(hand, at, hand)


def along_path(points, fraction):
    """The point [fraction] of the way along the polyline through [points]."""
    lengths = [((bx - ax) ** 2 + (by - ay) ** 2) ** 0.5 for (ax, ay), (bx, by) in zip(points, points[1:])]
    remaining = sum(lengths) * fraction
    for ((ax, ay), (bx, by)), length in zip(zip(points, points[1:]), lengths):
        if remaining <= length:
            t = remaining / length
            return ax + (bx - ax) * t, ay + (by - ay) * t
        remaining -= length
    return points[-1]


def trail(scene, sprite, points, steps):
    """[sprite] fading in along the way through [points], at each of [steps] (fraction, alpha)."""
    for fraction, alpha in steps:
        x, y = along_path(points, fraction)
        scene.alpha_composite(faded(sprite, alpha), (round(x - BAT_W / 2), round(y - sprite.height / 2)))


def flying(scene, sprite, at):
    """The bat at [at], firing, as it does all the time."""
    scene.alpha_composite(sprite, at)
    scene.alpha_composite(bolt(), (at[0] + 52, at[1] + 14))
    scene.alpha_composite(bolt(), (at[0] + 108, at[1] + 14))


def drag_preview():
    scene = jungle()
    sprite = bat()
    # The bat, and the dotted way the finger has drawn it along from the bottom left.
    bat_at = (300, 104)
    center = (bat_at[0] + BAT_W / 2, bat_at[1] + sprite.height / 2)
    path = [(108, 250), (176, 226), (240, 176), center]
    dotted(scene, path, TRAIL)
    trail(scene, sprite, path, ((0.1, 0.18), (0.4, 0.3), (0.7, 0.46)))
    flying(scene, sprite, bat_at)
    # The finger on its belly, from below, in the rings a touch sends out.
    tip = (round(center[0]), round(center[1]) + 12)
    overlay(scene, lambda d: circle(d, tip[0], tip[1] + 6, 19, None, TOUCH_RING + (210,)))
    overlay(scene, lambda d: circle(d, tip[0], tip[1] + 6, 26, None, TOUCH_RING + (100,)))
    lay(scene, pointing_hand(), (tip[0] - POINTER_TIP[0], tip[1] - POINTER_TIP[1]))
    return scene


def stick_preview():
    scene = jungle()
    sprite = bat()
    # Pushed up and to the right, nearly all the way.
    cx, cy = STICK_HOME
    knob = (cx + 30, cy - 30)
    # The bat flying off up and to the right, across the screen from the thumb, its trail behind it.
    bat_at = (360, 70)
    center = (bat_at[0] + BAT_W / 2, bat_at[1] + sprite.height / 2)
    path = [(center[0] - 150, center[1] + 120), center]
    trail(scene, sprite, path, ((0.25, 0.18), (0.5, 0.3), (0.75, 0.46)))
    flying(scene, sprite, bat_at)
    stick(scene, knob)
    lay(scene, thumb_hand(), (knob[0] - THUMB_TIP[0], knob[1] - THUMB_TIP[1]))
    return scene


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, build in (("steering_drag_preview.png", drag_preview), ("steering_stick_preview.png", stick_preview)):
        image = build().convert("RGB")
        image.save(OUT / name)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
