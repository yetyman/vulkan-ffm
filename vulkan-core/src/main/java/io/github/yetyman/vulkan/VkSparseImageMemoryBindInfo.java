package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkSparseImageMemoryBindInfo structure.
 * Specifies sparse image memory binding operations for a specific image.
 */
public class VkSparseImageMemoryBindInfo {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemorySegment image;
        private MemorySegment[] binds;

        private Builder() {
        }

        public Builder image(MemorySegment image) {
            this.image = image;
            return this;
        }

        public Builder binds(MemorySegment... binds) {
            this.binds = binds;
            return this;
        }

        public MemorySegment build(Arena arena) {
            MemorySegment segment = io.github.yetyman.vulkan.generated.VkSparseImageMemoryBindInfo.allocate(arena);
            io.github.yetyman.vulkan.generated.VkSparseImageMemoryBindInfo.image(segment, image);
            io.github.yetyman.vulkan.generated.VkSparseImageMemoryBindInfo.bindCount(segment, binds != null ? binds.length : 0);

            if (binds != null && binds.length > 0) {
                MemorySegment bindsArray = arena.allocate(binds[0].byteSize() * binds.length);
                for (int i = 0; i < binds.length; i++) {
                    MemorySegment.copy(binds[i], 0, bindsArray, i * binds[0].byteSize(), binds[i].byteSize());
                }
                io.github.yetyman.vulkan.generated.VkSparseImageMemoryBindInfo.pBinds(segment, bindsArray);
            } else {
                io.github.yetyman.vulkan.generated.VkSparseImageMemoryBindInfo.pBinds(segment, MemorySegment.NULL);
            }

            return segment;
        }
    }
}
