package at.smiech.cyanbat.util

// The frame every screen of the game draws, in framebuffer pixels: 16:9, the shape of most phones
// held sideways and of most monitors, so on either it fills the screen or near enough. It is also
// the playfield, the same on every device, and what spawn points, boss stations, the overlays and
// the desert's sky are laid out against. Each host hands it to the engine; nothing else declares it.
const val FRAME_BUFFER_WIDTH = 640
const val FRAME_BUFFER_HEIGHT = 360

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

// Scoring. Only kills score, and every unbroken run of 3 steps the multiplier up by one, with no
// ceiling: a streak of thirty kills pays x11 a kill, and one of a hundred x34.
//
// The step is 3 because that is what play actually supports: watching a run, a life tends to
// yield two or three kills before the bat is clipped. At five the multiplier essentially never
// appeared. Raise it to make combos rarer.
const val POINTS_PER_HIT = 50
const val HITS_PER_MULTIPLIER_STEP = 3

// The combo readout; see ComboMeter and ComboHeat. Its title is drawn at the HUD's size, and its
// count grows from there by COMBO_COUNT_GROWTH a doubling of the multiplier, up to
// COMBO_COUNT_MAX_SIZE, which is as big as it gets without crowding the wave readout above it.
const val COMBO_FONT_SIZE = 15
const val COMBO_COUNT_GROWTH = 3f
const val COMBO_COUNT_MAX_SIZE = 25

// How much of a step's pop the count swells by at its start, and the title on a new rung. The pop
// eases back over COMBO_POP_SECONDS: long enough to catch the eye, over before the next kill.
const val COMBO_POP_SECONDS = 0.35f
const val COMBO_POP_GROWTH = 0.5f
const val COMBO_TITLE_POP_GROWTH = 0.3f

// The fire on it, as the heat it is stoked to along the tops of the letters; its flames stand about
// as many cells tall as their heat, a cell two pixels square. The least fire is what the first step
// gets, so it is visibly alight; the most is what SUPERNOVA gets, and reaches up past the wave
// readout without swallowing the score.
const val COMBO_FLAME_MIN_HEAT = 4
const val COMBO_FLAME_MAX_HEAT = 11
const val COMBO_MIN_FLAME_STRENGTH = 0.2f

// A step flares the fire by COMBO_FLARE_HEAT, a kill by COMBO_KILL_FLARE of that, and either dies
// back over COMBO_FLARE_SECONDS.
const val COMBO_FLARE_HEAT = 5f
const val COMBO_KILL_FLARE = 0.4f
const val COMBO_FLARE_SECONDS = 0.5f

// Past the last named rung the fire cycles through every color on the ladder and back, this many
// times a second at SUPERNOVA's multiplier, and as many again for every doubling of it.
const val COMBO_SUPERNOVA_CYCLES_PER_SECOND = 0.35f

// Health. The bat used to have three lives, so one hit cost a third of everything it had; a third
// of a 100 point bar is 34, which keeps the run exactly as survivable as it was - three hits and
// the bat is done - while leaving room for damage worth reading off the screen.
const val PLAYER_MAX_HIT_POINTS = 100
const val DAMAGE_PER_HIT = 34

// Wounds. Every creature's sheet stacks it three times, top to bottom - unhurt, wounded, battered -
// and a row takes over as its health falls to each of these marks; see WoundComponent. The bosses
// change phase at the same marks, so every creature in the game, the bat included, looks hurt at
// the same point of its bar, and a boss looks as far through its fight as it is.
const val WOUNDED_AT = 0.66f
const val BATTERED_AT = 0.33f
val WOUND_MARKS = floatArrayOf(WOUNDED_AT, BATTERED_AT)
const val WOUND_ROWS = 3

// What a wound costs an ordinary enemy, a row of WOUND_MARKS apiece: how fast it still flies - its
// pattern, its wingbeat and all - and how often it still fires, as fractions of its own. A hurt
// enemy becomes a straggler, falling behind its swarm or its formation, which rewards the player for
// finishing what they started. The bosses are left at full pace: their fights escalate as they are
// hurt, and are special enough, and hard enough to reach, without being made easier at the end.
val WOUNDED_PACE = floatArrayOf(1f, 0.8f, 0.6f)
val WOUNDED_FIRE_RATE = floatArrayOf(1f, 0.75f, 0.5f)

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

