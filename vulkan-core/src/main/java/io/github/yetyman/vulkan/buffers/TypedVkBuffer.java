package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkQueue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed array view over any {@link ManagedBuffer}.
 * Elements are fixed-stride, serialized via {@link BufferWritable}.
 * Subclass and implement {@link #getInstance} to control T allocation (pooled or fresh).
 * Override {@link #releaseInstance} to return instances to a pool on eviction or close.
 * When constructed with {@code mirrored=true}, written objects are retained and reads are zero-cost.
 * When not mirrored, reads perform a GPU readback — slow, avoid in hot paths.
 */
public abstract class TypedVkBuffer<T extends BufferWritable> implements AutoCloseable {
    private final ManagedBuffer buffer;
    private final int stride;
    private final int count;
    private final ArrayList<T> mirror;

    public TypedVkBuffer(ManagedBuffer buffer, int byteSize, int count, boolean mirrored) {
        if ((long) byteSize * count > buffer.size())
            throw new IllegalArgumentException("Buffer too small: need " + ((long) byteSize * count) + ", have " + buffer.size());
        this.buffer = buffer;
        this.stride = byteSize;
        this.count = count;
        if (mirrored) {
            this.mirror = new ArrayList<>(count);
            for (int i = 0; i < count; i++) mirror.add(null);
        } else {
            this.mirror = null;
        }
    }

    /** @return a T instance to populate during a read. May be pooled or freshly allocated. */
    protected abstract T getInstance();

    /** Called when a mirrored slot is overwritten or on close. No-op by default. */
    protected void releaseInstance(T instance) {}

    public int count() { return count; }
    public int stride() { return stride; }
    public ManagedBuffer buffer() { return buffer; }

    // -------------------------------------------------------------------------
    // Single-element write
    // -------------------------------------------------------------------------

    public void write(int index, T value) {
        TransferCompletion tc = writeAsync(index, value);
        tc.await();
        tc.close();
    }

    public TransferCompletion writeAsync(int index, T value) {
        checkIndex(index);
        ByteBuffer scratch = ByteBuffer.allocate(stride);
        value.writeTo(scratch);
        scratch.rewind();
        if (mirror != null) {
            T old = mirror.set(index, value);
            if (old != null && old != value) releaseInstance(old);
        }
        return buffer.writeAsync(scratch, (long) index * stride);
    }

    // -------------------------------------------------------------------------
    // Bulk write
    // -------------------------------------------------------------------------

    public void write(List<T> values, int startIndex) {
        TransferCompletion tc = writeAsync(values, startIndex);
        tc.await();
        tc.close();
    }

    public TransferCompletion writeAsync(List<T> values, int startIndex) {
        checkIndex(startIndex);
        checkIndex(startIndex + values.size() - 1);
        ByteBuffer scratch = ByteBuffer.allocate(stride * values.size());
        for (int i = 0; i < values.size(); i++) {
            values.get(i).writeTo(scratch);
            if (mirror != null) {
                T old = mirror.set(startIndex + i, values.get(i));
                if (old != null && old != values.get(i)) releaseInstance(old);
            }
        }
        scratch.rewind();
        return buffer.writeAsync(scratch, (long) startIndex * stride);
    }

    // -------------------------------------------------------------------------
    // Single-element read
    // -------------------------------------------------------------------------

    /**
     * Returns the element at {@code index}.
     * If mirrored, returns the retained object. Otherwise performs a GPU readback into a new instance
     * from {@link #getInstance} — slow, avoid in hot paths.
     */
    public T read(int index) {
        checkIndex(index);
        if (mirror != null) return mirror.get(index);
        System.err.println("WARNING: TypedVkBuffer.read() without mirror performs a GPU readback. " +
                           "Prefer mirrored=true for frequent reads.");
        T instance = getInstance();
        instance.readFrom(buffer.read((long) index * stride, stride));
        return instance;
    }

    /** Reads into a provided instance. Useful when the caller manages its own object lifecycle. */
    public T read(int index, T target) {
        checkIndex(index);
        if (mirror != null) return mirror.get(index);
        System.err.println("WARNING: TypedVkBuffer.read() without mirror performs a GPU readback. " +
                           "Prefer mirrored=true for frequent reads.");
        target.readFrom(buffer.read((long) index * stride, stride));
        return target;
    }

    // -------------------------------------------------------------------------
    // Bulk read
    // -------------------------------------------------------------------------

    /**
     * Bulk read into a caller-supplied list. Single GPU readback for the full range when not mirrored.
     * Elements are read into existing list entries via {@link BufferWritable#readFrom} — list must have at least {@code length} elements.
     * Prefer this over repeated single reads when not mirrored.
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
        ByteBuffer raw = buffer.read((long) startIndex * stride, (long) stride * length);
        for (int i = 0; i < length; i++) {
            targets.get(i).readFrom(raw.slice(i * stride, stride));
        }
    }

    /**
     * Bulk read returning a {@link List}.
     * If mirrored, returns an unmodifiable sublist view of the mirror — zero allocation, zero GPU cost.
     * If not mirrored, performs a single GPU readback into a new list of instances from {@link #getInstance}.
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

    public void copyTo(TypedVkBuffer<T> dst, int srcIndex, int dstIndex, int elementCount,
                       VkQueue queue, VkCommandPool commandPool) {
        buffer.copyTo(dst.buffer, (long) srcIndex * stride, (long) dstIndex * stride,
                      (long) elementCount * stride, queue, commandPool);
    }

    public TransferCompletion copyToAsync(TypedVkBuffer<T> dst, int srcIndex, int dstIndex, int elementCount,
                                          VkQueue queue, VkCommandPool commandPool) {
        return buffer.copyToAsync(dst.buffer, (long) srcIndex * stride, (long) dstIndex * stride,
                                  (long) elementCount * stride, queue, commandPool);
    }

    @Override
    public void close() {
        if (mirror != null) {
            for (T t : mirror) { if (t != null) releaseInstance(t); }
            mirror.clear();
        }
        buffer.close();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= count)
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for count " + count);
    }
}
