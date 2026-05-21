package io.github.yetyman.vulkan.graph.resources;

/**
 * Defines the initial state for a temporal resource on its first read (before any write has occurred).
 * Used by the starting point resolution algorithm to validate that frame 0 is sound.
 */
public sealed interface InitialState {

    /** Zero-clear the resource on first read */
    record Clear(float r, float g, float b, float a) implements InitialState {
        public static final Clear BLACK = new Clear(0, 0, 0, 0);
        public static final Clear BLACK_OPAQUE = new Clear(0, 0, 0, 1);
        public static final Clear WHITE = new Clear(1, 1, 1, 1);
    }

    /** Clear depth/stencil */
    record ClearDepthStencil(float depth, int stencil) implements InitialState {
        public static final ClearDepthStencil FAR = new ClearDepthStencil(1.0f, 0);
    }

    /** Application provides initial data before first submission */
    record Preloaded() implements InitialState {}

    /** Explicitly undefined - shader handles the "no previous data" case internally,
     *  e.g. via a frame counter uniform that gates temporal reads */
    record Undefined() implements InitialState {}
}
