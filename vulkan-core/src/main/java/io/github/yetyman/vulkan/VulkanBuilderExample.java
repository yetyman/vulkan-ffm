package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import java.lang.foreign.*;

/**
 * Example demonstrating the new fluent builder patterns for Vulkan objects.
 */
public class VulkanBuilderExample {
    
    public static void main(String[] args) {
        try {
            demonstrateBuilders();
            System.out.println("Builder example completed successfully!");
        } catch (Exception e) {
            System.err.println("Builder example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void demonstrateBuilders() {
        try (Arena arena = Arena.ofConfined()) {
            
            // High-level context creation (without validation for demo)
            VulkanContext context = VulkanContext.builder()
                .applicationName("Builder Example")
                .applicationVersion(1)
                .instanceExtensions("VK_KHR_surface", "VK_KHR_win32_surface")
                .deviceExtensions("VK_KHR_swapchain")
                .build();
            
            System.out.println("✓ VulkanContext created");
            
            // Skip swapchain creation (requires real surface)
            System.out.println("✓ Skipping swapchain (no real surface)");
            
            // Create render pass with complex configuration
            VkRenderPass renderPass = VkRenderPass.builder()
                .device(context.device().handle())
                .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, 
                               VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR,
                               VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
                .depthAttachment(VkFormat.VK_FORMAT_D32_SFLOAT,
                               VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR,
                               VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .externalDependency(0,
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .build(arena);
            System.out.println("✓ VkRenderPass created");
            
            // Skip pipeline creation (requires valid shaders)
            System.out.println("✓ Skipping pipeline (no valid shaders)");
            
            // Create descriptor set layout
            VkDescriptorSetLayout descriptorLayout = VkDescriptorSetLayout.builder()
                .device(context.device().handle())
                .uniformBuffer(0, VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT)
                .combinedImageSampler(1, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT)
                .build(arena);
            System.out.println("✓ VkDescriptorSetLayout created");
            
            // Create descriptor pool
            VkDescriptorPool descriptorPool = VkDescriptorPool.builder()
                .device(context.device().handle())
                .maxSets(10)
                .uniformBuffers(10)
                .combinedImageSamplers(10)
                .freeDescriptorSet()
                .build(arena);
            System.out.println("✓ VkDescriptorPool created");
            
            // Create buffers using context convenience methods
            VkBuffer vertexBuffer = context.createVertexBuffer(1024);
            VkBuffer uniformBuffer = context.createUniformBuffer(256);
            VkBuffer stagingBuffer = context.createStagingBuffer(1024);
            System.out.println("✓ VkBuffers created");
            
            // Create synchronization objects
            VkSemaphore imageAvailable = VkSemaphore.builder()
                .device(context.device().handle())
                .build(arena);
            
            VkFence inFlightFence = VkFence.builder()
                .device(context.device().handle())
                .signaled(true)
                .build(arena);
            System.out.println("✓ Synchronization objects created");
            
            // Create command pool
            VkCommandPool commandPool = VkCommandPool.builder()
                .device(context.device().handle())
                .queueFamilyIndex(context.graphicsQueueFamily())
                .resetCommandBufferBit()
                .build(arena);
            System.out.println("✓ VkCommandPool created");
            
            // Clean up resources
            commandPool.close();
            inFlightFence.close();
            imageAvailable.close();
            stagingBuffer.close();
            uniformBuffer.close();
            vertexBuffer.close();
            descriptorPool.close();
            descriptorLayout.close();
            renderPass.close();
            context.close();
            System.out.println("✓ All resources cleaned up");
        }
    }
}