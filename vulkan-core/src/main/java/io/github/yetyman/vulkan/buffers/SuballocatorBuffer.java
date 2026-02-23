package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed-size slab allocator backed by a single VkBuffer.
 * All suballocations are exactly {@code slotSize} bytes, aligned to device requirements.
 * Alloc and free are O(1). Create one instance per size class.
 */
public class SuballocatorBuffer extends AbstractBuffer {
    private final ManagedBuffer backingBuffer;
    private final long slotSize;
    private final int slotCount;
    private final ArrayDeque<Integer> freeSlots;
    private final ReentrantLock lock = new ReentrantLock();

    public SuballocatorBuffer(VkDevice device,
                              long totalSize, BufferUsage usage, long slotSize,
                              MemoryStrategy backingStrategy,
                              VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, totalSize, usage, MemoryStrategy.SUBALLOCATOR);

        long alignment = switch (usage) {
            case UNIFORM -> device.physicalDevice().getMinUniformBufferOffsetAlignment();
            case STORAGE -> device.physicalDevice().getMinStorageBufferOffsetAlignment();
            case VERTEX  -> 4L;
            case TRANSFER -> 1L;
            case MIXED   -> Math.max(device.physicalDevice().getMinUniformBufferOffsetAlignment(),
                                     device.physicalDevice().getMinStorageBufferOffsetAlignment());
        };
        this.slotSize = alignUp(slotSize, alignment);
        this.slotCount = (int) (totalSize / this.slotSize);
        if (this.slotCount == 0) throw new IllegalArgumentException("slotSize exceeds totalSize");

        this.freeSlots = new ArrayDeque<>(slotCount);
        for (int i = slotCount - 1; i >= 0; i--) freeSlots.push(i);

        ManagedBuffer backing = null;
        try {
            backing = BufferFactory.create(backingStrategy, backingStrategy, totalSize, usage, device, transferQueue, commandPool);
        } catch (Exception e) {
            arena.close();
            throw e;
        }
        this.backingBuffer = backing;
    }

    /** @return a new slot, or throws {@link IllegalStateException} if the slab is full. */
    public Suballocation allocate() {
        lock.lock();
        try {
            Integer slot = freeSlots.poll();
            if (slot == null) throw new IllegalStateException("SuballocatorBuffer is full (slotCount=" + slotCount + ")");
            return new Suballocation(slot, slot * slotSize, slotSize);
        } finally {
            lock.unlock();
        }
    }

    /** Returns a slot to the free stack in O(1). */
    public void free(Suballocation alloc) {
        lock.lock();
        try {
            freeSlots.push(alloc.slot);
        } finally {
            lock.unlock();
        }
    }

    /** @return number of slots currently available */
    public int availableSlots() {
        lock.lock();
        try { return freeSlots.size(); } finally { lock.unlock(); }
    }

    /** @return the fixed slot size in bytes (after alignment) */
    public long slotSize() { return slotSize; }

    /** @return total number of slots in this slab */
    public int slotCount() { return slotCount; }

    @Override public MemorySegment handle() { return backingBuffer.handle(); }

    @Override
    public void write(ByteBuffer data, long offset) {
        TransferCompletion tc = writeAsync(data, offset);
        tc.await();
        tc.close();
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        return backingBuffer.writeAsync(data, offset);
    }

    @Override public ByteBuffer read(long offset, long size) { return backingBuffer.read(offset, size); }
    @Override public void flush() { backingBuffer.flush(); }
    @Override public void copyTo(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool) { backingBuffer.copyTo(dst, srcOffset, dstOffset, length, queue, commandPool); }
    @Override public TransferCompletion copyToAsync(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool) { return backingBuffer.copyToAsync(dst, srcOffset, dstOffset, length, queue, commandPool); }
    @Override public void closeImpl() { backingBuffer.close(); }

    private static long alignUp(long value, long alignment) {
        return alignment <= 1 ? value : (value + alignment - 1) & ~(alignment - 1);
    }

    /**
     * A fixed-size slot within the slab, implementing {@link ManagedBuffer} so it can be passed
     * anywhere a buffer is expected. {@link #close()} returns the slot to the slab.
     * {@link #handle()} returns the backing buffer handle — bind with {@link #offset()} as the dynamic offset.
     */
    public class Suballocation implements ManagedBuffer {
        private final int slot;
        private final long offset;
        private final long size;

        private Suballocation(int slot, long offset, long size) {
            this.slot = slot;
            this.offset = offset;
            this.size = size;
        }

        /** @return byte offset within the backing buffer */
        public long offset() { return offset; }

        @Override public MemorySegment handle() { return backingBuffer.handle(); }
        @Override public long size() { return size; }
        @Override public BufferUsage usage() { return SuballocatorBuffer.this.usage(); }
        @Override public MemoryStrategy memoryStrategy() { return SuballocatorBuffer.this.memoryStrategy(); }

        @Override
        public void write(ByteBuffer data, long ignored) {
            if (data.remaining() > size) throw new IllegalArgumentException("Data exceeds slot size");
            SuballocatorBuffer.this.write(data, offset);
        }

        /** Writes to this slot. Offset parameter is ignored — slot offset is fixed. */
        public void write(ByteBuffer data) { write(data, 0); }

        @Override
        public TransferCompletion writeAsync(ByteBuffer data, long ignored) {
            if (data.remaining() > size) throw new IllegalArgumentException("Data exceeds slot size");
            return SuballocatorBuffer.this.writeAsync(data, offset);
        }

        /** Writes asynchronously to this slot. Offset parameter is ignored — slot offset is fixed. */
        public TransferCompletion writeAsync(ByteBuffer data) { return writeAsync(data, 0); }

        @Override public ByteBuffer read(long ignored, long readSize) { return SuballocatorBuffer.this.read(offset, readSize); }
        public ByteBuffer read() { return SuballocatorBuffer.this.read(offset, size); }

        @Override public void flush() { SuballocatorBuffer.this.flush(); }

        @Override
        public void copyTo(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool) {
            SuballocatorBuffer.this.copyTo(dst, offset + srcOffset, dstOffset, length, queue, commandPool);
        }

        @Override
        public TransferCompletion copyToAsync(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool) {
            return SuballocatorBuffer.this.copyToAsync(dst, offset + srcOffset, dstOffset, length, queue, commandPool);
        }

        /** Returns this slot to the slab. */
        @Override public void close() { SuballocatorBuffer.this.free(this); }

        public void free() { close(); }
    }
}
