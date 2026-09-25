# /// script
# requires-python = ">=3.9"
# dependencies = ["pillow"]
# ///
"""Generate the level select's preview cards: one staged frame of each level.

    uv run tools/generate_level_previews.py

Composed from the game's own assets rather than screenshotted, so a preview can never show art the
game no longer ships - re-run this after regenerating any of the sheets it reads. Each one is a
full 480x320 frame, the size the game renders at, with the bat, some scenery and the level's
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


def load(name):
    return Image.open(ASSETS / name).convert("RGBA")


def frame(sheet, width, index):
    return sheet.crop((index * width, 0, (index + 1) * width, sheet.height))


def enemy(sheet, species, beat=0):
    return frame(sheet, ENEMY_W, species * ENEMY_FRAMES + beat)


def stage(background_name, background_x, top, bottom):
    scene = load(background_name).crop((background_x, 0, background_x + WIDTH, HEIGHT))
    top_rock = load(top[0])
    scene.alpha_composite(top_rock, (top[1], 0))
    bottom_rock = load(bottom[0])
    scene.alpha_composite(bottom_rock, (bottom[1], HEIGHT - bottom_rock.height))
    scene.alpha_composite(frame(load("cyanBat.png"), BAT_W, 1), (96, 140))
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


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, build in (("level1_preview.png", cave), ("level2_preview.png", forest)):
        image = build().convert("RGB")
        image.save(OUT / name)
        print(f"{OUT / name} ({image.width}x{image.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
