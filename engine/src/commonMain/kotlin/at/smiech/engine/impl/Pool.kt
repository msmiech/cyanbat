package at.smiech.engine.impl

/**
 * A free list of reusable objects, so a per-frame stream such as touch events
 * allocates nothing once warm. Keeps at most [maxSize] freed objects; any beyond
 * that are left to the garbage collector.
 */
class Pool<T>(private val factory: PoolObjectFactory<T>, private val maxSize: Int) {
    /** Creates a fresh object when the pool has none free. */
    interface PoolObjectFactory<T> {
        fun createObject(): T
    }

    private val freeObjects: MutableList<T> = ArrayList(maxSize)

    /**
     * A freed object if there is one, otherwise a new one. Its fields hold whatever was last set.
     */
    fun newObject(): T {
        return if (freeObjects.isEmpty()) factory.createObject() else freeObjects.removeAt(
            freeObjects.size - 1
        )
    }

    /** Returns [obj] to the pool for [newObject] to hand out again. */
    fun free(obj: T) {
        if (freeObjects.size < maxSize) freeObjects.add(obj)
    }
}
