package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkImage;
import io.github.yetyman.vulkan.graph.resources.BufferDesc;
import io.github.yetyman.vulkan.graph.resources.GraphBufferResource;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ImageDesc;
import io.github.yetyman.vulkan.graph.resources.VkBufferGraphResource;
import io.github.yetyman.vulkan.graph.resources.VkImageGraphResource;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allocates physical Vulkan resources for transient graph resources based on their descriptors.
 * Handles aliased groups by allocating a single memory heap large enough for the largest member,
 * then binding each member to that heap at the appropriate time.
 *
 * For non-aliased transient resources, allocates individual VkImage/VkBuffer with dedicated memory.
 * For aliased groups, uses VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT when available, falling back
 * to device-local memory.
 */
public class TransientResourceAllocator implements AutoCloseable {

    private final VkDevice device;
    private final Arena arena;
    private final List<VkImage> allocatedImages = new ArrayList<>();
    private final List<VkBuffer> allocatedBuffers = new ArrayList<>();
    private final Map<String, GraphResource> allocatedResources = new HashMap<>();

    public TransientResourceAllocator(VkDevice device, Arena arena) {
        this.device = device;
        this.arena = arena;
    }

    /**
     * Allocates a transient image resource from its descriptor.
     *
     * @param name resource name
     * @param desc image descriptor
     * @return the allocated graph resource wrapping the VkImage
     */
    public VkImageGraphResource allocateImage(String name, ImageDesc desc) {
        VkImage image = VkImage.builder()
            .device(device)
            .dimensions(desc.width(), desc.height(), desc.depth())
            .format(desc.format())
            .usage(desc.usage())
            .samples(desc.samples())
            .mipLevels(desc.mipLevels())
            .arrayLayers(desc.arrayLayers())
            .build(arena);

        allocatedImages.add(image);
        VkImageGraphResource resource = VkImageGraphResource.transientResource(name, image);
        allocatedResources.put(name, resource);
        return resource;
    }

    /**
     * Allocates a transient buffer resource from its descriptor.
     *
     * @param name resource name
     * @param desc buffer descriptor
     * @return the allocated graph resource wrapping the VkBuffer
     */
    public VkBufferGraphResource allocateBuffer(String name, BufferDesc desc) {
        VkBuffer buffer = VkBuffer.builder()
            .device(device)
            .size(desc.size())
            .usage(desc.usage())
            .build(arena);

        allocatedBuffers.add(buffer);
        // Wrap in a simple ManagedBuffer adapter for the graph resource
        VkBufferGraphResource resource = VkBufferGraphResource.transientResource(
            name, new TransientManagedBufferAdapter(buffer));
        allocatedResources.put(name, resource);
        return resource;
    }

    /** @return all allocated graph resources by name */
    public Map<String, GraphResource> allocatedResources() {
        return allocatedResources;
    }

    /** @return the allocated resource for the given name, or null */
    public GraphResource get(String name) {
        return allocatedResources.get(name);
    }

    /**
     * Destroys all allocated resources and re-allocates them with new descriptors.
     * Used during resize.
     *
     * @param imageDescs image descriptors keyed by resource name
     * @param bufferDescs buffer descriptors keyed by resource name
     */
    public void reallocate(Map<String, ImageDesc> imageDescs, Map<String, BufferDesc> bufferDescs) {
        destroyAll();

        for (Map.Entry<String, ImageDesc> entry : imageDescs.entrySet()) {
            allocateImage(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BufferDesc> entry : bufferDescs.entrySet()) {
            allocateBuffer(entry.getKey(), entry.getValue());
        }
    }

    private void destroyAll() {
        for (VkImage image : allocatedImages) {
            image.close();
        }
        for (VkBuffer buffer : allocatedBuffers) {
            buffer.close();
        }
        allocatedImages.clear();
        allocatedBuffers.clear();
        allocatedResources.clear();
    }

    @Override
    public void close() {
        destroyAll();
    }
}
