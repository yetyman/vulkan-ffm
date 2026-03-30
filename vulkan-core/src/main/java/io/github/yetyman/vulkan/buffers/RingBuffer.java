package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReferenceArray;


public class RingBuffer extends AbstractBuffer {
    private final ManagedBuffer[] buffers;
    private final int frameCount;
    private final boolean singleOffset;
    private final long alignedFrameSize; // only used in single-offset mode
    /** volatile ensures nextFrame() writes are visible to any thread reading handle()/write()/etc. */
    private volatile int currentFrame = 0;
    /** Tracks in-flight async completions per slot. Awaited before writing to a slot. */
    private final AtomicReferenceArray<TransferCompletion> inFlight;

    /** Separate-buffers constructor (default). */
    public RingBuffer(VkDevice device,
                     long size, BufferUsage usage, MemoryStrategy underlyingStrategy, int frameCount,
                     VkQueue transferQueue) {
        super(device, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
        this.singleOffset = false;
        this.alignedFrameSize = 0;
        this.buffers = new ManagedBuffer[frameCount];
        this.inFlight = new AtomicReferenceArray<>(frameCount);

        try {
            for (int i = 0; i < frameCount; i++) {
                buffers[i] = BufferFactory.create(underlyingStrategy, underlyingStrategy, size, usage, device, transferQueue);
            }
        } catch (Exception e) {
            for (ManagedBuffer b : buffers) { if (b != null) b.close(); }
            arena.close();
            throw e;
        }
    }

    /**
     * Single-offset constructor — one buffer of size frameCount * alignedFrameSize.
     * Use when VulkanCapabilities.unifiedMemory is true (UMA or ReBAR).
     * Bind with dynamicOffset() passed to vkCmdBindDescriptorSets.
     * Descriptor type must be UNIFORM_BUFFER_DYNAMIC or STORAGE_BUFFER_DYNAMIC.
     */
    public RingBuffer(VkDevice device,
                     long size, BufferUsage usage, MemoryStrategy underlyingStrategy, int frameCount,
                     VkQueue transferQueue, boolean singleOffset) {
        super(device, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
        this.singleOffset = singleOffset;
        this.inFlight = new AtomicReferenceArray<>(frameCount);

        if (!singleOffset) {
            this.alignedFrameSize = 0;
            this.buffers = new ManagedBuffer[frameCount];
            try {
                for (int i = 0; i < frameCount; i++) {
                    buffers[i] = BufferFactory.create(underlyingStrategy, underlyingStrategy, size, usage, device, transferQueue);
                }
            } catch (Exception e) {
                for (ManagedBuffer b : buffers) { if (b != null) b.close(); }
                arena.close();
                throw e;
            }
        } else {
            // Align frame size to the device's minimum dynamic offset alignment
            long alignment = usage == BufferUsage.UNIFORM
                ? device.physicalDevice().getMinUniformBufferOffsetAlignment()
                : device.physicalDevice().getMinStorageBufferOffsetAlignment();
            this.alignedFrameSize = ((size + alignment - 1) / alignment) * alignment;
            this.buffers = new ManagedBuffer[1];
            try {
                buffers[0] = BufferFactory.create(underlyingStrategy, underlyingStrategy,
                    alignedFrameSize * frameCount, usage, device, transferQueue);
            } catch (Exception e) {
                if (buffers[0] != null) buffers[0].close();
                arena.close();
                throw e;
            }
        }
    }

    public RingBuffer(VkDevice device,
                     long size, BufferUsage usage,
                     AccessFrequency cpuWrite, AccessFrequency cpuRead,
                     AccessFrequency gpuRead, AccessFrequency gpuWrite,
                     int frameCount,
                     VkQueue transferQueue) {
        super(device, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
        this.singleOffset = false;
        this.alignedFrameSize = 0;
        this.buffers = new ManagedBuffer[frameCount];
        this.inFlight = new AtomicReferenceArray<>(frameCount);

        try {
            for (int i = 0; i < frameCount; i++) {
                buffers[i] = BufferFactory.createAutomatic(
                    cpuWrite, cpuRead, gpuRead, gpuWrite, size,
                    usage, device, transferQueue
                );
            }
        } catch (Exception e) {
            for (ManagedBuffer b : buffers) { if (b != null) b.close(); }
            arena.close();
            throw e;
        }
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        int frame = currentFrame;
        awaitSlot(frame);
        ManagedBuffer buf = singleOffset ? buffers[0] : buffers[frame];
        long actualOffset = singleOffset ? frame * alignedFrameSize + offset : offset;
        TransferCompletion tc = buf.writeAsync(data, actualOffset, queue);
        inFlight.set(frame, tc);
        return tc;
    }

    /** Awaits and clears any in-flight completion for the given slot before reuse. */
    private void awaitSlot(int slot) {
        TransferCompletion prev = inFlight.getAndSet(slot, null);
        if (prev != null) { prev.await(); prev.close(); }
    }

    @Override
    public ByteBuffer read(long offset, long size) {
        if (singleOffset) return buffers[0].read(currentFrame * alignedFrameSize + offset, size);
        return buffers[currentFrame].read(offset, size);
    }

    @Override
    public void flush() {
        if (singleOffset) buffers[0].flush();
        else buffers[currentFrame].flush();
    }

    /**
     * Returns the VkBuffer handle.
     * In single-offset mode this is always the same handle — bind once and use dynamicOffset() each frame.
     * In separate-buffers mode this changes each frame — rebind each frame.
     */
    @Override
    public MemorySegment handle() {
        return singleOffset ? buffers[0].handle() : buffers[currentFrame].handle();
    }

    /**
     * Returns the byte offset into the backing buffer for the current frame.
     * Only meaningful in single-offset mode — pass this to vkCmdBindDescriptorSets
     * as the dynamic offset for UNIFORM_BUFFER_DYNAMIC / STORAGE_BUFFER_DYNAMIC descriptors.
     * Returns 0 in separate-buffers mode.
     */
    public int dynamicOffset() {
        return singleOffset ? (int)(currentFrame * alignedFrameSize) : 0;
    }

    /** @return true if this ring buffer uses a single backing allocation with dynamic offsets. */
    public boolean isSingleOffset() { return singleOffset; }

    public void nextFrame() {
        currentFrame = (currentFrame + 1) % frameCount;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    @Override
    public void closeImpl() {
        for (int i = 0; i < frameCount; i++) awaitSlot(i);
        for (ManagedBuffer buffer : buffers) {
            if (buffer != null) buffer.close();
        }
    }
}
