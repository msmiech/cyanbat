package at.grueneis.game.framework

class Pool<T>(private val factory: PoolObjectFactory<T>, private val maxSize: Int) {
    interface PoolObjectFactory<T> {
        fun createObject(): T
    }

    private val freeObjects: MutableList<T>
    fun newObject(): T {
        return if (freeObjects.size == 0) factory.createObject() else freeObjects.removeAt(freeObjects.size - 1)
    }

    fun free(`object`: T) {
        if (freeObjects.size < maxSize) freeObjects.add(`object`)
    }

    init {
        freeObjects = ArrayList(maxSize)
    }
}