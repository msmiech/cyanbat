# Stages

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## Stage content

**Stage content is split four ways.**

- `Stage` (registered in `GameAssets.load`) is how a stage looks and sounds. Its `Backdrop` is
  either a `Strip` (the jungle, the cave: one tiled image) or a `Sky` (the desert and the lagoon: a
  sky the game draws, changing with the time of day on the stage clock, over parallax bands of
  ground). Its `Approach`, where it has one, is what its obstacles turn into on the way to its boss.
- `StageDesign` is its waves and boss, and for the lagoon a second boss after it (`encore`);
  `StageDesign.forStage` maps a stage id to its design. Its `bossToughness` keeps a boss harder than
  the waves that lead to it: the Moth Queen is fought at the strength she had as stage 2's boss, at
  the end of an eased opening stage.
- `StageProgression` turns seconds elapsed into the current wave's stats, scaled up per stage, and
  the boss by its own `bossDifficulty`; what a boss calls in arrives as its `escortWave`, at the
  boss's difficulty.
- `EnemyGenerator` owns only the clock and the dice, and spawns through `EntityFactory`.

Adding a stage touches all four, plus new generators in `tools/` (its music among them, with a
`StageMusic` in `GameAssets`), a preview card, the stage select's strings, and the recorder's
`STAGE_COVERAGE`, which says how much of the stage the README's reel shows. The endless stage, after
the last, is made differently on every count; see The endless stage.

## The lagoon

**The lagoon** (stage 4) is the hardest stage: its waves are the desert's design pushed further,
with sharks that come in from behind along the waterline (`Squad.FROM_BEHIND`, a `LEAP` cruising
right)
and puffers that throw rings. A temple comes into sight on the way to the Naga: three of its bands
switch to temple strips, and its obstacles to temple stones.

The Naga is not its end, and nothing outside the run gives that away - not the stage select, not the
README, not the recorder's reel. Once it falls, the Sky Viper, a winged coral-banded serpent, comes
down out of the sky in the interlude (see [A run](run.md)), bites the bat and poisons it, and the
stage is won when the viper falls. `SkyViperBrain` flies its entrance and its fight: passes across
the frame from out of sight - a swoop from a hover, aimed once; a weave round the bat's height; from
its first wound a rain of venom (`ShotPattern.DOWN`) along the top - out of sight again between
them. Its fight is the Naga's piece again, from the top; it has no music of its own.

## Poison

