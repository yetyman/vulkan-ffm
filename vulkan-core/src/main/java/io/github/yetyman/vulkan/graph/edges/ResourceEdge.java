package io.github.yetyman.vulkan.graph.edges;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

/**
 * A directed dependency between a node and a resource, declaring how the node accesses it.
 * The graph derives all barriers and execution ordering from these declarations.
 */
public class ResourceEdge {

    private final GraphResource resource;
    private final AccessType accessType;
    private final int accessMask;
    private final int stageMask;
    private final int imageLayout; // only meaningful for images, -1 for buffers

    private ResourceEdge(GraphResource resource, AccessType accessType, int accessMask, int stageMask, int imageLayout) {
        this.resource = resource;
        this.accessType = accessType;
        this.accessMask = accessMask;
        this.stageMask = stageMask;
        this.imageLayout = imageLayout;
    }

    /** Creates a read edge with the given access and stage masks */
    public static ResourceEdge read(GraphResource resource, int accessMask, int stageMask) {
        return new ResourceEdge(resource, AccessType.READ, accessMask, stageMask, -1);
    }

    /** Creates a read edge for an image with a required layout */
    public static ResourceEdge readImage(GraphResource resource, int accessMask, int stageMask, int requiredLayout) {
        return new ResourceEdge(resource, AccessType.READ, accessMask, stageMask, requiredLayout);
    }

    /** Creates a write edge with the given access and stage masks */
    public static ResourceEdge write(GraphResource resource, int accessMask, int stageMask) {
        return new ResourceEdge(resource, AccessType.WRITE, accessMask, stageMask, -1);
    }

    /** Creates a write edge for an image with a target layout */
    public static ResourceEdge writeImage(GraphResource resource, int accessMask, int stageMask, int targetLayout) {
        return new ResourceEdge(resource, AccessType.WRITE, accessMask, stageMask, targetLayout);
    }

    /** @return the resource this edge references */
    public GraphResource resource() { return resource; }

    /** @return whether this is a read or write */
    public AccessType accessType() { return accessType; }

    /** @return the VkAccessFlagBits mask */
    public int accessMask() { return accessMask; }

    /** @return the VkPipelineStageFlagBits mask */
    public int stageMask() { return stageMask; }

    /** @return required/target image layout, or -1 for buffers */
    public int imageLayout() { return imageLayout; }

    /** @return true if this edge declares a write */
    public boolean isWrite() { return accessType == AccessType.WRITE; }

    /** @return true if this edge declares a read */
    public boolean isRead() { return accessType == AccessType.READ; }

    public enum AccessType {
        READ,
        WRITE
    }
}
