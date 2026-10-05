package at.smiech.cyanbat.desktop

import at.smiech.cyanbat.CyanBatEnvironment
import at.smiech.cyanbat.HighscoreStore
import at.smiech.cyanbat.StageUnlockStore
import at.smiech.cyanbat.data.AudioSettings
import at.smiech.cyanbat.desktop.recorder.RunProbe
import at.smiech.cyanbat.ecs.BossPartComponent
import at.smiech.cyanbat.ecs.ElitePalette
import at.smiech.cyanbat.resource.GameAssets
import at.smiech.cyanbat.resource.SoundEffect
import at.smiech.cyanbat.service.CacoImpBrain
import at.smiech.cyanbat.service.EnemyGun
import at.smiech.cyanbat.service.EnemySpecies
import at.smiech.cyanbat.service.EntityFactory
import at.smiech.cyanbat.service.StageProgression
import at.smiech.cyanbat.ui.game.GameScreen
import at.smiech.cyanbat.util.BAT_LIGHT_FADE_SECONDS
import at.smiech.cyanbat.util.BOSS_AFTERSHOCK_SECONDS
import at.smiech.cyanbat.util.BOSS_SPRITE_SCALE
import at.smiech.cyanbat.util.BURROW_SHOWING
import at.smiech.cyanbat.util.CACO_IMP_PROWL_BAT_CLEARANCE
import at.smiech.cyanbat.util.DAMAGE_PER_HIT
import at.smiech.cyanbat.util.ELITE_EXPERIENCE_FACTOR
import at.smiech.cyanbat.util.ELITE_SCORE_FACTOR
import at.smiech.cyanbat.util.FRAME_BUFFER_HEIGHT
import at.smiech.cyanbat.util.FRAME_BUFFER_WIDTH
import at.smiech.cyanbat.util.IMPACT_FRAME
import at.smiech.cyanbat.util.IMPACT_FRAME_COUNT
import at.smiech.cyanbat.util.IMPACT_LIGHT_SECONDS
import at.smiech.cyanbat.util.SAND_WYRM_PLATE_SHARE
import at.smiech.cyanbat.util.SHOT_FRAME_COUNT
import at.smiech.cyanbat.util.SHOT_FRAME_WIDTH
import at.smiech.cyanbat.util.STAGE_COMPLETE_ARMING_SECONDS
import at.smiech.cyanbat.util.STAGE_COMPLETE_DELAY_SECONDS
import at.smiech.cyanbat.util.TICK_INITIAL
import at.smiech.cyanbat.util.WOUND_ROWS
import at.smiech.engine.GameButton
import at.smiech.engine.GameLoop
import at.smiech.engine.Haptics
import at.smiech.engine.Sound
import at.smiech.engine.ecs.AuraComponent
import at.smiech.engine.ecs.CollisionComponent
import at.smiech.engine.ecs.CollisionGroup
import at.smiech.engine.ecs.EntityId
import at.smiech.engine.ecs.HealthComponent
import at.smiech.engine.ecs.LightComponent
import at.smiech.engine.ecs.OccluderComponent
import at.smiech.engine.ecs.PaceComponent
import at.smiech.engine.ecs.SpriteComponent
import at.smiech.engine.ecs.TransformComponent
import at.smiech.engine.ecs.WeaponComponent
import at.smiech.engine.math.Rect
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [GameScreen] itself, flown headless on the desktop's own host.
 *
 * The screen needs a live Game and a real asset set, which is why the rest of the game's logic is
 * tested apart from it. The recorder already hosts it this way, and its [RunProbe] is how these
 * tests read the parts of the run the screen keeps to itself.
 */
class GameScreenTest {

