package at.smiech.cyanbat.util

const val TICK_INITIAL = 0.019f // in seconds

// The bat fires automatically on this cadence.
const val SHOT_INTERVAL_SECONDS = 1f

// Shot travel per tick, in framebuffer pixels. Enemies close at 1.2-2.5, so this outruns them.
const val SHOT_SPEED = 4f

// Length of the buzz when the bat takes a hit, in ms.
const val HIT_VIBRATION_MILLIS = 250L

// Pause. The overlay dims rather than hides the run, so the player can still see the obstacle
// they are about to fly back into.
const val PAUSE_DIM = 0xB4000000.toInt()

// How long the pause overlay ignores a tap. Long enough to outlast the finger lift at the end of
// an Android back gesture, which is what paused the game in the first place, and short enough
// that a player reaching to resume never notices it.
const val RESUME_ARMING_SECONDS = 0.35f

// How long the game over screen ignores a tap. Longer than the other overlays: a player steering
// with a finger down watches the bat fall and lifts it only once the screen has changed, and
// that lift is not them asking to leave.
const val GAME_OVER_ARMING_SECONDS = 0.8f

// Scoring. Surviving pays 1 point per tick (~52/second), so a kill at the base rate is worth
// about a second of survival and a maxed-out streak roughly eight.
//
// The step is 3 because that is what play actually supports: watching a run, a life tends to
// yield two or three kills before the bat is clipped. At five the multiplier essentially never
// appeared. Raise it to make combos rarer.
const val POINTS_PER_HIT = 50
const val HITS_PER_MULTIPLIER_STEP = 3
const val MAX_SCORE_MULTIPLIER = 8

// Health. The bat used to have three lives, so one hit cost a third of everything it had; a third
// of a 100 point bar is 34, which keeps the run exactly as survivable as it was - three hits and
// the bat is done - while leaving room for damage worth reading off the screen.
const val PLAYER_MAX_HIT_POINTS = 100
const val DAMAGE_PER_HIT = 34

// Obstacles are scenery a shot clears rather than a target that soaks hits, so one shot still
// takes one down.
const val DESTRUCTIBLE_HIT_POINTS = DAMAGE_PER_HIT

// A shot is spent by whatever it touches, whether or not that thing dies. It has to be a single
// point rather than a hit's worth: now that enemies deal less damage than a shot survives, a
// shot pegged to DAMAGE_PER_HIT would punch through a wave-one enemy and fly on.
const val SHOT_HIT_POINTS = 1

// The bat's health bar, in framebuffer pixels: thick enough to read at a glance on a phone, and
// clear of the sprite so the bat itself stays legible.
const val HEALTH_BAR_HEIGHT = 3f
const val HEALTH_BAR_OFFSET_Y = 2f

// Damage numbers over a hit enemy. They rise a little under a pixel per tick (~31px/second) and
// are gone inside a second, so a busy screen does not fill up with them.
const val DAMAGE_TEXT_FONT_SIZE = 12
const val DAMAGE_TEXT_DURATION_SECONDS = 0.7f
const val DAMAGE_TEXT_RISE_PER_TICK = 0.6f

// The cyan wake behind the bat. Segments drift at the scenery's own speed, so the wake hangs in
// the world instead of being towed along behind the sprite. The cadence is set by how fast the bat
// can move, not by how the wake looks standing still: at full tilt it covers about 12px between
// segments, so they still touch and the streak does not break into beads.
const val TRAIL_DRIFT_PER_TICK = -2f
const val TRAIL_INTERVAL_SECONDS = 0.03f

// Half a second of drift is roughly a bat-length of wake. The original trail faded over two
// seconds and stretched almost half the screen behind the bat, which read as a smear.
const val TRAIL_DURATION_SECONDS = 0.5f

// What a segment is down to as it dies, so the wake tapers away instead of ending square.
const val TRAIL_MIN_SCALE = 0.15f

