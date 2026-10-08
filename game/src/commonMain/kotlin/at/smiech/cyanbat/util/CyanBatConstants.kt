package at.smiech.cyanbat.util

/**
 * The frame every screen draws, in framebuffer pixels: 16:9, the shape of most
 * phones held sideways and most monitors. It is also the playfield, the same on
 * every device, which spawn points, boss stations, the overlays and the desert's
 * sky are laid out against. Each host hands it to the engine.
 */
const val FRAME_BUFFER_WIDTH = 640
const val FRAME_BUFFER_HEIGHT = 360

/** The fixed simulation tick, in seconds. */
const val TICK_INITIAL = 0.019f

/** Seconds between the bat's automatic shots. */
const val SHOT_INTERVAL_SECONDS = 1f

/** Shot travel per tick, in framebuffer pixels. Enemies close at 1.2-2.5, so this outruns them. */
const val SHOT_SPEED = 4f

/** Length of the vibration when the bat takes a hit, in milliseconds. */
const val HIT_VIBRATION_MILLIS = 250L

/**
 * The pause overlay's dimming. It dims rather than hides the run, so the player can still see what
 * they are about to fly back into.
 */
const val PAUSE_DIM = 0xB4000000.toInt()

/**
 * How long the pause overlay ignores a tap: long enough to outlast the finger lift at the end of
 * the Android back gesture that paused the game, short enough to go unnoticed.
 */
const val RESUME_ARMING_SECONDS = 0.35f

/**
 * How long the game over screen ignores a tap. Longer than the other overlays: a player steering
 * with a finger down tends to lift it only once the screen has changed.
 */
const val GAME_OVER_ARMING_SECONDS = 0.8f

/**
 * Scoring. Only kills score, and every unbroken run of [HITS_PER_MULTIPLIER_STEP]
 * kills raises the multiplier by one, with no ceiling: thirty kills pays x11 a
 * kill, a hundred x34. Raise the step to make combos rarer.
 */
const val POINTS_PER_HIT = 50
const val HITS_PER_MULTIPLIER_STEP = 3

/**
 * The combo readout; see ComboMeter and ComboHeat. Its title is drawn at the HUD's size, and its
 * count grows by [COMBO_COUNT_GROWTH] per doubling of the multiplier, up to [COMBO_COUNT_MAX_SIZE],
 * the most it can grow without crowding the wave readout above it.
 */
const val COMBO_FONT_SIZE = 15
const val COMBO_COUNT_GROWTH = 3f
const val COMBO_COUNT_MAX_SIZE = 25

/**
 * How much the count swells when it steps up, and the title on a new rung, easing back over
 * [COMBO_POP_SECONDS]: long enough to catch the eye, over before the next kill.
 */
const val COMBO_POP_SECONDS = 0.35f
const val COMBO_POP_GROWTH = 0.5f
const val COMBO_TITLE_POP_GROWTH = 0.3f

/**
 * The readout's fire, as the heat along the tops of the letters; flames stand about as many cells
 * tall as their heat, a cell being two pixels square. The first step gets the least, so it is
 * visibly alight; SUPERNOVA the most, reaching past the wave readout without covering the score.
 */
const val COMBO_FLAME_MIN_HEAT = 4
const val COMBO_FLAME_MAX_HEAT = 11
const val COMBO_MIN_FLAME_STRENGTH = 0.2f

/**
 * A step flares the fire by [COMBO_FLARE_HEAT], a kill by [COMBO_KILL_FLARE] of that, and either
 * dies back over [COMBO_FLARE_SECONDS].
 */
const val COMBO_FLARE_HEAT = 5f
const val COMBO_KILL_FLARE = 0.4f
const val COMBO_FLARE_SECONDS = 0.5f

/**
 * Past the last named rung the fire cycles through every color on the ladder and back, this many
 * times a second at SUPERNOVA's multiplier, and as many again for every doubling of it.
 */
const val COMBO_SUPERNOVA_CYCLES_PER_SECOND = 0.35f

/**
 * The bat's health, and what a hit or one of the bat's shots deals: three hits and the bat is done.
 */
const val PLAYER_MAX_HIT_POINTS = 100
const val DAMAGE_PER_HIT = 34

/**
 * Wounds. Every creature's sheet stacks it three times, top to bottom (unhurt, wounded, battered),
 * and a row takes over as its health falls to each of these marks; see WoundComponent. The bosses
 * change phase at the same marks, so every creature looks hurt at the same point of its bar, and a
 * boss looks as far through its fight as it is.
 */
const val WOUNDED_AT = 0.66f
const val BATTERED_AT = 0.33f
val WOUND_MARKS = floatArrayOf(WOUNDED_AT, BATTERED_AT)
const val WOUND_ROWS = 3

/**
 * What a wound costs an ordinary enemy, per row of [WOUND_MARKS]: its pace (flight, pattern and
 * wingbeat) and its rate of fire, as fractions of its own. A hurt enemy straggles behind its group,
 * rewarding the player for finishing what they started. Bosses keep full pace: their fights
 * escalate as they are hurt.
 */
val WOUNDED_PACE = floatArrayOf(1f, 0.8f, 0.6f)
val WOUNDED_FIRE_RATE = floatArrayOf(1f, 0.75f, 0.5f)

/**
 * Obstacles are scenery a shot clears rather than targets that soak hits: one shot destroys one.
 */
const val DESTRUCTIBLE_HIT_POINTS = DAMAGE_PER_HIT

/**
 * A shot is spent by whatever it touches, whether or not that dies. A single point, since enemies
 * deal less than a hit's worth and a shot with more would punch through them.
 */
const val SHOT_HIT_POINTS = 1

/**
 * The bat's health bar, in framebuffer pixels: thick enough to read on a phone,
 * clear of the sprite.
 */
const val HEALTH_BAR_HEIGHT = 3f
const val HEALTH_BAR_OFFSET_Y = 2f

/**
 * Damage numbers over a hit enemy. They rise a little under a pixel per tick (about 31 px a second)
 * and are gone within a second, so a busy screen does not fill up with them.
 */
const val DAMAGE_TEXT_FONT_SIZE = 12
const val DAMAGE_TEXT_DURATION_SECONDS = 0.7f
const val DAMAGE_TEXT_RISE_PER_TICK = 0.6f

