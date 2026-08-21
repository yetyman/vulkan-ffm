package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.util.ObjectPool;

import java.lang.foreign.MemorySegment;

/**
 * Pooled, zero-allocation {@link BufferWriteScope} for the deferred-mirror write path.
 *
 * <p>Instances live in a static thread-local {@link ObjectPool}. All state is passed via
 * {@link #configure} — the scope holds no permanent references to any buffer or strategy.
 * On {@link #close()}: marks the written range dirty, then returns itself to the originating
 * pool via a backreference set at acquire time.
 */
final class DeferredMirrorWriteScope implements BufferWriteScope {

    private static final ThreadLocal<ObjectPool<DeferredMirrorWriteScope>> POOL =
            ThreadLocal.withInitial(() -> {
                ObjectPool<DeferredMirrorWriteScope> p = new ObjectPool<>(8,
                        DeferredMirrorWriteScope::new, null);
                p.preallocate(4);
                return p;
            });

    static DeferredMirrorWriteScope acquire(MemorySegment segment, long offset, long size, DirtyStrategy dirtyStrategy) {
        ObjectPool<DeferredMirrorWriteScope> pool = POOL.get();
        DeferredMirrorWriteScope scope = pool.acquire();
        scope.configure(segment, offset, size, dirtyStrategy, pool);
        return scope;
    }

    private MemorySegment segment;
    private long offset;
    private long size;
    private DirtyStrategy dirtyStrategy;
    private ObjectPool<DeferredMirrorWriteScope> originPool;
    private boolean closed;

    DeferredMirrorWriteScope() {
    }

    private void configure(MemorySegment segment, long offset, long size,
                           DirtyStrategy dirtyStrategy, ObjectPool<DeferredMirrorWriteScope> originPool) {
        this.segment = segment;
        this.offset = offset;
        this.size = size;
        this.dirtyStrategy = dirtyStrategy;
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
    public GpuCompletion completion() {
        return GpuCompletion.completed();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        dirtyStrategy.markDirty(offset, size);
        segment = null;
        dirtyStrategy = null;
        originPool.release(this);
        originPool = null;
    }
}
