package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirroredBuffer;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

/** Typed buffer for {@code double[]} arrays. Direct transfer with no serialization overhead. */
public class DoubleVkBuffer implements AutoCloseable {
    private static final int STRIDE = 8;
    private final ManagedBuffer buffer;
    private final int count;
    private final DoubleBuffer mirror;

    public DoubleVkBuffer(ManagedBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof MirroredBuffer m) ? m.mirror().asDoubleBuffer() : null;
    }

    public int count() { return count; }
    public ManagedBuffer buffer() { return buffer; }
    /** @return a live double view of the CPU mirror, or {@code null} if not mirrored. */
    public DoubleBuffer mirror() { return mirror; }

    public void write(double[] data, int startIndex) {
        TransferCompletion tc = writeAsync(data, startIndex);
        tc.await(); tc.close();
    }

    public TransferCompletion writeAsync(double[] data, int startIndex) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * STRIDE);
        bb.asDoubleBuffer().put(data);
        return buffer.writeAsync(bb, (long) startIndex * STRIDE);
    }

    public double[] read(int startIndex, int length) {
        if (mirror != null) {
            double[] result = new double[length];
            mirror.position(startIndex);
            mirror.get(result, 0, length);
            mirror.rewind();
            return result;
        }
        ByteBuffer bb = buffer.read((long) startIndex * STRIDE, (long) length * STRIDE);
        double[] result = new double[length];
        bb.asDoubleBuffer().get(result);
        return result;
    }

    @Override public void close() { buffer.close(); }
}