// A segment against the bat's own frame: a quarter of its width, and the height of the tail that
// sheds it - the middle band of the sprite, not the whole of it.
const val TRAIL_SEGMENT_WIDTH_FRACTION = 0.25f
const val TRAIL_SEGMENT_HEIGHT_FRACTION = 0.25f

// --- Stage progression -------------------------------------------------------------------------
//
// A stage is a stack of one-minute waves ending in a boss. The numbers below are the whole
// difficulty curve; StageProgression does nothing but read them against the clock.

// A wave is a minute because that is the unit the player already counts in - "I got to four
// minutes" is a thing someone says about a run, "I got to wave 240 seconds" is not.
const val WAVE_DURATION_SECONDS = 60f

// Stage 1's boss arrives at the five minute mark, and so on the sixth wave index.
const val BOSS_WAVE = 5

// What each stage adds over the one before it: everything scaled, spawn gaps included.
const val STAGE_DIFFICULTY_STEP = 0.35f

// Spawn density, in seconds between arrivals. The opening is deliberately sparse - a first minute
// the player can breathe in is what makes the fifth minute mean anything - and the floor is set
// by the bat's one-second fire rate: any tighter and enemies arrive faster than they can be shot.
const val OPENING_SPAWN_INTERVAL_SECONDS = 2.6f
const val MINIMUM_SPAWN_INTERVAL_SECONDS = 0.9f

// How far either side of the interval a spawn may land, as a fraction of it. Without it the
// spawns fall into a metronome and the stage reads as a pattern to memorize.
const val SPAWN_INTERVAL_JITTER = 0.3f

// Enemy toughness per wave. Health is in units of the bat's 34-damage shot, so the opening wave
// dies to one shot and the wave before the boss takes three.
const val ENEMY_BASE_HIT_POINTS = 34
const val ENEMY_HIT_POINTS_PER_WAVE = 17

// What an enemy takes off the bat's 100 point bar. The opening wave is survivable eight times
// over; by the last wave it is three, which is where the game used to start.
const val ENEMY_BASE_DAMAGE = 12
const val ENEMY_DAMAGE_PER_WAVE = 6

// Closing speed, as a fraction added per wave. Small on purpose: enemies that outrun the bat's
// shots cannot be fought at all, only dodged.
const val ENEMY_SPEED_PER_WAVE = 0.1f

// What each wave draws from - the species mix - is part of a stage's design rather than its
// difficulty, so it lives with the rest of the design in StageDesign.

// The boss. Its health is a fight length: at one 34-damage shot a second, 1920 points is roughly
// 25 seconds of landed hits, which leaves room to be driven off and come back without the fight
// resetting. Its contact damage is deliberately worse than anything else in the stage.
const val BOSS_HIT_POINTS_PER_STAGE = 1920
const val BOSS_DAMAGE_PER_STAGE = 50

// How much bigger the boss is drawn than the sprite sheet's enemies. Its collision box grows with
// it, which is most of what makes it dangerous to sit next to.
const val BOSS_SPRITE_SCALE = 3f

// Seconds between the boss's shots. Slower than the bat's, because the bat has to spend part of
// its time dodging and the boss does not.
const val BOSS_SHOT_INTERVAL_SECONDS = 1.6f

// Banked for clearing the stage, on top of whatever the run scored on the way.
const val STAGE_COMPLETE_BONUS = 10_000

// How long the wave and boss announcements stay up, in seconds.
const val WAVE_BANNER_SECONDS = 2.2f

// Wave and boss announcements, sized against the 480px framebuffer. The character width is what
// the banner is centered by: the Graphics API cannot measure a string, so a nominal advance for
// the sans-serif face both platforms use is the closest thing available.
const val BANNER_FONT_SIZE = 26
const val BANNER_CHAR_WIDTH = 15

// How long the victory overlay ignores input, in seconds. Longer than the pause overlay's, because
// the player has just been steering with a finger down and the boss went up in a blast worth
// watching.
const val STAGE_COMPLETE_ARMING_SECONDS = 1.2f


// --- The forest --------------------------------------------------------------------------------
//
// Stage 2's enemies fly in groups, shoot back and carry shields. What each species does is in
// EnemySpecies and which wave sends what is in StageDesign; these are the numbers underneath.