// The boss. Its health is a fight length, against the bat that reaches it rather than the one that
// set out: five minutes in, that bat is twenty-odd levels up, its gun fanned out, quickened and
// heavier, with weapons of its own besides. A quarter of this, which the bosses had before the
// power-ups grew, went down to it in ten to thirty seconds - to a bat built for damage in five or
// ten - before the later phases had shown what they do. At this, a bat built for damage takes half
// a minute or so and one built otherwise a minute or more, which leaves every phase room to play
// out and the player room to be driven off and come back without the fight resetting. Its contact
// damage is deliberately worse than anything else in the stage.
const val BOSS_HIT_POINTS_PER_STAGE = 10240
const val BOSS_DAMAGE_PER_STAGE = 50

// How much bigger the boss is drawn than the sprite sheet's enemies. Its collision box grows with
// it, which is most of what makes it dangerous to sit next to.
const val BOSS_SPRITE_SCALE = 3f

// A boss's health bar, for the bosses that spend part of their fight where they cannot be seen -
// the Sand Wyrm under the sand, the Caco Imp in the dark: pinned under the stage timer rather than
// hung under the boss, where it would go with the head under the sand and give the imp away in the
// dark. The Moth Queen is always in sight, and wears hers under her.
const val BOSS_BAR_WIDTH = 200
const val BOSS_BAR_TOP = 25
const val BOSS_BAR_HEIGHT = 4

// Banked for clearing the stage, on top of whatever the run scored on the way.
const val STAGE_COMPLETE_BONUS = 10_000

// How long the wave and boss announcements stay up, in seconds.
const val WAVE_BANNER_SECONDS = 2.2f

// Wave and boss announcements, sized against the 640px framebuffer.
const val BANNER_FONT_SIZE = 26

// The stage timer, top center.
const val STAGE_TIMER_FONT_SIZE = 18

// How long the victory overlay ignores input, in seconds. Longer than the pause overlay's, because
// the player may still be steering with a finger down when it comes up, and the fanfare's drop,
// which the overlay lands on, deserves a moment to be heard.
const val STAGE_COMPLETE_ARMING_SECONDS = 1.2f

// When the victory's fanfare comes in after the boss goes down, in seconds: once the boss's blast
// and the aftershocks after it have gone off, so the two are heard one after the other rather than
// over each other.
const val VICTORY_FANFARE_DELAY_SECONDS = 0.8f

// Where the fanfare's drop lands, from its first note: six beats at 120 BPM. Set by
// tools/generate_victory_music.py (LANDING_BEAT); change the two together.
const val VICTORY_FANFARE_LANDING_SECONDS = 3f

// How long the run plays on after its boss goes down before the stage complete overlay comes up: the
// overlay lands on the fanfare's drop. Long enough to watch the boss go up and hear the fanfare
// climb to it, and the player can keep flying through it.
const val STAGE_COMPLETE_DELAY_SECONDS =
    VICTORY_FANFARE_DELAY_SECONDS + VICTORY_FANFARE_LANDING_SECONDS

// How far a blast or a break drifts each tick, in framebuffer pixels: left, the way the scenery
// goes, so it stays where the thing was rather than where the screen was.
const val BURST_DRIFT = -1f

// When the boss's wreck goes up again after its first blast, in seconds after it: the blasts in
// bossDeath.wav, from tools/generate_combat_sounds.py (AFTERSHOCKS). Change the two together, or the
// blasts heard and the blasts seen drift apart.
val BOSS_AFTERSHOCK_SECONDS = floatArrayOf(0.2f, 0.42f, 0.66f)

// The first aftershock's size against the piece of the boss it goes off on, and how much smaller
// each one after it is. Nearly the boss's own blast, because a fireball's art fills only the middle
// of its frame and anything smaller is lost next to a boss; dying away, so it reads as the wreck
// going up again rather than as more bosses dying.
const val BOSS_AFTERSHOCK_SCALE = 0.9f
const val BOSS_AFTERSHOCK_FALLOFF = 0.85f


// --- The jungle --------------------------------------------------------------------------------
//
// Stage 1's enemies fly in groups, shoot back and carry shields. What each species does is in
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
// Stage 1's boss. Three phases, marked by her health: each one raises her shield, and she gets
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

// The health fractions at which she changes phase, and so at which she looks wounded and then
// battered; see WOUND_MARKS.
const val MOTH_QUEEN_PHASE_2_AT = WOUNDED_AT
const val MOTH_QUEEN_PHASE_3_AT = BATTERED_AT

// The shield she raises at each change of phase, as a fraction of her full health. It does not
// recharge: it is a wall to break through, once, at the start of each phase.
const val MOTH_QUEEN_SHIELD_FRACTION = 0.12f

