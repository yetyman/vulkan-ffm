package io.github.yetyman.vulkan.graph.resources;

/**
 * A graph resource backed by a VkBuffer / IBuffer.
 */
public interface GraphBufferResource extends GraphResource {

    /** @return buffer size in bytes */
    long size();
}
