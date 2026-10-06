package at.smiech.cyanbat.resource

/**
 * Every sound effect a run plays: its file, how loud it plays, and how soon it may play again.
 *
 * The generated files are all near full scale, so [volume] sets the balance, between the effects
 * and against the music. Each was set by measuring the effect's loudness (K-weighted, over its
 * loudest tenth of a second) against a stage's music with bed, pulse, drive and lead up, as in the
 * middle of a fight, splitting the difference between a full-range speaker and a phone's. The
 * levels below are relative to that music.
 *
 * [gapSeconds] is the shortest time between two plays of the effect; see
 * [at.smiech.cyanbat.ui.game.SoundBoard]. Zero for an effect that cannot pile up.
 */
enum class SoundEffect(val file: String, val volume: Float, val gapSeconds: Float = 0f) {
    /**
     * The bat's gun, about 8 dB under the music: it plays once a second at the base cadence and
     * over three times a second with Rapid Fire stacked, so it confirms rather than announces.
     * Played once per volley, however many shots are in it.
     */
    SHOT("shotFire.wav", 0.25f),

    /**
     * An enemy's volley, about 12 dB under the music and well under the bat's gun, so the player's
     * own gun stays the one they hear. Spaced out, because a swarm can fire on the same tick.
     */
    ENEMY_SHOT("enemyShot.wav", 0.2f, gapSeconds = 0.1f),

    /**
     * One of the bat's shots landing, a little over its gun. Spaced out, because a fan of shots
     * into a swarm lands several on one tick.
     */
    HIT("shotHit.wav", 0.5f, gapSeconds = 0.05f),

    /** A shot spent on a shield's bubble, as loud as a hit that got through. */
    SHIELD_HIT("shieldHit.wav", 0.28f, gapSeconds = 0.06f),

    /** An enemy destroyed, about as loud as the music: a kill is the payoff. */
    ENEMY_DEATH("enemyDeath.wav", 0.75f, gapSeconds = 0.06f),

    /** An obstacle broken, a little under a kill. */
    OBSTACLE_SHATTER("rockShatter.wav", 0.85f, gapSeconds = 0.08f),

    /** The bat taking a blow, just over the music: the one sound the player cannot miss. */
    BAT_HIT("batHit.wav", 0.7f),

    /** The bat going down. Full volume: the stage's music has stopped for it. */
    BAT_DEATH("deathSound.mp3", 1f),

    /**
     * The stage's boss going down. Full volume and alone: the music stops dead for it, and the
     * victory fanfare waits for its blasts.
     */
    BOSS_DEATH("bossDeath.wav", 1f),

    /**
     * The swell of the bat's aura crossing a tier; see [at.smiech.engine.ecs.AuraComponent].
     * Deliberately low: it lands with the level-up banner and dialog, over music still playing.
     */
    AURA_SURGE("auraSurge.wav", 0.35f),

    /**
     * The bat's frost beam firing, between a hit and a kill, about 4 dB under the music: an event a
     * few seconds apart rather than the patter of the fight. It is all treble, so it survives a
     * phone's speaker.
     */
    FROST_BEAM("frostBeam.wav", 0.36f),
}
