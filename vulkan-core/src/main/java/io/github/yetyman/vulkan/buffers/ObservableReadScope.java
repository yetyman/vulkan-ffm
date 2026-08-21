package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.util.ObjectPool;

import java.lang.foreign.MemorySegment;

/**
 * Pooled, zero-allocation {@link BufferReadScope} for the observable read path
 * (mirrored or inherent observability where close is a no-op).
 *
 * <p>Instances live in a static thread-local {@link ObjectPool}. Configured on acquire,
 * returned to the originating pool on close. No action is performed on close beyond
 * returning to the pool — the memory is owned by the observability and remains valid.
 */
final class ObservableReadScope implements BufferReadScope {

    private static final ThreadLocal<ObjectPool<ObservableReadScope>> POOL =
            ThreadLocal.withInitial(() -> {
                ObjectPool<ObservableReadScope> p = new ObjectPool<>(8,
                        ObservableReadScope::new, null);
                p.preallocate(4);
                return p;
            });

    static ObservableReadScope acquire(MemorySegment segment, long offset, long size) {
        ObjectPool<ObservableReadScope> pool = POOL.get();
        ObservableReadScope scope = pool.acquire();
        scope.configure(segment, offset, size, pool);
        return scope;
    }

    private MemorySegment segment;
    private long offset;
    private long size;
    private ObjectPool<ObservableReadScope> originPool;
    private boolean closed;

    ObservableReadScope() {
    }

    private void configure(MemorySegment segment, long offset, long size,
                           ObjectPool<ObservableReadScope> originPool) {
        this.segment = segment;
        this.offset = offset;
        this.size = size;
        this.originPool = originPool;
        this.closed = false;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public long offset() {
        return offset;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        segment = null;
        originPool.release(this);
        originPool = null;
    }
}
