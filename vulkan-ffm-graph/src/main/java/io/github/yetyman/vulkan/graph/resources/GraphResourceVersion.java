package io.github.yetyman.vulkan.graph.resources;

/**
 * A versioned reference to a graph resource, used for feedback edges (TAA history, ping-pong).
 * The graph maintains framesBack+1 physical copies and indexes by frameGeneration % (N+1).
 */
public class GraphResourceVersion {

    private final GraphResource resource;
    private final int frameOffset;  // 0 = current, -1 = previous, -2 = two frames ago
    private final int version;      // monotonic write counter

    public GraphResourceVersion(GraphResource resource, int frameOffset, int version) {
        this.resource = resource;
        this.frameOffset = frameOffset;
        this.version = version;
    }

    /** @return the underlying resource */
    public GraphResource resource() { return resource; }

    /** @return frame offset (0=current, -1=previous, etc.) */
    public int frameOffset() { return frameOffset; }

    /** @return monotonic write version counter */
    public int version() { return version; }
}
