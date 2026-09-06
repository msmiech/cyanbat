package at.smiech.cyanbat.util

const val DEBUG = false
const val TAG = "CyanBat"
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

// Scoring. Surviving pays 1 point per tick (~52/second), so a kill at the base rate is worth
// about a second of survival and a maxed-out streak roughly eight.
//
// The step is 3 because that is what play actually supports: watching a run, a life tends to
// yield two or three kills before the bat is clipped. At five the multiplier essentially never
// appeared. Raise it to make combos rarer.
const val POINTS_PER_HIT = 50
const val HITS_PER_MULTIPLIER_STEP = 3
const val MAX_SCORE_MULTIPLIER = 8