// Seconds between the wasp swarms she calls in, from phase two on, and how much faster they come
// once she is enraged.
const val MOTH_QUEEN_SUMMON_SECONDS = 9f
const val MOTH_QUEEN_ENRAGED_SUMMON_SECONDS = 7f

// How much faster she flies her figure eight in the last phase.
const val MOTH_QUEEN_ENRAGED_TEMPO = 1.6f

// What one of her bolts deals, as a share of her contact damage - about 67, two thirds of the bat's
// bar, since she is fought as hard as she was as the second stage's boss (StageDesign.JUNGLE). Her
// rings put a dozen bolts on screen at once, so each has to be a setback rather than half a death: a
// ring bolt costs about a fifth of the bar, an aimed one - the ones the player should always be
// dodging - a little under a third.
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
const val XP_PER_KILL_PER_WAVE = 6

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

// Spread Shot. Each pick adds one more shot to the fan, which stays centered on straight ahead; the
// cap is what keeps the spread readable and the frame from filling with shots.
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

// The level up dialog, laid out against the 640x360 framebuffer. Three cards in a row with a gutter
// between them, centered horizontally and sitting just below the middle of the screen.
const val POWER_UP_CARD_WIDTH = 140
const val POWER_UP_CARD_HEIGHT = 96
const val POWER_UP_CARD_GAP = 10
const val POWER_UP_CARD_TOP = 150

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

// The third batch: weapons besides the gun. Each works on its own - an orb circling the bat, the wake
// it leaves, a beam on a clock - so it keeps fighting while the player is busy dodging. What they
// deal is a share of the shot's damage, rounded up, so Heavy Rounds and Counterweight keep paying
// into them rather than leaving them behind as a run's damage climbs.

// Guardian Orb. Each pick adds an orb, and the orbs share the circle evenly. They go round clockwise,
// as the frame is drawn, far enough out to clear the bat's wings with a little room, and once round
// in ORB_SECONDS_PER_TURN: quick enough to sweep the space around the bat, slow enough to follow.
// Capped where the ring is nearly closed. A new orb spreads the ring out round it over about
// ORB_SETTLE_SECONDS, rather than every orb jumping to its new place on the same frame.
const val MAX_ORBS = 5
const val ORB_RADIUS = 44f
const val ORB_SECONDS_PER_TURN = 1.6f
const val ORB_SETTLE_SECONDS = 0.35f
const val ORB_DAMAGE_FRACTION = 0.6f

// Its sheet: six 14x14 frames of a glint going round it, from tools/generate_orb_sprite.py. It hits
// with the sphere and not the outline round it. It gives off no light in the dark: it never leaves
// the bat's own, and a light apiece would cost an old phone frame time for nothing to see.
const val ORB_FRAME = 14
const val ORB_FRAME_COUNT = 6
const val ORB_FRAME_SECONDS = 0.08f
const val ORB_COLLISION_TOLERANCE = 2f

// How soon one orb may hit the same thing again, in seconds. Longer than an orb takes to sweep
// through an enemy, so one pass lands once; shorter than the gap between two orbs of a full ring,
// so each of them lands as it comes round.
const val ORB_REHIT_SECONDS = 0.3f

// Charged Trail. Each pick draws the wake out longer and makes its shock hit harder, by its level:
// level 0 is the plain wake every run starts with, which hurts nothing. Capped below the two seconds
// the wake once had, which read as a smear.
const val MAX_WAKE_LEVEL = 3
val WAKE_SECONDS = floatArrayOf(TRAIL_DURATION_SECONDS, 1.0f, 1.3f, 1.6f)
val WAKE_DAMAGE_FRACTION = floatArrayOf(0f, 0.3f, 0.45f, 0.6f)

// How soon the wake may shock the same thing again. An enemy flies about as fast as the wake drifts,
// so one that touches it can stay in it for the rest of its life; this, not the touch, is what sets
// how hard it is hurt there.
const val WAKE_REHIT_SECONDS = 0.4f

// A charged segment keeps more of its size as it dies, and stops shocking once it has faded past
// WAKE_HARMLESS_FROM of its life, so what hurts is always something the player can see.
const val WAKE_MIN_SCALE = 0.45f
const val WAKE_HARMLESS_FROM = 0.8f
const val WAKE_COLOR = 0xFF00E5FF.toInt()
const val WAKE_CORE_COLOR = 0xFFE6FFFF.toInt()