// A shield handed out by a wave's shieldChance, as a fraction of the enemy's own health. Under
// one, so a shielded enemy is tougher rather than twice as tough - the point is the extra shot it
// takes to pop, and the moment of seeing it go.
const val WAVE_SHIELD_FRACTION = 0.6f

// A swarm: how many, and how many more per wave, up to a cap. Five is enough to read as a flock
// rather than as a handful of wasps; the cap is what keeps a late swarm from being a wall.
const val SWARM_SIZE = 5
const val SWARM_SIZE_PER_TWO_WAVES = 1
const val SWARM_SIZE_MAX = 7

// How loosely a swarm is packed around its path, in framebuffer pixels either way.
const val SWARM_SPREAD_X = 40f
const val SWARM_SPREAD_Y = 22f

// A V formation: how far each rank sits behind the one in front, and how far out to either side.
const val FORMATION_RANK_SPACING_X = 22f
const val FORMATION_RANK_SPACING_Y = 17f
const val FORMATION_RANKS = 2

// How much room a group needs from the top and bottom edges of the frame, so its path never carries
// its outer members off screen: a sway of up to ~40px, plus the widest rank or scatter, plus half a
// sprite.
const val GROUP_EDGE_MARGIN = 85f

// Where a hovering or diving enemy stops, as a fraction of the framebuffer width, picked per enemy
// from this range. Far enough in that it is on screen and in reach; far enough back that the bat
// still has room to get out of the way.
const val HOLD_X_MIN_FRACTION = 0.5f
const val HOLD_X_MAX_FRACTION = 0.82f

// Enemy fire, beyond straight bolts. An aimed shot flies at the player's position when it was
// fired, which a moving player simply is not at by the time it arrives; the speeds on the guns
// themselves (EnemySpecies) are set so it can be seen and sidestepped.
//
// The fraction of an armed enemy's first interval it waits, at most, before its first volley - so
// a formation that spawned on one tick does not fire as one.
const val FIRST_SHOT_JITTER = 0.6f


// --- The Moth Queen ----------------------------------------------------------------------------
//
// Stage 2's boss. Three phases, marked by her health: each one raises her shield, and she gets
// faster and calls in wasps as she goes.

// Her sheet: four frames of wingbeat, drawn at 96x80 - the footprint of the cave's boss, at the
// pixel density of everything else.
const val MOTH_QUEEN_FRAME_WIDTH = 96
const val MOTH_QUEEN_FRAME_COUNT = 4
const val MOTH_QUEEN_FRAME_SECONDS = 0.11f

// Her hit box sits this far inside her frame. Much of a moth's frame is the air between its wings.
const val MOTH_QUEEN_COLLISION_TOLERANCE = 14f

// The colorway of shot.png her bolts are drawn in: rose, like her wings.
const val MOTH_QUEEN_SHOT_VARIANT = 6

// The health fractions at which she changes phase.
const val MOTH_QUEEN_PHASE_2_AT = 0.66f
const val MOTH_QUEEN_PHASE_3_AT = 0.33f

// The shield she raises at each change of phase, as a fraction of her full health. It does not
// recharge: it is a wall to break through, once, at the start of each phase.
const val MOTH_QUEEN_SHIELD_FRACTION = 0.12f

// Seconds between the wasp swarms she calls in, from phase two on, and how much faster they come
// once she is enraged.
const val MOTH_QUEEN_SUMMON_SECONDS = 9f
const val MOTH_QUEEN_ENRAGED_SUMMON_SECONDS = 7f

// How much faster she flies her figure eight in the last phase.
const val MOTH_QUEEN_ENRAGED_TEMPO = 1.6f

// What one of her bolts deals, as a share of her contact damage - which at stage 2 is about 67, two
// thirds of the bat's bar. Her rings put a dozen bolts on screen at once, so each has to be a
// setback rather than half a death: a ring bolt costs about a fifth of the bar, an aimed one - the
// ones the player should always be dodging - a little under a third.
const val MOTH_QUEEN_RING_DAMAGE = 0.3f
const val MOTH_QUEEN_FAN_DAMAGE = 0.45f


