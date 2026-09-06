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
