package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferReadScope;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MirrorCapable;

import java.lang.foreign.MemorySegment;
import java.nio.FloatBuffer;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Typed buffer view for {@code float} data.
 *
 * <p>Writes go through {@link IBuffer#acquireWrite}, so a bulk write is one intrinsified
 * {@link MemorySegment#copy} straight into the memory the underlying strategy considers closest to
 * final -- mapped memory, ReBAR memory, or staging memory. No intermediate buffer is allocated.
 *
 * <p>{@link #writeStrided} is the primitive for interleaving a planar attribute stream into a
 * packed vertex buffer: it writes {@code componentsPerElement} contiguous values per element and
 * advances {@code dstStrideBytes} between elements.
 */
public class FloatVkBuffer implements AutoCloseable {
    private static final int STRIDE = 4;

    private final IBuffer buffer;
    private final int count;
    private final FloatBuffer mirror;

    public FloatVkBuffer(IBuffer buffer, int count) {
        if ((long) count * STRIDE > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) count * STRIDE) + ", have " + buffer.size());
        this.buffer = buffer;
        this.count = count;
        this.mirror = (buffer instanceof ManagedBuffer mb && mb.observability() instanceof MirrorCapable mc)
                ? mc.mirrorMemory().asByteBuffer().asFloatBuffer() : null;
    }

    public int count() {
        return count;
    }

    public IBuffer buffer() {
        return buffer;
    }

    /**
     * @return a live float view of the CPU mirror, or {@code null} if not mirrored.
     */
    public FloatBuffer mirror() {
        return mirror;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void write(float[] data, int startIndex, VkQueue queue) {
        writeRange(data, 0, data.length, startIndex, queue);
    }

    public GpuCompletion writeAsync(float[] data, int startIndex, VkQueue queue) {
        return writeRangeAsync(data, 0, data.length, startIndex, queue);
    }

    /**
     * Writes {@code count} values from {@code src} starting at {@code srcOffset} into element
     * {@code dstIndex} onward. The source array need not be exactly the size of the write.
     */
    public void writeRange(float[] src, int srcOffset, int count, int dstIndex, VkQueue queue) {
        GpuCompletion tc = writeRangeAsync(src, srcOffset, count, dstIndex, queue);
        tc.flush();
        tc.await();
        tc.close();
    }

    public GpuCompletion writeRangeAsync(float[] src, int srcOffset, int count, int dstIndex, VkQueue queue) {
        checkRange(dstIndex, count);
        BufferWriteScope scope = buffer.acquireWrite((long) dstIndex * STRIDE, (long) count * STRIDE, queue);
        MemorySegment.copy(src, srcOffset, scope.segment(), JAVA_FLOAT_UNALIGNED, 0, count);
        scope.close();
        return scope.completion();
    }

    /**
     * Writes {@code count} values from a native source into element {@code dstIndex} onward.
     * Lets a memory-mapped file or an arena-backed producer feed the buffer with no Java array.
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
     * advancing {@code dstStrideBytes} in the destination between elements.
     *
     * <p>This is how a planar attribute stream is interleaved into a packed vertex buffer.
     * {@code dstElementIndex} counts strided elements, not floats.
     */
    public GpuCompletion writeStridedAsync(float[] src, int srcOffset, int componentsPerElement, int count,
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
                dst.set(JAVA_FLOAT_UNALIGNED, base + (long) c * STRIDE, src[s++]);
            }
        }
        scope.close();
        return scope.completion();
    }

    public void writeStrided(float[] src, int srcOffset, int componentsPerElement, int count,
                             int dstElementIndex, long dstStrideBytes, VkQueue queue) {
        GpuCompletion tc = writeStridedAsync(src, srcOffset, componentsPerElement, count, dstElementIndex, dstStrideBytes, queue);
        tc.flush();
        tc.await();
        tc.close();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public float[] read(int startIndex, int length) {
        float[] result = new float[length];
        readRange(startIndex, length, result, 0);
        return result;
    }

    /**
     * Reads {@code length} values into {@code dst} at {@code dstOffset}. Zero-copy from the CPU
     * mirror when the backing buffer has {@link MirrorCapable} observability.
     */
    public void readRange(int startIndex, int length, float[] dst, int dstOffset) {
        checkRange(startIndex, length);
        if (mirror != null) {
            mirror.position(startIndex);
            mirror.get(dst, dstOffset, length);
            mirror.rewind();
            return;
        }
        try (BufferReadScope scope = buffer.acquireRead((long) startIndex * STRIDE, (long) length * STRIDE, null)) {
            MemorySegment.copy(scope.segment(), JAVA_FLOAT_UNALIGNED, 0, dst, dstOffset, length);
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
