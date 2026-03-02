package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirroredBuffer;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

/** Typed buffer for {@code short[]} arrays. Direct transfer with no serialization overhead. */
public class ShortVkBuffer implements AutoCloseable {
    private static final int STRIDE = 2;
    private final ManagedBuffer buffer;
    private final int count;
    private final ShortBuffer mirror;

    public ShortVkBuffer(ManagedBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof MirroredBuffer m) ? m.mirror().asShortBuffer() : null;
    }

    public int count() { return count; }
    public ManagedBuffer buffer() { return buffer; }
    /** @return a live short view of the CPU mirror, or {@code null} if not mirrored. */
    public ShortBuffer mirror() { return mirror; }

    public void write(short[] data, int startIndex) {
        TransferCompletion tc = writeAsync(data, startIndex);
        tc.await(); tc.close();
    }

    public TransferCompletion writeAsync(short[] data, int startIndex) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * STRIDE);
        bb.asShortBuffer().put(data);
        return buffer.writeAsync(bb, (long) startIndex * STRIDE);
    }

    public short[] read(int startIndex, int length) {
        if (mirror != null) {
            short[] result = new short[length];
            mirror.position(startIndex);
            mirror.get(result, 0, length);
            mirror.rewind();
            return result;
        }
        ByteBuffer bb = buffer.read((long) startIndex * STRIDE, (long) length * STRIDE);
        short[] result = new short[length];
        bb.asShortBuffer().get(result);
        return result;
    }

    @Override public void close() { buffer.close(); }
}
