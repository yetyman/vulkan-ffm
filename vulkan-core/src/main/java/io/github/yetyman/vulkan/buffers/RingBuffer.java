package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReferenceArray;


public class RingBuffer extends AbstractBuffer {
    private final ManagedBuffer[] buffers;
    private final int frameCount;
    /** volatile ensures nextFrame() writes are visible to any thread reading handle()/write()/etc. */
    private volatile int currentFrame = 0;
    /** Tracks in-flight async completions per slot. Awaited before writing to a slot. */
    private final AtomicReferenceArray<TransferCompletion> inFlight;

    public RingBuffer(VkDevice device,
                     long size, BufferUsage usage, MemoryStrategy underlyingStrategy, int frameCount,
                     VkQueue transferQueue) {
        super(device, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
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

    public RingBuffer(VkDevice device,
                     long size, BufferUsage usage,
                     AccessFrequency cpuWrite, AccessFrequency cpuRead,
                     AccessFrequency gpuRead, AccessFrequency gpuWrite,
                     int frameCount,
                     VkQueue transferQueue) {
        super(device, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
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
        TransferCompletion tc = buffers[frame].writeAsync(data, offset, queue);
        inFlight.set(frame, tc);
        return tc;
    }

    /** Awaits and clears any in-flight completion for the given slot before reuse. */
    private void awaitSlot(int slot) {
        TransferCompletion prev = inFlight.getAndSet(slot, null);
        if (prev != null) prev.await();
    }

    @Override
    public ByteBuffer read(long offset, long size) {
        return buffers[currentFrame].read(offset, size);
    }

    @Override
    public void flush() {
        buffers[currentFrame].flush();
    }

    /**
     * Returns the handle of the current frame's buffer.
     * This is the buffer that should be bound for the current frame's rendering.
     */
    @Override
    public MemorySegment handle() {
        return buffers[currentFrame].handle();
    }

    public void nextFrame() {
        currentFrame = (currentFrame + 1) % frameCount;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    @Override
    public void closeImpl() {
        for (ManagedBuffer buffer : buffers) {
            if (buffer != null) {
                buffer.close();
            }
        }
        // Don't call super.closeImpl() — child buffers own their own VkBuffers
    }
}
