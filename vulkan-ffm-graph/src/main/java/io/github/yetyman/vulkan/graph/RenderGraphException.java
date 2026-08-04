package io.github.yetyman.vulkan.graph;

/**
 * Thrown when the render graph encounters a structural or validation error.
 */
public class RenderGraphException extends RuntimeException {

    public RenderGraphException(String message) {
        super(message);
    }

    public RenderGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