// Frost Beam. A beam from the bat at an ordinary enemy on screen, picked at random, on across the
// frame; it freezes every ordinary enemy along it. Bosses and elites are never frozen - a boss's fight
// would stop dead, and an elite is a prize to chase - and the beam goes through them. Each pick fires
// it more often and freezes for longer, to a cap. It never hurts anything itself.
const val MAX_FROST_LEVEL = 4
const val FROST_BEAM_INTERVAL_SECONDS = 3.5f
const val FROST_BEAM_INTERVAL_FACTOR = 0.8f
const val FROST_SECONDS = 1.8f
const val FROST_SECONDS_PER_LEVEL = 0.5f

// How far either side of its line the beam freezes, and how long it shows: a flash, not a fixture. A
// white core in a pale glow, edged in a deep blue, as every sprite is edged in a dark outline: without
// it the beam all but vanished into the desert's noon sky.
const val FROST_BEAM_HALF_WIDTH = 4f
const val FROST_BEAM_SHOW_SECONDS = 0.3f
const val FROST_BEAM_CORE_COLOR = 0xFFF0FCFF.toInt()
const val FROST_BEAM_GLOW_COLOR = 0xFF8ED8FF.toInt()
const val FROST_BEAM_EDGE_COLOR = 0xFF2C6CC0.toInt()

// Something frozen is washed in ice blue, its shape and a ghost of its markings showing through, and
// the wash thins out over the last FROST_THAW_SECONDS of the freeze, so the player sees it is about to
// come free. A deep, strong blue, because a pale one over the warm hostiles came out grey: the desert's
// locusts went the color of the sand. It drifts with the scenery meanwhile, as a block of ice would: it
// has stopped, not been pinned to the screen. And it is harmless while it lasts: the bat flies through
// it, though the bat's weapons still hurt it.
const val FROST_TINT = 0xB8489CF0.toInt()
const val FROST_THAW_SECONDS = 0.5f
const val FROST_DRIFT = BURST_DRIFT
const val FROST_LIGHT_COLOR = 0xFFB4E6FF.toInt()

// In the dark, the flash of cold light the beam leaves on each thing it froze: small and brief, a
// glint of it on what it caught rather than a light thrown across the cave.
const val FROST_FLASH_RADIUS = 40
const val FROST_FLASH_SECONDS = 0.2f


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
// `shot.png` is one bolt, four frames of it, drawn fourteen times over: the player's cyan, then the
// cave's three enemy palettes in the order the enemy sheet lays them out, then the jungle's shooters,
// the Sand Wyrm's, one per ElitePalette, and the Naga's. Which colorway a species fires is on
// EnemySpecies, and an elite fires its palette's instead. A shot is the color of whatever fired it, so
// a screen holding the bat's fire and the boss's at the same time says which is which by color rather
// than by which way a bolt happens to be travelling. `impact.png`, the hit a shot leaves where it is
// spent, comes in the same colorways in the same order.

// Width of one bolt in the sheet. The sprite is addressed by frame like the enemy sheet, so this
// is what positions a shot rather than the pixmap's own width - which is now the whole strip.
const val SHOT_FRAME_WIDTH = 24

// The bolt's frames, laid side by side within its colorway: its core throbs and sends a ripple back
// down its body, once every SHOT_FRAME_COUNT * SHOT_FRAME_SECONDS. Quick, since the bolt is energy
// and a slower throb reads as a light blinking on something solid.
const val SHOT_FRAME_COUNT = 4
const val SHOT_FRAME_SECONDS = 0.05f

// A hit: a star of light opening into a ring and a spray of sparks, played once. Square, and smaller
// than a death's blast, since a tough enemy takes a stream of hits and must still be seen under them.
// A quarter of a second in all - an instant, but one the eye catches.
const val IMPACT_FRAME = 21
const val IMPACT_FRAME_COUNT = 6
const val IMPACT_FRAME_SECONDS = 0.04f

// How far ahead of a shot's middle its hit goes off, along the way it was going: about where its nose
// is, which is where it struck, on the edge of what it struck.
const val IMPACT_LEAD = 9f

// The player's colorway, and the offset from an enemy's type to its own. The enemy strips are
// indexed 0..2 and sit behind the player's, so a type-2 boss fires the fourth bolt.
const val PLAYER_SHOT_VARIANT = 0
const val ENEMY_SHOT_VARIANT_OFFSET = 1

// The body of each colorway's bolt, in the sheet's order: what color of light a shot gives off where
// the stage is dark. From `generate_shot_sprite.py`, which SpriteSheetTest holds them to.
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
// The cave is flown in the dark. The bat carries its own light, every shot and every blast is one,
// and whatever stands in a light's way - a rock, an imp - throws a shadow away from it; see
// LightingSystem. These are the whole of how dark it is and how far each light reaches. The jungle
// and the desert are flown in daylight, and none of it applies there.

