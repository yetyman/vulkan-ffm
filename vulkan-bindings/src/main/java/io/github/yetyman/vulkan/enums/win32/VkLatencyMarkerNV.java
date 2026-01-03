package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkLatencyMarkerNV
 * Generated from jextract bindings
 */
public record VkLatencyMarkerNV(int value) {

    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_INPUT_SAMPLE_NV = new VkLatencyMarkerNV(6);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_MAX_ENUM_NV = new VkLatencyMarkerNV(2147483647);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_OUT_OF_BAND_PRESENT_END_NV = new VkLatencyMarkerNV(11);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_OUT_OF_BAND_PRESENT_START_NV = new VkLatencyMarkerNV(10);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_OUT_OF_BAND_RENDERSUBMIT_END_NV = new VkLatencyMarkerNV(9);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_OUT_OF_BAND_RENDERSUBMIT_START_NV = new VkLatencyMarkerNV(8);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_PRESENT_END_NV = new VkLatencyMarkerNV(5);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_PRESENT_START_NV = new VkLatencyMarkerNV(4);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_RENDERSUBMIT_END_NV = new VkLatencyMarkerNV(3);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_RENDERSUBMIT_START_NV = new VkLatencyMarkerNV(2);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_SIMULATION_END_NV = new VkLatencyMarkerNV(1);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_SIMULATION_START_NV = new VkLatencyMarkerNV(0);
    public static final VkLatencyMarkerNV VK_LATENCY_MARKER_TRIGGER_FLASH_NV = new VkLatencyMarkerNV(7);

    public static VkLatencyMarkerNV fromValue(int value) {
        return switch (value) {
            case 6 -> VK_LATENCY_MARKER_INPUT_SAMPLE_NV;
            case 2147483647 -> VK_LATENCY_MARKER_MAX_ENUM_NV;
            case 11 -> VK_LATENCY_MARKER_OUT_OF_BAND_PRESENT_END_NV;
            case 10 -> VK_LATENCY_MARKER_OUT_OF_BAND_PRESENT_START_NV;
            case 9 -> VK_LATENCY_MARKER_OUT_OF_BAND_RENDERSUBMIT_END_NV;
            case 8 -> VK_LATENCY_MARKER_OUT_OF_BAND_RENDERSUBMIT_START_NV;
            case 5 -> VK_LATENCY_MARKER_PRESENT_END_NV;
            case 4 -> VK_LATENCY_MARKER_PRESENT_START_NV;
            case 3 -> VK_LATENCY_MARKER_RENDERSUBMIT_END_NV;
            case 2 -> VK_LATENCY_MARKER_RENDERSUBMIT_START_NV;
            case 1 -> VK_LATENCY_MARKER_SIMULATION_END_NV;
            case 0 -> VK_LATENCY_MARKER_SIMULATION_START_NV;
            case 7 -> VK_LATENCY_MARKER_TRIGGER_FLASH_NV;
            default -> new VkLatencyMarkerNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
