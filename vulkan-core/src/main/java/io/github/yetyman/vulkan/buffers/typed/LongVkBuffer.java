package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirroredBuffer;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/** Typed buffer for {@code long[]} arrays. Direct transfer with no serialization overhead. */
public class LongVkBuffer implements AutoCloseable {
    private static final int STRIDE = 8;
    private final ManagedBuffer buffer;
    private final int count;
    private final LongBuffer mirror;

    public LongVkBuffer(ManagedBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof MirroredBuffer m) ? m.mirror().asLongBuffer() : null;
    }

    public int count() { return count; }
    public ManagedBuffer buffer() { return buffer; }
    /** @return a live long view of the CPU mirror, or {@code null} if not mirrored. */
    public LongBuffer mirror() { return mirror; }

    public void write(long[] data, int startIndex, VkQueue queue) {
        TransferCompletion tc = writeAsync(data, startIndex, queue);
        tc.await(); tc.close();
    }

    public TransferCompletion writeAsync(long[] data, int startIndex, VkQueue queue) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * STRIDE);
        bb.asLongBuffer().put(data);
        return buffer.writeAsync(bb, (long) startIndex * STRIDE, queue);
    }

    public long[] read(int startIndex, int length) {
        if (mirror != null) {
            long[] result = new long[length];
            mirror.position(startIndex);
            mirror.get(result, 0, length);
            mirror.rewind();
            return result;
        }
        ByteBuffer bb = buffer.read((long) startIndex * STRIDE, (long) length * STRIDE);
        long[] result = new long[length];
        bb.asLongBuffer().get(result);
        return result;
    }

    @Override public void close() { buffer.close(); }
}
