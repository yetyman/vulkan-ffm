package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkIndirectCommandsTokenTypeNV
 * Generated from jextract bindings
 */
public record VkIndirectCommandsTokenTypeNV(int value) {

    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_DISPATCH_NV = new VkIndirectCommandsTokenTypeNV(1000428004);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_INDEXED_NV = new VkIndirectCommandsTokenTypeNV(5);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_NV = new VkIndirectCommandsTokenTypeNV(1000328000);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_NV = new VkIndirectCommandsTokenTypeNV(6);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_TASKS_NV = new VkIndirectCommandsTokenTypeNV(7);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_INDEX_BUFFER_NV = new VkIndirectCommandsTokenTypeNV(2);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_MAX_ENUM_NV = new VkIndirectCommandsTokenTypeNV(2147483647);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_PIPELINE_NV = new VkIndirectCommandsTokenTypeNV(1000428003);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_PUSH_CONSTANT_NV = new VkIndirectCommandsTokenTypeNV(4);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_SHADER_GROUP_NV = new VkIndirectCommandsTokenTypeNV(0);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_STATE_FLAGS_NV = new VkIndirectCommandsTokenTypeNV(1);
    public static final VkIndirectCommandsTokenTypeNV VK_INDIRECT_COMMANDS_TOKEN_TYPE_VERTEX_BUFFER_NV = new VkIndirectCommandsTokenTypeNV(3);

    public static VkIndirectCommandsTokenTypeNV fromValue(int value) {
        return switch (value) {
            case 1000428004 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DISPATCH_NV;
            case 5 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_INDEXED_NV;
            case 1000328000 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_NV;
            case 6 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_NV;
            case 7 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_TASKS_NV;
            case 2 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_INDEX_BUFFER_NV;
            case 2147483647 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_MAX_ENUM_NV;
            case 1000428003 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_PIPELINE_NV;
            case 4 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_PUSH_CONSTANT_NV;
            case 0 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_SHADER_GROUP_NV;
            case 1 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_STATE_FLAGS_NV;
            case 3 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_VERTEX_BUFFER_NV;
            default -> new VkIndirectCommandsTokenTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
