package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;

/**
 * Standard {@link BufferReadScope}: a segment plus a release action run once on close.
 */
final class DefaultBufferReadScope implements BufferReadScope {

    private final MemorySegment segment;
    private final long offset;
    private final long size;
    private final Runnable onClose;

    private boolean closed = false;

    DefaultBufferReadScope(MemorySegment segment, long offset, long size, Runnable onClose) {
        this.segment = segment;
        this.offset = offset;
        this.size = size;
        this.onClose = onClose;
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
        if (onClose != null) onClose.run();
    }
}