/**
 * The cyan wake behind the bat. Segments drift at the scenery's speed, so the wake hangs in the
 * world rather than being towed behind the sprite. The cadence is set by the bat's top speed: at
 * full tilt it covers about 12 px between segments, so they still touch.
 */
const val TRAIL_DRIFT_PER_TICK = -2f
const val TRAIL_INTERVAL_SECONDS = 0.03f

/** How long a wake segment lives: half a second of drift is roughly a bat-length of wake. */
const val TRAIL_DURATION_SECONDS = 0.5f

/** What a segment shrinks to by the end, so the wake tapers rather than ending square. */
const val TRAIL_MIN_SCALE = 0.15f

/**
 * A segment's size relative to the bat's frame: a quarter of its width, and the height of the tail
 * that sheds it.
 */
const val TRAIL_SEGMENT_WIDTH_FRACTION = 0.25f
const val TRAIL_SEGMENT_HEIGHT_FRACTION = 0.25f

// --- Stage progression -------------------------------------------------------------------------
//
// A stage is a stack of one-minute waves ending in a boss. These numbers are the whole difficulty
// curve; StageProgression only reads them against the clock. Which species each wave sends is part
// of a stage's design, in StageDesign.

/** A wave lasts a minute, the unit players already count a run in. */
const val WAVE_DURATION_SECONDS = 60f

/** The wave index the boss arrives on: stage 1's boss arrives at five minutes. */
const val BOSS_WAVE = 5

/** What each stage adds over the one before: everything scaled, spawn gaps included. */
const val STAGE_DIFFICULTY_STEP = 0.35f

/**
 * Spawn density, in seconds between arrivals. The opening is sparse, giving the player room to
 * breathe; the floor is set by the bat's one-second fire rate, since any tighter and enemies arrive
 * faster than they can be shot.
 */
const val OPENING_SPAWN_INTERVAL_SECONDS = 2.6f
const val MINIMUM_SPAWN_INTERVAL_SECONDS = 0.9f

/**
 * How far either side of the interval a spawn may land, as a fraction of it, so spawns do not fall
 * into a memorizable rhythm.
 */
const val SPAWN_INTERVAL_JITTER = 0.3f

/**
 * Enemy health per wave, in units of the bat's 34-damage shot: the opening wave dies to one shot,
 * the wave before the boss takes three.
 */
const val ENEMY_BASE_HIT_POINTS = 34
const val ENEMY_HIT_POINTS_PER_WAVE = 17

/**
 * What an enemy takes off the bat's 100-point bar: the opening wave can hit it
 * eight times, the last wave three.
 */
const val ENEMY_BASE_DAMAGE = 12
const val ENEMY_DAMAGE_PER_WAVE = 6

/**
 * Closing speed, as a fraction added per wave. Small on purpose: enemies that outran the bat's
 * shots could only be dodged, not fought.
 */
const val ENEMY_SPEED_PER_WAVE = 0.1f

/**
 * The boss's health and contact damage at stage 1's difficulty. The health is a fight length
 * against the bat that reaches it, which by then is many power-ups strong: a damage build takes
 * about half a minute, others a minute or more, leaving every phase room to play out. Its contact
 * damage is deliberately worse than anything else in the stage.
 */
const val BOSS_HIT_POINTS_PER_STAGE = 10240
const val BOSS_DAMAGE_PER_STAGE = 50

/**
 * How much bigger the Caco Imp is drawn than the sheet's enemies; its collision box grows with it.
 */
const val BOSS_SPRITE_SCALE = 3f

/**
 * The health bar of a boss that spends part of its fight out of sight (the Sand Wyrm under the
 * sand, the Caco Imp in the dark), pinned under the stage timer rather than hung under the boss.
 * The Moth Queen is always in sight and wears hers under her.
 */
const val BOSS_BAR_WIDTH = 200
const val BOSS_BAR_TOP = 25
const val BOSS_BAR_HEIGHT = 4

/** Banked for clearing the stage, on top of what the run scored. */
const val STAGE_COMPLETE_BONUS = 10_000

/** How long the wave and boss announcements stay up, in seconds. */
const val WAVE_BANNER_SECONDS = 2.2f

/** Font size of the wave and boss announcements. */
const val BANNER_FONT_SIZE = 26

/** Font size of the stage timer, top center. */
const val STAGE_TIMER_FONT_SIZE = 18

/**
 * How long the victory overlay ignores input, in seconds. Longer than the pause overlay's, since
 * the player may still be steering when it comes up, and the fanfare's drop deserves a moment.
 */
const val STAGE_COMPLETE_ARMING_SECONDS = 1.2f

/**
 * When the victory fanfare comes in after the boss goes down, in seconds: after the boss's blast
 * and its aftershocks, so the two are heard one after the other.
 */
const val VICTORY_FANFARE_DELAY_SECONDS = 0.8f

/**
 * Where the fanfare's drop lands, from its first note: six beats at 120 BPM. Set by
 * tools/generate_victory_music.py (LANDING_BEAT); change the two together.
 */
const val VICTORY_FANFARE_LANDING_SECONDS = 3f

/**
 * How long the run plays on after its boss goes down before the stage complete overlay comes up, so
 * the overlay lands on the fanfare's drop. The player can keep flying meanwhile.
 */
const val STAGE_COMPLETE_DELAY_SECONDS =
    VICTORY_FANFARE_DELAY_SECONDS + VICTORY_FANFARE_LANDING_SECONDS

/**
 * How far a blast or a break drifts each tick, in framebuffer pixels: left with the scenery, so it
 * stays where the thing was in the world.
 */
const val BURST_DRIFT = -1f

/**
 * When the boss's wreck bursts again after its first blast, in seconds: the blasts in
 * bossDeath.wav, from tools/generate_combat_sounds.py (AFTERSHOCKS). Change the two together, or
 * the blasts heard and seen drift apart.
 */
val BOSS_AFTERSHOCK_SECONDS = floatArrayOf(0.2f, 0.42f, 0.66f)

/**
 * The first aftershock's size against the part of the boss it goes off on, and how much smaller
 * each later one is. Nearly the boss's own blast, since a fireball fills only the middle of its
 * frame; dying away, so it reads as the wreck bursting rather than more bosses dying.
 */
const val BOSS_AFTERSHOCK_SCALE = 0.9f
const val BOSS_AFTERSHOCK_FALLOFF = 0.85f


