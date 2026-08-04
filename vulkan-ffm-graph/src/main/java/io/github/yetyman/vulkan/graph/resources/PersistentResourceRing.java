package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.VkTimelineSemaphore;

import java.util.List;

/**
 * Manages a ring of physical resource copies for persistent/feedback resources.
 * For a resource with framesBack=N, maintains N+1 copies and indexes by
 * frameGeneration % (N+1).
 *
 * Example: framesBack=1 (double buffered) -> 2 copies, alternating each frame.
 * Example: framesBack=2 (triple buffered) -> 3 copies, can read N-2.
 *
 * Synchronization: each slot has a timeline semaphore value representing the frame generation
 * that last wrote to it. Before writing to a slot, the executor must wait until the GPU has
 * finished reading from that slot (i.e., the frame that wrote it has completed). The semaphore
 * is signaled after the frame's submission completes.
 */
public class PersistentResourceRing<T extends GraphResource> {

    private final String name;
    private final List<T> copies;
    private final int framesBack;

    // Timeline semaphore for synchronizing ring slot access.
    // Value N means: frame generation N has finished using this ring's resources.
    // Before writing to slot S, wait until semaphore >= (generation that last wrote S).
    private VkTimelineSemaphore semaphore;
    private final long[] slotWriteGenerations;

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
        this.slotWriteGenerations = new long[copies.size()];
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

    /**
     * Returns the slot index for the current frame's write target.
     */
    public int currentSlotIndex(long frameGeneration) {
        return (int) (frameGeneration % copies.size());
    }

    /**
     * Records that a frame generation is about to write to its slot.
     * The executor should call this after ensuring the slot is safe to write
     * (i.e., after waiting on the semaphore if needed).
     */
    public void recordWrite(long frameGeneration) {
        int index = currentSlotIndex(frameGeneration);
        slotWriteGenerations[index] = frameGeneration;
    }

    /**
     * Returns the frame generation that last wrote to the slot that the current frame
     * wants to write to. The executor must wait until this generation has completed on the GPU
     * before writing.
     *
     * @return the generation that last wrote to the current write slot, or 0 if never written
     */
    public long lastWriteGenerationForCurrentSlot(long frameGeneration) {
        int index = currentSlotIndex(frameGeneration);
        return slotWriteGenerations[index];
    }

    /**
     * Sets the timeline semaphore used for ring synchronization.
     * The semaphore value represents completed frame generations.
     */
    public void setSemaphore(VkTimelineSemaphore semaphore) {
        this.semaphore = semaphore;
    }

    /** @return the timeline semaphore for this ring, or null if not set */
    public VkTimelineSemaphore semaphore() { return semaphore; }

    /**
     * Waits (CPU-side) until the slot for the given frame generation is safe to write.
     * This blocks until the frame that previously wrote to this slot has completed on the GPU.
     *
     * @param frameGeneration the frame about to write
     */
    public void waitForSlot(long frameGeneration) {
        if (semaphore == null) return;
        long lastWrite = lastWriteGenerationForCurrentSlot(frameGeneration);
        if (lastWrite > 0) {
            semaphore.await(lastWrite);
        }
    }

    /**
     * Returns the semaphore value that the executor should signal after the frame's
     * GPU work completes. This is simply the frame generation itself.
     */
    public long signalValue(long frameGeneration) {
        return frameGeneration;
    }

    /** @return all physical copies */
    public List<T> allCopies() { return copies; }
}

