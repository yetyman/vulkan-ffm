package io.github.yetyman.vulkan.mesh;

/**
 * A half-open range of elements, {@code [firstElement, firstElement + elementCount)}.
 *
 * <p>The unit of partial work throughout the module: a transcode window, a dirty range, a
 * progressive upload chunk. Its presence in every data-movement signature is what makes partial
 * residency, parallel fan-out, and incremental update all fall out of the same shape.
 *
 * @param firstElement first element index, zero-based
 * @param elementCount number of elements; zero is a legitimate empty window
 */
public record ElementWindow(long firstElement, long elementCount) {

    public ElementWindow {
        if (firstElement < 0) throw new IllegalArgumentException("firstElement must be >= 0");
        if (elementCount < 0) throw new IllegalArgumentException("elementCount must be >= 0");
    }

    /**
     * @return a window covering {@code count} elements from zero
     */
    public static ElementWindow all(long count) {
        return new ElementWindow(0, count);
    }

    /**
     * @return an empty window
     */
    public static ElementWindow empty() {
        return new ElementWindow(0, 0);
    }

    /**
     * @return one element
     */
    public static ElementWindow single(long index) {
        return new ElementWindow(index, 1);
    }

    /**
     * @return true if this window contains no elements
     */
    public boolean isEmpty() {
        return elementCount == 0;
    }

    /**
     * @return the element index one past the end of this window
     */
    public long endExclusive() {
        return firstElement + elementCount;
    }

    /**
     * @return the smallest window containing both this and {@code other}. Useful for coalescing
     * dirty ranges: two disjoint edits become one slightly larger upload rather than two.
     */
    public ElementWindow union(ElementWindow other) {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;
        long start = Math.min(firstElement, other.firstElement);
        long end = Math.max(endExclusive(), other.endExclusive());
        return new ElementWindow(start, end - start);
    }

    /**
     * @return this window clamped so it does not extend past {@code limit} elements
     */
    public ElementWindow clampTo(long limit) {
        if (firstElement >= limit) return new ElementWindow(firstElement, 0);
        return new ElementWindow(firstElement, Math.min(elementCount, limit - firstElement));
    }
}