// --- Enemy groups, shields and fire ------------------------------------------------------------
//
// What each species does is in EnemySpecies and which wave sends what in StageDesign; these are the
// numbers underneath.

/**
 * A shield handed out by a wave's shieldChance, as a fraction of the enemy's own health: under one,
 * so a shielded enemy takes an extra shot rather than twice as many.
 */
const val WAVE_SHIELD_FRACTION = 0.6f

/**
 * A swarm's size: [SWARM_SIZE], plus [SWARM_SIZE_PER_TWO_WAVES] every two waves, up to
 * [SWARM_SIZE_MAX], which keeps a late swarm from being a wall.
 */
const val SWARM_SIZE = 5
const val SWARM_SIZE_PER_TWO_WAVES = 1
const val SWARM_SIZE_MAX = 7

/** How loosely a swarm is packed around its path, in framebuffer pixels either way. */
const val SWARM_SPREAD_X = 40f
const val SWARM_SPREAD_Y = 22f

/** A V formation: how far each rank sits behind the one in front and out to either side. */
const val FORMATION_RANK_SPACING_X = 22f
const val FORMATION_RANK_SPACING_Y = 17f
const val FORMATION_RANKS = 2

/**
 * How much room a group needs from the top and bottom edges, so its path never
 * carries its outer members off screen: a sway of up to about 40 px, plus the
 * widest rank or scatter, plus half a sprite.
 */
const val GROUP_EDGE_MARGIN = 85f

/**
 * Where a hovering or diving enemy stops, as a fraction of the framebuffer width, picked per enemy:
 * on screen and in reach, while leaving the bat room to get out of the way.
 */
const val HOLD_X_MIN_FRACTION = 0.5f
const val HOLD_X_MAX_FRACTION = 0.82f

/**
 * The most of an armed enemy's first interval it waits before its first volley, as a fraction, so a
 * formation spawned on one tick does not fire as one.
 */
const val FIRST_SHOT_JITTER = 0.6f


// --- The Moth Queen ----------------------------------------------------------------------------
//
// Stage 1's boss. Three phases marked by her health: each raises her shield, and she speeds up and
// calls in wasps as she goes.

/** Her sheet: four frames of wingbeat, 96 by 80, the footprint of the Caco Imp. */
const val MOTH_QUEEN_FRAME_WIDTH = 96
const val MOTH_QUEEN_FRAME_COUNT = 4
const val MOTH_QUEEN_FRAME_SECONDS = 0.11f

/**
 * How far inside her frame her hit box sits; much of a moth's frame is the air between its wings.
 */
const val MOTH_QUEEN_COLLISION_TOLERANCE = 14f

/** The colorway of shot.png her bolts are drawn in: rose, like her wings. */
const val MOTH_QUEEN_SHOT_VARIANT = 6

/** The health fractions at which she changes phase; see [WOUND_MARKS]. */
const val MOTH_QUEEN_PHASE_2_AT = WOUNDED_AT
const val MOTH_QUEEN_PHASE_3_AT = BATTERED_AT

/**
 * The shield she raises at each change of phase, as a fraction of her full health. It does not
 * recharge: a wall to break through once per phase.
 */
const val MOTH_QUEEN_SHIELD_FRACTION = 0.12f

/** Seconds between the wasp swarms she calls in from phase two on, and once she is enraged. */
const val MOTH_QUEEN_SUMMON_SECONDS = 9f
const val MOTH_QUEEN_ENRAGED_SUMMON_SECONDS = 7f

/** How much faster she flies her figure eight in the last phase. */
const val MOTH_QUEEN_ENRAGED_TEMPO = 1.6f

/**
 * What one of her bolts deals, as a share of her contact damage (about 67, since
 * she is fought at the strength she had as stage 2's boss; see StageDesign.JUNGLE).
 * A ring bolt costs about a fifth of the bat's bar and an aimed one a little under
 * a third, since her rings put a dozen bolts on screen.
 */
const val MOTH_QUEEN_RING_DAMAGE = 0.3f
const val MOTH_QUEEN_FAN_DAMAGE = 0.45f


// --- Experience and power-ups ------------------------------------------------------------------
//
// A second curve running against the stage's: the stage gets harder on a clock, the bat stronger on
// kills, and a run is the race between them. Experience is per run and never persisted.

/**
 * What a kill is worth, scaled by its wave so pressing on beats farming the opening minute, where
 * enemies die to a single shot.
 */
const val XP_PER_KILL = 10
const val XP_PER_KILL_PER_WAVE = 6

/** What killing the stage's boss is worth: roughly a late level on its own. */
const val XP_PER_BOSS = 250

/**
 * What the first level up costs, and what each later one adds: often enough that picks matter,
 * rarely enough that the dialog is an event rather than an interruption.
 */
const val XP_FIRST_LEVEL = 50
const val XP_LEVEL_STEP = 35

/** How many power-ups each level up offers. */
const val POWER_UP_CHOICES = 3

/**
 * Rapid Fire, as a multiplier on the gap between shots. The floor is a little over three shots a
 * second: past that the bat is a wall of bullets and dodging stops mattering.
 */
const val RAPID_FIRE_FACTOR = 0.82f
const val MIN_SHOT_INTERVAL_SECONDS = 0.3f

/**
 * Spread Shot: each pick adds one shot to the fan, which stays centered on straight ahead. The cap
 * keeps the spread readable.
 */
const val MAX_EXTRA_SHOTS = 4
const val SPREAD_ANGLE_DEGREES = 9f

/** Vitality and Heavy Rounds, which have no ceiling. */
const val VITALITY_HIT_POINTS = 25
const val HEAVY_ROUNDS_DAMAGE = 12

/**
 * Armor Plating, as a multiplier on incoming damage, floored well above zero: a bat that cannot be
 * hurt has no run left to play.
 */
const val ARMOR_FACTOR = 0.85f
const val ARMOR_FLOOR = 0.4f

/**
 * Mercy invulnerability after a hit, without which one obstacle would strip the whole bar while the
 * sprites overlap; Second Wind lengthens it, up to the cap.
 */
const val PLAYER_HIT_COOLDOWN_SECONDS = 0.5f
const val SECOND_WIND_SECONDS = 0.3f
const val MAX_HIT_COOLDOWN_SECONDS = 1.5f

