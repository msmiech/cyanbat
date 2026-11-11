# A run

Part of the notes for coding agents; [AGENTS.md](../../AGENTS.md) has the rules that hold everywhere
and the list of these files.

## A run

**A run.** `GameScreen` is one run of one stage.

- Pause, the level-up offer and "stage complete" are state inside it rather than separate
  `Screen`s, because swapping screens disposes the run. `GameOverScreen` is its own screen.
- Moving to the next stage builds a fresh `GameScreen`. That is what resets score, experience and
  power-ups. It is handed the run's `Difficulty`, which carries on (see Difficulty).
- On Android the run lives only in `CyanBatGameActivity`, and recreating it starts the run over. So
  it takes every configuration change a run can meet in place - dark mode coming on, a fold or a
  resize, another display's density, a pad dropping out - through its `configChanges`, each with its
  reason in the manifest. Anything it comes to read from its configuration, a theme or a resource,
  has to follow a change in `onConfigurationChanged` rather than wait for a recreation.
- The boss going down does not put "stage complete" up at once. Everything hostile goes with it, and
  the run plays on for `STAGE_COMPLETE_DELAY_SECONDS` (`playOutVictory`) under a banner that the
  boss has fallen: the bat flies on, unarmed and untouchable, the wreck bursts again at
  `BOSS_AFTERSHOCK_SECONDS`, the stage's music stops dead for the boss's blast, and a fanfare comes
  in. The overlay lands on the fanfare's drop. The aftershocks are timed to the blasts in
  `bossDeath.wav` and the delay to the fanfare's `LANDING_BEAT`; change each with its script.
- A stage with a second boss (`StageDesign.encore`; only the lagoon) is not won by its first. The
  first goes up as any does (`wreckBoss`), and the run plays an interlude instead (`Interlude`,
  `playInterlude`), a cutscene on the world's own ticks: the wreck's aftershocks with the bat still
  flown; any level up the first boss bought, offered there rather than over the second's name; then
  black bars come in (`letterbox`, which hides the HUD), the bat is taken out of the player's hands
  to a mark (`holdBat`, input dropped rather than queued), and once the bars are in the second boss
  comes on (`EnemyGenerator.spawnEncore`). Its brain flies its entrance and calls back for the bite
  and for its arrival, which begins its fight partway through a frame. Nothing collides in the
  interlude, the bat fires nothing, and the music is silent until the fight.
- Overlays read taps through `TapDetector` plus an arming delay, so the finger that was steering
  when an overlay opened does not pick something when it lifts.
- Every way the bat is kept from harm is its `hitCooldown`: the mercy after a hit, which Second Wind
  lengthens and a Second Life starts over, and the grace after a level-up pick
  (`POWER_UP_GRACE_SECONDS`). `InvulnerabilitySystem` reads it every tick and draws the bat
  see-through while it runs, a dither pulsing between faint and strong that always ends on the
  faint, so the bat coming back whole is the moment it can be hurt again. Something new that keeps
  the bat safe should set the cooldown (`InvulnerabilitySystem.grant`), and it shows by itself. A
  won stage leaves the bat untouchable by `stageComplete` instead, with nothing left to hurt it, and
  does not show.
- In-game text (HUD, banners, overlays) is drawn at frame coordinates in `GameScreen` and
  `GameOverScreen`, in the player's language (see [Languages](languages.md)). The rows are
  hard-coded, but every centered or right-aligned line is placed by its measured width, so
  rewording one needs no new x.

## Difficulty

**Difficulty.** Every run is flown on a `Difficulty` - Easy, Normal or Hard - which the menu asks
for in a `DifficultyDialog` whenever a stage starts from it, by Start Game or a stage card. The
dialog starts on the one last chosen, kept with the settings (`SettingsRepository.difficulty`)
rather than in the menu's view model, which Android makes again for every return from a run.

- Hard is the game as it is tuned, and every factor on it is 1: the stages, waves and bosses are
  balanced against Hard alone, and the easier ones are discounts on it. Tests and the recorder fly
  Hard, which is `GameScreen`'s default.
- An easier difficulty touches two things and nothing else: `StageProgression.enemyDensity`, which
  stretches the gap between spawns (the waves' species, health and damage are unchanged, and so is
  every boss), and the bat's start, in `PlayerLoadout`: its `maxHitPoints`, `shotDamage` and
  `experienceMultiplier`, and its `startingPowerUps` (Regeneration on Normal, Regeneration and
  Second Life on Easy, none on Hard), each applied as a pick is. Picks build on that start as they
  would on Hard's, so a power-up held from the start is still offered if it stacks.
- A run keeps its difficulty: the stage complete overlay flies on to the next stage on the same, and
  Android hands it to the game activity as the `at.smiech.cyanbat.DIFFICULTY` extra, by name.
- A won stage records its clear on the run's difficulty (`StageUnlockStore.recordClearAsync`), next
  to the unlock it earns; the store keeps the hardest. The stage select draws it as stars on the
  stage's card, one per `Difficulty.stars`. Stars are drawn (`Stars`), not set as text, since not
  every platform's font has one.
