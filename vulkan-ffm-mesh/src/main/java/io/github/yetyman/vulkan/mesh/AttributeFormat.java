package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.enums.VkFormat;

import java.util.OptionalInt;

/**
 * How one element of one attribute is encoded in memory.
 *
 * <p>The mapping to a {@code VkFormat} is deliberately optional. The fastest vertex encodings
 * frequently have no {@code VkFormat} at all: octahedral normals packed into two 16-bit values,
 * positions quantized against a per-partition scale and bias, cluster and material identifiers
 * bit-packed into one integer. None of those can be bound as a vertex input attribute; all of them
 * are read from a storage buffer and decoded by the shader. Requiring a {@code VkFormat} would make
 * the entire vertex-pulling and mesh-shader family of paradigms second-class, which is exactly the
 * bias this module exists to avoid.
 *
 * <p>{@link #vertexInputFormat()} being empty therefore means "usable, but not through
 * {@code vkCmdBindVertexBuffers}" -- not "invalid".
 */
public final class AttributeFormat {

    // Floating point
    public static final AttributeFormat F32 = of(ComponentType.F32, 1, false);
    public static final AttributeFormat F32x2 = of(ComponentType.F32, 2, false);
    public static final AttributeFormat F32x3 = of(ComponentType.F32, 3, false);
    public static final AttributeFormat F32x4 = of(ComponentType.F32, 4, false);
    /** 4x4 matrix as 16 floats. Consumes four vertex input locations when bound as vertex input. */
    public static final AttributeFormat F32x16 = of(ComponentType.F32, 16, false);
    public static final AttributeFormat F16x2 = of(ComponentType.F16, 2, false);
    public static final AttributeFormat F16x4 = of(ComponentType.F16, 4, false);

    // Normalized fixed point
    public static final AttributeFormat U8x4_NORM = of(ComponentType.U8, 4, true);
    public static final AttributeFormat S8x4_NORM = of(ComponentType.S8, 4, true);
    public static final AttributeFormat U16x2_NORM = of(ComponentType.U16, 2, true);
    public static final AttributeFormat U16x4_NORM = of(ComponentType.U16, 4, true);
    public static final AttributeFormat S16x2_NORM = of(ComponentType.S16, 2, true);
    public static final AttributeFormat S16x4_NORM = of(ComponentType.S16, 4, true);

    // Integer
    public static final AttributeFormat U8x4 = of(ComponentType.U8, 4, false);
    public static final AttributeFormat U16x4 = of(ComponentType.U16, 4, false);
    public static final AttributeFormat U32 = of(ComponentType.U32, 1, false);
    public static final AttributeFormat U32x2 = of(ComponentType.U32, 2, false);
    public static final AttributeFormat U32x3 = of(ComponentType.U32, 3, false);
    public static final AttributeFormat U32x4 = of(ComponentType.U32, 4, false);
    public static final AttributeFormat S32 = of(ComponentType.S32, 1, false);

    /**
     * Octahedral-encoded unit vector in two normalized 16-bit values (4 bytes).
     * Shader-decoded: this has a byte-compatible {@code VkFormat}, but the decode from octahedral
     * to cartesian must happen in the shader either way, so it is exposed as a packed format.
     */
    public static final AttributeFormat OCT16 = packed("oct16", 4);

    /** 10-10-10-2 normalized vector in one 32-bit word. */
    public static final AttributeFormat R10G10B10A2_NORM =
            packed("r10g10b10a2_norm", 4, VkFormat.VK_FORMAT_A2B10G10R10_UNORM_PACK32.value());

    private final ComponentType componentType;
    private final int componentCount;
    private final boolean normalized;
    private final int byteSize;
    private final String name;
    private final int vkFormat; // -1 when there is none

    private AttributeFormat(ComponentType componentType, int componentCount, boolean normalized,
                            int byteSize, String name, int vkFormat) {
        this.componentType = componentType;
        this.componentCount = componentCount;
        this.normalized = normalized;
        this.byteSize = byteSize;
        this.name = name;
        this.vkFormat = vkFormat;
    }

    /**
     * Creates a format from independent scalar components.
     *
     * @param normalized whether integer components are interpreted as normalized fixed point
     */
    public static AttributeFormat of(ComponentType componentType, int componentCount, boolean normalized) {
        if (componentType == ComponentType.PACKED)
            throw new IllegalArgumentException("use packed(name, byteSize) for PACKED formats");
        if (componentCount <= 0) throw new IllegalArgumentException("componentCount must be positive");
        if (normalized && !componentType.supportsNormalized())
            throw new IllegalArgumentException(componentType + " cannot be normalized");
        int size = componentType.byteSize() * componentCount;
        String name = componentType + (componentCount == 1 ? "" : "x" + componentCount) + (normalized ? "_NORM" : "");
        return new AttributeFormat(componentType, componentCount, normalized, size, name,
                lookupVkFormat(componentType, componentCount, normalized));
    }

    /**
     * Creates an opaque packed format with no independent scalar components and no vertex input
     * mapping. The shader is responsible for decoding it.
     *
     * @param name     diagnostic name; carries no semantics
     * @param byteSize encoded size in bytes
     */
    public static AttributeFormat packed(String name, int byteSize) {
        return packed(name, byteSize, -1);
    }

