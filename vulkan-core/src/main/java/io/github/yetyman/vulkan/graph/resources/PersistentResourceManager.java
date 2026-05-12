package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.graph.edges.FeedbackEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;

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
 */
public class PersistentResourceManager {

    private final Map<String, PersistentResourceRing<? extends GraphResource>> rings;
    private long currentFrameGeneration = 0;

    public PersistentResourceManager(Map<String, PersistentResourceRing<? extends GraphResource>> rings) {
        this.rings = new HashMap<>(rings);
    }

    /**
     * Advances the frame counter. Call at the start of each frame's execution.
     */
    public void advanceFrame(long frameGeneration) {
        this.currentFrameGeneration = frameGeneration;
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
     * This allows the executor to substitute the correct physical resource when
     * processing feedback reads.
     *
     * @param nodes the active nodes to resolve feedback edges for
     * @return map of feedback edge resource names to their resolved previous-frame copies
     */
    public Map<String, GraphResource> resolveFeedbackEdges(List<RenderNode> nodes) {
        Map<String, GraphResource> resolved = new HashMap<>();
        for (RenderNode node : nodes) {
            for (FeedbackEdge edge : node.feedbackReads()) {
                String resName = edge.resource().name();
                GraphResource previous = resolvePreviousResource(resName, edge.framesBack());
                if (previous != null) {
                    // Key by resource name -- all nodes reading the same feedback resource
                    // at the same framesBack get the same physical copy
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
}
