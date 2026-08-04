package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import io.github.yetyman.vulkan.graph.edges.FeedbackEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.scheduling.QueueSubmission;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages persistent resource rings and resolves feedback edges to the correct
 * physical resource copy for each frame.
 *
 * Each frame, call {@link #advanceFrame(long)} to update the ring indices.
 * Then call {@link #resolveCurrentResource(String)} to get the write target
 * and {@link #resolvePreviousResource(String, int)} to get a read target.
 *
 * Synchronization: before advancing, waits on each ring's timeline semaphore to ensure
 * the slot being written to is no longer in use by the GPU. After the frame's GPU work
 * completes, the caller must signal the semaphores via {@link #getSignalValue(long)}.
 */
public class PersistentResourceManager {

    private final Map<String, PersistentResourceRing<? extends GraphResource>> rings;
    private long currentFrameGeneration = 0;

    public PersistentResourceManager(Map<String, PersistentResourceRing<? extends GraphResource>> rings) {
        this.rings = new HashMap<>(rings);
    }

    /**
     * Initializes timeline semaphores for all rings. Call once after construction.
     *
     * @param device the logical device
     * @param arena arena for semaphore allocation (must outlive the rings)
     */
    public void initializeSemaphores(VkDevice device, Arena arena) {
        for (PersistentResourceRing<?> ring : rings.values()) {
            if (ring.semaphore() == null) {
                VkTimelineSemaphore sem = VkTimelineSemaphore.create(device, 0, arena);
                ring.setSemaphore(sem);
            }
        }
    }

    /**
     * Advances the frame counter and waits for ring slots to be safe.
     * Call at the start of each frame's execution.
     *
     * This performs CPU-side waits on timeline semaphores to ensure that the ring slot
     * about to be written is no longer being read by the GPU. For framesBack=1 with
     * 2 frames in flight, this is a no-op (the slot was last written 2 frames ago and
     * the fence for that frame has already been waited on). For framesBack=2 with 3
     * frames in flight, this may block briefly.
     */
    public void advanceFrame(long frameGeneration) {
        this.currentFrameGeneration = frameGeneration;

        // Wait for each ring's write slot to be safe
        for (PersistentResourceRing<?> ring : rings.values()) {
            ring.waitForSlot(frameGeneration);
            ring.recordWrite(frameGeneration);
        }
    }

    /**
     * Returns the timeline semaphore signal value for the current frame.
     * The executor should signal all ring semaphores with this value after the frame's
     * GPU work completes (typically by adding it to the last queue submission).
     */
    public long getSignalValue(long frameGeneration) {
        return frameGeneration;
    }

    /**
     * Adds timeline semaphore signals for all rings to the given queue submission.
     * Call this on the last submission of the frame to signal that the frame's GPU work
     * is complete and ring slots can be reused.
     */
    public void addSignalsToSubmission(QueueSubmission submission, long frameGeneration) {
        for (PersistentResourceRing<?> ring : rings.values()) {
            VkTimelineSemaphore sem = ring.semaphore();
            if (sem != null) {
                submission.signalTimeline(sem, frameGeneration);
            }
        }
    }

    /**
     * Returns the current frame's write target for a persistent resource.
     *
     * @param name resource name
     * @return the GraphResource to write to this frame, or null if not a persistent resource
     */
    public GraphResource resolveCurrentResource(String name) {
        PersistentResourceRing<? extends GraphResource> ring = rings.get(name);
        if (ring == null) return null;
        return ring.current(currentFrameGeneration);
    }

    /**
     * Returns a previous frame's read target for a persistent resource.
     *
     * @param name resource name
     * @param framesBack how many frames back (1 = previous frame)
     * @return the GraphResource from N frames ago, or null if not a persistent resource
     */
    public GraphResource resolvePreviousResource(String name, int framesBack) {
        PersistentResourceRing<? extends GraphResource> ring = rings.get(name);
        if (ring == null) return null;
        return ring.previous(currentFrameGeneration, framesBack);
    }

    /**
     * Resolves all feedback edges for the given nodes, returning a map of
     * (node name + resource name) -> resolved GraphResource for the previous frame copy.
     */
    public Map<String, GraphResource> resolveFeedbackEdges(List<RenderNode> nodes) {
        Map<String, GraphResource> resolved = new HashMap<>();
        for (RenderNode node : nodes) {
            for (FeedbackEdge edge : node.feedbackReads()) {
                String resName = edge.resource().name();
                GraphResource previous = resolvePreviousResource(resName, edge.framesBack());
                if (previous != null) {
                    String key = resName + ":" + edge.framesBack();
                    resolved.put(key, previous);
                }
            }
        }
        return resolved;
    }

    /** @return the ring for a given resource name, or null */
    public PersistentResourceRing<? extends GraphResource> ring(String name) {
        return rings.get(name);
    }

    /** @return true if the named resource is managed as a persistent ring */
    public boolean isPersistent(String name) {
        return rings.containsKey(name);
    }

    /** @return current frame generation */
    public long currentFrameGeneration() { return currentFrameGeneration; }

    /** @return true if any rings have semaphores configured */
    public boolean hasSemaphores() {
        for (PersistentResourceRing<?> ring : rings.values()) {
            if (ring.semaphore() != null) return true;
        }
        return false;
    }

    /** Closes all timeline semaphores owned by the rings */
    public void closeSemaphores() {
        for (PersistentResourceRing<?> ring : rings.values()) {
            VkTimelineSemaphore sem = ring.semaphore();
            if (sem != null) {
                sem.close();
                ring.setSemaphore(null);
            }
        }
    }
}