    /**
     * Creates an opaque packed format that happens to have a byte-compatible {@code VkFormat},
     * such as 10-10-10-2.
     */
    public static AttributeFormat packed(String name, int byteSize, int vkFormat) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (byteSize <= 0) throw new IllegalArgumentException("byteSize must be positive");
        return new AttributeFormat(ComponentType.PACKED, 1, false, byteSize, name, vkFormat);
    }

    /**
     * @return bytes occupied by one element in this format
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * @return the scalar component encoding, or {@link ComponentType#PACKED}
     */
    public ComponentType componentType() {
        return componentType;
    }

    /**
     * @return number of independent scalar components, or 1 for packed formats
     */
    public int componentCount() {
        return componentCount;
    }

    /**
     * @return whether integer components are interpreted as normalized fixed point
     */
    public boolean normalized() {
        return normalized;
    }

    /**
     * @return diagnostic name
     */
    public String name() {
        return name;
    }

    /**
     * @return the {@code VkFormat} value usable as a vertex input attribute, or empty when this
     * encoding must be decoded manually by the shader from a storage buffer
     */
    public OptionalInt vertexInputFormat() {
        return vkFormat < 0 ? OptionalInt.empty() : OptionalInt.of(vkFormat);
    }

    /**
     * @return true if this format can be bound through {@code vkCmdBindVertexBuffers} and described
     * in a pipeline vertex input state
     */
    public boolean isVertexInputCapable() {
        return vkFormat >= 0;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Maps scalar component combinations onto Vulkan vertex formats. Returns -1 for combinations
     * Vulkan has no format for, which is a normal outcome rather than an error.
     */
    private static int lookupVkFormat(ComponentType type, int count, boolean normalized) {
        if (count < 1 || count > 4) return -1;
        return switch (type) {
            case F32 -> switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R32_SFLOAT.value();
                case 2 -> VkFormat.VK_FORMAT_R32G32_SFLOAT.value();
                case 3 -> VkFormat.VK_FORMAT_R32G32B32_SFLOAT.value();
                default -> VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value();
            };
            case F16 -> switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R16_SFLOAT.value();
                case 2 -> VkFormat.VK_FORMAT_R16G16_SFLOAT.value();
                case 3 -> VkFormat.VK_FORMAT_R16G16B16_SFLOAT.value();
                default -> VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value();
            };
            case F64 -> switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R64_SFLOAT.value();
                case 2 -> VkFormat.VK_FORMAT_R64G64_SFLOAT.value();
                case 3 -> VkFormat.VK_FORMAT_R64G64B64_SFLOAT.value();
                default -> VkFormat.VK_FORMAT_R64G64B64A64_SFLOAT.value();
            };
            case U8 -> normalized ? switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R8_UNORM.value();
                case 2 -> VkFormat.VK_FORMAT_R8G8_UNORM.value();
                case 3 -> VkFormat.VK_FORMAT_R8G8B8_UNORM.value();
                default -> VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value();
            } : switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R8_UINT.value();
                case 2 -> VkFormat.VK_FORMAT_R8G8_UINT.value();
                case 3 -> VkFormat.VK_FORMAT_R8G8B8_UINT.value();
                default -> VkFormat.VK_FORMAT_R8G8B8A8_UINT.value();
            };
            case S8 -> normalized ? switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R8_SNORM.value();
                case 2 -> VkFormat.VK_FORMAT_R8G8_SNORM.value();
                case 3 -> VkFormat.VK_FORMAT_R8G8B8_SNORM.value();
                default -> VkFormat.VK_FORMAT_R8G8B8A8_SNORM.value();
            } : switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R8_SINT.value();
                case 2 -> VkFormat.VK_FORMAT_R8G8_SINT.value();
                case 3 -> VkFormat.VK_FORMAT_R8G8B8_SINT.value();
                default -> VkFormat.VK_FORMAT_R8G8B8A8_SINT.value();
            };
            case U16 -> normalized ? switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R16_UNORM.value();
                case 2 -> VkFormat.VK_FORMAT_R16G16_UNORM.value();
                case 3 -> VkFormat.VK_FORMAT_R16G16B16_UNORM.value();
                default -> VkFormat.VK_FORMAT_R16G16B16A16_UNORM.value();
            } : switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R16_UINT.value();
                case 2 -> VkFormat.VK_FORMAT_R16G16_UINT.value();
                case 3 -> VkFormat.VK_FORMAT_R16G16B16_UINT.value();
                default -> VkFormat.VK_FORMAT_R16G16B16A16_UINT.value();
            };
            case S16 -> normalized ? switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R16_SNORM.value();
                case 2 -> VkFormat.VK_FORMAT_R16G16_SNORM.value();
                case 3 -> VkFormat.VK_FORMAT_R16G16B16_SNORM.value();
                default -> VkFormat.VK_FORMAT_R16G16B16A16_SNORM.value();
            } : switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R16_SINT.value();
                case 2 -> VkFormat.VK_FORMAT_R16G16_SINT.value();
                case 3 -> VkFormat.VK_FORMAT_R16G16B16_SINT.value();
                default -> VkFormat.VK_FORMAT_R16G16B16A16_SINT.value();
            };
            case U32 -> switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R32_UINT.value();
                case 2 -> VkFormat.VK_FORMAT_R32G32_UINT.value();
                case 3 -> VkFormat.VK_FORMAT_R32G32B32_UINT.value();
                default -> VkFormat.VK_FORMAT_R32G32B32A32_UINT.value();
            };
            case S32 -> switch (count) {
                case 1 -> VkFormat.VK_FORMAT_R32_SINT.value();
                case 2 -> VkFormat.VK_FORMAT_R32G32_SINT.value();
                case 3 -> VkFormat.VK_FORMAT_R32G32B32_SINT.value();
                default -> VkFormat.VK_FORMAT_R32G32B32A32_SINT.value();
            };
            case U64, S64, PACKED -> -1;
        };
    }
}
