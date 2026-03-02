package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirroredBuffer;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/** Typed buffer for {@code float[]} arrays. Direct transfer with no serialization overhead. */
public class FloatVkBuffer implements AutoCloseable {
    private static final int STRIDE = 4;
    private final ManagedBuffer buffer;
    private final int count;
    private final FloatBuffer mirror;

    public FloatVkBuffer(ManagedBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof MirroredBuffer m) ? m.mirror().asFloatBuffer() : null;
    }

    public int count() { return count; }
    public ManagedBuffer buffer() { return buffer; }
    /** @return a live float view of the CPU mirror, or {@code null} if not mirrored. */
    public FloatBuffer mirror() { return mirror; }

    public void write(float[] data, int startIndex) {
        TransferCompletion tc = writeAsync(data, startIndex);
        tc.await(); tc.close();
    }

    public TransferCompletion writeAsync(float[] data, int startIndex) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * STRIDE);
        bb.asFloatBuffer().put(data);
        return buffer.writeAsync(bb, (long) startIndex * STRIDE);
    }

    public float[] read(int startIndex, int length) {
        if (mirror != null) {
            float[] result = new float[length];
            mirror.position(startIndex);
            mirror.get(result, 0, length);
            mirror.rewind();
            return result;
        }
        ByteBuffer bb = buffer.read((long) startIndex * STRIDE, (long) length * STRIDE);
        float[] result = new float[length];
        bb.asFloatBuffer().get(result);
        return result;
    }

    @Override public void close() { buffer.close(); }
}