// The light where no other reaches, as the color the cave is multiplied by: about a quarter, and a
// little bluer than gray. The walls sink into the dark, while an imp the bat's light has not reached
// yet is still a warm shape the player can see coming - the dark is the cave's mood, not a way to
// hide what is about to hit the bat.
const val CAVE_AMBIENT = 0xFF3C4258.toInt()

// How much of the light shows in the air as well as on the rock. Multiplying alone barely lights the
// cave's navy walls with a warm light, or anything with the bat's cyan but what is cyan already; much
// more than a breath of it and the light reads as fog.
const val CAVE_GLOW = 0.08f

// The bat's light: near white with a breath of cyan, so what it falls on keeps its own colors, and wide
// enough to light most of the frame's height and an imp a second before it arrives. It goes out over
// BAT_LIGHT_FADE_SECONDS as the bat falls, which is about how long the fall takes.
const val BAT_LIGHT_COLOR = 0xFFDCF8FF.toInt()
const val BAT_LIGHT_RADIUS = 160
const val BAT_LIGHT_FADE_SECONDS = 1.2f

// A shot's light, in its bolt's color lifted part of the way to white so that it lights more than
// what shares its hue. The bat's shots light a little further than anything fired back at it.
const val PLAYER_SHOT_LIGHT_RADIUS = 50
const val ENEMY_SHOT_LIGHT_RADIUS = 44
const val SHOT_LIGHT_INTENSITY = 0.85f
const val SHOT_LIGHT_PALENESS = 0.3f

// Where a shot is spent, its hit flares up: it lights up what was hit at the moment it is hit, the one
// moment a creature in the dark most needs to be seen. Wider than the shot's own light, so a hit lights
// the rock and the creatures around what it struck as well, and paler, as a flash is hotter than the
// bolt that made it. The bat's reach further, as its shots' do. It dies away with the spark.
const val PLAYER_IMPACT_LIGHT_RADIUS = 80
const val ENEMY_IMPACT_LIGHT_RADIUS = 70
const val IMPACT_LIGHT_PALENESS = 0.5f
const val IMPACT_LIGHT_SECONDS = IMPACT_FRAME_COUNT * IMPACT_FRAME_SECONDS

// A blast's light: fire-colored, as wide as BLAST_LIGHT_RADIUS for a blast drawn at its own size and
// in step with bigger ones, and dying with the fireball. Its radius is rounded to BLAST_LIGHT_STEP,
// since every radius of light is drawn once and kept, and boss blasts come in every size.
const val BLAST_LIGHT_COLOR = 0xFFFFB464.toInt()
const val BLAST_LIGHT_RADIUS = 56
const val BLAST_LIGHT_STEP = 8
const val BLAST_LIGHT_MAX_RADIUS = 160

// An elite gives off the light of its glow, so it is seen across the dark - it is a prize to chase.
const val ELITE_LIGHT_RADIUS = 60

// How strongly a light glints off the edge of a creature or a rock on the side it comes from; see
// Gloss. Just enough to show which way each light reaches it and to lift its outline off the dark,
// so the player reads where things are and where the light is - not so much that the cave looks wet.
// Rock a touch more than hide: the cave's limestone is damp.
const val CREATURE_SHINE = 0.7f
const val ROCK_SHINE = 0.75f

// The cave's boss is alight; what its light does through its fight is under "The Caco Imp".


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


// --- The desert --------------------------------------------------------------------------------
//
// Stage 3 is flown from noon to nightfall; what the sky does is in Daylight. Its enemies loop, leap
// out of the sand and grow their shells back - what each species does is in EnemySpecies, and these
// are the numbers underneath.

// How long a shell that grows back has to go untouched before it starts to: long enough that a
// player keeping up fire on a scarab never sees it happen, short enough that one left alone does.
const val SHIELD_REGROWTH_DELAY_SECONDS = 2.5f

// How much of something coming in under the sand shows above the bottom edge: its ridged back, and
// nothing else. Enough to see coming the whole way in, too little to be mistaken for flying.
const val BURROW_SHOWING = 11f


// --- Elites ------------------------------------------------------------------------------------
//
// Now and then an ordinary enemy arrives as an elite: wreathed in a glow of its own color, tougher,
// quicker on the trigger, and worth a good deal more. How often is per wave, in StageDesign; the
// colors are ElitePalette's. An elite is a prize to chase rather than a threat to avoid, so what it
// pays is set a little above what it costs.

