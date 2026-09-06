package at.smiech.engine.ecs

import at.smiech.engine.Pixmap
import at.smiech.engine.math.Rect
import at.smiech.engine.math.Vector2

/**
 * Basic components for game entities.
 */

data class TransformComponent(var rect: Rect) : Component

data class VelocityComponent(var velocity: Vector2) : Component

data class SpriteComponent(
    val pixmap: Pixmap,
    var srcX: Int = 0,
    var srcY: Int = 0,
    var srcWidth: Int = pixmap.width,
    var srcHeight: Int = pixmap.height
) : Component

data class AnimationComponent(
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int,
    val interval: Float,
    val isLooping: Boolean = true,
    var currentTime: Float = 0f,
    var currentFrame: Int = 0,
    var isFinished: Boolean = false
) : Component

data class CollisionComponent(
    val tolerance: Float = 0f,
    val group: CollisionGroup = CollisionGroup.OTHER
) : Component

enum class CollisionGroup {
    PLAYER, ENEMY, PLAYER_PROJECTILE, ENEMY_PROJECTILE, OBSTACLE, OTHER
}

data class HealthComponent(var lives: Int = 1, var alive: Boolean = true) : Component

data class LifetimeComponent(val removeIfOutOfBounds: Boolean = true) : Component

// Specialized components for behavior
data class PlayerControlComponent(var hitCooldown: Float = 0f) : Component

enum class EnemyMovementType { SINE, ZIGZAG, SCOUT }
data class EnemyBehaviorComponent(
    val type: EnemyMovementType,
    val initialY: Float,
    var elapsedTime: Float = 0f,
    var verticalDirection: Float = 1f,
    var nextDirectionChange: Float = 0f
) : Component

data class TrailComponent(var color: Int) : Component

data class BackgroundComponent(val isLooping: Boolean = true) : Component

data class ZIndexComponent(val zIndex: Int = 0) : Component
