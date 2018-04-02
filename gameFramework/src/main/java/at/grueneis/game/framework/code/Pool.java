package at.grueneis.game.framework.code;

import java.util.ArrayList;
import java.util.List;

public class Pool<T>
{
	public interface PoolObjectFactory<T>
	{
		public T createObject();
	}
	
	private final List<T> freeObjects;
	private final PoolObjectFactory<T> factory;
	private final int maxSize;
	
	public Pool(PoolObjectFactory<T> factory, int maxSize)
	{
		this.factory = factory;
		this.maxSize = maxSize;
		freeObjects = new ArrayList<T>(maxSize);
	}
	
	public T newObject()
	{
		if (freeObjects.isEmpty()) return factory.createObject();
		return freeObjects.remove(freeObjects.size() - 1);
	}
	
	public void free(T obj)
	{
		if ( freeObjects.size()<maxSize) freeObjects.add(obj);
	}
}
