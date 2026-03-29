package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VkExtent3D;
import io.github.yetyman.vulkan.generated.VkImageSubresource;
import io.github.yetyman.vulkan.generated.VkOffset3D;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkSparseImageMemoryBind structure.
 * Specifies a sparse image memory binding for a specific subresource tile.
 */
public class VkSparseImageMemoryBind {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int aspectMask;
        private int mipLevel;
        private int arrayLayer;
        private int offsetX, offsetY, offsetZ;
        private int extentWidth, extentHeight, extentDepth = 1;
        private MemorySegment memory = MemorySegment.NULL;
        private long memoryOffset;
        private int flags;

        private Builder() {}

        public Builder aspectMask(int aspectMask) { this.aspectMask = aspectMask; return this; }
        public Builder mipLevel(int mipLevel) { this.mipLevel = mipLevel; return this; }
        public Builder arrayLayer(int arrayLayer) { this.arrayLayer = arrayLayer; return this; }

        public Builder offset(int x, int y, int z) {
            this.offsetX = x; this.offsetY = y; this.offsetZ = z;
            return this;
        }

        public Builder extent(int width, int height, int depth) {
            this.extentWidth = width; this.extentHeight = height; this.extentDepth = depth;
            return this;
        }

        public Builder memory(MemorySegment memory) { this.memory = memory; return this; }
        public Builder memoryOffset(long memoryOffset) { this.memoryOffset = memoryOffset; return this; }
        public Builder flags(int flags) { this.flags = flags; return this; }

        public MemorySegment build(Arena arena) {
            MemorySegment segment = io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.allocate(arena);

            MemorySegment subresource = io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.subresource(segment);
            VkImageSubresource.aspectMask(subresource, aspectMask);
            VkImageSubresource.mipLevel(subresource, mipLevel);
            VkImageSubresource.arrayLayer(subresource, arrayLayer);

            MemorySegment offset = io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.offset(segment);
            VkOffset3D.x(offset, offsetX);
            VkOffset3D.y(offset, offsetY);
            VkOffset3D.z(offset, offsetZ);

            MemorySegment extent = io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.extent(segment);
            VkExtent3D.width(extent, extentWidth);
            VkExtent3D.height(extent, extentHeight);
            VkExtent3D.depth(extent, extentDepth);

            io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.memory(segment, memory);
            io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.memoryOffset(segment, memoryOffset);
            io.github.yetyman.vulkan.generated.VkSparseImageMemoryBind.flags(segment, flags);

            return segment;
        }
    }
}
