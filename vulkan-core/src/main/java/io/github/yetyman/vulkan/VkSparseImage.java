package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.*;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sparse residency image with on-demand page binding.
 * Allows large virtual image address spaces with physical memory committed only for accessed regions.
 * Requires VK_IMAGE_CREATE_SPARSE_BINDING_BIT and VK_IMAGE_CREATE_SPARSE_RESIDENCY_BIT.
 *
 * <p>Use cases: virtual texturing (megatextures), streaming terrain, large atlas textures.
 *
 * <pre>{@code
 * try (VkSparseImage sparseImage = VkSparseImage.builder()
 *         .device(device)
 *         .dimensions(16384, 16384)
 *         .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value())
 *         .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
 *         .sparseQueue(sparseQueue)
 *         .build(arena)) {
 *     sparseImage.commitRegion(0, 0, 0); // commit mip 0 tile at (0,0)
 * }
 * }</pre>
 */
public class VkSparseImage implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    private final VkQueue sparseQueue;
    private final int width, height;
    private final int format;
    private final int mipLevels;
    private final Arena arena;

    // key = encoded (mip << 48 | tileY << 24 | tileX)
    private final ConcurrentHashMap<Long, MemorySegment> committedPages = new ConcurrentHashMap<>();
    private final long tileWidth, tileHeight;
    private final int memoryTypeIndex;

    private VkSparseImage(MemorySegment handle, VkDevice device, VkQueue sparseQueue,
                          int width, int height, int format, int mipLevels,
                          long tileWidth, long tileHeight, int memoryTypeIndex, Arena arena) {
        this.handle = handle;
        this.device = device;
        this.sparseQueue = sparseQueue;
        this.width = width;
        this.height = height;
        this.format = format;
        this.mipLevels = mipLevels;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.memoryTypeIndex = memoryTypeIndex;
        this.arena = arena;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the VkImage handle
     */
    public MemorySegment handle() {
        return handle;
    }

    /**
     * @return tile width in texels for mip level 0
     */
    public long tileWidth() {
        return tileWidth;
    }

    /**
     * @return tile height in texels for mip level 0
     */
    public long tileHeight() {
        return tileHeight;
    }

    /**
     * Commits physical memory for the tile at (tileX, tileY) on the given mip level.
     * No-op if already committed.
     */
    public void commitRegion(int tileX, int tileY, int mipLevel) {
        long key = ((long) mipLevel << 48) | ((long) tileY << 24) | tileX;
        committedPages.computeIfAbsent(key, k -> allocateAndBindTile(tileX, tileY, mipLevel));
    }

    /**
     * Decommits physical memory for the tile at (tileX, tileY) on the given mip level.
     * No-op if not committed.
     */
    public void decommitRegion(int tileX, int tileY, int mipLevel) {
        long key = ((long) mipLevel << 48) | ((long) tileY << 24) | tileX;
        MemorySegment memory = committedPages.remove(key);
        if (memory != null) unbindAndFreeMemory(memory, tileX, tileY, mipLevel);
    }

    private MemorySegment allocateAndBindTile(int tileX, int tileY, int mipLevel) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = VkMemoryRequirements.allocate(tmp);
            Vulkan.getImageMemoryRequirements(device.handle(), handle, req);
            long alignment = VkMemoryRequirements.alignment(req);
            long tileSize = tileWidth * tileHeight * 4;
            tileSize = ((tileSize + alignment - 1) / alignment) * alignment;

            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(tmp);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO.value());
            VkMemoryAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
            VkMemoryAllocateInfo.allocationSize(allocInfo, tileSize);
            VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);

            MemorySegment memPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.allocateMemory(device.handle(), allocInfo, memPtr).check();
            MemorySegment memory = memPtr.get(ValueLayout.ADDRESS, 0);

            MemorySegment bind = VkSparseImageMemoryBind.builder()
                    .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                    .mipLevel(mipLevel)
                    .arrayLayer(0)
                    .offset((int) (tileX * tileWidth), (int) (tileY * tileHeight), 0)
                    .extent((int) tileWidth, (int) tileHeight, 1)
                    .memory(memory)
                    .memoryOffset(0)
                    .flags(0)
                    .build(tmp);

            bindAndWait(tmp, bind);

            return memory;
        }
    }

    private void unbindAndFreeMemory(MemorySegment memory, int tileX, int tileY, int mipLevel) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment bind = VkSparseImageMemoryBind.builder()
                    .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                    .mipLevel(mipLevel)
                    .arrayLayer(0)
                    .offset((int) (tileX * tileWidth), (int) (tileY * tileHeight), 0)
                    .extent((int) tileWidth, (int) tileHeight, 1)
                    .memory(MemorySegment.NULL)
                    .memoryOffset(0)
                    .flags(0)
                    .build(tmp);

            bindAndWait(tmp, bind);
        }
        Vulkan.freeMemory(device.handle(), memory);
    }

    private void bindAndWait(Arena tmp, MemorySegment bind) {
        MemorySegment imageBindInfo = VkSparseImageMemoryBindInfo.builder()
                .image(handle)
                .binds(bind)
                .build(tmp);

        MemorySegment bindSparseInfo = VkBindSparseInfo.builder()
                .imageBinds(imageBindInfo)
                .build(tmp);

        try (VkFence fence = VkFence.builder().device(device).build(tmp)) {
            Vulkan.queueBindSparse(sparseQueue.handle(), 1, bindSparseInfo, fence.handle()).check();
            VkFenceOps.wait(device, fence, Long.MAX_VALUE, tmp).check();
        }
    }

    @Override
    public void close() {
        for (MemorySegment memory : committedPages.values()) {
            Vulkan.freeMemory(device.handle(), memory);
        }
        committedPages.clear();
        Vulkan.destroyImage(device.handle(), handle);
        arena.close();
    }

    public static class Builder {
        private VkDevice device;
        private VkQueue sparseQueue;
        private int width, height;
        private int format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value();
        private int usage = VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value()
                | VkImageUsageFlagBits.VK_IMAGE_USAGE_TRANSFER_DST_BIT.value();
        private int mipLevels = 1;

        private Builder() {
        }

        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        public Builder sparseQueue(VkQueue queue) {
            this.sparseQueue = queue;
            return this;
        }

        public Builder dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder format(int format) {
            this.format = format;
            return this;
        }

        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }

        public Builder mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public VkSparseImage build(Arena outerArena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (sparseQueue == null) throw new IllegalStateException("sparseQueue not set");
            if (width <= 0 || height <= 0) throw new IllegalStateException("invalid dimensions");

            Arena arena = Arena.ofShared();
            try {
                int sparseFlags = VkImageCreateFlagBits.VK_IMAGE_CREATE_SPARSE_BINDING_BIT.value()
                        | VkImageCreateFlagBits.VK_IMAGE_CREATE_SPARSE_RESIDENCY_BIT.value();

                MemorySegment imageInfo = VkImageCreateInfo.allocate(arena);
                VkImageCreateInfo.sType(imageInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO.value());
                VkImageCreateInfo.pNext(imageInfo, MemorySegment.NULL);
                VkImageCreateInfo.flags(imageInfo, sparseFlags);
                VkImageCreateInfo.imageType(imageInfo, VkImageType.VK_IMAGE_TYPE_2D.value());
                VkImageCreateInfo.format(imageInfo, format);
                MemorySegment ext = VkImageCreateInfo.extent(imageInfo);
                VkExtent3D.width(ext, width);
                VkExtent3D.height(ext, height);
                VkExtent3D.depth(ext, 1);
                VkImageCreateInfo.mipLevels(imageInfo, mipLevels);
                VkImageCreateInfo.arrayLayers(imageInfo, 1);
                VkImageCreateInfo.samples(imageInfo, VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value());
                VkImageCreateInfo.tiling(imageInfo, VkImageTiling.VK_IMAGE_TILING_OPTIMAL.value());
                VkImageCreateInfo.usage(imageInfo, usage);
                VkImageCreateInfo.sharingMode(imageInfo, VkSharingMode.VK_SHARING_MODE_EXCLUSIVE.value());
                VkImageCreateInfo.initialLayout(imageInfo, VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value());

                MemorySegment imagePtr = arena.allocate(ValueLayout.ADDRESS);
                Vulkan.createImage(device.handle(), imageInfo, imagePtr).check();
                MemorySegment image = imagePtr.get(ValueLayout.ADDRESS, 0);

                // Query sparse image memory requirements for tile dimensions
                MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);
                VulkanFFM.vkGetImageSparseMemoryRequirements(device.handle(), image, countPtr, MemorySegment.NULL);
                int reqCount = countPtr.get(ValueLayout.JAVA_INT, 0);
                long tileW = 256, tileH = 256;
                if (reqCount > 0) {
                    MemorySegment reqs = arena.allocate(VkSparseImageMemoryRequirements.layout(), reqCount);
                    VulkanFFM.vkGetImageSparseMemoryRequirements(device.handle(), image, countPtr, reqs);
                    MemorySegment first = reqs.asSlice(0, VkSparseImageMemoryRequirements.layout());
                    MemorySegment formatProps = VkSparseImageMemoryRequirements.formatProperties(first);
                    MemorySegment gran = VkSparseImageFormatProperties.imageGranularity(formatProps);
                    tileW = VkExtent3D.width(gran);
                    tileH = VkExtent3D.height(gran);
                }

                // Find device-local memory type
                MemorySegment req = VkMemoryRequirements.allocate(arena);
                Vulkan.getImageMemoryRequirements(device.handle(), image, req);
                int memTypeBits = VkMemoryRequirements.memoryTypeBits(req);
                int memTypeIndex = device.physicalDevice().findMemoryType(
                        memTypeBits, VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value());
                if (memTypeIndex < 0) throw new RuntimeException("No suitable memory type for sparse image");

                return new VkSparseImage(image, device, sparseQueue, width, height, format,
                        mipLevels, tileW, tileH, memTypeIndex, arena);
            } catch (Exception e) {
                arena.close();
                throw e;
            }
        }
    }
}