// Its health against an ordinary one of its kind. At two and a half, an elite in the cave's second
// wave takes four of the bat's opening shots and one in its last takes eight: a chase across the
// frame rather than a tap.
const val ELITE_HIT_POINT_FACTOR = 2.5f

// The gap between its volleys against its kind's. One that carries no gun of its own is issued the
// waves' gun on spawn, so an elite always shoots - its shots in its own color are half of how it is
// told apart.
const val ELITE_FIRE_INTERVAL_FACTOR = 0.6f

// What killing one pays, in ordinary kills of its wave: experience and points both. It still counts
// as one kill to the streak, which is the player's skill rather than their prize.
const val ELITE_EXPERIENCE_FACTOR = 3
const val ELITE_SCORE_FACTOR = 3

// Its glow, in the terms of the bat's aura: as bright as the bat's ever gets, with the first tier of
// sparks and lightning in it. Full brightness because an enemy is smaller than the bat, and its sprite
// covers the brightest part of its own halo; anything less was a faint ring that a glance passed over.
const val ELITE_AURA_INTENSITY = 1f
const val ELITE_AURA_TIER = 1


// --- The Sand Wyrm -----------------------------------------------------------------------------
//
// Stage 3's boss: ten armored parts, head first, that breach out of the dunes in arcs and dive back
// under. Every part is its own entity, so it can be hit, rammed and flashed anywhere along its
// length; what lands on the body lands on the head, which carries the health.

// Every part of it is drawn in a square frame this size on its sheet, and so is the sand it throws
// up. The parts get smaller toward the tail inside their frames, rather than being scaled down,
// which would make their pixels uneven.
const val SAND_WYRM_FRAME = 48

// Its jaws working, two frames on a slow beat.
const val SAND_WYRM_HEAD_FRAMES = 2
const val SAND_WYRM_HEAD_FRAME_SECONDS = 0.16f

// The spray of sand a breach throws up, four frames from the sheet's seventh.
const val SAND_WYRM_PLUME_FRAME = 6
const val SAND_WYRM_PLUME_FRAMES = 4
const val SAND_WYRM_PLUME_FRAME_SECONDS = 0.07f

// How far apart its parts sit along the path the head has flown, in framebuffer pixels. A little
// under a part's own width, so the plates overlap and it reads as one body rather than a string of
// beads.
const val SAND_WYRM_SPACING = 19f

// How long it stays under between breaches, less once it is enraged; and how much of that the sand
// spends boiling where it is about to come up - the warning, which is never shorter than this.
const val SAND_WYRM_BURROW_SECONDS = 2.1f
const val SAND_WYRM_ENRAGED_BURROW_SECONDS = 1.3f
const val SAND_WYRM_TELL_SECONDS = 1.1f

// How fast it crosses the frame on a breach, before the leap's own forward push. Faster enraged.
const val SAND_WYRM_BREACH_SPEED = 1.5f
const val SAND_WYRM_ENRAGED_BREACH_SPEED = 1.9f

// The health fractions at which it changes phase: it starts calling up its brood at the first,
// and is enraged at the second. They are the marks its wounds show at too; see WOUND_MARKS.
const val SAND_WYRM_PHASE_2_AT = WOUNDED_AT
const val SAND_WYRM_PHASE_3_AT = BATTERED_AT

// What its head and its body deal on contact, as shares of the boss damage its stage scales to -
// 85 at stage 3, most of the bat's bar.
//
// The head is set against the Moth Queen's, about 64 against her 67. Her contact is a risk the
// player takes by closing in; the wyrm's is its attack, aimed at the player on every breach. It is
// the harder fight for its health, its armor and the way it moves, not for hitting harder.
//
// The body is lower again. It is two hundred pixels long and takes a second to pour past a point,
// and the bat's mercy window runs out twice in that, so one pass that is not dodged lands the head
// and two plates.
const val SAND_WYRM_HEAD_DAMAGE = 0.75f
const val SAND_WYRM_BODY_DAMAGE = 0.3f

// How much of a shot into one of its plates carries through to the head, which takes the whole of
// what hits it. The plates are armor: at full weight a late run's spread lands every shot of its fan
// on the body, a piercing shot lands on every plate it passes through, and the wyrm went down in
// seconds - faster than the jungle's Moth Queen against the same bat. At half, the head is worth
// aiming for and the body is still worth hitting.
const val SAND_WYRM_PLATE_SHARE = 0.5f

// What its spit deals, as a share of the head's contact damage: an aimed bolt a little under a
// third of the bat's bar and a ring bolt about a fifth, the Moth Queen's own.
const val SAND_WYRM_SPIT_DAMAGE = 0.45f
const val SAND_WYRM_RING_DAMAGE = 0.3f

