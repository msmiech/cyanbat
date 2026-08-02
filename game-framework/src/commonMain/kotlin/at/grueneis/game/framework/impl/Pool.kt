package at.grueneis.game.framework.impl

class Pool<T>(private val factory: PoolObjectFactory<T>, private val maxSize: Int) {
    interface PoolObjectFactory<T> {
        fun createObject(): T
    }

    private val freeObjects: MutableList<T> = ArrayList(maxSize)
    fun newObject(): T {
        return if (freeObjects.isEmpty()) factory.createObject() else freeObjects.removeAt(freeObjects.size - 1)
    }

    fun free(obj: T) {
        if (freeObjects.size < maxSize) freeObjects.add(obj)
    }
}