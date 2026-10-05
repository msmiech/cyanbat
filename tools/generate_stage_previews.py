# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the stage select's preview cards: one staged frame of each stage.

    uv run tools/generate_stage_previews.py

Composed from the game's own assets rather than screenshotted, so a preview can never show art the
game no longer ships - re-run this after regenerating any of the sheets it reads. Each one is a
full 640x360 frame, the size the game renders at, with the bat, some scenery and the stage's
hostiles placed where they would be mid-run.

Written to the shared Compose resources, where the menu picks them up on both platforms.
"""

import math
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
OUT = ROOT / "game" / "src" / "commonMain" / "composeResources" / "drawable"

WIDTH, HEIGHT = 640, 360
ENEMY_W = 32
ENEMY_FRAMES = 4
BAT_W = 45

# The shot sheet holds each colorway's bolt in four frames side by side; the previews show the first.
SHOT_W = 24
SHOT_FRAMES = 4

# The bat's and the hostiles' sheets stack every frame unhurt, wounded and battered; the previews
# show them unhurt, from the top row.
WOUND_ROWS = 3


def load(name):
    return Image.open(ASSETS / name).convert("RGBA")


def frame(sheet, width, index, rows=1):
    return sheet.crop((index * width, 0, (index + 1) * width, sheet.height // rows))


def enemy(sheet, species, beat=0):
    return frame(sheet, ENEMY_W, species * ENEMY_FRAMES + beat, WOUND_ROWS)


def stage(background_name, background_x, top, bottom):
    scene = load(background_name).crop((background_x, 0, background_x + WIDTH, HEIGHT))
    top_rock = load(top[0])
    scene.alpha_composite(top_rock, (top[1], 0))
    bottom_rock = load(bottom[0])
    scene.alpha_composite(bottom_rock, (bottom[1], HEIGHT - bottom_rock.height))
    scene.alpha_composite(frame(load("cyanBat.png"), BAT_W, 1, WOUND_ROWS), (128, 160))
    return scene


def bolt(variant):
    return frame(load("shot.png"), SHOT_W, variant * SHOT_FRAMES)


def shot(scene, variant, x, y):
    scene.alpha_composite(bolt(variant), (x, y))


def bubble(scene, cx, cy, radius):
    """The shield as the game draws it: a faint fill and a brighter rim."""
    overlay = Image.new("RGBA", scene.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    draw.ellipse(box, fill=(184, 196, 255, 36), outline=(184, 196, 255, 190))
    scene.alpha_composite(overlay)


def cave():
    scene = stage("background.png", 300, ("topObstacle2.png", 440), ("bottomObstacle2.png", 253))
    sheet = load("enemies.png")
    for species, (x, y), beat in ((0, (400, 130), 0), (1, (507, 190), 2), (2, (333, 220), 1), (0, (547, 110), 3)):
        scene.alpha_composite(enemy(sheet, species, beat), (x, y))
    shot(scene, 0, 182, 174)
    shot(scene, 0, 237, 174)
    return scene


def jungle():
    # Where the strip shows the ruins best: a terrace, a tower and its broken bridge behind it.
    scene = stage("jungleBackground.png", 330, ("jungleTopObstacle2.png", 333), ("jungleBottomObstacle1.png", 480))
    sheet = load("jungleEnemies.png")
    # A swarm up high, a V of wisps low, and a shielded beetle between them.
    for i, (x, y) in enumerate(((400, 80), (426, 68), (418, 98), (448, 86), (442, 112))):
        scene.alpha_composite(enemy(sheet, 0, i % 4), (x, y))
    for x, y in ((440, 220), (462, 204), (462, 236), (484, 188), (484, 252)):
        scene.alpha_composite(enemy(sheet, 4, (x // 11) % 4), (x, y))
    scene.alpha_composite(enemy(sheet, 1, 1), (533, 150))
    bubble(scene, 549, 164, 22)
    scene.alpha_composite(enemy(sheet, 2, 0), (307, 138))
    shot(scene, 4, 273, 166)
    shot(scene, 0, 182, 174)
    return scene


# The desert's sky is drawn by the game rather than painted, so its card draws it the way the game
# does: bands of color blended between two of `Daylight`'s keyframes - these are its golden hour and
# its sunset, zenith to horizon - with the sun going down behind the dunes. Keep them in step with
# `Daylight.SKY` if that changes.
GOLDEN_HOUR = ((0xD4, 0x9A, 0x6C), (0xEE, 0xAA, 0x66), (0xFA, 0xC0, 0x70), (0xFF, 0xD6, 0x86))
SUNSET = ((0x84, 0x4A, 0x6E), (0xD6, 0x68, 0x5C), (0xF6, 0x8C, 0x4C), (0xFF, 0xB2, 0x54))
SKY_STOPS = (0.0, 0.4, 0.75, 1.0)
HORIZON = 276
BAND = 3

# The desert's scenery is drawn in four lights, stacked; the card is lit by the third, sunset.
SUNSET_ROW = 2
KEYFRAMES = 4


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def sky_color(height, toward_sunset):
    stops = [mix(g, s, toward_sunset) for g, s in zip(GOLDEN_HOUR, SUNSET)]
    for i in range(len(SKY_STOPS) - 1):
        if height <= SKY_STOPS[i + 1] or i == len(SKY_STOPS) - 2:
            t = (height - SKY_STOPS[i]) / (SKY_STOPS[i + 1] - SKY_STOPS[i])
            return mix(stops[i], stops[i + 1], max(0.0, min(1.0, t)))


def keyframe(name, row, x=0, width=None):
    sheet = load(name)
    height = sheet.height // KEYFRAMES
    width = width or sheet.width
    return sheet.crop((x, row * height, x + width, (row + 1) * height))


def turned(sprite, clockwise):
    """A sprite turned the way the game turns one: clockwise, about its center, nearest-neighbor."""
    return sprite.rotate(-clockwise, resample=Image.NEAREST, expand=True)


def desert():
    scene = Image.new("RGBA", (WIDTH, HEIGHT))
    draw = ImageDraw.Draw(scene)
    for top in range(0, HEIGHT, BAND):
        color = sky_color((top + BAND / 2) / HORIZON, 0.75)
        draw.rectangle((0, top, WIDTH, top + BAND - 1), fill=color + (255,))

    # The sun, low and swollen, inside the three rings of halo the game gives it.
    sun_x, sun_y, radius = 496, 256, 19
    sun = (255, 150, 72)
    halo = Image.new("RGBA", scene.size, (0, 0, 0, 0))
    halo_draw = ImageDraw.Draw(halo)
    for ring, alpha in ((3.4, 0.1), (2.4, 0.16), (1.6, 0.26)):
        r = radius * ring
        halo_draw.ellipse((sun_x - r, sun_y - r, sun_x + r, sun_y + r), fill=sun + (round(255 * alpha * 0.95),))
        scene.alpha_composite(halo)
        halo = Image.new("RGBA", scene.size, (0, 0, 0, 0))
        halo_draw = ImageDraw.Draw(halo)
    draw.ellipse((sun_x - radius, sun_y - radius, sun_x + radius, sun_y + radius), fill=sun + (255,))
    core = radius * 0.72
    draw.ellipse((sun_x - core, sun_y - core, sun_x + core, sun_y + core), fill=mix(sun, (255, 255, 255), 0.55) + (255,))

    for name, top, x in (("desertFar.png", 192, 120), ("desertMid.png", 254, 300), ("desertNear.png", 304, 700)):
        scene.alpha_composite(keyframe(name, SUNSET_ROW, x, WIDTH), (0, top))

    warrior = keyframe("desertObstacle2.png", SUNSET_ROW)
    scene.alpha_composite(warrior, (424, HEIGHT - warrior.height))
    platform = keyframe("desertObstacle4.png", SUNSET_ROW)
    scene.alpha_composite(platform, (32, HEIGHT - platform.height))

    sheet = load("desertEnemies.png")
    # A wyrmling leaping nose first out of the sand, a hawk over the top of its loop, a cloud of
    # locusts, and a djinn throwing a fan of fire at the bat.
    scene.alpha_composite(turned(enemy(sheet, 2, 1), 38), (315, 216))
    scene.alpha_composite(turned(enemy(sheet, 1, 1), 150), (440, 76))
    for i, (x, y) in enumerate(((517, 152), (539, 140), (533, 168), (559, 156), (555, 180), (577, 166))):
        scene.alpha_composite(enemy(sheet, 0, i % 4), (x, y))
    scene.alpha_composite(enemy(sheet, 3, 0), (381, 120))
    # Its fan, flying left and spreading, as the game turns enemy fire to face where it goes.
    fire = bolt(4).transpose(Image.FLIP_LEFT_RIGHT)
    for x, y, tilt in ((339, 114, 14), (333, 130, 0), (339, 146, -14)):
        scene.alpha_composite(turned(fire, tilt), (x, y))
    scene.alpha_composite(frame(load("cyanBat.png"), BAT_W, 1, WOUND_ROWS), (128, 160))
    shot(scene, 0, 182, 174)
    shot(scene, 0, 237, 174)
    return scene


# The lagoon's sky is drawn by the game too: these are `Daybreak.SKY`'s sunrise and early morning,
# zenith to horizon, blended a little toward the morning - the striped sun just clear of the sea.
# Keep them in step with `Daybreak` if that changes.
LAGOON_SUNRISE = ((0x2E, 0x2E, 0x86), (0x72, 0x44, 0xA8), (0xEC, 0x5C, 0x9C), (0xFF, 0x9C, 0x6E))
LAGOON_MORNING = ((0x3E, 0x78, 0xCC), (0x9C, 0x7C, 0xD0), (0xFF, 0x88, 0xB8), (0xFF, 0xC0, 0x9A))
LAGOON_HORIZON = 232

# The lagoon's scenery is drawn in four lights, stacked; the card is lit by the third, sunrise.
SUNRISE_ROW = 2


def lagoon_sky(height):
    stops = [mix(a, b, 0.25) for a, b in zip(LAGOON_SUNRISE, LAGOON_MORNING)]
    for i in range(len(SKY_STOPS) - 1):
        if height <= SKY_STOPS[i + 1] or i == len(SKY_STOPS) - 2:
            t = (height - SKY_STOPS[i]) / (SKY_STOPS[i + 1] - SKY_STOPS[i])
            return mix(stops[i], stops[i + 1], max(0.0, min(1.0, t)))


def banded_sun(scene, x, y, radius, crown, foot, bands):
    """The sun as the game draws one rising through the haze over the sea, a row at a time."""
    draw = ImageDraw.Draw(scene)
    rows = round(radius)
    for row in range(-rows, rows):
        dy = row + 0.5
        down = dy / radius
        if down > 0.12 and (down * 5) % 1 < (0.12 + 0.43 * down) * bands:
            continue
        half = max(0.0, radius * radius - dy * dy) ** 0.5
        if half < 0.5:
            continue
        color = mix(crown, foot, (dy + radius) / (2 * radius))
        draw.rectangle((round(x - half), round(y + row), round(x + half) - 1, round(y + row)), fill=color + (255,))
        core = max(0.0, (radius * 0.72) ** 2 - dy * dy) ** 0.5
        if core >= 0.5:
            draw.rectangle((round(x - core), round(y + row), round(x + core) - 1, round(y + row)),
                           fill=mix(color, (255, 255, 255), 0.55) + (255,))


def lagoon():
    scene = Image.new("RGBA", (WIDTH, HEIGHT))
    draw = ImageDraw.Draw(scene)
    for top in range(0, HEIGHT, BAND):
        color = lagoon_sky((top + BAND / 2) / LAGOON_HORIZON)
        draw.rectangle((0, top, WIDTH, top + BAND - 1), fill=color + (255,))

    # The sun half out of the sea on the right, swollen, banded by the haze, in its three halos.
    sun_x, sun_y, radius = 566, 226, 22
    crown, foot = (255, 116, 128), (255, 64, 138)
    for ring, alpha in ((3.4, 0.1), (2.4, 0.16), (1.6, 0.26)):
        halo = Image.new("RGBA", scene.size, (0, 0, 0, 0))
        r = radius * ring
        ImageDraw.Draw(halo).ellipse((sun_x - r, sun_y - r, sun_x + r, sun_y + r), fill=crown + (round(255 * alpha),))
        scene.alpha_composite(halo)
    banded_sun(scene, sun_x, sun_y, radius, crown, foot, 1.0)

    for name, top, x in (("lagoonClouds.png", 12, 260), ("lagoonSea.png", 232, 0), ("lagoonFar.png", 164, 120),
                         ("lagoonMid.png", 186, 140), ("lagoonNear.png", 302, 200)):
        scene.alpha_composite(keyframe(name, SUNRISE_ROW, x, WIDTH), (0, top))

    arch = keyframe("lagoonObstacle4.png", SUNRISE_ROW)
    scene.alpha_composite(arch, (40, HEIGHT - arch.height))
    needle = keyframe("lagoonObstacle1.png", SUNRISE_ROW)
    scene.alpha_composite(needle, (452, HEIGHT - needle.height))

    sheet = load("lagoonEnemies.png")
    # A shark leaping forward out of the water behind the bat, a school of piranhas, a crab behind
    # its shell's bubble, and a puffer throwing its ring of spines.
    scene.alpha_composite(turned(enemy(sheet, 2, 1), -34), (70, 230))
    for i, (x, y) in enumerate(((509, 96), (531, 84), (527, 112), (553, 100), (549, 124), (571, 110))):
        scene.alpha_composite(enemy(sheet, 0, i % 4), (x, y))
    scene.alpha_composite(enemy(sheet, 1, 2), (431, 196))
    bubble(scene, 447, 210, 22)
    scene.alpha_composite(enemy(sheet, 3, 2), (352, 112))
    spine = bolt(2)
    for k in range(8):
        angle = k * 45 + 22
        r = 30
        px = 368 + r * math.cos(math.radians(angle)) - 12
        py = 126 + r * math.sin(math.radians(angle)) - 6
        scene.alpha_composite(turned(spine, angle), (round(px), round(py)))
    scene.alpha_composite(frame(load("cyanBat.png"), BAT_W, 1, WOUND_ROWS), (168, 150))
    shot(scene, 0, 222, 164)
    shot(scene, 0, 277, 164)
    return scene


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, build in (("stage1_preview.png", jungle), ("stage2_preview.png", cave), ("stage3_preview.png", desert),
                        ("stage4_preview.png", lagoon)):
        image = build().convert("RGB")
        image.save(OUT / name)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
