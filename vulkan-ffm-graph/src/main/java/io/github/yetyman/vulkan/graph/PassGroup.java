package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.nodes.RenderNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dynamic group of passes whose membership can change between submissions.
 * Used for runtime-variable pass counts (N shadow maps, N material passes, N chunks).
 *
 * Each frame, the application clears the group and re-populates it with the current set
 * of passes. The graph detects when the count changes and recompiles (cache miss on PassMask).
 * When the count is the same as a previous frame, the cached compiled graph is reused.
 *
 * Usage:
 * <pre>
 *   PassGroup shadows = graph.addPassGroup("shadows", 16);
 *
 *   // Each frame:
 *   shadows.clear();
 *   for (int i = 0; i < visibleLightCount; i++) {
 *       shadows.add(ComputePassNode.builder()...build());
 *   }
 *   // Graph automatically includes group members in compilation
 * </pre>
 */
public class PassGroup {

    private final String name;
    private final int maxCount;
    private final List<RenderNode> nodes = new ArrayList<>();
    private int lastCount = -1;

    PassGroup(String name, int maxCount) {
        this.name = name;
        this.maxCount = maxCount;
    }

    /** @return the group name */
    public String name() { return name; }

    /** @return the maximum expected pass count (for pre-allocation) */
    public int maxCount() { return maxCount; }

    /** @return the current passes in this group (unmodifiable view) */
    public List<RenderNode> nodes() { return Collections.unmodifiableList(nodes); }

    /** @return the current number of passes */
    public int count() { return nodes.size(); }

    /** @return true if the count changed since last frame (triggers recompile) */
    public boolean countChanged() { return nodes.size() != lastCount; }

    /** Clears all passes from the group. Call at the start of each frame before re-populating. */
    public void clear() {
        lastCount = nodes.size();
        nodes.clear();
    }

    /** Adds a pass to the group for this frame. */
    public void add(RenderNode node) {
        if (nodes.size() >= maxCount) {
            throw new RenderGraphException(
                "PassGroup '" + name + "' exceeded maxCount=" + maxCount +
                ". Increase maxCount or reduce pass count.");
        }
        nodes.add(node);
    }
}