/**
 * The grace after a level-up pick, when nothing can hurt the bat: the run comes back with whatever
 * was bearing down on it still there, and the finger that tapped a card has to find the bat again.
 */
const val POWER_UP_GRACE_SECONDS = 1f

/**
 * How the bat shows it cannot be hurt: see-through, a dither pulsing between the faintest and the
 * strongest share of its pixels once every [INVULNERABLE_PULSE_SECONDS]. Never whole while it lasts,
 * so the bat coming back whole is the moment it can be hurt again; never fainter than six pixels in
 * sixteen, below which it thins to a scatter of dots the player loses against the jungle.
 */
const val INVULNERABLE_PULSE_SECONDS = 0.3f
const val INVULNERABLE_FAINTEST = 0.375f
const val INVULNERABLE_STRONGEST = 0.75f

/**
 * The level-up dialog's cards, against the 640x360 frame: three in a row with a gutter between
 * them, centered horizontally, just below the middle of the screen.
 */
const val POWER_UP_CARD_WIDTH = 140
const val POWER_UP_CARD_HEIGHT = 96
const val POWER_UP_CARD_GAP = 10
const val POWER_UP_CARD_TOP = 150

/** The room between a card's edges and its text, on either side. */
const val POWER_UP_CARD_PADDING = 8

/**
 * How long the dialog ignores input: the player was steering when the level up landed, and the lift
 * that follows is not a choice.
 */
const val POWER_UP_ARMING_SECONDS = 0.35f

/** The experience bar's height, drawn across the very top edge. */
const val XP_BAR_HEIGHT = 3

/**
 * Regeneration, in health per second: an Int so the card prints it without a decimal point, and
 * small against the 12-36 damage of a hit, so it helps a careful run recover between waves without
 * carrying one through a wave it is losing.
 */
const val REGEN_PER_SECOND = 2
const val MAX_HEALTH_REGEN_PER_SECOND = 10f

/**
 * Fast Learner and Bounty Hunter, as fractions added to their multipliers. Uncapped but small, so
 * they pay off over a whole run, which makes taking one over an immediate upgrade a real decision.
 */
const val XP_BONUS = 0.05f
const val SCORE_BONUS = 0.10f

/**
 * Second Life: the bat comes back at half a bar, so a free death keeps a run alive without undoing
 * the damage that ended it, and it stacks only so far.
 */
const val REVIVE_HEALTH_FRACTION = 0.5f
const val MAX_REVIVES = 3

/**
 * Counterweight: a flat cut off every hit, paid for with a share more damage dealt. Flat, so it is
 * worth most against swarms of weak enemies. Incoming damage still floors at 1.
 */
const val COUNTERWEIGHT_REDUCTION = 1
const val COUNTERWEIGHT_BONUS = 0.10f
const val MAX_FLAT_DAMAGE_REDUCTION = 20

/**
 * Piercing Shot and Ricochet, in targets and reflections per shot. Capped, since a shot that passed
 * through everything or never left the frame would make the frame unreadable.
 */
const val MAX_SHOT_PIERCE = 4
const val MAX_SHOT_BOUNCE = 3

// The standalone weapons, each working on its own (an orb circling the bat, the wake, a beam on a
// clock) so it keeps fighting while the player dodges. Their damage is a share of the shot's,
// rounded up, so Heavy Rounds and Counterweight keep paying into them.

/**
 * Guardian Orb: each pick adds an orb, and the orbs share the circle evenly, going round clockwise
 * once every [ORB_SECONDS_PER_TURN] at [ORB_RADIUS], clear of the bat's wings. Capped where the
 * ring is nearly closed. A new orb spreads the ring out over about [ORB_SETTLE_SECONDS].
 */
const val MAX_ORBS = 5
const val ORB_RADIUS = 44f
const val ORB_SECONDS_PER_TURN = 1.6f
const val ORB_SETTLE_SECONDS = 0.35f
const val ORB_DAMAGE_FRACTION = 0.6f

/**
 * The orb's sheet: six 14x14 frames of a glint going round it, from
 * tools/generate_orb_sprite.py. It hits with the sphere, not its outline, and gives
 * off no light, since it is always inside the bat's.
 */
const val ORB_FRAME = 14
const val ORB_FRAME_COUNT = 6
const val ORB_FRAME_SECONDS = 0.08f
const val ORB_COLLISION_TOLERANCE = 2f

/**
 * How soon one orb may hit the same target again, in seconds: longer than an orb takes to sweep
 * through an enemy, shorter than the gap between two orbs of a full ring.
 */
const val ORB_REHIT_SECONDS = 0.3f

/**
 * Charged Trail, by level: each pick lengthens the wake and strengthens its shock. Level 0 is the
 * plain, harmless wake every run starts with.
 */
const val MAX_WAKE_LEVEL = 3
val WAKE_SECONDS = floatArrayOf(TRAIL_DURATION_SECONDS, 1.0f, 1.3f, 1.6f)
val WAKE_DAMAGE_FRACTION = floatArrayOf(0f, 0.3f, 0.45f, 0.6f)

/**
 * How soon the wake may shock the same target again. An enemy flies about as fast
 * as the wake drifts, so one can stay in it for a long time; this, not the touch,
 * sets how much it is hurt there.
 */
const val WAKE_REHIT_SECONDS = 0.4f

/**
 * A charged segment keeps more of its size as it fades, and stops shocking once past
 * [WAKE_HARMLESS_FROM] of its life, so what hurts is always visible.
 */
const val WAKE_MIN_SCALE = 0.45f
const val WAKE_HARMLESS_FROM = 0.8f
const val WAKE_COLOR = 0xFF00E5FF.toInt()
const val WAKE_CORE_COLOR = 0xFFE6FFFF.toInt()

/**
 * Frost Beam: a beam from the bat at a random ordinary enemy on screen and on across the frame,
 * freezing every ordinary enemy along it. Bosses and elites are never frozen (a boss's fight would
 * stop dead, and an elite is a prize to chase). Each pick fires more often and freezes longer, to a
 * cap. It never deals damage.
 */
const val MAX_FROST_LEVEL = 4
const val FROST_BEAM_INTERVAL_SECONDS = 3.5f
const val FROST_BEAM_INTERVAL_FACTOR = 0.8f
const val FROST_SECONDS = 1.8f
const val FROST_SECONDS_PER_LEVEL = 0.5f

