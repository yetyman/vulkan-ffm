package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkIndirectCommandsTokenTypeEXT
 * Generated from jextract bindings
 */
public record VkIndirectCommandsTokenTypeEXT(int value) {

    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DISPATCH_EXT = new VkIndirectCommandsTokenTypeEXT(9);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_COUNT_EXT = new VkIndirectCommandsTokenTypeEXT(8);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_EXT = new VkIndirectCommandsTokenTypeEXT(6);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_INDEXED_COUNT_EXT = new VkIndirectCommandsTokenTypeEXT(7);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_INDEXED_EXT = new VkIndirectCommandsTokenTypeEXT(5);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_COUNT_EXT = new VkIndirectCommandsTokenTypeEXT(1000328001);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_COUNT_NV_EXT = new VkIndirectCommandsTokenTypeEXT(1000202003);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_EXT = new VkIndirectCommandsTokenTypeEXT(1000328000);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_NV_EXT = new VkIndirectCommandsTokenTypeEXT(1000202002);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_EXECUTION_SET_EXT = new VkIndirectCommandsTokenTypeEXT(0);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_INDEX_BUFFER_EXT = new VkIndirectCommandsTokenTypeEXT(3);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_MAX_ENUM_EXT = new VkIndirectCommandsTokenTypeEXT(2147483647);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_PUSH_CONSTANT_EXT = new VkIndirectCommandsTokenTypeEXT(1);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_SEQUENCE_INDEX_EXT = new VkIndirectCommandsTokenTypeEXT(2);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_TRACE_RAYS2_EXT = new VkIndirectCommandsTokenTypeEXT(1000386004);
    public static final VkIndirectCommandsTokenTypeEXT VK_INDIRECT_COMMANDS_TOKEN_TYPE_VERTEX_BUFFER_EXT = new VkIndirectCommandsTokenTypeEXT(4);

    public static VkIndirectCommandsTokenTypeEXT fromValue(int value) {
        return switch (value) {
            case 9 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DISPATCH_EXT;
            case 8 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_COUNT_EXT;
            case 6 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_EXT;
            case 7 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_INDEXED_COUNT_EXT;
            case 5 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_INDEXED_EXT;
            case 1000328001 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_COUNT_EXT;
            case 1000202003 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_COUNT_NV_EXT;
            case 1000328000 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_EXT;
            case 1000202002 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_DRAW_MESH_TASKS_NV_EXT;
            case 0 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_EXECUTION_SET_EXT;
            case 3 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_INDEX_BUFFER_EXT;
            case 2147483647 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_MAX_ENUM_EXT;
            case 1 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_PUSH_CONSTANT_EXT;
            case 2 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_SEQUENCE_INDEX_EXT;
            case 1000386004 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_TRACE_RAYS2_EXT;
            case 4 -> VK_INDIRECT_COMMANDS_TOKEN_TYPE_VERTEX_BUFFER_EXT;
            default -> new VkIndirectCommandsTokenTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
