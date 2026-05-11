package io.github.yetyman.vulkan.graph.resources;

import java.util.List;

/**
 * Manages a ring of physical resource copies for persistent/feedback resources.
 * For a resource with framesBack=N, maintains N+1 copies and indexes by
 * frameGeneration % (N+1).
 *
 * Example: framesBack=1 (double buffered) -> 2 copies, alternating each frame.
 * Example: framesBack=2 (triple buffered) -> 3 copies, can read N-2.
 */
public class PersistentResourceRing<T extends GraphResource> {

    private final String name;
    private final List<T> copies;
    private final int framesBack;

    /**
     * @param name resource name
     * @param copies the physical resource copies (size = framesBack + 1)
     * @param framesBack how many frames back can be read (1 = previous, 2 = two frames ago)
     */
    public PersistentResourceRing(String name, List<T> copies, int framesBack) {
        if (copies.size() != framesBack + 1) {
            throw new IllegalArgumentException(
                "Expected " + (framesBack + 1) + " copies for framesBack=" + framesBack +
                ", got " + copies.size());
        }
        this.name = name;
        this.copies = List.copyOf(copies);
        this.framesBack = framesBack;
    }

    /** @return resource name */
    public String name() { return name; }

    /** @return how many frames back can be read */
    public int framesBack() { return framesBack; }

    /** @return total number of physical copies */
    public int copyCount() { return copies.size(); }

    /**
     * Returns the resource copy to write to for the current frame.
     *
     * @param frameGeneration monotonic frame counter
     */
    public T current(long frameGeneration) {
        int index = (int) (frameGeneration % copies.size());
        return copies.get(index);
    }

    /**
     * Returns the resource copy to read from N frames ago.
     *
     * @param frameGeneration current frame's generation counter
     * @param framesAgo how many frames back to read (1..framesBack)
     */
    public T previous(long frameGeneration, int framesAgo) {
        if (framesAgo < 1 || framesAgo > framesBack) {
            throw new IllegalArgumentException(
                "framesAgo=" + framesAgo + " out of range [1.." + framesBack + "]");
        }
        int index = (int) ((frameGeneration - framesAgo + copies.size()) % copies.size());
        return copies.get(index);
    }

    /** @return all physical copies */
    public List<T> allCopies() { return copies; }
}
