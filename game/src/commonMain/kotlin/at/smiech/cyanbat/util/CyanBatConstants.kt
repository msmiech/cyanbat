package at.smiech.cyanbat.util

const val INITIAL_ENEMY_GENERATION_INTERVAL = 5_000L // in ms
const val MINIMUM_ENEMY_GENERATION_INTERVAL = 1_000L // in ms

// Upper bound of the random spread added on top of MINIMUM_ENEMY_GENERATION_INTERVAL. It shrinks
// as the run goes on so enemies arrive faster, and bottoms out here instead of reaching zero.
const val MINIMUM_ENEMY_GENERATION_SPREAD = 500L // in ms
const val ENEMY_GENERATION_SPREAD_DECAY = 84L // in ms, per spawn
const val TICK_INITIAL = 0.019f // in ms

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

// Enemies, obstacles and shots carried a single hit point each and died to anything that touched
// them. Pegging them to one hit's damage keeps that true.
const val DESTRUCTIBLE_HIT_POINTS = DAMAGE_PER_HIT

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
