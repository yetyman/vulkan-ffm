package io.github.yetyman.vulkan.graph.edges;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

/**
 * An optional resource dependency with a fallback value.
 * If the source is unavailable (writer inactive, stale, or failed), the fallback is used instead.
 *
 * Usage:
 * <pre>
 *   .readsOptional(OptionalEdge.of(ssaoBuffer, 0x20, 0x80, Fallback.clear(1.0f)))
 * </pre>
 */
public class OptionalEdge {

    private final GraphResource resource;
    private final int accessMask;
    private final int stageMask;
    private final Fallback fallback;

    private OptionalEdge(GraphResource resource, int accessMask, int stageMask, Fallback fallback) {
        this.resource = resource;
        this.accessMask = accessMask;
        this.stageMask = stageMask;
        this.fallback = fallback;
    }

    /** Creates an optional read edge with a fallback */
    public static OptionalEdge of(GraphResource resource, int accessMask, int stageMask, Fallback fallback) {
        return new OptionalEdge(resource, accessMask, stageMask, fallback);
    }

    /** @return the resource this edge references */
    public GraphResource resource() { return resource; }

    /** @return the VkAccessFlagBits mask */
    public int accessMask() { return accessMask; }

    /** @return the VkPipelineStageFlagBits mask */
    public int stageMask() { return stageMask; }

    /** @return the fallback to use when the source is unavailable */
    public Fallback fallback() { return fallback; }

    /**
     * Fallback value when an optional edge's source is unavailable.
     */
    public sealed interface Fallback {
        /** Constant clear value */
        record ClearValue(float r, float g, float b, float a) implements Fallback {
            public static final ClearValue BLACK = new ClearValue(0, 0, 0, 0);
            public static final ClearValue WHITE = new ClearValue(1, 1, 1, 1);
        }

        /** Another resource in the graph */
        record AlternateResource(GraphResource alternate) implements Fallback {}

        /** The resource's own previous valid content (for persistent resources) */
        record RetainPrevious() implements Fallback {}

        /** Convenience factories */
        static Fallback clear(float v) { return new ClearValue(v, v, v, v); }
        static Fallback clear(float r, float g, float b, float a) { return new ClearValue(r, g, b, a); }
        static Fallback alternate(GraphResource res) { return new AlternateResource(res); }
        static Fallback retain() { return new RetainPrevious(); }
    }
}
