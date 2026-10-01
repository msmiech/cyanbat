# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the stage select's preview cards: one staged frame of each stage.

    uv run tools/generate_stage_previews.py

Composed from the game's own assets rather than screenshotted, so a preview can never show art the
game no longer ships - re-run this after regenerating any of the sheets it reads. Each one is a
full 480x320 frame, the size the game renders at, with the bat, some scenery and the stage's
hostiles placed where they would be mid-run.

Written to the shared Compose resources, where the menu picks them up on both platforms.
"""

import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
OUT = ROOT / "game" / "src" / "commonMain" / "composeResources" / "drawable"

WIDTH, HEIGHT = 480, 320
ENEMY_W = 32
ENEMY_FRAMES = 4
BAT_W = 45

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
    scene.alpha_composite(frame(load("cyanBat.png"), BAT_W, 1, WOUND_ROWS), (96, 140))
    return scene


def shot(scene, variant, x, y):
    scene.alpha_composite(frame(load("shot.png"), 24, variant), (x, y))


def bubble(scene, cx, cy, radius):
    """The shield as the game draws it: a faint fill and a brighter rim."""
    overlay = Image.new("RGBA", scene.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    draw.ellipse(box, fill=(184, 196, 255, 36), outline=(184, 196, 255, 190))
    scene.alpha_composite(overlay)


def cave():
    scene = stage("background.png", 300, ("topObstacle2.png", 330), ("bottomObstacle2.png", 190))
    sheet = load("enemies.png")
    for species, (x, y), beat in ((0, (300, 110), 0), (1, (380, 170), 2), (2, (250, 200), 1), (0, (410, 90), 3)):
        scene.alpha_composite(enemy(sheet, species, beat), (x, y))
    shot(scene, 0, 150, 154)
    shot(scene, 0, 205, 154)
    return scene


def forest():
    scene = stage("forestBackground.png", 520, ("forestTopObstacle2.png", 250), ("forestBottomObstacle1.png", 360))
    sheet = load("forestEnemies.png")
    # A swarm up high, a V of wisps low, and a shielded beetle between them.
    for i, (x, y) in enumerate(((300, 60), (326, 48), (318, 78), (348, 66), (342, 92))):
        scene.alpha_composite(enemy(sheet, 0, i % 4), (x, y))
    for x, y in ((330, 200), (352, 184), (352, 216), (374, 168), (374, 232)):
        scene.alpha_composite(enemy(sheet, 4, (x // 11) % 4), (x, y))
    scene.alpha_composite(enemy(sheet, 1, 1), (400, 130))
    bubble(scene, 416, 144, 22)
    scene.alpha_composite(enemy(sheet, 2, 0), (230, 118))
    shot(scene, 4, 196, 146)
    shot(scene, 0, 150, 154)
    return scene


# The desert's sky is drawn by the game rather than painted, so its card draws it the way the game
# does: bands of color blended between two of `Daylight`'s keyframes - these are its golden hour and
# its sunset, zenith to horizon - with the sun going down behind the dunes. Keep them in step with
# `Daylight.SKY` if that changes.
GOLDEN_HOUR = ((0xD4, 0x9A, 0x6C), (0xEE, 0xAA, 0x66), (0xFA, 0xC0, 0x70), (0xFF, 0xD6, 0x86))
SUNSET = ((0x84, 0x4A, 0x6E), (0xD6, 0x68, 0x5C), (0xF6, 0x8C, 0x4C), (0xFF, 0xB2, 0x54))
SKY_STOPS = (0.0, 0.4, 0.75, 1.0)
HORIZON = 236
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
    sun_x, sun_y, radius = 372, 216, 19
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

    for name, top, x in (("desertFar.png", 152, 120), ("desertMid.png", 214, 300), ("desertNear.png", 264, 700)):
        scene.alpha_composite(keyframe(name, SUNSET_ROW, x, WIDTH), (0, top))

    obelisk = keyframe("desertObstacle2.png", SUNSET_ROW)
    scene.alpha_composite(obelisk, (318, HEIGHT - obelisk.height))
    wall = keyframe("desertObstacle4.png", SUNSET_ROW)
    scene.alpha_composite(wall, (24, HEIGHT - wall.height))

    sheet = load("desertEnemies.png")
    # A wyrmling leaping nose first out of the sand, a hawk over the top of its loop, a cloud of
    # locusts, and a djinn throwing a fan of fire at the bat.
    scene.alpha_composite(turned(enemy(sheet, 2, 1), 38), (236, 176))
    scene.alpha_composite(turned(enemy(sheet, 1, 1), 150), (330, 56))
    for i, (x, y) in enumerate(((388, 132), (410, 120), (404, 148), (430, 136), (426, 160), (448, 146))):
        scene.alpha_composite(enemy(sheet, 0, i % 4), (x, y))
    scene.alpha_composite(enemy(sheet, 3, 0), (286, 100))
    # Its fan, flying left and spreading, as the game turns enemy fire to face where it goes.
    bolt = frame(load("shot.png"), 24, 4).transpose(Image.FLIP_LEFT_RIGHT)
    for x, y, tilt in ((244, 94, 14), (238, 110, 0), (244, 126, -14)):
        scene.alpha_composite(turned(bolt, tilt), (x, y))
    scene.alpha_composite(frame(load("cyanBat.png"), BAT_W, 1, WOUND_ROWS), (96, 140))
    shot(scene, 0, 150, 154)
    shot(scene, 0, 205, 154)
    return scene


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, build in (("stage1_preview.png", cave), ("stage2_preview.png", forest), ("stage3_preview.png", desert)):
        image = build().convert("RGB")
        image.save(OUT / name)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
