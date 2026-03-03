package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirroredBuffer;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Typed buffer for {@code int[]} arrays. Direct transfer with no serialization overhead. */
public class IntVkBuffer implements AutoCloseable {
    private static final int STRIDE = 4;
    private final ManagedBuffer buffer;
    private final int count;
    private final IntBuffer mirror;

    public IntVkBuffer(ManagedBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof MirroredBuffer m) ? m.mirror().asIntBuffer() : null;
    }

    public int count() { return count; }
    public ManagedBuffer buffer() { return buffer; }
    /** @return a live int view of the CPU mirror, or {@code null} if not mirrored. */
    public IntBuffer mirror() { return mirror; }

    public void write(int[] data, int startIndex, VkQueue queue) {
        TransferCompletion tc = writeAsync(data, startIndex, queue);
        tc.await(); tc.close();
    }

    public TransferCompletion writeAsync(int[] data, int startIndex, VkQueue queue) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * STRIDE);
        bb.asIntBuffer().put(data);
        return buffer.writeAsync(bb, (long) startIndex * STRIDE, queue);
    }

    public int[] read(int startIndex, int length) {
        if (mirror != null) {
            int[] result = new int[length];
            mirror.position(startIndex);
            mirror.get(result, 0, length);
            mirror.rewind();
            return result;
        }
        ByteBuffer bb = buffer.read((long) startIndex * STRIDE, (long) length * STRIDE);
        int[] result = new int[length];
        bb.asIntBuffer().get(result);
        return result;
    }

    @Override public void close() { buffer.close(); }
}