// --- Experience and power-ups ------------------------------------------------------------------
//
// A second curve running against the stage's own: the cave gets harder on a clock, the bat gets
// stronger on kills, and a run is the race between them. Experience is per-run and is never
// persisted - a level already bought would make the next run start halfway through this one.

// What a kill is worth, scaled by the wave it came from so that pressing on beats farming the
// opening minute, where enemies die to a single shot.
const val XP_PER_KILL = 10
const val XP_PER_KILL_PER_WAVE = 5

// Killing the stage's boss, which is worth roughly a late level on its own.
const val XP_PER_BOSS = 250

// What the first level up costs, and what each one after it adds. Against stage 1's roughly 100
// reachable kills this lands somewhere near ten level ups across a full run - often enough that a
// pick matters, rare enough that the dialog is an event rather than an interruption.
const val XP_FIRST_LEVEL = 50
const val XP_LEVEL_STEP = 35

// How many power-ups are offered per level up.
const val POWER_UP_CHOICES = 3

// Rapid Fire, as a multiplier on the gap between shots. The floor is a little over three shots a
// second: past that the bat is a wall of bullets and dodging stops being the game.
const val RAPID_FIRE_FACTOR = 0.82f
const val MIN_SHOT_INTERVAL_SECONDS = 0.3f

// Spread Shot. Each pick adds one more shot to the fan, alternating above and below the straight
// one; the cap is what keeps the spread readable and the frame from filling with shots.
const val MAX_EXTRA_SHOTS = 4
const val SPREAD_ANGLE_DEGREES = 9f

// Vitality and Heavy Rounds, the two that scale without a ceiling. They are what guarantees a
// level up always has three things to offer.
const val VITALITY_HIT_POINTS = 25
const val HEAVY_ROUNDS_DAMAGE = 12

// Armor Plating, as a multiplier on incoming damage. Floored well above zero: a bat that cannot
// be hurt has no run left to play.
const val ARMOR_FACTOR = 0.85f
const val ARMOR_FLOOR = 0.4f

// Mercy invulnerability after a hit. Without one a single obstacle would strip the whole bar over
// the frames the two sprites spend overlapping; Second Wind buys more of it, up to the cap.
const val PLAYER_HIT_COOLDOWN_SECONDS = 0.5f
const val SECOND_WIND_SECONDS = 0.3f
const val MAX_HIT_COOLDOWN_SECONDS = 1.5f

// The level up dialog, laid out against the 480x320 framebuffer. Three cards in a row with a gutter
// between them, centered horizontally and sitting just below the middle of the screen.
const val POWER_UP_CARD_WIDTH = 140
const val POWER_UP_CARD_HEIGHT = 96
const val POWER_UP_CARD_GAP = 10
const val POWER_UP_CARD_TOP = 130

// How long the dialog ignores input. The player was steering with a finger down when the level up
// landed, and the lift that follows is not them choosing a card.
const val POWER_UP_ARMING_SECONDS = 0.35f

// The experience bar, drawn across the very top edge where nothing else is.
const val XP_BAR_HEIGHT = 3

// The second batch of power-ups. Where the first six sharpen what the bat already does, these
// change what it can do at all - regenerate, cheat death, shoot through things, come off the walls.

// Regeneration, in health a second. An Int so the card can print it without a decimal point, and
// small against the 12-36 damage of a single hit: it is what lets a careful run recover between
// waves, never what carries one through a wave it is losing.
const val REGEN_PER_SECOND = 2
const val MAX_HEALTH_REGEN_PER_SECOND = 10f

// Fast Learner and Bounty Hunter, as fractions added to their multipliers. Both uncapped, and both
// deliberately small: they pay off over a whole run rather than in the wave they were picked, which
// is what makes taking one over an immediate upgrade a real decision.
const val XP_BONUS = 0.05f
const val SCORE_BONUS = 0.10f

