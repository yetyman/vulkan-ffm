package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
/**
 * Decorator over {@link DeviceLocalBuffer} that maintains a CPU-side mirror of all written data.
 * Reads are served from the mirror at zero GPU cost.
 * Writes go to both the mirror and the device-local buffer via staging transfer.
 *
 * <p>Only safe when the CPU is the sole writer. GPU writes will not update the mirror.
 * Best suited for discrete GPUs with data that is rarely written but frequently read on both sides
 * (e.g. static scene bounds, material parameters, LOD tables).
 */
public class MirroredBuffer extends AbstractBuffer {
    private final DeviceLocalBuffer deviceBuffer;
    private ByteBuffer mirror;

    public MirroredBuffer(VkDevice device, long size, BufferUsage usage,
                          VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, size, usage, MemoryStrategy.DEVICE_LOCAL_MIRRORED);
        try {
            this.deviceBuffer = new DeviceLocalBuffer(device, size, usage, transferQueue, commandPool, false);
            this.mirror = ByteBuffer.allocate((int) size);
            // expose the device buffer's VkBuffer handle via vkBuffer field
            this.vkBuffer = deviceBuffer.vkBuffer;
        } catch (Exception e) {
            arena.close();
            throw e;
        }
    }

    @Override
    public MemorySegment handle() { return deviceBuffer.handle(); }

    @Override
    public void write(ByteBuffer data, long offset) {
        TransferCompletion tc = writeAsync(data, offset);
        tc.await();
        tc.close();
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        ByteBuffer slice = data.slice();
        mirror.position((int) offset);
        mirror.put(slice);
        mirror.rewind();
        data.rewind();
        return deviceBuffer.writeAsync(data, offset);
    }

    /** @return a read-only view of the CPU mirror — zero GPU cost. */
    @Override
    public ByteBuffer read(long offset, long length) {
        return mirror.slice((int) offset, (int) length).asReadOnlyBuffer();
    }

    @Override
    public void flush() { deviceBuffer.flush(); }

    @Override
    public void closeImpl() {
        deviceBuffer.close();
        mirror = null;
    }
}
