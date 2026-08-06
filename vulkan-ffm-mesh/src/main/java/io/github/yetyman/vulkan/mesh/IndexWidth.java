package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.enums.VkIndexType;

import java.util.OptionalInt;

/**
 * Width of one index in an index stream.
 *
 * <p>{@link #U8} is included because 8-bit indices are a legitimate compact storage form for
 * meshlet-local indices read from a storage buffer, whether or not the device can bind them
 * directly. Binding U8 as an index buffer requires {@code VK_KHR_index_type_uint8}; whether that is
 * available is a device capability question, not a vocabulary question, so
 * {@link #vkIndexType()} reports it as present and the caller checks device support.
 */
public enum IndexWidth {
    /** 8-bit indices. Requires VK_KHR_index_type_uint8 to bind as an index buffer. */
    U8(1),
    /** 16-bit indices. */
    U16(2),
    /** 32-bit indices. */
    U32(4);

    private final int byteSize;

    IndexWidth(int byteSize) {
        this.byteSize = byteSize;
    }

    /**
     * @return bytes per index
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * @return the largest vertex index this width can address
     */
    public long maxIndex() {
        return switch (this) {
            case U8 -> 0xFFL;
            case U16 -> 0xFFFFL;
            case U32 -> 0xFFFFFFFFL;
        };
    }

    /**
     * @return the narrowest width that can address {@code vertexCount} vertices
     */
    public static IndexWidth narrowestFor(long vertexCount) {
        if (vertexCount <= 0xFFL) return U8;
        if (vertexCount <= 0xFFFFL) return U16;
        return U32;
    }

    /**
     * @return the corresponding {@code VkIndexType} value
     */
    public OptionalInt vkIndexType() {
        return switch (this) {
            case U8 -> OptionalInt.of(VkIndexType.VK_INDEX_TYPE_UINT8.value());
            case U16 -> OptionalInt.of(VkIndexType.VK_INDEX_TYPE_UINT16.value());
            case U32 -> OptionalInt.of(VkIndexType.VK_INDEX_TYPE_UINT32.value());
        };
    }
}
