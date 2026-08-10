package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-size slab allocator backed by a single {@link IBuffer}.
 * All suballocations are exactly {@code slotSize} bytes, aligned to device requirements.
 * Alloc and free are O(1). Create one instance per size class.
 */
public class SuballocatorBuffer implements IBuffer {
    private final long totalSize;
    private final BufferUsage usage;
    private final IBuffer backingBuffer;
    private final long slotSize;
    private final int slotCount;
    private final ArrayDeque<Integer> freeSlots;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SuballocatorBuffer(VkDevice device,
                              long totalSize, BufferUsage usage, long slotSize,
                              MemoryStrategy backingStrategy,
                              VkQueue transferQueue) {
        this.totalSize = totalSize;
        this.usage = usage;

        long alignment = switch (usage) {
            case UNIFORM -> device.physicalDevice().getMinUniformBufferOffsetAlignment();
            case STORAGE -> device.physicalDevice().getMinStorageBufferOffsetAlignment();
            case VERTEX -> 4L;
            case INDEX -> 4L;
            case TRANSFER -> 1L;
            case INDIRECT -> 4L;
            case STORAGE_INDIRECT -> device.physicalDevice().getMinStorageBufferOffsetAlignment();
            case MIXED -> Math.max(device.physicalDevice().getMinUniformBufferOffsetAlignment(),
                    device.physicalDevice().getMinStorageBufferOffsetAlignment());
        };
        this.slotSize = alignUp(slotSize, alignment);
        this.slotCount = (int) (totalSize / this.slotSize);
        if (this.slotCount == 0) throw new IllegalArgumentException("slotSize exceeds totalSize");

        this.freeSlots = new ArrayDeque<>(slotCount);
        for (int i = slotCount - 1; i >= 0; i--) freeSlots.push(i);

        this.backingBuffer = BufferFactory.create(backingStrategy, backingStrategy, totalSize, usage, device, transferQueue);
    }

