package io.github.yetyman.vulkan.graph.edges;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

/**
 * A cross-frame dependency on a previous version of a resource.
 * Used for temporal effects (TAA history, simulation ping-pong, particle state).
 * The graph manages the physical ring buffer automatically.
 */
public class FeedbackEdge {

    private final GraphResource resource;
    private final int framesBack;   // how many frames back to read (1 = previous frame)
    private final int accessMask;
    private final int stageMask;
    private final int imageLayout;  // -1 for buffers

    public FeedbackEdge(GraphResource resource, int framesBack, int accessMask, int stageMask, int imageLayout) {
        if (framesBack < 1) throw new IllegalArgumentException("framesBack must be >= 1");
        this.resource = resource;
        this.framesBack = framesBack;
        this.accessMask = accessMask;
        this.stageMask = stageMask;
        this.imageLayout = imageLayout;
    }

    /** Creates a feedback edge reading the previous frame's version of a buffer */
    public static FeedbackEdge buffer(GraphResource resource, int framesBack, int accessMask, int stageMask) {
        return new FeedbackEdge(resource, framesBack, accessMask, stageMask, -1);
    }

    /** Creates a feedback edge reading the previous frame's version of an image */
    public static FeedbackEdge image(GraphResource resource, int framesBack, int accessMask, int stageMask, int layout) {
        return new FeedbackEdge(resource, framesBack, accessMask, stageMask, layout);
    }

    /** @return the resource being read from a previous frame */
    public GraphResource resource() { return resource; }

    /** @return how many frames back (1 = previous frame, 2 = two frames ago) */
    public int framesBack() { return framesBack; }

    /** @return the VkAccessFlagBits mask for the read */
    public int accessMask() { return accessMask; }

    /** @return the VkPipelineStageFlagBits mask */
    public int stageMask() { return stageMask; }

    /** @return required image layout, or -1 for buffers */
    public int imageLayout() { return imageLayout; }
}