/**
 * How far either side of its line the beam freezes, and how briefly it shows. A
 * white core in a pale glow, edged in deep blue as every sprite is outlined,
 * without which it vanished into bright skies.
 */
const val FROST_BEAM_HALF_WIDTH = 4f
const val FROST_BEAM_SHOW_SECONDS = 0.3f
const val FROST_BEAM_CORE_COLOR = 0xFFF0FCFF.toInt()
const val FROST_BEAM_GLOW_COLOR = 0xFF8ED8FF.toInt()
const val FROST_BEAM_EDGE_COLOR = 0xFF2C6CC0.toInt()

/**
 * A frozen enemy is washed in a strong ice blue (a pale one turned warm sprites grey) that thins
 * over the last [FROST_THAW_SECONDS] so the player sees the thaw coming. It drifts with the scenery
 * meanwhile, and is harmless to the bat while the bat's weapons still hurt it.
 */
const val FROST_TINT = 0xB8489CF0.toInt()
const val FROST_THAW_SECONDS = 0.5f
const val FROST_DRIFT = BURST_DRIFT
const val FROST_LIGHT_COLOR = 0xFFB4E6FF.toInt()

/** In the dark, the brief flash of cold light the beam leaves on each thing it froze. */
const val FROST_FLASH_RADIUS = 40
const val FROST_FLASH_SECONDS = 0.2f


// --- The aura ----------------------------------------------------------------------------------
//
// The bat's level, shown on the bat itself: the experience bar says how close the next power-up is,
// the aura how far the run has come.

/**
 * The level at which the glow is brightest; high enough that it keeps growing
 * through a typical run.
 */
const val AURA_FULL_INTENSITY_LEVEL = 24

/**
 * Levels per tier: every tenth level adds sparks and an arc of lightning, with a sound, at the
 * round numbers a player notices reaching.
 */
const val AURA_LEVELS_PER_TIER = 10


// --- Hit feedback ------------------------------------------------------------------------------

/**
 * How long an enemy stays lit after a hit: long enough to register, short enough that a tough enemy
 * under rapid fire looks hit repeatedly rather than glowing continuously.
 */
const val HIT_FLASH_SECONDS = 0.08f

/**
 * The hit flash's color: near-white, which reads against every sprite, cyan ones included. The
 * alpha is short of solid, so the enemy stays recognizable under it.
 */
const val HIT_FLASH_COLOR = 0xE6FFFFFF.toInt()


// --- Critical hits -----------------------------------------------------------------------------
//
// A rare, loud payoff on an otherwise even stream of shots. The chance and the multiplier are a
// pair: at one in a hundred, a modest multiplier would go unnoticed, so the rarity buys the size.

/**
 * How often a shot leaves the gun critical, 0..1. Rolled per projectile, so a spread build gets
 * more rolls per volley.
 */
const val CRITICAL_CHANCE = 0.01f

/**
 * What a critical is worth, as a multiplier on the run's shot damage, so Heavy
 * Rounds keeps paying into it. At four, a crit removes anything short of the final
 * waves in one hit, which makes it read as an event.
 */
const val CRITICAL_DAMAGE_MULTIPLIER = 4f

/** The damage number a crit puts up: bigger than the ordinary 12, and held longer. */
const val CRITICAL_TEXT_FONT_SIZE = 22
const val CRITICAL_TEXT_DURATION_SECONDS = 1.0f

/**
 * Sharpshooter, in chance added per pick. Added rather than multiplied, since scaling 1% by any
 * sane factor stays near 1%. At a 4x crit each pick is worth about 12% more damage: a third of the
 * first Heavy Rounds, but it compounds with every point of shot damage instead of thinning out.
 */
const val CRITICAL_CHANCE_BONUS = 0.04f

/**
 * Sharpshooter's ceiling, reachable only by spending nearly every pick on it; a critical that lands
 * more often than not is no longer a critical.
 */
const val MAX_CRITICAL_CHANCE = 0.5f


// --- Shot colorways ----------------------------------------------------------------------------
//
// `shot.png` is one bolt, four frames of it, drawn fourteen times over: the player's cyan, then the
// cave's three enemy palettes in the order the enemy sheet lays them out, then the jungle's
// shooters, the Sand Wyrm's, one per ElitePalette, and the Naga's. A shot is the color of whatever
// fired it, so whose fire is whose can be told by color. `impact.png`, the hit a shot leaves, comes
// in the same colorways in the same order.

/** Width of one bolt frame, which positions a shot on its sheet. */
const val SHOT_FRAME_WIDTH = 24

/**
 * The bolt's frames, side by side within its colorway: its core throbs and sends a ripple down its
 * body. Quick, since a slower throb looks like a light blinking on something solid.
 */
const val SHOT_FRAME_COUNT = 4
const val SHOT_FRAME_SECONDS = 0.05f

/**
 * A hit: a star of light opening into a ring and a spray of sparks, played once in about a quarter
 * of a second. Smaller than a death's blast, since a tough enemy takes a stream of hits and must
 * still be visible under them.
 */
const val IMPACT_FRAME = 21
const val IMPACT_FRAME_COUNT = 6
const val IMPACT_FRAME_SECONDS = 0.04f

/** How far ahead of a shot's middle its hit goes off: about at its nose, where it struck. */
const val IMPACT_LEAD = 9f

/**
 * The player's colorway, and the offset from an enemy type to its own: the enemy strips are indexed
 * 0..2 after the player's, so a type-2 boss fires the fourth bolt.
 */
const val PLAYER_SHOT_VARIANT = 0
const val ENEMY_SHOT_VARIANT_OFFSET = 1

/**
 * The body color of each colorway's bolt, in sheet order: the color of light a shot gives off in
 * the dark. From `generate_shot_sprite.py`, which SpriteSheetTest holds them to.
 */
val SHOT_BODY_COLORS = intArrayOf(
    0xFF00E5FF.toInt(), // the bat's cyan
    0xFFB05CE8.toInt(), // violet
    0xFFE8942C.toInt(), // amber
    0xFFDC4250.toInt(), // crimson
    0xFFE45ACC.toInt(), // magenta
    0xFFFC8C28.toInt(), // flame
    0xFFF080AA.toInt(), // rose
    0xFFFFCC40.toInt(), // molten gold
    0xFFFF3848.toInt(), // scarlet
    0xFFFF7A1E.toInt(), // ember
    0xFF74EE3C.toInt(), // venom
    0xFFA458FF.toInt(), // ultraviolet
    0xFFFF40C4.toInt(), // fuchsia
    0xFFFF3D8E.toInt(), // neon pink, the Naga's
)


