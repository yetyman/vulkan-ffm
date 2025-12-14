package io.github.yetyman.vulkan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VulkanLibraryTest {
    
    @Test
    void testVulkanLibraryLoads() {
        assertDoesNotThrow(() -> VulkanLibrary.load(), 
            "Vulkan library should load without throwing exceptions");
    }
}