// Second Life. The bat comes back at half a bar rather than a full one - a free death should keep a
// run alive, not undo the damage that ended it - and stacks only so far.
const val REVIVE_HEALTH_FRACTION = 0.5f
const val MAX_REVIVES = 3

// Counterweight: a flat cut off every hit, paid for with a share more damage dealt. Flat rather
// than proportional, so it is worth most against the swarms of weak enemies that armor barely
// notices. Incoming damage still floors at 1, so stacking this can blunt a hit but never void it.
const val COUNTERWEIGHT_REDUCTION = 1
const val COUNTERWEIGHT_BONUS = 0.10f
const val MAX_FLAT_DAMAGE_REDUCTION = 20

// Piercing Shot and Ricochet, in targets and reflections per shot. Both capped: a shot that passed
// through everything would clear the frame from one corner, and one that never left it would fill
// the frame with strays the player cannot read.
const val MAX_SHOT_PIERCE = 4
const val MAX_SHOT_BOUNCE = 3


// --- The aura ----------------------------------------------------------------------------------
//
// What the bat's own levels look like from the outside. The experience bar says how close the next
// power-up is; this says how far the run has already come, and it says it on the bat itself rather
// than in a corner of the HUD.

// The level at which the glow is as bright as it gets. Set against a full run, which lands
// somewhere near ten level ups: the halo is still visibly growing for the whole of an ordinary
// run, and only an exceptional one tops it out.
const val AURA_FULL_INTENSITY_LEVEL = 24

// Levels per tier. Every tenth level adds a spark cluster and one more arc of lightning, which is
// the step the sound is played on. Ten because it is the number a player counts in - "level 20"
// is a thing they will notice reaching, "level 7" is not.
const val AURA_LEVELS_PER_TIER = 10

// How loud the surge is played, against the 0..1 the engine takes. Deliberately low: it lands on
// the same beat as the level up banner and the power-up dialog, over music that is still playing.
const val AURA_SURGE_VOLUME = 0.35f


// --- Hit feedback ------------------------------------------------------------------------------

// How long an enemy stays lit after a hit lands. Two frames' worth at the fixed tick: long enough
// to register as a flash, short enough that a tough enemy under rapid fire reads as being hit
// repeatedly rather than as glowing continuously.
const val HIT_FLASH_SECONDS = 0.08f

// What it is lit up with. Near-white rather than the cyan of the shot that caused it: the flash
// has to say "this was hit" against a cave and a bat that are already cyan, and white is the one
// value that reads instantly against every sprite in the game. The alpha is the flash at full
// strength - short of solid, so the enemy underneath is still recognisable while it burns.
const val HIT_FLASH_COLOR = 0xE6FFFFFF.toInt()

// How loud the gun is, against the 0..1 the engine takes. Low, and it has to be: at the base
// cadence this plays once a second for a whole run, and with Rapid Fire stacked more than three
// times a second.
const val SHOT_VOLUME = 0.18f


// --- Critical hits -----------------------------------------------------------------------------
//
// A rare, loud payoff on an otherwise even stream of shots. The two numbers below are a pair and
// have to be read as one: at a one in a hundred chance, a modest multiplier would be a bonus the
// player never notices happening, so the rarity is what buys the size.

// How often a shot leaves the gun critical, as 0..1. Rolled per projectile, so a spread build gets
// more rolls per volley - which is the fan doing what a fan is supposed to do, not a bug.
const val CRITICAL_CHANCE = 0.01f

// What a critical is worth, as a multiplier on the shot damage the run has earned. Multiplied
// rather than fixed so that Heavy Rounds keeps paying into it: a crit is the bat's own gun landing
// well, and a flat number would quietly become the worse outcome late in a run.
//
// Four is chosen against what it kills. A base shot is 34 against enemies that run 34-119, so a
// crit at 136 removes anything short of the final waves in one hit, which is what makes it read as
// an event rather than as a slightly bigger number.
const val CRITICAL_DAMAGE_MULTIPLIER = 4f

