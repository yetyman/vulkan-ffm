package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferReadScope;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MirroredBuffer;

import java.lang.foreign.MemorySegment;
import java.nio.ShortBuffer;

import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * Typed buffer view for {@code short} data. Also the natural view for 16-bit index data.
 *
 * <p>Writes go through {@link IBuffer#acquireWrite}: a bulk write is one intrinsified
 * {@link MemorySegment#copy} into the memory the underlying strategy considers closest to final,
 * with no intermediate buffer allocated.
 */
public class ShortVkBuffer implements AutoCloseable {
    private static final int STRIDE = 2;

    private final IBuffer buffer;
    private final int count;
    private final ShortBuffer mirror;

    public ShortVkBuffer(IBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof MirroredBuffer m) ? m.mirror().asShortBuffer() : null;
    }

    public int count() {
        return count;
    }

    public IBuffer buffer() {
        return buffer;
    }

    /**
     * @return a live short view of the CPU mirror, or {@code null} if not mirrored.
     */
    public ShortBuffer mirror() {
        return mirror;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void write(short[] data, int startIndex, VkQueue queue) {
        writeRange(data, 0, data.length, startIndex, queue);
    }

    public GpuCompletion writeAsync(short[] data, int startIndex, VkQueue queue) {
        return writeRangeAsync(data, 0, data.length, startIndex, queue);
    }

    /**
     * Writes {@code count} values from {@code src} starting at {@code srcOffset} into element
     * {@code dstIndex} onward. The source array need not be exactly the size of the write.
     */
    public void writeRange(short[] src, int srcOffset, int count, int dstIndex, VkQueue queue) {
        GpuCompletion tc = writeRangeAsync(src, srcOffset, count, dstIndex, queue);
        tc.flush();
        tc.await();
        tc.close();
    }

    public GpuCompletion writeRangeAsync(short[] src, int srcOffset, int count, int dstIndex, VkQueue queue) {
        checkRange(dstIndex, count);
        BufferWriteScope scope = buffer.acquireWrite((long) dstIndex * STRIDE, (long) count * STRIDE, queue);
        MemorySegment.copy(src, srcOffset, scope.segment(), JAVA_SHORT_UNALIGNED, 0, count);
        scope.close();
        return scope.completion();
    }

    /**
     * Writes {@code count} values from a native source into element {@code dstIndex} onward.
     */
    public GpuCompletion writeRangeAsync(MemorySegment src, long srcOffset, int count, int dstIndex, VkQueue queue) {
        checkRange(dstIndex, count);
        BufferWriteScope scope = buffer.acquireWrite((long) dstIndex * STRIDE, (long) count * STRIDE, queue);
        MemorySegment.copy(src, srcOffset, scope.segment(), 0, (long) count * STRIDE);
        scope.close();
        return scope.completion();
    }

    /**
     * Writes {@code count} elements of {@code componentsPerElement} contiguous values each,
     * advancing {@code dstStrideBytes} between elements.
     */
    public GpuCompletion writeStridedAsync(short[] src, int srcOffset, int componentsPerElement, int count,
                                           int dstElementIndex, long dstStrideBytes, VkQueue queue) {
        if (componentsPerElement <= 0) throw new IllegalArgumentException("componentsPerElement must be positive");
        long begin = (long) dstElementIndex * dstStrideBytes;
        long span = count == 0 ? 0 : (long) (count - 1) * dstStrideBytes + (long) componentsPerElement * STRIDE;
        if (begin + span > buffer.size())
            throw new IndexOutOfBoundsException("Strided write exceeds buffer size");
        BufferWriteScope scope = buffer.acquireWrite(begin, span, queue);
        MemorySegment dst = scope.segment();
        int s = srcOffset;
        for (int e = 0; e < count; e++) {
            long base = (long) e * dstStrideBytes;
            for (int c = 0; c < componentsPerElement; c++) {
                dst.set(JAVA_SHORT_UNALIGNED, base + (long) c * STRIDE, src[s++]);
            }
        }
        scope.close();
        return scope.completion();
    }

    public void writeStrided(short[] src, int srcOffset, int componentsPerElement, int count,
                             int dstElementIndex, long dstStrideBytes, VkQueue queue) {
        GpuCompletion tc = writeStridedAsync(src, srcOffset, componentsPerElement, count, dstElementIndex, dstStrideBytes, queue);
        tc.flush();
        tc.await();
        tc.close();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public short[] read(int startIndex, int length) {
        short[] result = new short[length];
        readRange(startIndex, length, result, 0);
        return result;
    }

    /**
     * Reads {@code length} values into {@code dst} at {@code dstOffset}. Zero-copy from the CPU
     * mirror when the backing buffer is a {@link MirroredBuffer}.
     */
    public void readRange(int startIndex, int length, short[] dst, int dstOffset) {
        checkRange(startIndex, length);
        if (mirror != null) {
            mirror.position(startIndex);
            mirror.get(dst, dstOffset, length);
            mirror.rewind();
            return;
        }
        try (BufferReadScope scope = buffer.acquireRead((long) startIndex * STRIDE, (long) length * STRIDE, null)) {
            MemorySegment.copy(scope.segment(), JAVA_SHORT_UNALIGNED, 0, dst, dstOffset, length);
        }
    }

    @Override
    public void close() {
        buffer.close();
    }

    private void checkRange(int startIndex, int length) {
        if (startIndex < 0 || length < 0 || (long) startIndex + length > count)
            throw new IndexOutOfBoundsException("range [" + startIndex + ", " + (startIndex + length) + ") out of bounds for count " + count);
    }
}