    /**
     * The boss goes down partway through a tick's collision pass, and the score moves on after
     * that: the kill itself is counted once the pass has hurt both sides. A record banked as the
     * boss died came out below the score the victory overlay shows it beside.
     */
    @Test
    fun `a stage won on a record banks the score the overlay shows`() {
        val game = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT)
        val assets = GameAssets.load(game.graphics, game.audio)
        val highscores = RecordingHighscores()
        val screen = GameScreen(
            game,
            CyanBatEnvironment(
                assets = assets,
                haptics = Haptics.None,
                highscores = highscores,
                stageUnlocks = StageUnlockStore.InMemory(),
                onExitToMenu = {},
                audioSettings = Silent,
            ),
            CAVE,
        )
        try {
            val probe = RunProbe(screen)
            val world = probe.world

            // Straight to the boss. The stage clock is the generator's, and the boss arrives the
            // moment it reads the boss's minute.
            screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
            val bossId = assertNotNull(screen.enmGen.bossId, "the boss should have arrived")

            // One hit from dead, with one of the bat's shots already inside it, so it goes down in
            // the first collision pass.
            world.getComponent(bossId, HealthComponent::class)!!.hitPoints = 1
            val boss = world.getComponent(bossId, TransformComponent::class)!!.rect
            val shot = assets.graphics.shot
            EntityFactory(world).createShot(
                x = boss.centerX - SHOT_FRAME_WIDTH / 2f,
                y = boss.centerY - shot.height / 2f,
                width = SHOT_FRAME_WIDTH.toFloat(),
                height = shot.height.toFloat(),
                pixmap = shot,
                isPlayer = true,
            )

            // The longest frame the loop hands out, which is two ticks: the one that wins the stage,
            // and one after it that must not move the score on again.
            screen.update(GameLoop.MAX_FRAME_DELTA_SECONDS)

            assertTrue(probe.stageComplete, "the boss should be down")
            assertEquals(probe.score, screen.highscore, "the overlay's highscore against its score")
            assertEquals(probe.score, highscores.saved[CAVE], "the record stored for the stage")
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    /**
     * The Sand Wyrm's body is ten entities, and only its head carries health: a shot into a plate
     * has to land on the head, or most of the fight's shots would be wasted - but only part of it,
     * because the plates are armor and the head is the place to aim.
     */
    @Test
    fun `a shot into a plate lands part of its damage on the wyrm, and into the head all of it`() = wyrmFight {
        val world = probe.world
        val health = world.getComponent(head, HealthComponent::class)!!

        // The tail: the part with the most empty frame around it, so a shot at its middle meets
        // nothing else of the body.
        val tail = world.query(BossPartComponent::class).maxBy {
            world.getComponent(it, CollisionComponent::class)!!.tolerance
        }
        val beforeTail = health.hitPoints
        shootInto(world.getComponent(tail, TransformComponent::class)!!.rect.centerY, at = tail)
        assertEquals((DAMAGE_PER_HIT * SAND_WYRM_PLATE_SHARE).roundToInt(), beforeTail - health.hitPoints)

        // High on the head, clear of the plate that hangs below it.
        val beforeHead = health.hitPoints
        shootInto(world.getComponent(head, TransformComponent::class)!!.rect.top + 12f, at = head)
        assertEquals(DAMAGE_PER_HIT, beforeHead - health.hitPoints)
    }

    /** One of the bat's shots, just short of [at]'s middle at the height [y], and a tick to land. */
    private fun WyrmFight.shootInto(y: Float, at: EntityId? = null) {
        val world = probe.world
        val target = at ?: world.query(BossPartComponent::class).first { it != head }
        val rect = world.getComponent(target, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        EntityFactory(world).createShot(
            x = rect.centerX - SHOT_FRAME_WIDTH,
            y = y - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(),
            height = shot.height.toFloat(),
            pixmap = shot,
            isPlayer = true,
        )
        screen.update(TICK_INITIAL * 1.5f)
    }

    /**
     * Its body sweeping through the bat is how it hits. Were the bat's own contact damage to land on
     * it for every tick of the overlap, every blow the wyrm landed would cost it a slice of its bar.
     */
    @Test
    fun `the bat flying into the Sand Wyrm hurts the bat and not the wyrm`() = wyrmFight {
        val world = probe.world
        val wyrm = world.getComponent(head, HealthComponent::class)!!
        val bat = world.getComponent(probe.batId, HealthComponent::class)!!
        val wyrmBefore = wyrm.hitPoints
        val batBefore = bat.hitPoints
        val batRect = world.getComponent(probe.batId, TransformComponent::class)!!.rect
        world.getComponent(head, TransformComponent::class)!!.rect =
            Rect.fromLTWH(batRect.left, batRect.top, batRect.width, batRect.height)

        screen.update(TICK_INITIAL * 1.5f)

        assertTrue(bat.hitPoints < batBefore, "the wyrm flew through the bat without hurting it")
        assertEquals(wyrmBefore, wyrm.hitPoints, "the wyrm was hurt by landing a blow")
    }

    /**
     * Every creature's sheet stacks it unhurt, wounded and battered, and a run draws one row of it -
     * the one its health calls for. Drawn whole, the bat would be three bats, one above the other.
     */
    @Test
    fun `the bat is drawn from the row its health calls for, and healing puts it back`() = flight(CAVE) {
        val world = probe.world
        val sprite = world.getComponent(probe.batId, SpriteComponent::class)!!
        val health = world.getComponent(probe.batId, HealthComponent::class)!!
        assertEquals(BAT_ROW, sprite.srcHeight, "the bat is drawn a row at a time")
        assertEquals(BAT_ROW.toFloat(), world.getComponent(probe.batId, TransformComponent::class)!!.rect.height)

        health.hitPoints = 50
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(BAT_ROW, sprite.srcY, "wounded")

        health.hitPoints = 20
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(2 * BAT_ROW, sprite.srcY, "battered")

        health.hitPoints = health.maxHitPoints
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(0, sprite.srcY, "healed")
    }

    /**
     * The cave's boss is an imp drawn three times its size: one row of the imps' sheet magnified,
     * not the sheet's three rows - and its box sized off that one row, or the fight changes shape.
     */
    @Test
    fun `the cave's boss is one imp drawn large, and wears its wounds`() = flight(CAVE) {
        screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
        val boss = assertNotNull(screen.enmGen.bossId, "the boss should have arrived")
        val world = probe.world
        val sprite = world.getComponent(boss, SpriteComponent::class)!!
        assertEquals(IMP_ROW, sprite.srcHeight)
        assertEquals(IMP_ROW * BOSS_SPRITE_SCALE, world.getComponent(boss, TransformComponent::class)!!.rect.height)

        val health = world.getComponent(boss, HealthComponent::class)!!
        health.hitPoints = health.maxHitPoints / 4
        screen.update(TICK_INITIAL * 1.5f)

        assertEquals(2 * IMP_ROW, sprite.srcY)
    }

    /**
     * A wounded enemy is a straggler: slower in everything it does, and slower to fire, the worse
     * it is hurt. Checked on the run's own wiring - the wound system's callback into the screen -
     * because that is the part a unit test of the engine cannot see.
     */
    @Test
    fun `a wounded enemy slows down and fires less, the worse it is hurt`() = flight(CAVE) {
        val world = probe.world
        val imp = EntityFactory(world).createEnemy(
            x = 400f, y = 100f, width = 28f, height = IMP_ROW.toFloat(),
            pixmap = assets.stage(CAVE).enemySheet, species = EnemySpecies.STRIKER, hitPoints = 100,
        )
        val health = world.getComponent(imp, HealthComponent::class)!!
        val pace = world.getComponent(imp, PaceComponent::class)!!
        assertEquals(PaceComponent(), pace, "an unhurt enemy goes at its own pace")

        health.hitPoints = 50
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(PaceComponent(motion = 0.8f, fire = 0.75f), pace, "wounded")

        health.hitPoints = 20
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(PaceComponent(motion = 0.6f, fire = 0.5f), pace, "battered")
    }

    /**
     * The bosses are special, and hard enough to reach: their wounds show, but never cost them a
     * step or a shot.
     */
    @Test
    fun `a battered boss keeps its pace`() = flight(CAVE) {
        screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
        val boss = assertNotNull(screen.enmGen.bossId, "the boss should have arrived")
        val world = probe.world
        val health = world.getComponent(boss, HealthComponent::class)!!
        val weapon = world.getComponent(boss, WeaponComponent::class)!!
        health.hitPoints = health.maxHitPoints / 4
        screen.update(TICK_INITIAL * 1.5f)
        val before = weapon.timeSinceLastShot

        screen.update(TICK_INITIAL)

        assertNull(world.getComponent(boss, PaceComponent::class), "the boss was given a pace to slow")
        assertEquals(TICK_INITIAL, weapon.timeSinceLastShot - before, 0.0001f, "its gun slowed down")
    }

    /**
     * Only the head has health to be wounded by; the plates are drawn from whatever row it is on,
     * so a battered head is never towing a pristine body.
     */
    @Test
    fun `the Sand Wyrm is wounded along its whole body at once`() = wyrmFight {
        val world = probe.world
        val health = world.getComponent(head, HealthComponent::class)!!
        health.hitPoints = health.maxHitPoints / 2

        screen.update(TICK_INITIAL * 1.5f)

        val parts = world.query(BossPartComponent::class)
        assertEquals(10, parts.size, "the head and nine plates")
        for (part in parts) {
            val sprite = world.getComponent(part, SpriteComponent::class)!!
            assertEquals(WYRM_FRAME, sprite.srcHeight)
            assertEquals(WYRM_FRAME, sprite.srcY, "part $part is not drawn wounded")
        }
    }

    /**
     * An elite pays several kills' worth of points and of experience - checked on the run's own
     * wiring, the collision that kills it, against an ordinary imp shot down the same way.
     */
    @Test
    fun `an elite pays out several kills' worth of points and experience`() = flight(CAVE) {
        val (ordinaryPoints, ordinaryExperience) = shootDown(elite = null)
        val (elitePoints, eliteExperience) = shootDown(elite = ElitePalette.SCARLET)

        assertTrue(ordinaryPoints > 0 && ordinaryExperience > 0, "the ordinary imp paid nothing")
        assertEquals(ordinaryPoints * ELITE_SCORE_FACTOR, elitePoints, "points")
        assertEquals(ordinaryExperience * ELITE_EXPERIENCE_FACTOR, eliteExperience, "experience")
    }

    /**
     * What shooting down one imp - an [elite] one, or an ordinary one for null - adds to the run's
     * score and to its experience. One shot from dead, with that shot already inside it.
     */
    private fun Flight.shootDown(elite: ElitePalette?): Pair<Int, Int> {
        val imp = imp(x = 300f, y = 60f, elite = elite, hitPoints = 1)
        val score = probe.score
        val experience = probe.experience
        shoot(imp)
        return (probe.score - score) to (probe.experience - experience)
    }

    /** One of the bat's shots, already inside [target], and a tick for it to land. */
    private fun Flight.shoot(target: EntityId) {
        val rect = probe.world.getComponent(target, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        EntityFactory(probe.world).createShot(
            x = rect.centerX - SHOT_FRAME_WIDTH,
            y = rect.centerY - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(),
            height = shot.height.toFloat(),
            pixmap = shot,
            isPlayer = true,
        )
        screen.update(TICK_INITIAL * 1.5f)
    }

    /**
     * Brings the cave's boss in and shoots it down, one hit from dead. On screen, where a boss is
     * fought: it arrives from past the right edge.
     */
    private fun Flight.downBoss() {
        screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
        val boss = assertNotNull(screen.enmGen.bossId, "the boss should have arrived")
        val transform = probe.world.getComponent(boss, TransformComponent::class)!!
        transform.rect = Rect.fromLTWH(300f, 100f, transform.rect.width, transform.rect.height)
        probe.world.getComponent(boss, HealthComponent::class)!!.hitPoints = 1
        shoot(boss)
        assertTrue(probe.stageComplete, "the boss should be down")
    }

    /** Steps the run on through [seconds] of frames, as the game loop would. */
    private fun Flight.fly(seconds: Float) {
        repeat((seconds / FRAME_SECONDS).roundToInt()) { screen.update(FRAME_SECONDS) }
    }

    private fun Flight.hostiles(): List<EntityId> = probe.world.query(CollisionComponent::class).filter {
        probe.world.getComponent(it, CollisionComponent::class)?.group in
            setOf(CollisionGroup.ENEMY, CollisionGroup.ENEMY_PROJECTILE)
    }

    /**
     * The run plays on for a few seconds after its boss, and nothing may be left in them to shoot
     * the bat down: whatever the boss called in goes up with it, and the shots in the air go too.
     */
    @Test
    fun `everything hostile goes down with the boss`() = flight(CAVE) {
        val straggler = imp(x = 300f, y = 60f, elite = null)
        val impRect = probe.world.getComponent(straggler, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        EntityFactory(probe.world).createShot(
            x = impRect.left - SHOT_FRAME_WIDTH, y = impRect.centerY, width = SHOT_FRAME_WIDTH.toFloat(),
            height = shot.height.toFloat(), pixmap = shot, isPlayer = false,
        )

        downBoss()
        fly(0.1f)

        assertEquals(emptyList(), hostiles(), "left in the stage after its boss")
    }

    /** Should anything be left by the boss, a stage won is still won. */
    @Test
    fun `the bat cannot be hurt once the stage is won`() = flight(CAVE) {
        downBoss()
        val health = probe.world.getComponent(probe.batId, HealthComponent::class)!!
        val before = health.hitPoints
        val bat = probe.world.getComponent(probe.batId, TransformComponent::class)!!.rect
        imp(x = bat.left, y = bat.top, elite = null)

        fly(0.5f)

        assertEquals(before, health.hitPoints)
    }

    /**
     * The boss is seen going up, and the fanfare heard coming in, before the overlay takes the
     * player on: a press made while it plays out is not them asking to leave.
     */
    @Test
    fun `the overlay waits for the boss's fall to play out`() = flight(CAVE) {
        downBoss()
        assertEquals("THE CACO IMP FALLS", probe.banner)
        val blasts = { probe.world.query(SpriteComponent::class).count {
            probe.world.getComponent(it, SpriteComponent::class)?.pixmap === assets.graphics.explosion
        } }
        val first = BOSS_AFTERSHOCK_SECONDS.first()
        fly(first + 0.05f)
        assertTrue(blasts() >= 2, "the wreck did not go up again: ${blasts()} blasts")

        game.controlHandler.onButtonPress(GameButton.CONFIRM)
        fly(STAGE_COMPLETE_DELAY_SECONDS + STAGE_COMPLETE_ARMING_SECONDS + 0.1f - (first + 0.05f))
        assertNull(game.currentScreen, "a press made before the overlay was up took the player on")
        assertNull(probe.banner, "left under the overlay")

        game.controlHandler.onButtonPress(GameButton.CONFIRM)
        screen.update(FRAME_SECONDS)
        val next = assertNotNull(game.currentScreen as? GameScreen, "the overlay did not take the player on")
        try {
            assertEquals(CAVE + 1, next.currentStage.id)
        } finally {
            next.dispose()
        }
    }

    /**
     * A fight heard through the run's own wiring: a shot landing, a kill, the bat taking a blow, and
     * the boss going down - which, with all it takes with it, is one sound and not a crowd of them.
     */
    @Test
    fun `the fight is heard`() {
        val heard = mutableListOf<SoundEffect>()
        val sounds = SoundEffect.entries.associateWith { effect ->
            object : Sound {
                override fun play(volume: Float) {
                    heard += effect
                }

                override fun dispose() = Unit
            }
        }
        // The guns are left out: the bat's fires on its own clock, and so might the imps'.
        val fight = { heard.filterNot { it == SoundEffect.SHOT || it == SoundEffect.ENEMY_SHOT } }

        flight(CAVE, sounds) {
            val imp = imp(x = 300f, y = 60f, elite = null)
            shoot(imp)
            assertEquals(listOf(SoundEffect.HIT), fight(), "a shot landing")

            probe.world.getComponent(imp, HealthComponent::class)!!.hitPoints = 1
            shoot(imp)
            assertEquals(listOf(SoundEffect.HIT, SoundEffect.ENEMY_DEATH), fight(), "a kill")

            val bat = probe.world.getComponent(probe.batId, TransformComponent::class)!!.rect
            val shot = assets.graphics.shot
            EntityFactory(probe.world).createShot(
                x = bat.centerX, y = bat.centerY, width = SHOT_FRAME_WIDTH.toFloat(),
                height = shot.height.toFloat(), pixmap = shot, isPlayer = false,
            )
            screen.update(TICK_INITIAL * 1.5f)
            assertEquals(SoundEffect.BAT_HIT, fight().last(), "the bat hit")

            heard.clear()
            imp(x = 300f, y = 200f, elite = null)
            downBoss()
            fly(0.1f)
            assertEquals(listOf(SoundEffect.BOSS_DEATH), fight(), "the boss and its escort going down")
        }
    }

    @Test
    fun `an elite fires in the colors of its glow`() = flight(CAVE) {
        val elite = imp(x = 300f, y = 60f, elite = ElitePalette.VENOM, gun = EnemySpecies.ISSUED_GUN)
        val weapon = probe.world.getComponent(elite, WeaponComponent::class)!!
        weapon.timeSinceLastShot = weapon.interval

        screen.update(TICK_INITIAL * 1.5f)

        val shot = enemyShots().single()
        assertEquals(
            ElitePalette.VENOM.shotVariant * SHOT_FRAME_WIDTH * SHOT_FRAME_COUNT,
            probe.world.getComponent(shot, SpriteComponent::class)!!.baseSrcX,
        )
    }

    /**
     * Fire from under the sand would come from where nothing can be seen, and nothing would have
     * warned the player of it. An elite wyrmling is the first thing that cruises in down there armed.
     */
    @Test
    fun `an armed enemy under the sand holds its fire until it comes up`() = flight(DESERT) {
        val world = probe.world
        val sheet = assets.stage(DESERT).enemySheet
        val height = (sheet.height / WOUND_ROWS).toFloat()
        val wyrmling = EntityFactory(world).createEnemy(
            x = 300f, y = FRAME_BUFFER_HEIGHT - BURROW_SHOWING, width = 28f, height = height, pixmap = sheet,
            species = EnemySpecies.WYRMLING, gun = EnemySpecies.ISSUED_GUN, elite = ElitePalette.EMBER,
        )
        val weapon = world.getComponent(wyrmling, WeaponComponent::class)!!

        weapon.timeSinceLastShot = weapon.interval
        screen.update(TICK_INITIAL * 1.5f)
        assertTrue(enemyShots().isEmpty(), "it fired from under the sand")

        world.getComponent(wyrmling, TransformComponent::class)!!.rect = Rect.fromLTWH(300f, 150f, 28f, height)
        weapon.timeSinceLastShot = weapon.interval
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(1, enemyShots().size, "it held its fire once it was up")
    }

    /**
     * The cave's scenery is a sprite like the rest, and it used to be drawn in one pass with them -
     * after the halos, and over every one of them, the bat's included. Read off the drawn frame,
     * because a draw call that is made and then painted over is exactly what a recorded one misses.
     */
    @Test
    fun `a halo shows over the cave's scenery`() = flight(CAVE) {
        val elite = imp(x = 380f, y = 220f, elite = ElitePalette.SCARLET)
        val aura = probe.world.getComponent(elite, AuraComponent::class)!!
        // A few pixels above the imp, inside its glow, where there is nothing but scenery without it.
        val x = 394
        val y = 216

        screen.present(0f)
        val glowing = game.capture()[y * game.frameBufferWidth + x]
        aura.intensity = 0f
        aura.tier = 0
        screen.present(0f)
        val dark = game.capture()[y * game.frameBufferWidth + x]

        val red = { rgb: Int -> (rgb shr 16) and 0xFF }
        assertTrue(
            red(glowing) > red(dark) + 20,
            "the scarlet glow did not show: #%06X with it, #%06X without".format(glowing and 0xFFFFFF, dark and 0xFFFFFF),
        )
    }

    /**
     * The cave is flown in the dark, by the light the bat carries: the rock beside the bat is lit, and
     * the same rock across the cave is not. Read off the frame in the flat band of rock across the
     * middle of the cave, above the stage's name, before anything has arrived to stand in the way.
     */
    @Test
    fun `the cave is dark but for the light the bat carries`() = flight(CAVE) {
        placeBat()
        screen.update(TICK_INITIAL * 1.5f)
        screen.present(0f)
        val frame = game.capture()

        val beside = frame[ROCK_ROW * FRAME_BUFFER_WIDTH + BAT_MIDDLE_X - 60]
        val across = frame[ROCK_ROW * FRAME_BUFFER_WIDTH + 600]
        assertTrue(
            brightness(beside) > brightness(across) + 30,
            "beside the bat #%06X, across the cave #%06X".format(beside and 0xFFFFFF, across and 0xFFFFFF),
        )
    }

    /**
     * An imp in the bat's light throws its shadow away from the bat: the rock just behind it is in the
     * dark, where the rock as far from the bat on its other side is lit.
     */
    @Test
    fun `an imp throws a shadow away from the bat`() = flight(CAVE) {
        placeBat()
        EntityFactory(probe.world, lit = true).createEnemy(
            x = 248f, y = ROCK_ROW - IMP_ROW / 2f, width = 28f, height = IMP_ROW.toFloat(),
            pixmap = assets.stage(CAVE).enemySheet, species = EnemySpecies.SCOUT, hitPoints = 1000,
        )
        screen.update(TICK_INITIAL * 1.5f)
        screen.present(0f)
        val frame = game.capture()

        val behind = frame[ROCK_ROW * FRAME_BUFFER_WIDTH + 300]
        val opposite = frame[ROCK_ROW * FRAME_BUFFER_WIDTH + 2 * BAT_MIDDLE_X - 300]
        assertTrue(
            brightness(opposite) > brightness(behind) + 20,
            "behind the imp #%06X, as far off on the other side #%06X"
                .format(behind and 0xFFFFFF, opposite and 0xFFFFFF),
        )
    }

    /**
     * A spent shot leaves its hit at its nose, on the edge of what it struck, and in the dark the hit
     * flares up wider than the shot's own light. Both go once the spark has played.
     */
    @Test
    fun `a spent shot leaves its hit, which flares up in the dark and goes by itself`() = flight(CAVE) {
        holdFire()
        val lit = EntityFactory(probe.world, lit = true)
        val imp = lit.createEnemy(
            x = 400f, y = 60f, width = 28f, height = IMP_ROW.toFloat(),
            pixmap = assets.stage(CAVE).enemySheet, species = EnemySpecies.SCOUT, hitPoints = 1000,
        )
        val rect = probe.world.getComponent(imp, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        val shotId = lit.createShot(
            x = rect.centerX - SHOT_FRAME_WIDTH / 2f, y = rect.centerY - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(), height = shot.height.toFloat(), pixmap = shot, isPlayer = true,
        )
        val shotLight = probe.world.getComponent(shotId, LightComponent::class)!!.radius

        screen.update(TICK_INITIAL * 1.5f)
        val hit = hits().single()
        val light = assertNotNull(probe.world.getComponent(hit, LightComponent::class), "a hit in the dark is a light")
        assertTrue(light.radius > shotLight, "the hit's light reaches ${light.radius}, the shot's $shotLight")
        val at = probe.world.getComponent(hit, TransformComponent::class)!!.rect
        assertTrue(at.centerX > rect.centerX, "the hit went off at ${at.centerX}, short of the shot's nose")
        assertEquals(rect.centerY, at.centerY, 1f)

        fly(IMPACT_LIGHT_SECONDS + 0.05f)
        assertTrue(hits().isEmpty(), "the hit outlived its spark")
    }

    /** By day a hit is the spark alone, in the colors of the shot that made it. */
    @Test
    fun `a spent shot leaves its hit in daylight, unlit and in its own colors`() = flight(JUNGLE) {
        holdFire()
        val imp = imp(x = 400f, y = 60f, elite = null, hitPoints = 1000)
        val rect = probe.world.getComponent(imp, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        EntityFactory(probe.world).createShot(
            x = rect.centerX - SHOT_FRAME_WIDTH / 2f, y = rect.centerY - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(), height = shot.height.toFloat(), pixmap = shot, isPlayer = true,
            variant = ElitePalette.VENOM.shotVariant,
        )

        screen.update(TICK_INITIAL * 1.5f)
        val hit = hits().single()
        assertNull(probe.world.getComponent(hit, LightComponent::class))
        assertEquals(
            ElitePalette.VENOM.shotVariant * IMPACT_FRAME * IMPACT_FRAME_COUNT,
            probe.world.getComponent(hit, SpriteComponent::class)!!.baseSrcX,
        )
    }

    /** A piercing shot goes through what it pierces, but it struck it: it leaves its hit there. */
    @Test
    fun `a piercing shot leaves its hit in what it goes through`() = flight(CAVE) {
        holdFire()
        val imp = imp(x = 400f, y = 60f, elite = null, hitPoints = 1000)
        val rect = probe.world.getComponent(imp, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        val shotId = EntityFactory(probe.world).createShot(
            x = rect.centerX - SHOT_FRAME_WIDTH, y = rect.centerY - shot.height / 2f,
            width = SHOT_FRAME_WIDTH.toFloat(), height = shot.height.toFloat(), pixmap = shot, isPlayer = true,
            pierce = 1,
        )

        screen.update(TICK_INITIAL * 1.5f)
        assertEquals(1, hits().size, "the pierced imp's hit")
        assertTrue(probe.world.getComponent(shotId, HealthComponent::class)!!.alive, "the shot should fly on")
    }

    @Test
    fun `the bat's light goes out as it falls`() = flight(CAVE) {
        val world = probe.world
        val light = { world.getComponent(probe.batId, LightComponent::class)!! }
        assertEquals(1f, light().strength)

        world.getComponent(probe.batId, HealthComponent::class)!!.hitPoints = 1
        val bat = world.getComponent(probe.batId, TransformComponent::class)!!.rect
        val shot = assets.graphics.shot
        EntityFactory(world).createShot(
            x = bat.centerX, y = bat.centerY, width = SHOT_FRAME_WIDTH.toFloat(),
            height = shot.height.toFloat(), pixmap = shot, isPlayer = false,
        )
        screen.update(TICK_INITIAL * 1.5f)
        assertFalse(world.getComponent(probe.batId, HealthComponent::class)!!.alive, "the bat should be dead")
        assertTrue(light().strength in 0.5f..<1f, "the light, as the bat starts to fall: ${light().strength}")

        fly(BAT_LIGHT_FADE_SECONDS)
        assertEquals(0f, light().strength)
    }

    /** Only the cave is dark: the jungle's bat carries no light, and nothing in it throws a shadow. */
    @Test
    fun `the jungle is flown in daylight`() = flight(JUNGLE) {
        fly(2f)
        assertNull(probe.world.getComponent(probe.batId, LightComponent::class))
        assertTrue(probe.world.query(LightComponent::class).isEmpty(), "something in the jungle gives off light")
        assertTrue(probe.world.query(OccluderComponent::class).isEmpty(), "something in the jungle throws a shadow")
    }

    /**
     * The Caco Imp is alight, and lit by it; with its light out it is a shape in the dark. Read off
     * the frame, over the imp's own box, with it put back in the same place both times - and with the
     * bat's gun held, since every bolt it fires is a light of its own.
     */
    @Test
    fun `the Caco Imp is seen by its own light, and hides in the dark with it out`() = impFight {
        fun impBrightness(): Double {
            imp().rect = STATION
            placeBat()
            screen.present(0f)
            val frame = game.capture()
            var total = 0L
            for (y in STATION.top.toInt() until STATION.bottom.toInt()) {
                for (x in STATION.left.toInt() until STATION.right.toInt()) {
                    total += brightness(frame[y * FRAME_BUFFER_WIDTH + x])
                }
            }
            return total.toDouble() / (STATION.width * STATION.height)
        }

        screen.update(TICK_INITIAL * 1.5f)
        val lit = impBrightness()

        wound(0.6f)
        flyUntil { brain.dark }
        assertEquals(0f, probe.world.getComponent(boss, LightComponent::class)!!.intensity)
        val dark = impBrightness()

        // Wide of what was measured - about two thirds brighter lit, on Windows and on Linux alike -
        // since the box takes in the rock behind the imp as well as the imp, and the platforms' Skia
        // builds put a frame read on the CPU a few steps apart.
        val measured = "lit %.1f against dark %.1f, of 765".format(lit, dark)
        assertTrue(lit > dark * 1.35, measured)
        assertTrue(lit - dark > 25.0, measured)
        assertTrue(dark > 0.0, "unlit, it is still there to be seen")
    }

    @Test
    fun `the Caco Imp announces each phase of its fight`() = impFight {
        wound(0.6f)
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals("LIGHTS OUT", probe.banner)

        wound(0.3f)
        screen.update(TICK_INITIAL * 1.5f)
        assertEquals("THE IMP BLAZES", probe.banner)
    }

    /**
     * Its brain is what moves it in the dark, through the run's own systems: to a station clear of the
     * bat, which in a run is wherever the bat happens to be.
     */
    @Test
    fun `the Caco Imp prowls to a new station in the dark, clear of the bat`() = impFight {
        wound(0.6f)
        flyUntil { brain.prowl == CacoImpBrain.Prowl.PROWL }
        val start = imp().rect
        flyUntil { brain.prowl == CacoImpBrain.Prowl.LURK }

        val now = imp().rect
        assertEquals(brain.stationX, now.left, 1f)
        assertEquals(brain.stationY, now.top, 1f)
        assertTrue(hypot(now.left - start.left, now.top - start.top) > 20f, "it hardly moved")
        val bat = probe.world.getComponent(probe.batId, TransformComponent::class)!!.rect
        assertTrue(hypot(now.centerX - bat.centerX, now.centerY - bat.centerY) >= CACO_IMP_PROWL_BAT_CLEARANCE)
    }

    /** Puts the bat at the same place every time, its middle on [BAT_MIDDLE_X] and [ROCK_ROW]. */
    private fun Flight.placeBat() {
        probe.world.getComponent(probe.batId, TransformComponent::class)!!.rect =
            Rect.fromLTWH(BAT_MIDDLE_X - 22.5f, ROCK_ROW - BAT_ROW / 2f, 45f, BAT_ROW.toFloat())
    }

    private fun brightness(argb: Int): Int = ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)

    /** One of the cave's scouts at ([x], [y]), as an [elite] or an ordinary one for null. */
    private fun Flight.imp(
        x: Float,
        y: Float,
        elite: ElitePalette?,
        hitPoints: Int = 100,
        gun: EnemyGun? = null,
    ): EntityId = EntityFactory(probe.world).createEnemy(
        x = x, y = y, width = 28f, height = IMP_ROW.toFloat(), pixmap = assets.stage(CAVE).enemySheet,
        species = EnemySpecies.SCOUT, hitPoints = hitPoints, gun = gun, elite = elite,
    )

    /** Every hit on screen: what shots have left where they struck. */
    private fun Flight.hits(): List<EntityId> = probe.world.query(SpriteComponent::class).filter {
        probe.world.getComponent(it, SpriteComponent::class)!!.pixmap == assets.graphics.impact
    }

    /** Holds the bat's gun, so the only shots in the run are the ones a test puts there. */
    private fun Flight.holdFire() {
        probe.world.getComponent(probe.batId, WeaponComponent::class)!!.interval = Float.MAX_VALUE
    }

    private fun Flight.enemyShots(): List<EntityId> = probe.world.query(CollisionComponent::class).filter {
        probe.world.getComponent(it, CollisionComponent::class)?.group == CollisionGroup.ENEMY_PROJECTILE
    }

    /**
     * A run of one stage: the screen, what the probe reads of it, the assets it was built from, and
     * the host that draws its frames.
     */
    private open class Flight(val screen: GameScreen, val probe: RunProbe, val assets: GameAssets, val game: DesktopGame)

    /**
     * A run of [stage] on the desktop's own host, disposed of when [test] is done with it: silent, or
     * with its effects played through [sounds] for a test that listens to it.
     */
    private fun flight(stage: Int, sounds: Map<SoundEffect, Sound>? = null, test: Flight.() -> Unit) {
        val game = DesktopGame(FRAME_BUFFER_WIDTH, FRAME_BUFFER_HEIGHT)
        val assets = GameAssets.load(game.graphics, game.audio)
        if (sounds != null) assets.audio.effects = sounds
        val screen = GameScreen(
            game,
            CyanBatEnvironment(
                assets = assets,
                haptics = Haptics.None,
                highscores = RecordingHighscores(),
                stageUnlocks = StageUnlockStore.InMemory(),
                onExitToMenu = {},
                audioSettings = if (sounds != null) SoundsOnly else Silent,
            ),
            stage,
        )
        try {
            Flight(screen, RunProbe(screen), assets, game).test()
        } finally {
            screen.dispose()
            game.audio.dispose()
        }
    }

    /** A cave run at its boss: a [Flight], the Caco Imp, and its brain. */
    private class ImpFight(flight: Flight, val boss: EntityId, val brain: CacoImpBrain) :
        Flight(flight.screen, flight.probe, flight.assets, flight.game) {
        fun imp(): TransformComponent = probe.world.getComponent(boss, TransformComponent::class)!!

        fun wound(fraction: Float) {
            val health = probe.world.getComponent(boss, HealthComponent::class)!!
            health.hitPoints = (health.maxHitPoints * fraction).toInt()
        }

        fun flyUntil(limitSeconds: Float = 20f, done: () -> Boolean) {
            var left = (limitSeconds / TICK_INITIAL).toInt()
            while (!done()) {
                assertTrue(left-- > 0, "it never came round")
                screen.update(TICK_INITIAL)
            }
        }
    }

    /** A cave run straight at its boss, on its station, with the bat's gun held. */
    private fun impFight(test: ImpFight.() -> Unit) = flight(CAVE) {
        probe.world.getComponent(probe.batId, WeaponComponent::class)!!.interval = Float.MAX_VALUE
        screen.enmGen.update(StageProgression.forStage(CAVE).bossTimeSeconds)
        val boss = assertNotNull(screen.enmGen.bossId, "the imp should have arrived")
        probe.world.getComponent(boss, TransformComponent::class)!!.rect = STATION
        ImpFight(this, boss, screen.enmGen.bossBrain as CacoImpBrain).test()
    }

    /** A desert run at its boss: a [Flight], and the wyrm's head. */
    private class WyrmFight(flight: Flight, val head: EntityId) :
        Flight(flight.screen, flight.probe, flight.assets, flight.game)

    /** A desert run straight at its boss, with the escort cleared away. */
    private fun wyrmFight(test: WyrmFight.() -> Unit) = flight(DESERT) {
        screen.enmGen.update(StageProgression.forStage(DESERT).bossTimeSeconds)
        val head = assertNotNull(screen.enmGen.bossId, "the wyrm should have arrived")
        val world = probe.world
        for (id in world.query(CollisionComponent::class)) {
            val group = world.getComponent(id, CollisionComponent::class)?.group
            if (group == CollisionGroup.ENEMY && !world.hasComponent(id, BossPartComponent::class)) world.removeEntity(id)
        }
        WyrmFight(this, head).test()
    }

    private companion object {
        const val JUNGLE = 1
        const val CAVE = 2
        const val DESERT = 3

        /** Where the Caco Imp holds station: at 62% of the frame across, centered up and down. */
        val STATION = Rect.fromLTWH(396.8f, 136.5f, 96f, 87f)

        /**
         * A row of the flat band of rock across the middle of the cave, clear of the stage's name, and
         * where the light tests put the middle of the bat on it.
         */
        const val ROCK_ROW = 140
        const val BAT_MIDDLE_X = 222

        /**
         * One row of each sheet, spelled out rather than read off the code, for the reason
         * `SpriteSheetTest` gives: a test that derived them the way the game does would agree with it
         * about a sheet that had changed underneath both.
         */
        const val BAT_ROW = 40
        const val IMP_ROW = 29
        const val WYRM_FRAME = 48

        /** A frame of a 60 Hz display, which is what the game loop hands a screen at a time. */
        const val FRAME_SECONDS = 1f / 60
    }
}

/** Keeps the highest value banked for each stage, the way every real store does. */
private class RecordingHighscores : HighscoreStore {
    val saved = mutableMapOf<Int, Int>()

    /**
     * Never emits, so the screen's read of the stored record never comes back - as a slow disk's
     * might not before a run is over. The screen makes that read on the Swing thread, and an
     * answer would race this test's thread for the screen's highscore.
     */
    override val byStage: Flow<Map<Int, Int>> = flow { awaitCancellation() }

    override fun saveAsync(stageId: Int, value: Int) {
        saved[stageId] = maxOf(saved[stageId] ?: 0, value)
    }
}

private object Silent : AudioSettings {
    override val musicEnabled = false
    override val soundsEnabled = false
}

/** No music, which no test listens to and every run would otherwise start a thread to play. */
private object SoundsOnly : AudioSettings {
    override val musicEnabled = false
    override val soundsEnabled = true
}
