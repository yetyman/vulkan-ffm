package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCooperativeVectorMatrixLayoutNV
 * Generated from jextract bindings
 */
public record VkCooperativeVectorMatrixLayoutNV(int value) {

    public static final VkCooperativeVectorMatrixLayoutNV VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_COLUMN_MAJOR_NV = new VkCooperativeVectorMatrixLayoutNV(1);
    public static final VkCooperativeVectorMatrixLayoutNV VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_INFERENCING_OPTIMAL_NV = new VkCooperativeVectorMatrixLayoutNV(2);
    public static final VkCooperativeVectorMatrixLayoutNV VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_MAX_ENUM_NV = new VkCooperativeVectorMatrixLayoutNV(2147483647);
    public static final VkCooperativeVectorMatrixLayoutNV VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_ROW_MAJOR_NV = new VkCooperativeVectorMatrixLayoutNV(0);
    public static final VkCooperativeVectorMatrixLayoutNV VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_TRAINING_OPTIMAL_NV = new VkCooperativeVectorMatrixLayoutNV(3);

    public static VkCooperativeVectorMatrixLayoutNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_COLUMN_MAJOR_NV;
            case 2 -> VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_INFERENCING_OPTIMAL_NV;
            case 2147483647 -> VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_MAX_ENUM_NV;
            case 0 -> VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_ROW_MAJOR_NV;
            case 3 -> VK_COOPERATIVE_VECTOR_MATRIX_LAYOUT_TRAINING_OPTIMAL_NV;
            default -> new VkCooperativeVectorMatrixLayoutNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
