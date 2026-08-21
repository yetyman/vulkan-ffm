package io.github.yetyman.vulkan.util;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lightweight object pool with thread-safe cross-thread returns via a lock-free backbuffer.
 *
 * <p>The pool has two tiers:
 * <ul>
 *   <li><b>Local pool</b> — a plain array stack, zero-overhead, accessed only by the owning thread.
 *       {@link #acquire()} pulls from here first.</li>
 *   <li><b>Backbuffer</b> — a lock-free {@link AtomicReferenceArray} that any thread may
 *       {@link #release(Object)} into. The owning thread drains this lazily into the local pool
 *       when the local pool is empty at acquire time.</li>
 * </ul>
 *
 * <p>This design allows objects to be returned from any thread (e.g. a scope acquired on thread A
 * but closed on thread B) without synchronization on the hot acquire path. The backbuffer is only
 * touched (drained) when the local pool is empty, keeping the common single-thread case at full
 * speed.
 *
 * <p>If both pools are empty, the factory creates a new instance. If the backbuffer is full on
 * release, the object is dropped (becomes GC garbage — no leak, just lost reuse opportunity).
 */
public class ObjectPool<T> {

    private final Object[] pool;
    private final Supplier<T> factory;
    private final Consumer<T> reset;
    private int size;

    // Lock-free backbuffer for cross-thread returns
    private final AtomicReferenceArray<T> backbuffer;
    private final AtomicInteger backbufferSize;

    /**
     * @param capacity maximum pooled objects (local pool capacity; backbuffer is same size)
     * @param factory creates a new instance when pool is empty
     * @param reset called on release to clear the object for reuse (may be null)
     */
    public ObjectPool(int capacity, Supplier<T> factory, Consumer<T> reset) {
        this.pool = new Object[capacity];
        this.factory = factory;
        this.reset = reset;
        this.size = 0;
        this.backbuffer = new AtomicReferenceArray<>(capacity);
        this.backbufferSize = new AtomicInteger(0);
    }

    /**
     * Acquires an object from the local pool. If empty, drains the backbuffer first.
     * If both are empty, creates a new instance via the factory.
     */
    @SuppressWarnings("unchecked")
    public T acquire() {
        if (size > 0) {
            T obj = (T) pool[--size];
            pool[size] = null;
            return obj;
        }
        // Local pool empty — drain backbuffer lazily
        drainBackbuffer();
        if (size > 0) {
            T obj = (T) pool[--size];
            pool[size] = null;
            return obj;
        }
        return factory.get();
    }

    /**
     * Returns an object to this pool. Safe to call from any thread.
     *
     * <p>If called from the owning thread (detected by the local pool not being full or
     * other heuristic), the object goes directly into the local pool. Otherwise it goes
     * into the lock-free backbuffer. If both are full, the object is dropped.
     *
     * <p>Note: Currently all releases go through the backbuffer for simplicity and correctness.
     * The owning thread drains on next acquire. This keeps release lock-free from any thread.
     */
    public void release(T obj) {
        if (obj == null) return;
        if (reset != null) reset.accept(obj);

        // Try to insert into the backbuffer (lock-free, any thread)
        int idx = backbufferSize.getAndIncrement();
        if (idx < backbuffer.length()) {
            backbuffer.set(idx, obj);
        } else {
            // Backbuffer full — correct the counter and drop the object
            backbufferSize.decrementAndGet();
        }
    }

    /** @return number of objects currently in the local pool (does not count backbuffer) */
    public int available() { return size; }

    /** Pre-fills the local pool to the given count */
    public void preallocate(int count) {
        int toCreate = Math.min(count, pool.length) - size;
        for (int i = 0; i < toCreate; i++) {
            pool[size++] = factory.get();
        }
    }

    /**
     * Drains all objects from the backbuffer into the local pool.
     * Only called by the owning thread (from acquire), so local pool access is safe.
     */
    private void drainBackbuffer() {
        int count = backbufferSize.getAndSet(0);
        count = Math.min(count, backbuffer.length());
        for (int i = 0; i < count && size < pool.length; i++) {
            T obj = backbuffer.getAndSet(i, null);
            if (obj != null) {
                pool[size++] = obj;
            }
        }
    }
}
