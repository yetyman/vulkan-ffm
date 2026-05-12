package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.buffers.TransferCompletion;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Minimal ManagedBuffer adapter for graph-managed transient buffers.
 * The graph only needs handle() and size() for barrier tracking and descriptor binding.
 * Data transfer operations throw UnsupportedOperationException since transient buffers
 * are GPU-only intermediates not meant for CPU read/write.
 */
class TransientManagedBufferAdapter implements ManagedBuffer {

    private final VkBuffer buffer;

    TransientManagedBufferAdapter(VkBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public MemorySegment handle() { return buffer.handle(); }

    @Override
    public VkBuffer vkBuffer() { return buffer; }

    @Override
    public long size() { return buffer.size(); }

    @Override
    public BufferUsage usage() { return BufferUsage.STORAGE; }

    @Override
    public MemoryStrategy memoryStrategy() { return MemoryStrategy.DEVICE_LOCAL; }

    @Override
    public void write(ByteBuffer data, long offset, VkQueue queue) {
        throw new UnsupportedOperationException("Transient graph buffers do not support CPU write");
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        throw new UnsupportedOperationException("Transient graph buffers do not support CPU write");
    }

    @Override
    public ByteBuffer read(long offset, long size) {
        throw new UnsupportedOperationException("Transient graph buffers do not support CPU read");
    }

    @Override
    public void flush() { /* no-op for device-local */ }

    @Override
    public void copyTo(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        throw new UnsupportedOperationException("Use graph resource edges for transfers between transient buffers");
    }

    @Override
    public TransferCompletion copyToAsync(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        throw new UnsupportedOperationException("Use graph resource edges for transfers between transient buffers");
    }

    @Override
    public void close() {
        // Owned by TransientResourceAllocator -- do not close individually
    }
}
