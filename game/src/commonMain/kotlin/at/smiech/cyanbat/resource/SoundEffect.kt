package at.smiech.cyanbat.resource

/**
 * Every sound effect a run plays: the file it is in, how loud it plays, and how soon it may play
 * again.
 *
 * The generated files are all written near full scale, so [volume] is where the balance is set -
 * between the effects, and against the music under them. Each was set by measuring the effect's
 * loudness (K-weighted, over its loudest tenth of a second) against the stages' music with its
 * bed, pulse, drive and lead up, which is how a fight in its middle sounds, and splitting the
 * difference between a full-range speaker and a phone's, which loses most of an explosion's boom.
 * The levels below are given against that music.
 *
 * [gapSeconds] is the least time between two plays of the effect; see
 * [at.smiech.cyanbat.ui.game.SoundBoard]. Zero for an effect that cannot pile up on itself.
 */
enum class SoundEffect(val file: String, val volume: Float, val gapSeconds: Float = 0f) {
    /**
     * The bat's gun, about 8 dB under the music: it plays once a second at the base cadence and
     * more than three times a second with Rapid Fire stacked, so it confirms the trigger rather
     * than announcing it. Played once a volley, however many shots are in it.
     */
    SHOT("shotFire.wav", 0.25f),

    /**
     * An enemy's volley, about 12 dB under the music and well under the bat's own gun: a screen
     * can be full of enemies firing, and the gun the player is operating has to stay the one they
     * hear. Held apart, because a swarm can fire on the same tick.
     */
    ENEMY_SHOT("enemyShot.wav", 0.2f, gapSeconds = 0.1f),

    /**
     * One of the bat's shots landing, a little over its gun. Held apart, because a fan of shots
     * into a swarm lands several on one tick.
     */
    HIT("shotHit.wav", 0.5f, gapSeconds = 0.05f),

    /** A shot spent on a shield's bubble, as loud as a hit that got through. */
    SHIELD_HIT("shieldHit.wav", 0.28f, gapSeconds = 0.06f),

    /** Something burning up, about as loud as the music: a kill is the payoff, and it should pop. */
    ENEMY_DEATH("enemyDeath.wav", 0.75f, gapSeconds = 0.06f),

    /** An obstacle broken, a little under a kill: it is scenery cleared, not something beaten. */
    OBSTACLE_SHATTER("rockShatter.wav", 0.85f, gapSeconds = 0.08f),

    /** The bat taking a blow, just over the music: the one sound the player cannot miss. */
    BAT_HIT("batHit.wav", 0.7f),

    /** The bat going down. Full volume: the stage's music has stopped for it. */
    BAT_DEATH("deathSound.mp3", 1f),

    /**
     * The stage's boss going down. Full volume, and alone: the stage's music stops dead for it,
     * and the victory's fanfare waits for its blasts to go off.
     */
    BOSS_DEATH("bossDeath.wav", 1f),

    /**
     * The swell the bat's aura lets out each time it crosses a tier; see
     * [at.smiech.engine.ecs.AuraComponent]. Deliberately low: it lands on the same beat as the
     * level up banner and the power-up dialog, over music that is still playing.
     */
    AURA_SURGE("auraSurge.wav", 0.35f),
}