// The colorway of shot.png its spit is drawn in: molten gold, like the glow in its throat.
const val SAND_WYRM_SHOT_VARIANT = 7

// Its health bar is pinned under the stage timer, BOSS_BAR_TOP: the head spends half the fight under
// the sand, and a bar that went with it would go too.


// --- The Caco Imp ------------------------------------------------------------------------------
//
// Stage 2's boss: the cave's crimson imp drawn three times over, and alight in the dark. It weaves on
// station and fires; at its first wound it puts its light out and prowls the dark between ambushes;
// at its second it blazes up for good and calls in its own kind. See CacoImpBrain.

// The health fractions at which it changes phase; the marks its wounds show at too, see WOUND_MARKS.
const val CACO_IMP_PHASE_2_AT = WOUNDED_AT
const val CACO_IMP_PHASE_3_AT = BATTERED_AT

// Its light. Through the first phase it smoulders: a crimson glow, breathing slowly about its
// strength, that lights the rock around it and shows the whole of it from the far side of the cave -
// the fight is fought across the cave, and a boss sunk in the dark at its far end could not be read.
const val CACO_IMP_LIGHT_COLOR = 0xFFFF6A50.toInt()
const val CACO_IMP_LIGHT_RADIUS = 130
const val CACO_IMP_SMOULDER_INTENSITY = 0.75f
const val CACO_IMP_BREATH = 0.1f
const val CACO_IMP_BREATHS_PER_SECOND = 0.6f

// Lights out, its second phase, played over and over: its light dies away over the douse, it glides
// through the dark to a station of its own choosing at the prowl's speed and lurks there unlit, and
// then it flares - the one warning of where it has got to, the light coming up to more than its
// smoulder - fires on the flare, and burns there, lit, before it puts its light out again.
//
// The prowl, at about 90 pixels a second, is the pace its imps fly at and a fifth of the bat's top
// speed: the dark is where it hides, not where it hunts.
const val CACO_IMP_DOUSE_SECONDS = 0.6f
const val CACO_IMP_PROWL_SPEED = 1.7f
const val CACO_IMP_LURK_SECONDS = 0.6f
const val CACO_IMP_FLARE_SECONDS = 0.5f
const val CACO_IMP_FLARE_INTENSITY = 1f
const val CACO_IMP_BURN_SECONDS = 1.8f

// Where it may settle in the dark: no further left than this fraction of the frame, so it stays on
// the side hostiles come from, at least this far from the bat between their centers, so it never
// settles on top of it, and at least this far from where it was, so every prowl is a real move.
const val CACO_IMP_PROWL_LEFTMOST = 0.35f
const val CACO_IMP_PROWL_BAT_CLEARANCE = 170f
const val CACO_IMP_PROWL_LEAST_MOVE = 110f

// Ablaze, its last phase: a hotter, wider light, guttering like a fire; a quicker weave; and two of
// its own kind called in every few seconds.
const val CACO_IMP_BLAZE_COLOR = 0xFFFF9A4C.toInt()
const val CACO_IMP_BLAZE_RADIUS = 170
const val CACO_IMP_BLAZE_INTENSITY = 0.95f
const val CACO_IMP_FLICKER = 0.15f
const val CACO_IMP_BLAZE_TEMPO = 1.6f
const val CACO_IMP_SUMMON_SECONDS = 8f

// What one of its bolts deals, as a share of its contact damage - about 67 at stage 2, two thirds of
// the bat's bar - set as the Moth Queen's are: a bolt aimed at the player a little under a third of
// the bar, one of a ring about a fifth.
const val CACO_IMP_BOLT_DAMAGE = 0.45f
const val CACO_IMP_RING_DAMAGE = 0.3f


// --- The lagoon --------------------------------------------------------------------------------
//
// Stage 4 is flown from night to noon across a bay of limestone islands, toward a temple standing
// in the water; what the sky does is in Daybreak. Its enemies school, scuttle, throw rings of
// spines, and come at the bat from behind - what each species does is in EnemySpecies, and these are
// the numbers underneath.

// Where something coming from behind leaps from, as a fraction of the frame's width, picked per shark
// from this range: behind where the bat usually flies, so the arc it throws forward tops out about
// where the bat is.
const val BEHIND_HOLD_X_MIN_FRACTION = 0.06f
const val BEHIND_HOLD_X_MAX_FRACTION = 0.28f