    /**
     * @return a new slot, or throws {@link IllegalStateException} if the slab is full.
     */
    public Suballocation allocate() {
        lock.lock();
        try {
            Integer slot = freeSlots.poll();
            if (slot == null)
                throw new IllegalStateException("SuballocatorBuffer is full (slotCount=" + slotCount + ")");
            return new Suballocation(slot, slot * slotSize, slotSize);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a slot to the free stack in O(1).
     */
    public void free(Suballocation alloc) {
        lock.lock();
        try {
            freeSlots.push(alloc.slot);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return number of slots currently available
     */
    public int availableSlots() {
        lock.lock();
        try {
            return freeSlots.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the fixed slot size in bytes (after alignment)
     */
    public long slotSize() {
        return slotSize;
    }

    /**
     * @return total number of slots in this slab
     */
    public int slotCount() {
        return slotCount;
    }

    @Override
    public long size() {
        return totalSize;
    }

    @Override
    public BufferUsage usage() {
        return usage;
    }

    @Override
    public MemorySegment handle() {
        return backingBuffer.handle();
    }

    @Override
    public void write(ByteBuffer data, long offset, VkQueue queue) {
        backingBuffer.write(data, offset, queue);
    }

    @Override
    public GpuCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        return backingBuffer.writeAsync(data, offset, queue);
    }

    @Override
    public BufferWriteScope acquireWrite(long offset, long size, VkQueue queue) {
        return backingBuffer.acquireWrite(offset, size, queue);
    }

    @Override
    public BufferReadScope acquireRead(long offset, long size, VkQueue queue) {
        return backingBuffer.acquireRead(offset, size, queue);
    }

    @Override
    public ByteBuffer read(long offset, long size) {
        return backingBuffer.read(offset, size);
    }

    @Override
    public void flush() {
        backingBuffer.flush();
    }

    @Override
    public void copyTo(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        backingBuffer.copyTo(dst, srcOffset, dstOffset, length, queue);
    }

    @Override
    public GpuCompletion copyToAsync(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        return backingBuffer.copyToAsync(dst, srcOffset, dstOffset, length, queue);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            backingBuffer.close();
        }
    }

    private static long alignUp(long value, long alignment) {
        return alignment <= 1 ? value : (value + alignment - 1) & ~(alignment - 1);
    }

    /**
     * A fixed-size slot within the slab, implementing {@link IBuffer} so it can be passed
     * anywhere a buffer is expected. {@link #close()} returns the slot to the slab.
     * {@link #handle()} returns the backing buffer handle — bind with {@link #offset()} as the dynamic offset.
     */
    public class Suballocation implements IBuffer {
        private final int slot;
        private final long offset;
        private final long size;

        private Suballocation(int slot, long offset, long size) {
            this.slot = slot;
            this.offset = offset;
            this.size = size;
        }

        /**
         * @return byte offset within the backing buffer
         */
        public long offset() {
            return offset;
        }

        /**
         * @return the backing VkBuffer, if the slab's backing buffer is a {@link ManagedBuffer}.
         * Throws if the backing buffer does not expose one (e.g. a custom IBuffer implementation).
         */
        public VkBuffer vkBuffer() {
            if (backingBuffer instanceof ManagedBuffer mb) return mb.vkBuffer();
            throw new UnsupportedOperationException("Backing buffer does not expose a VkBuffer handle");
        }

        @Override
        public MemorySegment handle() {
            return backingBuffer.handle();
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public BufferUsage usage() {
            return SuballocatorBuffer.this.usage();
        }

        @Override
        public void write(ByteBuffer data, long ignored, VkQueue queue) {
            if (data.remaining() > size) throw new IllegalArgumentException("Data exceeds slot size");
            SuballocatorBuffer.this.write(data, offset, queue);
        }

        /**
         * Writes to this slot. Offset parameter is ignored — slot offset is fixed.
         */
        public void write(ByteBuffer data, VkQueue queue) {
            write(data, 0, queue);
        }

        @Override
        public GpuCompletion writeAsync(ByteBuffer data, long ignored, VkQueue queue) {
            if (data.remaining() > size) throw new IllegalArgumentException("Data exceeds slot size");
            return SuballocatorBuffer.this.writeAsync(data, offset, queue);
        }

        /**
         * Writes asynchronously to this slot. Offset parameter is ignored — slot offset is fixed.
         */
        public GpuCompletion writeAsync(ByteBuffer data, VkQueue queue) {
            return writeAsync(data, 0, queue);
        }

        /**
         * Acquires a write scope within this slot. {@code slotOffset} is relative to the slot,
         * not the backing buffer.
         */
        @Override
        public BufferWriteScope acquireWrite(long slotOffset, long writeSize, VkQueue queue) {
            if (slotOffset + writeSize > size) throw new IllegalArgumentException("Write exceeds slot size");
            return SuballocatorBuffer.this.acquireWrite(offset + slotOffset, writeSize, queue);
        }

        @Override
        public BufferReadScope acquireRead(long slotOffset, long readSize, VkQueue queue) {
            if (slotOffset + readSize > size) throw new IllegalArgumentException("Read exceeds slot size");
            return SuballocatorBuffer.this.acquireRead(offset + slotOffset, readSize, queue);
        }

        @Override
        public ByteBuffer read(long ignored, long readSize) {
            return SuballocatorBuffer.this.read(offset, readSize);
        }

        public ByteBuffer read() {
            return SuballocatorBuffer.this.read(offset, size);
        }

        @Override
        public void flush() {
            SuballocatorBuffer.this.flush();
        }

        @Override
        public void copyTo(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
            SuballocatorBuffer.this.copyTo(dst, offset + srcOffset, dstOffset, length, queue);
        }

        @Override
        public GpuCompletion copyToAsync(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
            return SuballocatorBuffer.this.copyToAsync(dst, offset + srcOffset, dstOffset, length, queue);
        }

        /**
         * Returns this slot to the slab.
         */
        @Override
        public void close() {
            SuballocatorBuffer.this.free(this);
        }

        public void free() {
            close();
        }
    }
}