- Clears are stored as stars under `cleared_stage_<id>`. A store kept before there were difficulties
  has unlocks and no clears, so each store gives it once (`clearsBeforeDifficulties`, marked done by
  `stage_clears_kept`) a Hard clear of every stage below its highest unlock: that is the only way
  they could have been opened then. Run again, it would count a later Easy clear's unlock as Hard.
- The highscore is one per stage, whatever the difficulty.

## Player progression

**Player progression** is per run and never persisted:

- `PlayerProgress` tracks experience and the bat's level.
- `PowerUp` builds the three-card offer. Maxed cards drop out of it, and the uncapped ones guarantee
  it can always be filled.
- `PlayerLoadout` holds the stats the picks derive.
- `ScoreTracker` carries fractional points between awards.
- Three picks are weapons of their own, each on its own clock: Guardian Orb, Charged Trail and Frost
  Beam. What the first two deal is a share of `shotDamage`, so the gun's upgrades pay into them, and
  a card can say something else once held (`PowerUp.describe`).
    - Orbs and charged wake segments collide as `CollisionGroup.PLAYER_CONTACT`, which meets enemies
      and nothing else, and land through `GameScreen.strike`. They are never spent: a target takes
      each
      `ContactWeapon` at most once a rehit time, kept on the target (`ContactCooldownComponent`) so
      it goes with it. A boss part's is its boss's, or a wake the Sand Wyrm pours through would land
      once a plate.
    - `OrbitSystem` carries the orbs round the bat clockwise, evenly spaced in creation order, at
      the loadout's `orbRadius` and `orbSecondsPerTurn`. Heavy Rounds makes the bat's shots and orbs
      bigger as well as harder (`shotScale`, up to `MAX_SHOT_SIZE_LEVEL`) and widens the ring with
      them; Rapid Fire turns the ring faster, and the orbs' rehit shortens in step. A bigger bolt or
      orb is not stretched: `shotHeavy.png` and `orbHeavy.png` draw them at every size, a band per
      size (`shotFrameWidth`, `heavyShotTop`, `orbFrameSize`, `heavyOrbTop`), and the factory takes
      the size rather than a scale; `sizeOrb` grows the orbs already circling.
    - `FrostBeamSystem` freezes ordinary enemies only - never a boss, a part of one or an elite
      (`GameScreen.canFreeze`). A freeze is `FrostSystem` holding the enemy's `PaceComponent` at
      zero, drifting it with the scenery and washing it blue (`TintComponent`, which `RenderSystem`
      draws like a flash). `slowWounded` leaves a frozen enemy's pace alone; the thaw hands back its
      wound's pace. Frozen, it is harmless: `handleCollision` lets the bat through it with no hit
      either way, while the bat's weapons still land.

## Achievements

**Achievements** (`Achievement`) are the game's own, since most of the platforms it runs on have
none to report to, and kept for good, each with a picture of the moment it was earned. The main
screen's Achievements opens them (`AchievementsScreen`): one column of cards that scrolls up and
down, a card's picture opening full size.

- A run earns them as they happen (`GameScreen.earn`): a level reached on every award of experience,
  a stage's clear in `completeStage` (`Achievement.forClear`, where the jungle on Hard earns its
  Easy and Normal too), a power-up maxed in `takePowerUp`, the first fall as the bat dies.
  Untouchable is no hit landing on the bat all stage (`batHit`, set where a hit starts its mercy);
  nothing getting past is no enemy culled out of the frame alive (`LifetimeSystem`'s `onLeave`,
  `onLeftFrame`), not counting one culled past the right edge on its way in.
- They are earned partway through a tick, and the frame presented after it is their picture:
  `keepAchievements`, last in `present`, takes it with `Graphics.capture` (`ComposeGraphics` draws
  the recording into a bitmap of its own at frame size, as `drawInto` does for the recorder) and
  hands the
  `FrameCapture` to the store. The notice along the bottom of the frame is drawn after it, so it is
  not in the picture. A run left before its frame was drawn keeps them without one.
- The store makes the PNG (`FrameCapture.encodePng`: Android's `Bitmap.compress`, Skia's encoder
  elsewhere) off the game's thread. A picture is 25 to 50 KB. Achievements earned together share
  one, kept once under the first one's key; each achievement's value is its picture's name, empty
  for none, and one already earned keeps its first. `KeyValueAchievementStore` keeps them on the
  desktop (the preferences, and files in `gameDataDirectory`), in a browser (local storage, the
  pictures as Base64) and on iOS (the user defaults, and files in Application Support);
  `DataStoreAchievementStore` on Android (files in `filesDir/achievements`).
- Stages cleared before there were achievements earn their clears once the menu opens
  (`achievementsOfClears`), without a picture; their cards show the stage's preview instead.
- Stored by `Achievement.key`, so the entries can be renamed or reordered; never change a key.
