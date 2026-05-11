package io.github.yetyman.vulkan.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lightweight object pool for plain Java objects. Single-threaded, zero-overhead acquire/release.
 * No synchronization, no Set tracking, no ConcurrentLinkedQueue -- just an array stack.
 *
 * Use for hot-loop objects like BarrierBatch, temporary lists, maps, etc.
 */
public class ObjectPool<T> {

    private final Object[] pool;
    private final Supplier<T> factory;
    private final Consumer<T> reset;
    private int size;

    /**
     * @param capacity maximum pooled objects
     * @param factory creates a new instance when pool is empty
     * @param reset called on release to clear the object for reuse (may be null)
     */
    public ObjectPool(int capacity, Supplier<T> factory, Consumer<T> reset) {
        this.pool = new Object[capacity];
        this.factory = factory;
        this.reset = reset;
        this.size = 0;
    }

    /** Acquires an object from the pool, or creates one if empty */
    @SuppressWarnings("unchecked")
    public T acquire() {
        if (size > 0) {
            T obj = (T) pool[--size];
            pool[size] = null;
            return obj;
        }
        return factory.get();
    }

    /** Returns an object to the pool. Resets it if a reset function was provided. */
    public void release(T obj) {
        if (obj == null) return;
        if (reset != null) reset.accept(obj);
        if (size < pool.length) {
            pool[size++] = obj;
        }
        // else: pool full, object becomes garbage (no leak, just GC'd)
    }

    /** @return number of objects currently in the pool */
    public int available() { return size; }

    /** Pre-fills the pool to the given count */
    public void preallocate(int count) {
        int toCreate = Math.min(count, pool.length) - size;
        for (int i = 0; i < toCreate; i++) {
            pool[size++] = factory.get();
        }
    }
}
