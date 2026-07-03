package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * N-buffered ring wrapping another {@link IBuffer} strategy — one slot per frame in flight.
 * Composes independent {@link IBuffer} instances (or a single instance sliced by dynamic offset);
 * holds no allocation or transfer strategy of its own.
 */
public class RingBuffer implements IBuffer {
    private final long size;
    private final BufferUsage usage;
    private final IBuffer[] buffers;
    private final int frameCount;
    private final boolean singleOffset;
    private final long alignedFrameSize; // only used in single-offset mode
    /**
     * volatile ensures nextFrame() writes are visible to any thread reading handle()/write()/etc.
     */
    private volatile int currentFrame = 0;
    /**
     * Tracks in-flight async completions per slot. Awaited before writing to a slot.
     */
    private final AtomicReferenceArray<TransferCompletion> inFlight;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Separate-buffers constructor (default).
     */
    public RingBuffer(VkDevice device,
                      long size, BufferUsage usage, MemoryStrategy underlyingStrategy, int frameCount,
                      VkQueue transferQueue) {
        this.size = size;
        this.usage = usage;
        this.frameCount = frameCount;
        this.singleOffset = false;
        this.alignedFrameSize = 0;
        this.buffers = new IBuffer[frameCount];
        this.inFlight = new AtomicReferenceArray<>(frameCount);

        try {
            for (int i = 0; i < frameCount; i++) {
                buffers[i] = BufferFactory.create(underlyingStrategy, underlyingStrategy, size, usage, device, transferQueue);
            }
        } catch (Exception e) {
            for (IBuffer b : buffers) {
                if (b != null) b.close();
            }
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
        this.size = size;
        this.usage = usage;
        this.frameCount = frameCount;
        this.singleOffset = singleOffset;
        this.inFlight = new AtomicReferenceArray<>(frameCount);

        if (!singleOffset) {
            this.alignedFrameSize = 0;
            this.buffers = new IBuffer[frameCount];
            try {
                for (int i = 0; i < frameCount; i++) {
                    buffers[i] = BufferFactory.create(underlyingStrategy, underlyingStrategy, size, usage, device, transferQueue);
                }
            } catch (Exception e) {
                for (IBuffer b : buffers) {
                    if (b != null) b.close();
                }
                throw e;
            }
        } else {
            // Align frame size to the device's minimum dynamic offset alignment
            long alignment = usage == BufferUsage.UNIFORM
                    ? device.physicalDevice().getMinUniformBufferOffsetAlignment()
                    : device.physicalDevice().getMinStorageBufferOffsetAlignment();
            this.alignedFrameSize = ((size + alignment - 1) / alignment) * alignment;
            this.buffers = new IBuffer[1];
            try {
                buffers[0] = BufferFactory.create(underlyingStrategy, underlyingStrategy,
                        alignedFrameSize * frameCount, usage, device, transferQueue);
            } catch (Exception e) {
                if (buffers[0] != null) buffers[0].close();
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
        this.size = size;
        this.usage = usage;
        this.frameCount = frameCount;
        this.singleOffset = false;
        this.alignedFrameSize = 0;
        this.buffers = new IBuffer[frameCount];
        this.inFlight = new AtomicReferenceArray<>(frameCount);

        try {
            for (int i = 0; i < frameCount; i++) {
                buffers[i] = BufferFactory.createAutomatic(
                        cpuWrite, cpuRead, gpuRead, gpuWrite, size,
                        usage, device, transferQueue
                );
            }
        } catch (Exception e) {
            for (IBuffer b : buffers) {
                if (b != null) b.close();
            }
            throw e;
        }
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public BufferUsage usage() {
        return usage;
    }

    @Override
    public void write(ByteBuffer data, long offset, VkQueue queue) {
        TransferCompletion tc = writeAsync(data, offset, queue);
        TransferBatchManager.flush(queue.device(), queue);
        tc.await();
        tc.close();
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        int frame = currentFrame;
        awaitSlot(frame);
        IBuffer buf = singleOffset ? buffers[0] : buffers[frame];
        long actualOffset = singleOffset ? frame * alignedFrameSize + offset : offset;
        TransferCompletion tc = buf.writeAsync(data, actualOffset, queue);
        inFlight.set(frame, tc);
        return tc;
    }

    /**
     * Awaits and clears any in-flight completion for the given slot before reuse.
     */
    private void awaitSlot(int slot) {
        TransferCompletion prev = inFlight.getAndSet(slot, null);
        if (prev != null) {
            prev.await();
            prev.close();
        }
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

    @Override
    public void copyTo(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        IBuffer buf = singleOffset ? buffers[0] : buffers[currentFrame];
        long actualOffset = singleOffset ? currentFrame * alignedFrameSize + srcOffset : srcOffset;
        buf.copyTo(dst, actualOffset, dstOffset, length, queue);
    }

    @Override
    public TransferCompletion copyToAsync(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        IBuffer buf = singleOffset ? buffers[0] : buffers[currentFrame];
        long actualOffset = singleOffset ? currentFrame * alignedFrameSize + srcOffset : srcOffset;
        return buf.copyToAsync(dst, actualOffset, dstOffset, length, queue);
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
     * Returns the VkBuffer handle for a specific slot.
     * In single-offset mode all slots share the same handle.
     * In separate-buffers mode each slot has its own handle — use this to build
     * descriptor sets for all slots upfront (e.g. for GPU-GPU ping-pong).
     */
    public MemorySegment handleAt(int slot) {
        if (slot < 0 || slot >= frameCount)
            throw new IndexOutOfBoundsException("slot " + slot + " out of range [0, " + frameCount + ")");
        return singleOffset ? buffers[0].handle() : buffers[slot].handle();
    }

    /**
     * @return the number of buffer slots in this ring.
     */
    public int slotCount() {
        return frameCount;
    }

    /**
     * @return the current active slot index.
     */
    public int currentSlot() {
        return currentFrame;
    }

    /**
     * Returns the byte offset into the backing buffer for the current frame.
     * Only meaningful in single-offset mode — pass this to vkCmdBindDescriptorSets
     * as the dynamic offset for UNIFORM_BUFFER_DYNAMIC / STORAGE_BUFFER_DYNAMIC descriptors.
     * Returns 0 in separate-buffers mode.
     */
    public int dynamicOffset() {
        return singleOffset ? (int) (currentFrame * alignedFrameSize) : 0;
    }

    /**
     * @return true if this ring buffer uses a single backing allocation with dynamic offsets.
     */
    public boolean isSingleOffset() {
        return singleOffset;
    }

    public void nextFrame() {
        currentFrame = (currentFrame + 1) % frameCount;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (int i = 0; i < frameCount; i++) awaitSlot(i);
            for (IBuffer buffer : buffers) {
                if (buffer != null) buffer.close();
            }
        }
    }
}
