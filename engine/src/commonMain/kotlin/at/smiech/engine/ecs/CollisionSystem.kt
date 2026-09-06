package at.smiech.engine.ecs

import at.smiech.engine.Input

class CollisionSystem(
    private val onCollision: (EntityId, EntityId) -> Unit
) : GameSystem() {
    override fun update(world: World, deltaTime: Float, input: Input?) {
        val collidables = world.query(TransformComponent::class, CollisionComponent::class)
        
        for (i in collidables.indices) {
            val id1 = collidables[i]
            val transform1 = world.getComponent(id1, TransformComponent::class)!!
            val collision1 = world.getComponent(id1, CollisionComponent::class)!!
            
            val rect1 = transform1.rect.inflate(-collision1.tolerance)
            
            for (j in i + 1 until collidables.size) {
                val id2 = collidables[j]
                val transform2 = world.getComponent(id2, TransformComponent::class)!!
                val collision2 = world.getComponent(id2, CollisionComponent::class)!!
                
                // Don't collide objects in the same group or friendly groups if needed
                if (shouldSkipCollision(collision1.group, collision2.group)) continue
                
                val rect2 = transform2.rect.inflate(-collision2.tolerance)
                
                if (rect1.intersects(rect2)) {
                    onCollision(id1, id2)
                }
            }
        }
    }

    private fun shouldSkipCollision(g1: CollisionGroup, g2: CollisionGroup): Boolean {
        if (g1 == g2) return true
        if (g1 == CollisionGroup.PLAYER && g2 == CollisionGroup.PLAYER_PROJECTILE) return true
        if (g2 == CollisionGroup.PLAYER && g1 == CollisionGroup.PLAYER_PROJECTILE) return true
        if (g1 == CollisionGroup.ENEMY && g2 == CollisionGroup.ENEMY_PROJECTILE) return true
        if (g2 == CollisionGroup.ENEMY && g1 == CollisionGroup.ENEMY_PROJECTILE) return true
        return false
    }
}
