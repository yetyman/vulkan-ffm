package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkDebugUtilsLabelEXT;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * Utility for inserting debug labels around render graph node execution.
 * Labels appear in RenderDoc, validation layer output, and GPU profilers.
 *
 * Labels are only inserted when debug labels are enabled on the executor.
 * The overhead is negligible (one struct allocation per node per frame from the frame arena).
 */
final class DebugLabels {

    private DebugLabels() {}

    /**
     * Begins a debug label region on the command buffer.
     *
     * @param commandBuffer the command buffer handle
     * @param name label name (appears in RenderDoc)
     * @param r red component (0..1)
     * @param g green component (0..1)
     * @param b blue component (0..1)
     * @param a alpha component (0..1)
     * @param arena arena for struct allocation
     */
    static void begin(MemorySegment commandBuffer, String name,
                      float r, float g, float b, float a, SegmentAllocator allocator) {
        MemorySegment label = VkDebugUtilsLabelEXT.allocate(allocator);
        VkDebugUtilsLabelEXT.sType(label, VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT.value());
        VkDebugUtilsLabelEXT.pNext(label, MemorySegment.NULL);
        VkDebugUtilsLabelEXT.pLabelName(label, allocator.allocateFrom(name));
        MemorySegment color = VkDebugUtilsLabelEXT.color(label);
        color.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, r);
        color.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, 1, g);
        color.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, 2, b);
        color.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, 3, a);
        VulkanFFM.vkCmdBeginDebugUtilsLabelEXT(commandBuffer, label);
    }

    /**
     * Begins a debug label with a color derived from the node type.
     */
    static void beginForNode(MemorySegment commandBuffer, String nodeName,
                             io.github.yetyman.vulkan.graph.nodes.NodeType nodeType, SegmentAllocator allocator) {
        float r, g, b;
        switch (nodeType) {
            case GRAPHICS -> { r = 0.2f; g = 0.6f; b = 1.0f; }  // blue
            case COMPUTE  -> { r = 0.2f; g = 1.0f; b = 0.4f; }  // green
            case TRANSFER -> { r = 1.0f; g = 0.8f; b = 0.2f; }  // yellow
            case CPU_WORK -> { r = 1.0f; g = 0.4f; b = 0.2f; }  // orange
            case PRESENT  -> { r = 0.8f; g = 0.2f; b = 1.0f; }  // purple
            default       -> { r = 0.7f; g = 0.7f; b = 0.7f; }  // gray
        }
        begin(commandBuffer, nodeName, r, g, b, 1.0f, allocator);
    }

    /**
     * Ends the current debug label region on the command buffer.
     */
    static void end(MemorySegment commandBuffer) {
        VulkanFFM.vkCmdEndDebugUtilsLabelEXT(commandBuffer);
    }
}
