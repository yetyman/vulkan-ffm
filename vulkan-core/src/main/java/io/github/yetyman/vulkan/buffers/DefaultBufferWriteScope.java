package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;
import java.util.function.Supplier;

/**
 * Standard {@link BufferWriteScope}: a segment plus a commit action run once on close.
 */
final class DefaultBufferWriteScope implements BufferWriteScope {

    private final MemorySegment segment;
    private final long offset;
    private final long size;
    private final Supplier<GpuCompletion> onCommit;

    private GpuCompletion completion = GpuCompletion.completed();
    private boolean closed = false;

    DefaultBufferWriteScope(MemorySegment segment, long offset, long size, Supplier<GpuCompletion> onCommit) {
        this.segment = segment;
        this.offset = offset;
        this.size = size;
        this.onCommit = onCommit;
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
        return completion;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (onCommit != null) {
            GpuCompletion result = onCommit.get();
            if (result != null) completion = result;
        }
    }
}
