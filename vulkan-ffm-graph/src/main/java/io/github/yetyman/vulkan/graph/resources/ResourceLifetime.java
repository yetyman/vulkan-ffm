package io.github.yetyman.vulkan.graph.resources;

/**
 * Tracks the first-write to last-read interval for a resource version within the graph.
 * Used by the aliaser to determine which transient resources can share physical memory.
 *
 * For multi-queue graphs, pass indices alone are insufficient to determine overlap because
 * passes on different queues may execute concurrently. Each usage records both the pass index
 * and the queue family. Two lifetimes can only be safely aliased if they are provably
 * non-overlapping in the partial order defined by semaphore edges between queues.
 *
 * The {@link #overlaps(ResourceLifetime, PartialOrder)} method uses the partial order to
 * determine true overlap. The simpler {@link #overlaps(ResourceLifetime)} method is a
 * conservative fallback that assumes any concurrent queue usage overlaps.
 */
public class ResourceLifetime {

    private int firstWritePass = -1;
    private int lastReadPass = -1;
    private int firstWriteQueue = -1;
    private int lastReadQueue = -1;

    /** Records a write at the given pass index on the given queue family */
    public void recordWrite(int passIndex, int queueFamily) {
        if (firstWritePass < 0 || passIndex < firstWritePass) {
            firstWritePass = passIndex;
            firstWriteQueue = queueFamily;
        }
        if (passIndex > lastReadPass) {
            lastReadPass = passIndex;
            lastReadQueue = queueFamily;
        }
    }

    /** Records a write at the given pass index (single-queue convenience) */
    public void recordWrite(int passIndex) {
        recordWrite(passIndex, -1);
    }

    /** Records a read at the given pass index on the given queue family */
    public void recordRead(int passIndex, int queueFamily) {
        if (passIndex > lastReadPass) {
            lastReadPass = passIndex;
            lastReadQueue = queueFamily;
        }
    }

    /** Records a read at the given pass index (single-queue convenience) */
    public void recordRead(int passIndex) {
        recordRead(passIndex, -1);
    }

    /** @return pass index of first write, or -1 if never written */
    public int firstWritePass() { return firstWritePass; }

    /** @return pass index of last read (or write), or -1 if never used */
    public int lastReadPass() { return lastReadPass; }

    /** @return queue family of first write, or -1 if unknown */
    public int firstWriteQueue() { return firstWriteQueue; }

    /** @return queue family of last read, or -1 if unknown */
    public int lastReadQueue() { return lastReadQueue; }

    /**
     * Conservative overlap check using linear pass indices only.
     * Two lifetimes overlap if their [firstWrite, lastRead] intervals intersect.
     * This is correct for single-queue graphs. For multi-queue graphs, use
     * {@link #overlaps(ResourceLifetime, PartialOrder)} instead.
     */
    public boolean overlaps(ResourceLifetime other) {
        if (firstWritePass < 0 || other.firstWritePass < 0) return false;
        return firstWritePass <= other.lastReadPass && other.firstWritePass <= lastReadPass;
    }

    /**
     * Partial-order-aware overlap check for multi-queue graphs.
     * Two lifetimes do NOT overlap only if one is provably completed before the other starts,
     * as determined by the semaphore-derived partial order between queues.
     *
     * If the two lifetimes are on different queues with no ordering relationship between them,
     * they are considered overlapping (conservative -- cannot safely alias).
     *
     * @param other the other lifetime to check against
     * @param order the partial order derived from inter-queue semaphore edges
     * @return true if the lifetimes may overlap (cannot be aliased)
     */
    public boolean overlaps(ResourceLifetime other, PartialOrder order) {
        if (firstWritePass < 0 || other.firstWritePass < 0) return false;

        // Same queue: use simple interval overlap
        boolean sameQueue = (firstWriteQueue == other.firstWriteQueue && lastReadQueue == other.lastReadQueue
            && firstWriteQueue >= 0);
        if (sameQueue) {
            return firstWritePass <= other.lastReadPass && other.firstWritePass <= lastReadPass;
        }

        // Different queues: check if one lifetime is provably before the other
        // "this" is before "other" if this.lastReadPass happens-before other.firstWritePass
        boolean thisBefore = order.happensBefore(lastReadPass, lastReadQueue, other.firstWritePass, other.firstWriteQueue);
        if (thisBefore) return false;

        // "other" is before "this"
        boolean otherBefore = order.happensBefore(other.lastReadPass, other.lastReadQueue, firstWritePass, firstWriteQueue);
        if (otherBefore) return false;

        // No ordering relationship -- conservatively assume overlap
        return true;
    }

    /** @return true if this lifetime has been populated with at least one usage */
    public boolean isValid() {
        return firstWritePass >= 0;
    }

    /**
     * Partial order interface for multi-queue lifetime reasoning.
     * Implementations derive ordering from inter-queue semaphore edges in the compiled graph.
     */
    public interface PartialOrder {
        /**
         * Returns true if pass A on queueA is guaranteed to complete before pass B on queueB starts.
         * This is determined by the semaphore edges between queues.
         *
         * @param passA pass index of the first event
         * @param queueA queue family of the first event (-1 if unknown)
         * @param passB pass index of the second event
         * @param queueB queue family of the second event (-1 if unknown)
         * @return true if A happens-before B
         */
        boolean happensBefore(int passA, int queueA, int passB, int queueB);
    }
}
