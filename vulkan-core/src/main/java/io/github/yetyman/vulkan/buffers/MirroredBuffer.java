package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorator giving the CPU immediate, random-access-readable visibility into the same data that
 * is present (or will be present) on the GPU side of the wrapped {@link IBuffer}, regardless of
 * what strategy the wrapped buffer uses — device-local with staging, ReBAR, mapped, anything.
 *
 * <p>The mirror is backed by a persistently-mapped, host-visible {@code VkBuffer} — not a plain
 * heap {@code ByteBuffer} — so that when the wrapped buffer is staging-based, a write only copies
 * the data once (into the mirror's own mapped memory) and the GPU-side copy is issued directly
 * from that same memory. No second, redundant CPU copy into a separate throwaway staging buffer.
 * The mirror is still a normal random-access {@link ByteBuffer} to callers via {@link #mirror()}
 * and {@link #read}.
 *
 * <p>Only safe for CPU-driven writes without a concurrent GPU writer — the mirror is not updated
 * by GPU-side writes automatically. When the wrapped buffer is written by the GPU (e.g. a compute
 * pass), call {@link #refreshFromGpu} to pull the current GPU-side contents back into the mirror
 * before reading. There is no detection of concurrent modification — callers are responsible for
 * sequencing refreshes after the GPU work that produced the data has completed.
 */
public class MirroredBuffer implements IBuffer {
    private final IBuffer inner;
    private final VkDevice device;
    private final Arena arena;
    private final VkBuffer mirrorBuffer;
    private final MemorySegment mirrorMapped;
    private ByteBuffer mirror;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Wraps an existing {@link IBuffer} of any strategy with a CPU mirror.
     *
     * @param device device the wrapped buffer belongs to — needed to allocate the mirror's own
     *               backing {@code VkBuffer}
     */
    public MirroredBuffer(VkDevice device, IBuffer inner) {
        this.inner = inner;
        this.device = device;
        this.arena = Arena.ofShared();
        try {
            this.mirrorBuffer = VkBuffer.builder()
                    .device(device).size(inner.size()).transferSrc().transferDst().hostVisible().build(arena);
            this.mirrorMapped = mirrorBuffer.map(arena);
            this.mirror = mirrorMapped.asByteBuffer();
        } catch (Exception e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Convenience constructor: allocates a new device-local buffer via {@link BufferFactory}
     * and wraps it with a mirror — equivalent to the former {@code MemoryStrategy.DEVICE_LOCAL_MIRRORED}.
     */
    public MirroredBuffer(VkDevice device, long size, BufferUsage usage, VkQueue transferQueue) {
        this(device, BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, size, usage, device, transferQueue));
    }

    /**
     * @return the wrapped buffer.
     */
    public IBuffer inner() {
        return inner;
    }

    @Override
    public long size() {
        return inner.size();
    }

    @Override
    public BufferUsage usage() {
        return inner.usage();
    }

    @Override
    public MemorySegment handle() {
        return inner.handle();
    }

    @Override
    public void write(ByteBuffer data, long offset, VkQueue queue) {
        GpuCompletion tc = writeAsync(data, offset, queue);
        TransferBatchManager.flush(device, queue);
        tc.await();
        tc.close();
    }

    @Override
    public GpuCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        int length = data.remaining();
        mirrorWrite(data, offset);

        if (inner instanceof ManagedBuffer managed) {
            // Data already sits in the mirror's own mapped memory — issue the GPU copy directly
            // from there instead of re-staging the same bytes through the inner buffer's own
            // TransferStrategy (which would copy them a second time).
            return managed.copyFromExternal(mirrorBuffer.handle(), offset, offset, length, queue);
        }
        // Fallback for IBuffer implementations that aren't a ManagedBuffer (e.g. RingBuffer,
        // SuballocatorBuffer, custom implementations) — no raw-handle copy entry point available,
        // so route through the normal write path. mirrorWrite() operated on a duplicate view,
        // so data's own position/limit are untouched here.
        return inner.writeAsync(data, offset, queue);
    }

    private void mirrorWrite(ByteBuffer data, long offset) {
        ByteBuffer slice = data.duplicate();
        mirror.position((int) offset);
        mirror.put(slice);
        mirror.rewind();
    }

    /**
     * Hands back the mirror's own mapped memory. The caller's write therefore lands in the mirror
     * (making it immediately CPU-readable) and, on commit, the GPU copy is issued directly from
     * that same memory with no second CPU copy.
     */
    @Override
    public BufferWriteScope acquireWrite(long offset, long size, VkQueue queue) {
        if (offset + size > inner.size()) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        MemorySegment target = mirrorMapped.asSlice(offset, size);
        if (inner instanceof ManagedBuffer managed) {
            return BufferWriteScope.of(target, offset, size,
                    () -> managed.copyFromExternal(mirrorBuffer.handle(), offset, offset, size, queue));
        }
        // No raw-handle copy entry point on the wrapped buffer: push the mirror's bytes through
        // the normal write path, which costs one extra CPU copy.
        return BufferWriteScope.of(target, offset, size,
                () -> inner.writeAsync(target.asByteBuffer(), offset, queue));
    }

    /**
     * Zero-cost: returns the mirror's memory directly. Reflects GPU-side writes only after
     * {@link #refreshFromGpu}.
     */
    @Override
    public BufferReadScope acquireRead(long offset, long size, VkQueue queue) {
        return BufferReadScope.of(mirrorMapped.asSlice(offset, size), offset, size, null);
    }

    /**
     * Pulls the wrapped buffer's current GPU-side contents back into the mirror.
     * Call this after GPU-side writes (e.g. a compute pass writing this buffer) complete,
     * before relying on {@link #read} or {@link #mirror} to reflect that data.
     * There is no automatic detection of GPU writes — callers must call this explicitly
     * and only after the producing GPU work has completed (e.g. after awaiting a fence/semaphore).
     */
    public GpuCompletion refreshFromGpu(long offset, long length, VkQueue queue) {
        if (inner instanceof ManagedBuffer managed) {
            return managed.copyToExternal(mirrorBuffer.handle(), offset, offset, length, queue);
        }
        // Fallback: read back through the normal (synchronous, potentially stalling) read path
        // and copy the result into the mirror ourselves.
        ByteBuffer fresh = inner.read(offset, length);
        mirror.position((int) offset);
        mirror.put(fresh);
        mirror.rewind();
        return GpuCompletion.completed();
    }

    /**
     * @return the raw CPU mirror ByteBuffer — random-access, immediately readable, backed by
     * native mapped memory. For typed buffer views.
     */
    public ByteBuffer mirror() {
        return mirror;
    }

    /**
     * @return a read-only view of the CPU mirror — zero GPU cost, zero extra copy.
     */
    @Override
    public ByteBuffer read(long offset, long length) {
        return mirror.slice((int) offset, (int) length).asReadOnlyBuffer();
    }

    @Override
    public void flush() {
        inner.flush();
    }

    @Override
    public void copyTo(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        inner.copyTo(dst, srcOffset, dstOffset, length, queue);
    }

    @Override
    public GpuCompletion copyToAsync(IBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue) {
        return inner.copyToAsync(dst, srcOffset, dstOffset, length, queue);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                inner.close();
                if (mirrorBuffer != null) mirrorBuffer.close();
            } finally {
                arena.close();
                mirror = null;
            }
        }
    }
}