// --- The dark ----------------------------------------------------------------------------------
//
// The cave is flown in the dark. The bat carries its own light, every shot and blast is one, and
// whatever stands in a light's way casts a shadow; see LightingSystem. These set how dark it is and
// how far each light reaches. The other stages are flown in daylight.

/**
 * The light where no other reaches: the color the cave is multiplied by, about a quarter and a
 * little bluer than gray. The walls sink into the dark, while an imp outside the bat's light is
 * still a warm shape the player can see coming: the dark sets the mood rather than hiding threats.
 */
const val CAVE_AMBIENT = 0xFF3C4258.toInt()

/**
 * How much of the light shows in the air as well as on surfaces. Multiplying alone barely lights
 * the navy walls with a warm light; much more than this reads as fog.
 */
const val CAVE_GLOW = 0.08f

/**
 * The bat's light: near white with a breath of cyan, so what it falls on keeps its colors, and wide
 * enough to light most of the frame's height. It fades over [BAT_LIGHT_FADE_SECONDS] as the bat
 * falls, about the length of the fall.
 */
const val BAT_LIGHT_COLOR = 0xFFDCF8FF.toInt()
const val BAT_LIGHT_RADIUS = 160
const val BAT_LIGHT_FADE_SECONDS = 1.2f

/**
 * A shot's light, in its bolt's color lifted part of the way to white so it lights more than its
 * own hue. The bat's shots light a little further than enemy fire.
 */
const val PLAYER_SHOT_LIGHT_RADIUS = 50
const val ENEMY_SHOT_LIGHT_RADIUS = 44
const val SHOT_LIGHT_INTENSITY = 0.85f
const val SHOT_LIGHT_PALENESS = 0.3f

/**
 * A hit's flare, lighting what was hit at the moment it most needs to be seen:
 * wider than the shot's light, so it lights the surroundings too, and paler. The
 * bat's reach further. It dies with the spark.
 */
const val PLAYER_IMPACT_LIGHT_RADIUS = 80
const val ENEMY_IMPACT_LIGHT_RADIUS = 70
const val IMPACT_LIGHT_PALENESS = 0.5f
const val IMPACT_LIGHT_SECONDS = IMPACT_FRAME_COUNT * IMPACT_FRAME_SECONDS

/**
 * A blast's light: fire-colored, [BLAST_LIGHT_RADIUS] for a blast at its own size and in step with
 * bigger ones, dying with the fireball. The radius is rounded to [BLAST_LIGHT_STEP], since every
 * light radius is rendered once and cached.
 */
const val BLAST_LIGHT_COLOR = 0xFFFFB464.toInt()
const val BLAST_LIGHT_RADIUS = 56
const val BLAST_LIGHT_STEP = 8
const val BLAST_LIGHT_MAX_RADIUS = 160

/** The light an elite gives off in its glow's color, so it is seen across the dark. */
const val ELITE_LIGHT_RADIUS = 60

/**
 * How strongly a light glints off a creature's or a rock's edge on the lit side; see Gloss. Enough
 * to show which way the light falls and lift outlines off the dark, without making the cave look
 * wet. Rock slightly more than hide, as the limestone is damp.
 */
const val CREATURE_SHINE = 0.7f
const val ROCK_SHINE = 0.75f


// --- The bat's death ---------------------------------------------------------------------------

/** Width of one bat frame, shared because the screen addresses the death sheet with it too. */
const val BAT_FRAME_WIDTH = 45

/**
 * The five frames of cyanBatDeath.png, played once and then held. Faster than the wingbeat, so
 * dying finishes well before the fall carries the bat off screen.
 */
const val BAT_DEATH_FRAME_COUNT = 5
const val BAT_DEATH_FRAME_SECONDS = 0.08f

/**
 * The fall, in pixels per tick added per second, and its top speed: a drop from mid-screen takes a
 * little under a second, long enough to watch without making the player wait.
 */
const val DEATH_GRAVITY = 7.5f
const val DEATH_TERMINAL_VELOCITY = 6f

/** The tumble as it falls. Cosmetic, like every rotation. */
const val DEATH_SPIN_DEGREES_PER_SECOND = 210f

/**
 * Seconds between the blasts thrown off on the way down: three or four over a typical fall, enough
 * to read as the bat coming apart without becoming a firework.
 */
const val DEATH_PUFF_INTERVAL_SECONDS = 0.22f

/**
 * Those blasts' size against an enemy's full blast: small, so they do not bury the falling sprite.
 */
const val DEATH_PUFF_SCALE = 0.55f


// --- The desert --------------------------------------------------------------------------------
//
// Stage 3 is flown from noon to nightfall; the sky is in Daylight. Its enemies loop, leap out of
// the sand and regrow their shells.

/**
 * How long a regrowing shell must go untouched before it starts to: long enough that sustained fire
 * never sees it happen, short enough that one left alone does.
 */
const val SHIELD_REGROWTH_DELAY_SECONDS = 2.5f

/**
 * How much of something coming in under the sand shows above the bottom edge: its back, visible the
 * whole way in without looking like it flies.
 */
const val BURROW_SHOWING = 11f


// --- Elites ------------------------------------------------------------------------------------
//
// Now and then an ordinary enemy arrives as an elite: wreathed in a glow of its own color, tougher,
// quicker on the trigger, and worth more. How often is per wave, in StageDesign; the colors are
// ElitePalette's. An elite is a prize to chase, so it pays a little more than it costs.

/** Its health against an ordinary one of its kind: a chase across the frame rather than a tap. */
const val ELITE_HIT_POINT_FACTOR = 2.5f

/**
 * The gap between its volleys against its kind's. One without a gun of its own is issued one, so an
 * elite always shoots: its colored shots are half of how it is told apart.
 */
const val ELITE_FIRE_INTERVAL_FACTOR = 0.6f

/**
 * What killing one pays, in ordinary kills of its wave, in experience and points alike. It still
 * counts as one kill to the streak.
 */
const val ELITE_EXPERIENCE_FACTOR = 3
const val ELITE_SCORE_FACTOR = 3

