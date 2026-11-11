# Bosses, wounds and elites

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## Bosses with a body

**Bosses with a body.** The Sand Wyrm is a head plus nine plates, each an entity, laid by
`SandWyrmBrain` along the path the head has flown. The Naga is a hooded head plus twelve parts, laid
by `NagaBrain` along a curve from where it came up out of the water to its head while it rears and
strikes, and along its head's path while it swims; it changes between the two only under the water.
Every part carries a `BossPartComponent`: a shot that hits a part lands on the head, which carries
the health (a plate passes on only its `share` of it). A shot lands on the boss once and spends one
pierce on it, however many parts it meets (`GameScreen.hasAlreadyStruck`); one that meets the head
lands there, whole. Nothing but the bat's weapons wears down any boss, these or the others: the bat
flying into one lands nothing. The brain holds the plates' ids for the whole fight, which is safe
only because nothing else moves, culls or kills them; `GameScreen` removes them when the boss dies.
Its health bar is pinned to the screen (`HealthBarComponent.pinnedTo`), because the head spends half
the fight under the sand. The Naga's is pinned for the same reason, and its fight is five phases, at
fifths of its health (`NAGA_PHASE_*_AT`), where every other boss changes phase at its wounds' marks.
The Sky Viper is a head, a pair of wings and ten parts, all laid by `SkyViperBrain` back along the
path its head has flown, the wings on a walk of their own since they sit among the parts. Its art
faces left like every hostile's, and nothing flips a sprite, so it only ever flies leftward, or as
near straight up or down as a dive needs (`STEEPEST`); a head turned past straight up would fly
upside down. It goes out of sight after every pass and starts the next from out of sight, so its
body is never seen to jump.

## Wounds

**Wounds.** Every creature's sheet - the bat's, the enemy sheets, the boss sheets - stacks each
frame three times, top to bottom: unhurt, wounded, battered (`WOUND_ROWS`). `WoundSystem`
moves a sprite down a row as its health falls past `WOUND_MARKS`, read off the health every tick, so
healing undoes it. The bosses change phase at the same marks.

- A creature's pixmap is three pictures tall. Its sprite must set `srcHeight` to one row - the
  default is the whole pixmap - and anything sized off the sheet divides by `WOUND_ROWS`.
- The Sand Wyrm's plates and the Naga's parts have no health; their brains draw them from the head's
  row. The sand plume and the Naga's splash are drawn on the top row only.
- The dead are left alone: the bat falls on its one-row death sheet, which is drawn battered.
- A wound also slows an ordinary enemy: `GameScreen` sets its `PaceComponent` from the row
  (`WOUNDED_PACE`, `WOUNDED_FIRE_RATE`) through `WoundSystem`'s callback. A pace is a clock of the
  entity's own, read by the movement, behavior, animation and weapon systems, so a slowed pattern
  keeps its shape and only takes longer. Bosses carry no pace and are never slowed. The recorder's
  forecast copies the pace, or it would dodge a wounded enemy where it is not going to be.
- A generator draws each wound from the creature's own parts (`pa.notch`, `pa.crack`, `pa.ring`,
  `pa.tear`) so it stays put through the animation, and writes the rows with `pa.save_rows`.
  `SpriteSheetTest` checks that every wounded frame differs from the one above it.

## Elites

**Elites.** From a stage's second minute, a group can arrive with an elite in it: a loner, one of a
swarm, or a formation's leader.

- `WaveDesign.eliteChance` is rolled once per group in `EnemyGenerator.spawnGroup`. Boss summons
  skip that roll, so a boss never calls up an elite.
- An elite has `ELITE_HIT_POINT_FACTOR` its kind's health (not its shield), and fires its kind's
  gun - or the issued one, for a kind that carries none, aimed for one that comes from behind - at
  `ELITE_FIRE_INTERVAL_FACTOR` the interval. Its `ElitePalette` is the color of its `AuraComponent`
  and of its `shot.png` colorway.
  `SpriteSheetTest` holds each colorway's body to its palette's rim.
- `GameScreen` reads its `EliteComponent` as it dies, to pay `ELITE_SCORE_FACTOR` and
  `ELITE_EXPERIENCE_FACTOR` kills' worth; it is still one kill to the streak.
- Shot down by the bat's weapons, it also drops a power-up (`GameScreen.dropPowerUp`): a silver plus
  spinning on the spot, drifting with the scenery from inside the frame. It collides as
  `CollisionGroup.PICKUP`, which meets the bat and nothing else, and holds nothing: flying into it
  draws `PowerUp.roll` from what the run can still use, then and there, and takes it as a pick is
  taken (`takePowerUp`), banner and all, with a chime of its own (`SoundEffect.POWER_UP`), but with
  no dialog and so no grace. In the dark it is a light, at z 16 over the dark. Nothing drops once
  the stage is won, and nothing is taken.
- An aura's halo is drawn between two `RenderSystem` passes: the scenery strip (below z 0), then
  every other sprite. In one pass the strip covered every halo in the jungle and the cave.
