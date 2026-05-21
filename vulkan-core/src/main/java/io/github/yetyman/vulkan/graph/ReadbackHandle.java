package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Handle for GPU-to-CPU readback of a resource.
 * The graph stages the data to a CPU-visible buffer after the last writer completes.
 * Data is available one frame after the write (inherent GPU latency).
 *
 * Usage:
 * <pre>
 *   ReadbackHandle rb = graph.addReadback("convergence")
 *       .source(convergenceBuffer)
 *       .offset(0).size(4)
 *       .build();
 *
 *   // After execution:
 *   if (rb.isReady()) {
 *       float metric = rb.readFloat(0);
 *   }
 * </pre>
 */
public class ReadbackHandle {

    private final String name;
    private final GraphResource source;
    private final long offset;
    private final long size;
    private final ReadbackFrequency frequency;

    // Staging buffer (persistently mapped)
    private MemorySegment stagingBuffer;
    private MemorySegment mappedMemory;
    private volatile boolean ready = false;
    private volatile boolean requested = false;

    private ReadbackHandle(Builder b) {
        this.name = b.name;
        this.source = b.source;
        this.offset = b.offset;
        this.size = b.size;
        this.frequency = b.frequency;
    }

    public static Builder builder() { return new Builder(); }

    /** @return true if readback data is available for reading */
    public boolean isReady() { return ready; }

    /** Requests a readback (for ON_DEMAND frequency) */
    public void request() { this.requested = true; }

    /** @return true if a readback has been requested and not yet fulfilled */
    public boolean isRequested() { return requested; }

    /** @return whether this readback should execute this submission */
    public boolean shouldExecute(long submissionIndex) {
        return switch (frequency) {
            case EVERY_SUBMISSION -> true;
            case ON_DEMAND -> requested;
            case ONCE -> !ready;
        };
    }

    /** Marks the readback as complete (called by executor after copy finishes) */
    public void markReady() {
        this.ready = true;
        this.requested = false;
    }

    /** Reads a float at the given byte offset from the staged data */
    public float readFloat(long byteOffset) {
        if (!ready || mappedMemory == null) return 0;
        return mappedMemory.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, byteOffset);
    }

    /** Reads an int at the given byte offset from the staged data */
    public int readInt(long byteOffset) {
        if (!ready || mappedMemory == null) return 0;
        return mappedMemory.get(java.lang.foreign.ValueLayout.JAVA_INT, byteOffset);
    }

    /** @return the raw mapped memory segment for custom reads */
    public MemorySegment mappedMemory() { return mappedMemory; }

    /** Sets the staging buffer and mapped memory (called during graph allocation) */
    public void setStagingBuffer(MemorySegment buffer, MemorySegment mapped) {
        this.stagingBuffer = buffer;
        this.mappedMemory = mapped;
    }

    /** @return resource name */
    public String name() { return name; }
    /** @return source resource */
    public GraphResource source() { return source; }
    /** @return byte offset into source */
    public long offset() { return offset; }
    /** @return bytes to read */
    public long size() { return size; }
    /** @return readback frequency */
    public ReadbackFrequency frequency() { return frequency; }
    /** @return staging buffer handle */
    public MemorySegment stagingBuffer() { return stagingBuffer; }

    public enum ReadbackFrequency {
        EVERY_SUBMISSION,
        ON_DEMAND,
        ONCE
    }

    public static class Builder {
        private String name;
        private GraphResource source;
        private long offset = 0;
        private long size;
        private ReadbackFrequency frequency = ReadbackFrequency.EVERY_SUBMISSION;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder source(GraphResource source) { this.source = source; return this; }
        public Builder offset(long offset) { this.offset = offset; return this; }
        public Builder size(long size) { this.size = size; return this; }
        public Builder frequency(ReadbackFrequency freq) { this.frequency = freq; return this; }

        public ReadbackHandle build() {
            if (name == null) throw new IllegalStateException("name not set");
            if (source == null) throw new IllegalStateException("source not set");
            if (size <= 0) throw new IllegalStateException("size must be > 0");
            return new ReadbackHandle(this);
        }
    }
}
