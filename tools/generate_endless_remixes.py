# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy==2.5.3", "scipy==1.18.1"]
# ///
"""Generate the endless stage's remixes of the four stages' music and the Naga's, as stems.

    uv run tools/generate_endless_remixes.py [jungle cave desert lagoon naga]

The endless stage plays its own theme (tools/generate_endless_music.py) and, between its returns,
faster and brighter versions of every stage's piece: the same scores, played by the same band,
quicker, a step or two higher, and over a house beat. A remix is the original score re-rendered
rather than the original stems sped up, so nothing is stretched and nothing turns chipmunk: every
note is played at the new tempo and pitch from the start.

Each remix is written as `music/rush_<stage>_<layer>.wav`, eight stems on a grid of its own,
repeated in its `StageMusic` in GameAssets.

How a remix is made from a score:

* **Tempo.** The score's piece is swapped for one at the faster tempo, with the same seed, so every
  part rolls the same dice. A score is written in beats, so every note, echo and reverb time
  follows.
* **Pitch.** Every pitch the score names goes through its `hz`, which is swapped for one a few
  semitones up. Its drums, gongs and bells are tuned in hertz rather than notes, so `membrane` and
  `bell` are swapped too, and the whole kit moves with the harmony rather than clashing with it.
* **Groove.** A house beat - a kick on every beat, an open hat off it, a clap on the backbeat - is
  laid into the drive stem over the score's own drums.
"""

import importlib
import sys

from musicsynth import Piece, bell, clap, eq, hat, hz, kick, membrane, peak, saturate, write_stems

# Each remix: its score, the tempo it is played at (one whose beat is a whole number of output
# frames), how many semitones up, and whether its beat divides in three, as the cave's 12/8 does.
# The lagoon's and the Naga's are already fast and high, so they move least.
REMIXES = {
    "jungle": (120, 2, False),
    "cave": (75, 2, True),
    "desert": (126, 2, False),
    "lagoon": (150, 1, False),
    "naga": (168, 1, False),
}

LAYERS = ("bed", "pulse", "drive", "lead", "boom", "roll", "chop", "fury")


def house(piece: Piece, bars: int, rng, compound: bool):
    """A house beat [bars] long: four on the floor, open hats off the beat, claps on two and four.

    In a [compound] meter, whose beat is three eighths, the hats fall on its eighths rather than
    halfway through it, where they would fight the music's triplet lilt.
    """
    loop = piece.loop(bars)
    beats = piece.beats_per_bar
    open_at, closed_at = (2 / 3, 1 / 3) if compound else (0.5, 0.75)
    for bar in range(bars):
        base = bar * beats
        for beat in range(beats):
            loop.add(kick(rng, low=52, high=165, seconds=0.22, velocity=1.0 if beat % 2 == 0 else 0.9), base + beat)
            loop.add(hat(rng, open_=True, velocity=0.55), base + beat + open_at, pan=0.3)
            loop.add(hat(rng, velocity=0.28), base + beat + closed_at, pan=0.4)
        for beat in range(1, beats, 2):
            loop.add(clap(rng, velocity=0.7), base + beat, pan=-0.05)
    loop.audio = eq(saturate(loop.audio, 1.2), peak(3400, 2.0, 1.0))
    return loop


def remix(stage: str, bpm: int, semitones: int, compound: bool):
    score = importlib.import_module(f"generate_{stage}_music")
    original = score.PIECE
    piece = Piece(f"rush_{stage}", bpm=bpm, beats_per_bar=original.beats_per_bar, seed=original.seed)
    ratio = 2 ** (semitones / 12)

    score.PIECE = piece
    score.hz = lambda midi: hz(midi + semitones)
    score.membrane = lambda f0, *args, **kwargs: membrane(f0 * ratio, *args, **kwargs)
    score.bell = lambda f0, *args, **kwargs: bell(f0 * ratio, *args, **kwargs)

    stems = {layer: getattr(score, f"render_{layer}")() for layer in LAYERS}
    drive = stems["drive"]
    drive.mix(house(piece, drive.bars, piece.rng(900), compound), 0.55)
    write_stems(piece, stems)


def main(argv: list[str]) -> int:
    for stage in argv or REMIXES:
        remix(stage, *REMIXES[stage])
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