// --- The Naga ----------------------------------------------------------------------------------
//
// Stage 4's boss, and the longest fight in the game: a hooded serpent of a head and twelve parts that
// rears up out of the water, sways, spits and strikes at the bat, and dives to come up somewhere
// else. Five phases rather than three, a fifth of its health apiece; see NagaBrain. Like the Sand
// Wyrm, every part is its own entity, and what lands on the body lands on the head, which carries
// the health.

// Every part is drawn in a square frame this size on its sheet, and so is the water it throws up -
// bigger than the Sand Wyrm's, as it is.
const val NAGA_FRAME = 64

// Its jaws working, two frames on a slow beat.
const val NAGA_HEAD_FRAMES = 2
const val NAGA_HEAD_FRAME_SECONDS = 0.18f

// The water a rise throws up, four frames from the sheet's eighth.
const val NAGA_SPLASH_FRAME = 7
const val NAGA_SPLASH_FRAMES = 4
const val NAGA_SPLASH_FRAME_SECONDS = 0.07f

// How far apart its parts sit along its body: the spacing they keep along the path the head has swum,
// and the least and most they stretch to while it rears, where the body runs from the water to the
// head however far the head has gone.
const val NAGA_SPACING = 21f
const val NAGA_SPACING_LEAST = 15f
const val NAGA_SPACING_MOST = 27f

// The health fractions at which it enters its second, third, fourth and fifth phases: a fifth of its
// health apiece, where every other boss has three phases at its wounds' marks. Its wounds still show
// at WOUND_MARKS, as everything's do.
const val NAGA_PHASE_2_AT = 0.8f
const val NAGA_PHASE_3_AT = 0.6f
const val NAGA_PHASE_4_AT = 0.4f
const val NAGA_PHASE_5_AT = 0.2f

// How much more health it has than its stage's difficulty gives a boss: well over twice the Sand
// Wyrm's, for a fight of five phases rather than three.
const val NAGA_VITALITY = 1.8f

// What its head and its body deal on contact, as shares of the boss damage its stage scales to, set
// as the Sand Wyrm's are: its strike is aimed at the bat, so its head is its attack, and its body is
// a long thing to brush.
const val NAGA_HEAD_DAMAGE = 0.75f
const val NAGA_BODY_DAMAGE = 0.3f

// How much of a shot into its body carries through to the head, as the Sand Wyrm's plates do: half,
// so the hood is the place to aim and the body is still worth hitting.
const val NAGA_PLATE_SHARE = 0.5f

// What its spit deals, as a share of the head's contact damage: an aimed bolt about a third of the
// bat's bar, and a bolt of a ring about a fifth.
const val NAGA_SPIT_DAMAGE = 0.42f
const val NAGA_RING_DAMAGE = 0.28f

// The colorway of shot.png its spit is drawn in: neon pink, like the light in its hood.
const val NAGA_SHOT_VARIANT = 13

// The shield its hood raises as it enters its fourth and its fifth phase, as a fraction of its full
// health. Like the Moth Queen's, it does not recharge: a wall to break through, once a phase.
const val NAGA_SHIELD_FRACTION = 0.08f

// Its round, in seconds. Under the water between rises, less once it is enraged, and of that the time
// the water spends boiling where it is about to come up - the warning, never shorter than this. Then
// coming up and going back down, and how long it rears between them while it only spits.
const val NAGA_SUBMERGED_SECONDS = 1.9f
const val NAGA_ENRAGED_SUBMERGED_SECONDS = 1.2f
const val NAGA_TELL_SECONDS = 1.1f
const val NAGA_RISE_SECONDS = 0.8f
const val NAGA_SINK_SECONDS = 0.7f
const val NAGA_REAR_SECONDS = 3.4f

// Seconds between its spits while it rears, and between them once it is enraged.
const val NAGA_SPIT_SECONDS = 1.1f
const val NAGA_ENRAGED_SPIT_SECONDS = 0.8f

// Its strike, from its second phase: it draws its head back - the warning, shorter enraged - then
// strikes straight at where the bat was as the warning ended, at this many pixels a tick, as far as
// its reach from where it came up, and draws back to its station again.
const val NAGA_COIL_SECONDS = 0.55f
const val NAGA_ENRAGED_COIL_SECONDS = 0.4f
const val NAGA_STRIKE_SPEED = 8f
const val NAGA_STRIKE_REACH = 280f
const val NAGA_RECOIL_SECONDS = 0.5f

// Its swim, from its fourth phase: in from the right low over the water, its body following its
// head's path in humps that break the surface and dip under it, at this many pixels a tick across
// the frame, faster enraged.
const val NAGA_SWIM_SPEED = 2.3f
const val NAGA_ENRAGED_SWIM_SPEED = 2.9f
