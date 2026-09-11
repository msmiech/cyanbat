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

// --- Level progression -------------------------------------------------------------------------
//
// A level is a stack of one-minute waves ending in a boss. The numbers below are the whole
// difficulty curve; LevelProgression does nothing but read them against the clock.

// A wave is a minute because that is the unit the player already counts in - "I got to four
// minutes" is a thing someone says about a run, "I got to wave 240 seconds" is not.
const val WAVE_DURATION_SECONDS = 60f

// Level 1's boss arrives at the five minute mark, and so on the sixth wave index.
const val BOSS_WAVE = 5

// What each level adds over the one before it: everything scaled, spawn gaps included.
const val LEVEL_DIFFICULTY_STEP = 0.35f

// Spawn density, in seconds between arrivals. The opening is deliberately sparse - a first minute
// the player can breathe in is what makes the fifth minute mean anything - and the floor is set
// by the bat's one-second fire rate: any tighter and enemies arrive faster than they can be shot.
const val OPENING_SPAWN_INTERVAL_SECONDS = 2.6f
const val MINIMUM_SPAWN_INTERVAL_SECONDS = 0.9f

// How far either side of the interval a spawn may land, as a fraction of it. Without it the
// spawns fall into a metronome and the level reads as a pattern to memorise.
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

/**
 * Which of the three enemy sprites each wave draws from, indexed by wave. Types are 0 SCOUT,
 * 1 SINE and 2 ZIGZAG - the mix is the point, so each minute brings a group that moves in a way
 * the last one did not.
 */
val WAVE_ENEMY_TYPES: List<List<Int>> = listOf(
    listOf(0),          // minute 1: scouts only, straight and readable
    listOf(0, 1),       // minute 2: weavers join them
    listOf(1, 2),       // minute 3: the scouts give way to zigzags
    listOf(0, 1, 2),    // minute 4: everything at once
    listOf(1, 2),       // minute 5: the two that are hardest to lead, as the boss escort
)

// The boss. Its health is a fight length: at one 34-damage shot a second, 850 points is roughly
// 25 seconds of landed hits, which leaves room to be driven off and come back without the fight
// resetting. Its contact damage is deliberately worse than anything else in the level.
const val BOSS_HIT_POINTS_PER_LEVEL = 850
const val BOSS_DAMAGE_PER_LEVEL = 40

// How much bigger the boss is drawn than the sprite sheet's enemies. Its collision box grows with
// it, which is most of what makes it dangerous to sit next to.
const val BOSS_SPRITE_SCALE = 3f

// Seconds between the boss's shots. Slower than the bat's, because the bat has to spend part of
// its time dodging and the boss does not.
const val BOSS_SHOT_INTERVAL_SECONDS = 1.6f

// Banked for clearing the level, on top of whatever the run scored on the way.
const val LEVEL_COMPLETE_BONUS = 10_000

// How long the wave and boss announcements stay up, in seconds.
const val WAVE_BANNER_SECONDS = 2.2f

// Wave and boss announcements, sized against the 480px framebuffer. The character width is what
// the banner is centred by: the Graphics API cannot measure a string, so a nominal advance for
// the sans-serif face both platforms use is the closest thing available.
const val BANNER_FONT_SIZE = 26
const val BANNER_CHAR_WIDTH = 15

// How long the victory overlay ignores input, in seconds. Longer than the pause overlay's, because
// the player has just been steering with a finger down and the boss went up in a blast worth
// watching.
const val LEVEL_COMPLETE_ARMING_SECONDS = 1.2f


// --- Experience and power-ups ------------------------------------------------------------------
//
// A second curve running against the level's own: the cave gets harder on a clock, the bat gets
// stronger on kills, and a run is the race between them. Experience is per-run and is never
// persisted - a level already bought would make the next run start halfway through this one.

// What a kill is worth, scaled by the wave it came from so that pressing on beats farming the
// opening minute, where enemies die to a single shot.
const val XP_PER_KILL = 10
const val XP_PER_KILL_PER_WAVE = 5

// Killing the level's boss, which is worth roughly a late level on its own.
const val XP_PER_BOSS = 250

// What the first level up costs, and what each one after it adds. Against level 1's roughly 100
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

// Armour Plating, as a multiplier on incoming damage. Floored well above zero: a bat that cannot
// be hurt has no run left to play.
const val ARMOUR_FACTOR = 0.85f
const val ARMOUR_FLOOR = 0.4f

// Mercy invulnerability after a hit. Without one a single obstacle would strip the whole bar over
// the frames the two sprites spend overlapping; Second Wind buys more of it, up to the cap.
const val PLAYER_HIT_COOLDOWN_SECONDS = 0.5f
const val SECOND_WIND_SECONDS = 0.3f
const val MAX_HIT_COOLDOWN_SECONDS = 1.5f

// The level up dialog, laid out against the 480x320 framebuffer. Three cards in a row with a gutter
// between them, centred horizontally and sitting just below the middle of the screen.
const val POWER_UP_CARD_WIDTH = 140
const val POWER_UP_CARD_HEIGHT = 96
const val POWER_UP_CARD_GAP = 10
const val POWER_UP_CARD_TOP = 130

// How long the dialog ignores input. The player was steering with a finger down when the level up
// landed, and the lift that follows is not them choosing a card.
const val POWER_UP_ARMING_SECONDS = 0.35f

// The experience bar, drawn across the very top edge where nothing else is.
const val XP_BAR_HEIGHT = 3
