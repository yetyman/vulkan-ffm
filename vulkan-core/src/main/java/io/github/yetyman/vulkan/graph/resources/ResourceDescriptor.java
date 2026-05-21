package io.github.yetyman.vulkan.graph.resources;

/**
 * Describes the format, dimensions, and usage of a graph resource.
 * Used by TemporalResource to describe what physical resources to allocate.
 */
public record ResourceDescriptor(
    ResourceKind kind,
    int format,
    int width,
    int height,
    int depth,
    int usageFlags,
    long bufferSize
) {

    public enum ResourceKind {
        IMAGE,
        BUFFER
    }

    /** Creates an image descriptor */
    public static ResourceDescriptor image(int format, int width, int height, int usageFlags) {
        return new ResourceDescriptor(ResourceKind.IMAGE, format, width, height, 1, usageFlags, 0);
    }

    /** Creates a buffer descriptor */
    public static ResourceDescriptor buffer(long size, int usageFlags) {
        return new ResourceDescriptor(ResourceKind.BUFFER, 0, 0, 0, 0, usageFlags, size);
    }

    /** Returns a copy with updated dimensions (for resize) */
    public ResourceDescriptor withDimensions(int newWidth, int newHeight) {
        return new ResourceDescriptor(kind, format, newWidth, newHeight, depth, usageFlags, bufferSize);
    }
}