/**
 * Its glow, in the terms of the bat's aura: the bat's brightest, with the first tier of sparks and
 * lightning. Full brightness because an enemy is smaller than the bat and covers its halo's
 * brightest part; anything less was easy to miss.
 */
const val ELITE_AURA_INTENSITY = 1f
const val ELITE_AURA_TIER = 1

/**
 * What one leaves where it is shot down: a power-up, taken by flying into it, and drawn at random
 * from those the run can still use as it is taken. Eight 19x19 frames of a silver plus spinning half
 * a turn, from tools/generate_power_up_drop_sprite.py, at the orb's pace. It drifts with the
 * scenery, as the blast it comes out of does, so one left alone goes off the left edge.
 */
const val POWER_UP_DROP_FRAME = 19
const val POWER_UP_DROP_FRAME_COUNT = 8
const val POWER_UP_DROP_FRAME_SECONDS = 0.08f

/**
 * In the dark it is a light, a cool white like its steel, so it is seen from across the cave and
 * glints off the rock it passes. A little short of an elite's, since it is half the size.
 */
const val POWER_UP_DROP_LIGHT_COLOR = 0xFFE4ECFF.toInt()
const val POWER_UP_DROP_LIGHT_RADIUS = 52


// --- The Sand Wyrm -----------------------------------------------------------------------------
//
// Stage 3's boss: ten armored parts, head first, that breach out of the dunes in
// arcs and dive back under. Every part is its own entity, and what lands on the
// body lands on the head, which carries the health.

/**
 * The square frame every part is drawn in on its sheet, as is the sand it throws
 * up. The parts shrink toward the tail within their frames rather than being
 * scaled, which would make their pixels uneven.
 */
const val SAND_WYRM_FRAME = 48

/** Its jaws working, two frames on a slow beat. */
const val SAND_WYRM_HEAD_FRAMES = 2
const val SAND_WYRM_HEAD_FRAME_SECONDS = 0.16f

/** The spray of sand a breach throws up, four frames from the sheet's seventh. */
const val SAND_WYRM_PLUME_FRAME = 6
const val SAND_WYRM_PLUME_FRAMES = 4
const val SAND_WYRM_PLUME_FRAME_SECONDS = 0.07f

/**
 * How far apart its parts sit along the head's path, in framebuffer pixels: a little under a part's
 * width, so the plates overlap and read as one body.
 */
const val SAND_WYRM_SPACING = 19f

/**
 * How long it stays under between breaches, less when enraged, and how much of that the sand spends
 * boiling where it will come up: the warning, which is never shorter than this.
 */
const val SAND_WYRM_BURROW_SECONDS = 2.1f
const val SAND_WYRM_ENRAGED_BURROW_SECONDS = 1.3f
const val SAND_WYRM_TELL_SECONDS = 1.1f

/** How fast it crosses the frame on a breach, before the leap's forward push; faster enraged. */
const val SAND_WYRM_BREACH_SPEED = 1.5f
const val SAND_WYRM_ENRAGED_BREACH_SPEED = 1.9f

/** The health fractions at which it calls up its brood and becomes enraged; see [WOUND_MARKS]. */
const val SAND_WYRM_PHASE_2_AT = WOUNDED_AT
const val SAND_WYRM_PHASE_3_AT = BATTERED_AT

/**
 * What its head and body deal on contact, as shares of its stage's boss damage (85
 * at stage 3). The head, about 64, matches the Moth Queen's 67: the wyrm is harder
 * for its health, armor and movement, not for hitting harder. The body is lower
 * again: it takes a second to pour past a point, and the bat's mercy window runs
 * out twice in that, so one undodged pass lands the head and two plates.
 */
const val SAND_WYRM_HEAD_DAMAGE = 0.75f
const val SAND_WYRM_BODY_DAMAGE = 0.3f

/**
 * How much of a shot into a plate carries through to the head, which takes all of what hits it. At
 * full weight a spread or piercing shot landed on many plates at once and the wyrm went down in
 * seconds; at half, the head is worth aiming for and the body still worth hitting.
 */
const val SAND_WYRM_PLATE_SHARE = 0.5f

/**
 * What its spit deals, as a share of the head's contact damage: an aimed bolt a little under a
 * third of the bat's bar, a ring bolt about a fifth, as for the Moth Queen.
 */
const val SAND_WYRM_SPIT_DAMAGE = 0.45f
const val SAND_WYRM_RING_DAMAGE = 0.3f

/** The colorway of shot.png its spit is drawn in: molten gold, like the glow in its throat. */
const val SAND_WYRM_SHOT_VARIANT = 7


// --- The Caco Imp ------------------------------------------------------------------------------
//
// Stage 2's boss: the cave's crimson imp drawn three times over, alight in the dark. It weaves on
// station and fires; at its first wound it puts its light out and prowls the dark between ambushes;
// at its second it blazes up for good and calls in its own kind. See CacoImpBrain.

/** The health fractions at which it changes phase; see [WOUND_MARKS]. */
const val CACO_IMP_PHASE_2_AT = WOUNDED_AT
const val CACO_IMP_PHASE_3_AT = BATTERED_AT

/**
 * Its light in the first phase: a smouldering crimson glow, breathing slowly about its strength,
 * that shows it from across the cave.
 */
const val CACO_IMP_LIGHT_COLOR = 0xFFFF6A50.toInt()
const val CACO_IMP_LIGHT_RADIUS = 130
const val CACO_IMP_SMOULDER_INTENSITY = 0.75f
const val CACO_IMP_BREATH = 0.1f
const val CACO_IMP_BREATHS_PER_SECOND = 0.6f

/**
 * Its second phase's round: its light dies over the douse, it glides unlit to a station of its
 * choosing at the prowl's speed and lurks there, then flares (the only warning of where it is),
 * fires, and burns lit before going dark again. The prowl, about 90 pixels a second, is a fifth of
 * the bat's top speed: the dark is where it hides, not where it hunts.
 */
const val CACO_IMP_DOUSE_SECONDS = 0.6f
const val CACO_IMP_PROWL_SPEED = 1.7f
const val CACO_IMP_LURK_SECONDS = 0.6f
const val CACO_IMP_FLARE_SECONDS = 0.5f
const val CACO_IMP_FLARE_INTENSITY = 1f
const val CACO_IMP_BURN_SECONDS = 1.8f

