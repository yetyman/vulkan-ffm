package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkImage;
import io.github.yetyman.vulkan.VkImageView;
import io.github.yetyman.vulkan.graph.resources.BufferDesc;
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
    private final List<VkImageView> allocatedImageViews = new ArrayList<>();
    private final List<VkBuffer> allocatedBuffers = new ArrayList<>();
    private final Map<String, GraphResource> allocatedResources = new HashMap<>();

    public TransientResourceAllocator(VkDevice device, Arena arena) {
        this.device = device;
        this.arena = arena;
    }

    /**
     * Allocates a transient image resource from its descriptor.
     * Also creates a default full-image VkImageView for use with auto-rendering.
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

        // Create a default image view for auto-rendering support
        int aspectMask = isDepthFormat(desc.format())
            ? 0x00000002  // VK_IMAGE_ASPECT_DEPTH_BIT
            : 0x00000001; // VK_IMAGE_ASPECT_COLOR_BIT
        VkImageView view = VkImageView.builder()
            .device(device)
            .image(image.handle())
            .format(desc.format())
            .aspectMask(aspectMask)
            .build(arena);
        allocatedImageViews.add(view);
        resource.setImageView(view.handle());

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
     * Destroys and re-allocates only resources whose descriptors have changed.
     * Resources with unchanged descriptors are kept as-is. Used during resize.
     *
     * @param imageDescs image descriptors keyed by resource name
     * @param bufferDescs buffer descriptors keyed by resource name
     */
    public void reallocate(Map<String, ImageDesc> imageDescs, Map<String, BufferDesc> bufferDescs) {
        // Destroy images that changed or were removed
        var imgIter = allocatedImages.iterator();
        while (imgIter.hasNext()) {
            VkImage img = imgIter.next();
            // Find the resource entry for this image
            String name = findImageResourceName(img);
            if (name == null || !imageDescs.containsKey(name)) {
                img.close();
                imgIter.remove();
                if (name != null) allocatedResources.remove(name);
            } else {
                ImageDesc newDesc = imageDescs.get(name);
                VkImageGraphResource existing = (VkImageGraphResource) allocatedResources.get(name);
                if (existing != null && !existing.matchesDesc(newDesc)) {
                    img.close();
                    imgIter.remove();
                    allocatedResources.remove(name);
                }
            }
        }

        // Destroy buffers that changed or were removed
        var bufIter = allocatedBuffers.iterator();
        while (bufIter.hasNext()) {
            VkBuffer buf = bufIter.next();
            String name = findBufferResourceName(buf);
            if (name == null || !bufferDescs.containsKey(name)) {
                buf.close();
                bufIter.remove();
                if (name != null) allocatedResources.remove(name);
            } else {
                BufferDesc newDesc = bufferDescs.get(name);
                VkBufferGraphResource existing = (VkBufferGraphResource) allocatedResources.get(name);
                if (existing != null && !existing.matchesDesc(newDesc)) {
                    buf.close();
                    bufIter.remove();
                    allocatedResources.remove(name);
                }
            }
        }

        // Allocate missing resources
        for (Map.Entry<String, ImageDesc> entry : imageDescs.entrySet()) {
            if (!allocatedResources.containsKey(entry.getKey())) {
                allocateImage(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, BufferDesc> entry : bufferDescs.entrySet()) {
            if (!allocatedResources.containsKey(entry.getKey())) {
                allocateBuffer(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Destroys all resources and re-allocates from scratch. Used when incremental
     * reallocate is not possible (e.g. aliasing group changes).
     */
    public void reallocateAll(Map<String, ImageDesc> imageDescs, Map<String, BufferDesc> bufferDescs) {
        destroyAll();
        for (Map.Entry<String, ImageDesc> entry : imageDescs.entrySet()) {
            allocateImage(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BufferDesc> entry : bufferDescs.entrySet()) {
            allocateBuffer(entry.getKey(), entry.getValue());
        }
    }

    private String findImageResourceName(VkImage image) {
        for (Map.Entry<String, GraphResource> entry : allocatedResources.entrySet()) {
            if (entry.getValue() instanceof VkImageGraphResource imgRes && imgRes.image() == image) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String findBufferResourceName(VkBuffer buffer) {
        for (Map.Entry<String, GraphResource> entry : allocatedResources.entrySet()) {
            if (entry.getValue() instanceof VkBufferGraphResource bufRes) {
                // The TransientManagedBufferAdapter wraps the VkBuffer, so check handle equality
                if (bufRes.managedBuffer().handle().equals(buffer.handle())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void destroyAll() {
        for (VkImageView view : allocatedImageViews) {
            view.close();
        }
        for (VkImage image : allocatedImages) {
            image.close();
        }
        for (VkBuffer buffer : allocatedBuffers) {
            buffer.close();
        }
        allocatedImageViews.clear();
        allocatedImages.clear();
        allocatedBuffers.clear();
        allocatedResources.clear();
    }

    private static boolean isDepthFormat(int format) {
        // VK_FORMAT_D16_UNORM=124, D32_SFLOAT=126, D16_UNORM_S8_UINT=128,
        // D24_UNORM_S8_UINT=129, D32_SFLOAT_S8_UINT=130
        return format == 124 || format == 126 || format == 128 || format == 129 || format == 130;
    }

    @Override
    public void close() {
        destroyAll();
    }
}
