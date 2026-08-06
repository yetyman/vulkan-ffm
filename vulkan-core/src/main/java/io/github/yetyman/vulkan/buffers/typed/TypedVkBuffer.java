package io.github.yetyman.vulkan.buffers.typed;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferReadScope;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.GpuLayout;
import io.github.yetyman.vulkan.buffers.IBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed array view over any {@link IBuffer}, with a fixed stride taken from a {@link GpuLayout}.
 *
 * <p>The layout is mandatory rather than derived from the element type: there is no single correct
 * serialization for a type, and requiring the caller to name one keeps packed, padded, and
 * quantized variants equal citizens.
 *
 * <p>Subclass and implement {@link #getInstance} to control T allocation (pooled or fresh).
 * Override {@link #releaseInstance} to return instances to a pool on eviction or close.
 * When constructed with {@code mirrored=true}, written objects are retained and reads are
 * zero-cost. When not mirrored, reads perform a GPU readback -- slow, avoid in hot paths.
 *
 * <p>All writes go through {@link IBuffer#acquireWrite}, so element data is serialized exactly once
 * directly into whatever memory the underlying strategy considers closest to final.
 */
public abstract class TypedVkBuffer<T> implements AutoCloseable {

    private final IBuffer buffer;
    private final GpuLayout<T> layout;
    private final int stride;
    private final int count;
    private final ArrayList<T> mirror;

    /**
     * @param layout   serialization format; its {@link GpuLayout#byteSize()} is the element stride
     * @param count    element capacity of this view
     * @param mirrored whether to retain written objects for zero-cost reads
     */
    public TypedVkBuffer(IBuffer buffer, GpuLayout<T> layout, int count, boolean mirrored) {
        if (layout == null) throw new IllegalArgumentException("layout required");
        int byteSize = layout.byteSize();
        if (byteSize <= 0) throw new IllegalArgumentException("layout must have a fixed positive byteSize");
        if ((long) byteSize * count > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) byteSize * count) + ", have " + buffer.size());
        this.buffer = buffer;
        this.layout = layout;
        this.stride = byteSize;
        this.count = count;
        if (mirrored) {
            this.mirror = new ArrayList<>(count);
            for (int i = 0; i < count; i++) mirror.add(null);
        } else {
            this.mirror = null;
        }
    }

    /**
     * @return a T instance to populate during a read. May be pooled or freshly allocated.
     */
    protected abstract T getInstance();

    /**
     * Called when a mirrored slot is overwritten or on close. No-op by default.
     */
    protected void releaseInstance(T instance) {
    }

    public int count() {
        return count;
    }

    public int stride() {
        return stride;
    }

    public IBuffer buffer() {
        return buffer;
    }

    public GpuLayout<T> layout() {
        return layout;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void write(int index, T value, VkQueue queue) {
        GpuCompletion tc = writeAsync(index, value, queue);
        tc.flush();
        tc.await();
        tc.close();
    }

    public GpuCompletion writeAsync(int index, T value, VkQueue queue) {
        checkIndex(index);
        BufferWriteScope scope = buffer.acquireWrite((long) index * stride, stride, queue);
        layout.writeTo(value, scope.segment(), 0);
        scope.close();
        if (mirror != null) {
            T old = mirror.set(index, value);
            if (old != null && old != value) releaseInstance(old);
        }
        return scope.completion();
    }

    public void write(List<T> values, int startIndex, VkQueue queue) {
        GpuCompletion tc = writeAsync(values, startIndex, queue);
        tc.flush();
        tc.await();
        tc.close();
    }

    /**
     * Writes {@code values.size()} consecutive elements in a single acquired region, so the whole
     * run is serialized once and committed once.
     */
    public GpuCompletion writeAsync(List<T> values, int startIndex, VkQueue queue) {
        checkIndex(startIndex);
        checkIndex(startIndex + values.size() - 1);
        BufferWriteScope scope = buffer.acquireWrite((long) startIndex * stride, (long) stride * values.size(), queue);
        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            layout.writeTo(value, scope.segment(), (long) i * stride);
            if (mirror != null) {
                T old = mirror.set(startIndex + i, value);
                if (old != null && old != value) releaseInstance(old);
            }
        }
        scope.close();
        return scope.completion();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the element at {@code index}.
     * If mirrored, returns the retained object. Otherwise performs a GPU readback into a new
     * instance from {@link #getInstance} -- slow, avoid in hot paths.
     */
    public T read(int index) {
        checkIndex(index);
        if (mirror != null) return mirror.get(index);
        return read(index, getInstance());
    }

    /**
     * Reads into a provided instance. Useful when the caller manages its own object lifecycle.
     */
    public T read(int index, T target) {
        checkIndex(index);
        if (mirror != null) return mirror.get(index);
        System.err.println("WARNING: TypedVkBuffer.read() without mirror performs a GPU readback. " +
                "Prefer mirrored=true for frequent reads.");
        try (BufferReadScope scope = buffer.acquireRead((long) index * stride, stride, null)) {
            layout.readFrom(target, scope.segment(), 0);
        }
        return target;
    }

    /**
     * Bulk read into a caller-supplied list. One acquired region for the full range when not
     * mirrored. The list must have at least {@code length} elements.
     */
    public void read(int startIndex, int length, List<T> targets) {
        checkIndex(startIndex);
        checkIndex(startIndex + length - 1);
        if (mirror != null) {
            for (int i = 0; i < length; i++) targets.set(i, mirror.get(startIndex + i));
            return;
        }
        System.err.println("WARNING: TypedVkBuffer.read() without mirror performs a GPU readback. " +
                "Prefer mirrored=true for frequent reads.");
        try (BufferReadScope scope = buffer.acquireRead((long) startIndex * stride, (long) stride * length, null)) {
            for (int i = 0; i < length; i++) {
                layout.readFrom(targets.get(i), scope.segment(), (long) i * stride);
            }
        }
    }

    /**
     * Bulk read returning a {@link List}.
     * If mirrored, returns an unmodifiable sublist view of the mirror -- zero allocation, zero GPU
     * cost. If not mirrored, performs one readback into new instances from {@link #getInstance}.
     */
    public List<T> read(int startIndex, int length) {
        checkIndex(startIndex);
        checkIndex(startIndex + length - 1);
        if (mirror != null) return Collections.unmodifiableList(mirror.subList(startIndex, startIndex + length));
        ArrayList<T> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) result.add(getInstance());
        read(startIndex, length, result);
        return result;
    }

    // -------------------------------------------------------------------------
    // GPU copy
    // -------------------------------------------------------------------------

    public void copyTo(TypedVkBuffer<T> dst, int srcIndex, int dstIndex, int elementCount, VkQueue queue) {
        buffer.copyTo(dst.buffer, (long) srcIndex * stride, (long) dstIndex * stride,
                (long) elementCount * stride, queue);
    }

    public GpuCompletion copyToAsync(TypedVkBuffer<T> dst, int srcIndex, int dstIndex, int elementCount, VkQueue queue) {
        return buffer.copyToAsync(dst.buffer, (long) srcIndex * stride, (long) dstIndex * stride,
                (long) elementCount * stride, queue);
    }

    @Override
    public void close() {
        if (mirror != null) {
            for (T t : mirror) {
                if (t != null) releaseInstance(t);
            }
            mirror.clear();
        }
        buffer.close();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= count)
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for count " + count);
    }
}