/**
 * Where it may settle in the dark: no further left than this fraction of the frame,
 * at least this far from the bat center to center, and at least this far from where
 * it was, so every prowl is a real move.
 */
const val CACO_IMP_PROWL_LEFTMOST = 0.35f
const val CACO_IMP_PROWL_BAT_CLEARANCE = 170f
const val CACO_IMP_PROWL_LEAST_MOVE = 110f

/**
 * Its last phase: a brighter, wider light guttering like a fire, a quicker weave, and two of its
 * kind called in every few seconds.
 */
const val CACO_IMP_BLAZE_COLOR = 0xFFFF9A4C.toInt()
const val CACO_IMP_BLAZE_RADIUS = 170
const val CACO_IMP_BLAZE_INTENSITY = 0.95f
const val CACO_IMP_FLICKER = 0.15f
const val CACO_IMP_BLAZE_TEMPO = 1.6f
const val CACO_IMP_SUMMON_SECONDS = 8f

/**
 * What one of its bolts deals, as a share of its contact damage (about 67 at stage
 * 2), set as the Moth Queen's are: an aimed bolt a little under a third of the
 * bat's bar, a ring bolt about a fifth.
 */
const val CACO_IMP_BOLT_DAMAGE = 0.45f
const val CACO_IMP_RING_DAMAGE = 0.3f


// --- The lagoon --------------------------------------------------------------------------------
//
// Stage 4 is flown from night to noon across a bay of limestone islands, toward a temple standing
// in the water; the sky is in Daybreak. Its enemies school, scuttle, throw rings of spines, and
// come at the bat from behind.

/**
 * Where something coming from behind leaps from, as a fraction of the frame's width, picked per
 * shark: behind where the bat usually flies, so its forward arc tops out near the bat.
 */
const val BEHIND_HOLD_X_MIN_FRACTION = 0.06f
const val BEHIND_HOLD_X_MAX_FRACTION = 0.28f


// --- The Naga ----------------------------------------------------------------------------------
//
// Stage 4's boss and the longest fight in the game: a hooded serpent of a head and twelve parts
// that rears out of the water, sways, spits and strikes, and dives to surface elsewhere. Five
// phases at fifths of its health; see NagaBrain. As with the Sand Wyrm, every part is its own
// entity and what lands on the body lands on the head.

/** The square frame every part is drawn in on its sheet, as is the water it throws up. */
const val NAGA_FRAME = 64

/** Its jaws working, two frames on a slow beat. */
const val NAGA_HEAD_FRAMES = 2
const val NAGA_HEAD_FRAME_SECONDS = 0.18f

/** The water a rise throws up, four frames from the sheet's eighth. */
const val NAGA_SPLASH_FRAME = 7
const val NAGA_SPLASH_FRAMES = 4
const val NAGA_SPLASH_FRAME_SECONDS = 0.07f

/**
 * How far apart its parts sit: along the head's path while it swims, and the least and most they
 * stretch to while it rears, when the body spans however far the head has risen.
 */
const val NAGA_SPACING = 21f
const val NAGA_SPACING_LEAST = 15f
const val NAGA_SPACING_MOST = 27f

/**
 * The health fractions at which it enters its second to fifth phases: a fifth apiece, where every
 * other boss changes phase at its wounds' marks. Its wounds still show at [WOUND_MARKS].
 */
const val NAGA_PHASE_2_AT = 0.8f
const val NAGA_PHASE_3_AT = 0.6f
const val NAGA_PHASE_4_AT = 0.4f
const val NAGA_PHASE_5_AT = 0.2f

/** Its extra health over its stage's boss health, for a fight of five phases rather than three. */
const val NAGA_VITALITY = 1.8f

/**
 * What its head and body deal on contact, as shares of its stage's boss damage, set like the Sand
 * Wyrm's: its strike makes the head its attack, and the body is a long thing to brush.
 */
const val NAGA_HEAD_DAMAGE = 0.75f
const val NAGA_BODY_DAMAGE = 0.3f

/** How much of a shot into its body carries through to the head, as with the Sand Wyrm's plates. */
const val NAGA_PLATE_SHARE = 0.5f

/**
 * What its spit deals, as a share of the head's contact damage: an aimed bolt about a third of the
 * bat's bar, a ring bolt about a fifth.
 */
const val NAGA_SPIT_DAMAGE = 0.42f
const val NAGA_RING_DAMAGE = 0.28f

/** The colorway of shot.png its spit is drawn in: neon pink, like the light in its hood. */
const val NAGA_SHOT_VARIANT = 13

/**
 * The shield its hood raises entering its fourth and fifth phases, as a fraction of its full
 * health. Like the Moth Queen's, it does not recharge.
 */
const val NAGA_SHIELD_FRACTION = 0.08f

/**
 * Its round, in seconds: under the water between rises (less when enraged), of
 * which the water boils where it will come up for the tell; rising and sinking; and
 * how long it rears while it only spits.
 */
const val NAGA_SUBMERGED_SECONDS = 1.9f
const val NAGA_ENRAGED_SUBMERGED_SECONDS = 1.2f
const val NAGA_TELL_SECONDS = 1.1f
const val NAGA_RISE_SECONDS = 0.8f
const val NAGA_SINK_SECONDS = 0.7f
const val NAGA_REAR_SECONDS = 3.4f

/** Seconds between its spits while it rears, and once it is enraged. */
const val NAGA_SPIT_SECONDS = 1.1f
const val NAGA_ENRAGED_SPIT_SECONDS = 0.8f

/**
 * Its strike, from phase two: it coils back as a warning (shorter enraged), lunges at where the bat
 * was at this many pixels a tick, as far as it can reach from where it came up, and draws back.
 */
const val NAGA_COIL_SECONDS = 0.55f
const val NAGA_ENRAGED_COIL_SECONDS = 0.4f
const val NAGA_STRIKE_SPEED = 8f
const val NAGA_STRIKE_REACH = 280f
const val NAGA_RECOIL_SECONDS = 0.5f

/**
 * Its swim, from phase four: in from the right low over the water, its body following in humps, at
 * this many pixels a tick, faster enraged.
 */
const val NAGA_SWIM_SPEED = 2.3f
const val NAGA_ENRAGED_SWIM_SPEED = 2.9f