**Poison.** The bite poisons the bat until the viper falls (`Poison`, on the run's ticks). It costs
no health: the run's world is drawn rippling, rows shifted sideways by whole frame pixels
(`Graphics.drawWarped`), and washed green, the edges more; a pixel either way at its rest, more as
the bite goes in and for a moment after every blow. The HUD is drawn over it, clear. It lifts over
the victory's seconds.

## The endless stage

**The endless stage** (stage 5, `ENDLESS_STAGE_ID`) opens once the lagoon is cleared and never ends:
round after round of waves and a boss, over country made up as it scrolls, until the bat goes down.
`Stage.endless` says which stage it is; its `Backdrop` is `Endless`, and a run of it is a
`GameScreen` like any other, which does what differs itself.

- A stage is open once unlocked or once the stage before it is cleared (`stageOpen`): a store kept
  before the endless stage existed recorded the lagoon's clear, which unlocked nothing then. Its
  card sits under the stages', full width, with no stars, since it cannot be cleared; its preview is
  rendered by `:desktop:renderEndlessPreview` from the scenery code itself.
- `EndlessCourse` deals the rounds, each a `StageProgression` like a stage's: three waves of two to
  four species from every stage, mixed afresh, then a boss from a shuffled bag of all five, never
  the same twice running. A round's `wavesBefore` numbers its waves on through the run, and their
  strength follows the number, so every wave is harder than the last; past a stage's boss wave,
  speed, bursts and blows stop growing (`StageProgression`'s caps), and health does not. Each round
  also opens `ENDLESS_DIFFICULTY_PER_ROUND` harder.
- `EnemyGenerator.beginRound` starts a round on the run's clock: `roundSeconds` restarts, nothing
  else does. Every species and boss is drawn from its own stage's sheets (`EnemySpecies.home`,
  `BossKind.home`), which the generator takes as `sheets` and `bossSheets`.
- A round's boss falling is `GameScreen.endRound`: wrecked, banked as a won stage's is, bonus and
  all, the music stopped dead, then a breather (`ENDLESS_ROUND_BREAK_SECONDS`) with the aftershocks
  and the fanfare, and the next round on the next piece of the playlist. The Sky Viper comes as any
  boss: its entrance still bites, and nothing collides until it arrives (`viperEntering`); its venom
  lifts as it falls. The game over screen says which wave the run reached.
- `EndlessScenery` is the country: the biome (`Biome`, from a shuffled bag every
  `ENDLESS_BIOME_SECONDS`), a cyclic day (`EndlessDay`, every `EndlessDay.SECONDS`), the weather,
  and four bands (`Band`) of land made a chunk at a time just out of view (`EndlessLand`). A chunk
  is palette codes (`ScenePalettes`: a biome's material and which way it faces), turned into one
  picture per `TimeOfDay` and crossfaded like the stages' scenery. Land is a pure function of
  band-x, seed and biome, so chunks meet without a seam; a new biome blends into each band from its
  next chunk, over
  `EndlessLand.transition`, the near band first and the far one last. Obstacles are generated per
  biome on each visit, in the same palettes. `EndlessScenerySystem` draws it all, first in the
  order, and lights the obstacles as `SkySystem` does.
- The night is dark: the stage is `lit`, and the scenery sets the `LightingSystem`'s ambient every
  tick, `DAYLIGHT` by day, storm or none (when the dark costs nothing, and a shot's colored light
  would read as a balloon in a bright land), down to `ENDLESS_NIGHT_AMBIENT` at the dead of night,
  lit up for a moment by a storm's lightning.
- `WeatherSystem` is rain, snow, ash with embers, dust, and fireflies at night, in a far layer
  behind every sprite and a near one (`front`) after them. Only the near one lands, on the bat and
  anything hostile, read against the outline of the frame each shows: snow settles and piles, rain
  beads and runs down, ash leaves soot (`WeatherMarksComponent`). A blow shakes it off (`shake`). A
  storm's thunder (`SoundEffect.THUNDER`) comes a moment after its flash.
- Its music is `Stage.playlist`: its own theme (`tools/generate_endless_music.py`) on the first
  round and every third, and between them remixes of the stages' and the Naga's pieces
  (`tools/generate_endless_remixes.py`: each score re-rendered faster, higher and over a house
  beat). Its `MusicDirector` is `endless`: the floor rises with every wave of the run rather than
  across a stage, and from `ENDLESS_FURY_WAVE` the heavy layer stays up.

## The sky's day

**The sky's day.** A `Day` is a pure function of how far through its day the stage is -
`Day.position`, elapsed time over the boss's arrival - and says what the sky, sun, moon and stars
look like then: `Daylight` is the desert's, from noon into night, and `Daybreak` the lagoon's, from
night into noon. `SkySystem` draws a `Backdrop.Sky` by its day, first in the system order, and puts
every entity carrying a `CrossfadeComponent` (the obstacles) in the same light.

- Scenery lit by the day is drawn once per entry of its day's `keyframes` (the desert's noon, golden
  hour, sunset and night; the lagoon's night, dawn, sunrise and noon), stacked top to bottom on its
  sheet, and crossfaded between neighbors with `drawPixmapFaded`. A generator writes the shapes once
  as materials and each keyframe as a palette, so the rows cannot drift apart.
- The sun the player can see is on the right - it sets there in the desert and rises there in the
  lagoon - so the scenery is lit from the right; the night palettes turn that round, to the moon on
  the left.
- The lagoon's sun rises out of the sea banded by the haze (`Day.sunBands`), drawn a row at a time,
  and lays a path of glints on the water (`Day.sunGlitter`) over the band with `water` rows.
- A band can turn into other scenery on the way to the boss (`ParallaxLayer.ahead`, from its
  `aheadFrom`): each stretch of it not yet in view is drawn from the other strip, so the new scenery
  scrolls in from the right. The two strips of a pair share their first and last columns, which the
  generator keeps to the band's own water and haze, so either follows on from the other.