// The damage number a crit puts up: bigger than the ordinary 12, and held a little longer, because
// it is the one number in the game worth actually reading.
const val CRITICAL_TEXT_FONT_SIZE = 22
const val CRITICAL_TEXT_DURATION_SECONDS = 1.0f

// Sharpshooter, in chance added per pick. Added rather than multiplied, and that is the whole
// decision: scaling 1% by any sane factor lands back near 1%, so a multiplied version would be a
// card that reads as an upgrade and plays as nothing.
//
// Four points is set against Heavy Rounds, which is what it competes with. At a 4x crit, expected
// damage runs 1 + 3p of an ordinary shot, so each pick here is worth about 12% more damage - a
// third of what the first Heavy Rounds gives, but it does not thin out as damage stacks the way
// a flat +12 does, and it compounds with every point of shot damage the run has already bought.
const val CRITICAL_CHANCE_BONUS = 0.04f

// Where it stops. Half is reachable only by a run that spends nearly every pick on it, which is
// what a build is supposed to cost - and it is a ceiling rather than no ceiling because a critical
// that lands more often than not has stopped being a critical and is just the damage number.
const val MAX_CRITICAL_CHANCE = 0.5f


// --- Shot colorways ----------------------------------------------------------------------------
//
// `shot.png` is one bolt drawn seven times over: the player's cyan, then the cave's three enemy
// palettes in the order the enemy sheet lays them out, then the forest's shooters. Which colorway
// a species fires is on EnemySpecies. A shot is the color of whatever fired it, so a screen
// holding the bat's fire and the boss's at the same time says which is which by color rather than
// by which way a bolt happens to be travelling.

// Width of one bolt in the sheet. The sprite is addressed by frame like the enemy sheet, so this
// is what positions a shot rather than the pixmap's own width - which is now the whole strip.
const val SHOT_FRAME_WIDTH = 24

// The player's colorway, and the offset from an enemy's type to its own. The enemy strips are
// indexed 0..2 and sit behind the player's, so a type-2 boss fires the fourth bolt.
const val PLAYER_SHOT_VARIANT = 0
const val ENEMY_SHOT_VARIANT_OFFSET = 1


// --- The bat's death ---------------------------------------------------------------------------
//
// What used to happen when the bat died was that it kept beating its wings, slid off the bottom of
// the frame at a flat two pixels a tick, and had a 483x257 picture of the words "You've lost!"
// dropped on top of it. The words are the game over screen's job; this is the bat's.

// Width of one bat frame. Shared rather than private to EntityFactory, because the screen has to
// address the death sheet with it too - the same reason SHOT_FRAME_WIDTH lives out here.
const val BAT_FRAME_WIDTH = 45

// The five frames of cyanBatDeath.png, played once and then held. Faster than the wingbeat: dying
// is one motion, and it has to be finished well before the fall carries the bat off screen.
const val BAT_DEATH_FRAME_COUNT = 5
const val BAT_DEATH_FRAME_SECONDS = 0.08f

// The fall, in pixels per tick added per second, and the fastest it may go. Velocities in this
// engine are per tick, so the first of these is a rate of change of a rate. Terminal is set so a
// drop from mid-screen takes a little under a second - long enough to watch, short enough that the
// player is not kept waiting for a run they have already lost.
const val DEATH_GRAVITY = 7.5f
const val DEATH_TERMINAL_VELOCITY = 6f

// The tumble. Cosmetic, like every rotation here - the collision box stays upright, though nothing
// is left to collide with by this point.
const val DEATH_SPIN_DEGREES_PER_SECOND = 210f

// Seconds between the blasts thrown off on the way down. Three or four over a typical fall: enough
// to read as the bat coming apart, few enough that it is not a firework.
const val DEATH_PUFF_INTERVAL_SECONDS = 0.22f

// How big those blasts are against the full-size one an enemy gets. Small - they are pieces coming
// off, and a full blast every fifth of a second would bury the sprite they are meant to be leaving.
const val DEATH_PUFF_SCALE = 0.55f
