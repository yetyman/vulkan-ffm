package io.github.yetyman.vulkan.graph.edges;

import io.github.yetyman.vulkan.graph.nodes.RenderNode;

/**
 * A manual ordering constraint between two nodes without resource involvement.
 * Used for GPU timer queries, debug markers, or external API calls that need ordering.
 *
 * Manual edges:
 * - Participate in topological sort like resource edges
 * - Do NOT insert barriers (no resource to transition)
 * - DO affect queue assignment (semaphore inserted if cross-queue)
 * - Can be conditionally active via a predicate
 */
public class DependencyEdge {

    private final RenderNode from;
    private final RenderNode to;
    private final String reason;
    private volatile boolean active = true;

    private DependencyEdge(RenderNode from, RenderNode to, String reason) {
        this.from = from;
        this.to = to;
        this.reason = reason;
    }

    /** Creates a dependency edge: 'from' must execute before 'to' */
    public static DependencyEdge of(RenderNode from, RenderNode to, String reason) {
        return new DependencyEdge(from, to, reason);
    }

    /** Creates a dependency edge without a reason string */
    public static DependencyEdge of(RenderNode from, RenderNode to) {
        return new DependencyEdge(from, to, null);
    }

    /** @return the node that must execute first */
    public RenderNode from() { return from; }

    /** @return the node that must execute after */
    public RenderNode to() { return to; }

    /** @return human-readable reason for this dependency (for debugging) */
    public String reason() { return reason; }

    /** @return true if this edge is currently active */
    public boolean isActive() { return active; }

    /** Removes this edge (deactivates it, triggers recompile on next frame) */
    public void remove() { this.active = false; }

    /** Reactivates a removed edge */
    public void restore() { this.active = true; }
}
