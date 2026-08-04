package io.github.yetyman.vulkan.graph.edges;

import java.lang.foreign.MemorySegment;

/**
 * An external semaphore dependency. Used for imported resources (streaming uploads, async compute)
 * where the graph must wait on or signal a semaphore not managed by the graph itself.
 */
public class SemaphoreEdge {

    private final MemorySegment semaphore;
    private final long waitValue;   // for timeline semaphores; 0 for binary
    private final int stageMask;    // pipeline stage to wait/signal at

    public SemaphoreEdge(MemorySegment semaphore, long waitValue, int stageMask) {
        this.semaphore = semaphore;
        this.waitValue = waitValue;
        this.stageMask = stageMask;
    }

    /** Creates a binary semaphore edge */
    public static SemaphoreEdge binary(MemorySegment semaphore, int stageMask) {
        return new SemaphoreEdge(semaphore, 0, stageMask);
    }

    /** Creates a timeline semaphore edge */
    public static SemaphoreEdge timeline(MemorySegment semaphore, long value, int stageMask) {
        return new SemaphoreEdge(semaphore, value, stageMask);
    }

    /** @return the semaphore handle */
    public MemorySegment semaphore() { return semaphore; }

    /** @return timeline wait/signal value (0 for binary) */
    public long waitValue() { return waitValue; }

    /** @return pipeline stage mask for the wait/signal point */
    public int stageMask() { return stageMask; }

    /** @return true if this is a timeline semaphore edge */
    public boolean isTimeline() { return waitValue > 0; }
}
