package io.github.yetyman.vulkan.sample.complex.postprocessing;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.highlevel.ShaderLoader;
import io.github.yetyman.vulkan.highlevel.VkTexture;
import io.github.yetyman.vulkan.highlevel.VkMemoryAllocator;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;

public class AdaptiveAA {
    private final Arena arena;
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    private final VkMemoryAllocator allocator;
    private final int width, height;
    
    // Render targets using VkTexture
    private VkTexture colorTarget;
    private VkTexture depthTarget;
    private VkTexture edgeTarget;
    private VkTexture previousFrame;
    
    // Render passes and pipelines
    private VkRenderPass sceneRenderPass;
    private VkRenderPass edgeRenderPass;
    private VkRenderPass aaRenderPass;
    private VkPipeline edgePipeline;
    private VkPipeline aaPipeline;
    
    // Descriptor sets
    private VkDescriptorSetLayout descriptorSetLayout;
    private VkDescriptorPool descriptorPool;
    private VkDescriptorSet descriptorSet;
    
    // Framebuffers
    private VkFramebuffer sceneFramebuffer;
    private VkFramebuffer edgeFramebuffer;
    
    private int frameIndex = 0;
    
    public AdaptiveAA(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice, int width, int height) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.allocator = VkMemoryAllocator.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .build(arena);
        this.width = width;
        this.height = height;
        
        createRenderTargets();
        createRenderPasses();
        createPipelines();
        createFramebuffers();
        createDescriptorSets();
    }
    
    private void createRenderTargets() {
        // Color target (RGBA8)
        colorTarget = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .nearest()
            .clampToEdge()
            .build(arena);
        
        // Depth target - use supported format with sampling for AA
        int depthFormat = findSupportedDepthFormat();
        depthTarget = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(depthFormat)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .nearest()
            .clampToEdge()
            .build(arena);
        
        // Edge detection target (R8)
        edgeTarget = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(VkFormat.VK_FORMAT_R8_UNORM)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .nearest()
            .clampToEdge()
            .build(arena);
        
        // Previous frame for TAA
        previousFrame = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .nearest()
            .clampToEdge()
            .build(arena);
    }
    
    private void createRenderPasses() {
        int depthFormat = findSupportedDepthFormat();
        
        // Scene render pass (color + depth) - transition color to shader read optimal for sampling
        sceneRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_R8G8B8A8_UNORM, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(depthFormat, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .build(arena);
        
        // Edge detection render pass - transition to shader read optimal
        edgeRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_R8_UNORM, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .build(arena);
        
        // Final AA render pass (to swapchain) - must match swapchain framebuffer structure
        aaRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(depthFormat, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
            .build(arena);
    }
    
    private void createPipelines() {
        // Create descriptor set layout for texture sampling
        descriptorSetLayout = createDescriptorSetLayout();
        
        try {
            // Edge detection pipeline
            byte[] edgeVertCode = ShaderLoader.compileShader("/shaders/fullscreen.vert");
            byte[] edgeFragCode = ShaderLoader.compileShader("/shaders/edge_detect.frag");
            
            edgePipeline = VkPipeline.builder()
                .device(device)
                .renderPass(edgeRenderPass.handle())
                .viewport(0, 0, width, height)
                .vertexShader(edgeVertCode)
                .fragmentShader(edgeFragCode)
                .triangleTopology()
                .descriptorSetLayouts(descriptorSetLayout.handle())
                .build(arena);
            
            // Adaptive AA pipeline
            byte[] aaVertCode = ShaderLoader.compileShader("/shaders/fullscreen.vert");
            byte[] aaFragCode = ShaderLoader.compileShader("/shaders/adaptive_aa.frag");
            
            aaPipeline = VkPipeline.builder()
                .device(device)
                .renderPass(aaRenderPass.handle())
                .viewport(0, 0, width, height)
                .vertexShader(aaVertCode)
                .fragmentShader(aaFragCode)
                .triangleTopology()
                .descriptorSetLayouts(descriptorSetLayout.handle())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT, 0, 8)
                .depthTest(true)
                .depthWrite(false)
                .depthCompareOp(VkCompareOp.VK_COMPARE_OP_ALWAYS)
                .build(arena);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to create AA pipelines: " + e.getMessage());
            throw new RuntimeException("AdaptiveAA pipeline creation failed", e);
        }
    }
    

    private VkDescriptorSetLayout createDescriptorSetLayout() {
        // Create 4 bindings including depth texture
        MemorySegment bindings = arena.allocate(VkDescriptorSetLayoutBinding.layout(), 4);
        
        for (int i = 0; i < 4; i++) {
            MemorySegment binding = bindings.asSlice(i * VkDescriptorSetLayoutBinding.layout().byteSize(), VkDescriptorSetLayoutBinding.layout());
            VkDescriptorSetLayoutBinding.binding(binding, i);
            VkDescriptorSetLayoutBinding.descriptorType(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            VkDescriptorSetLayoutBinding.descriptorCount(binding, 1);
            VkDescriptorSetLayoutBinding.stageFlags(binding, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutBinding.pImmutableSamplers(binding, MemorySegment.NULL);
        }
        
        MemorySegment layoutInfo = VkDescriptorSetLayoutCreateInfo.allocate(arena);
        VkDescriptorSetLayoutCreateInfo.sType(layoutInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
        VkDescriptorSetLayoutCreateInfo.pNext(layoutInfo, MemorySegment.NULL);
        VkDescriptorSetLayoutCreateInfo.flags(layoutInfo, 0);
        VkDescriptorSetLayoutCreateInfo.bindingCount(layoutInfo, 4);
        VkDescriptorSetLayoutCreateInfo.pBindings(layoutInfo, bindings);
        
        MemorySegment layoutPtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createDescriptorSetLayout(device.handle(), layoutInfo, layoutPtr).check();
        descriptorSetLayout = new VkDescriptorSetLayout(layoutPtr.get(ValueLayout.ADDRESS, 0), device);
        return descriptorSetLayout;
    }
    
    private void createDescriptorSets() {
        // Create descriptor pool for 4 textures including depth
        MemorySegment poolSize = VkDescriptorPoolSize.allocate(arena);
        VkDescriptorPoolSize.type(poolSize, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkDescriptorPoolSize.descriptorCount(poolSize, 4);
        
        MemorySegment poolInfo = VkDescriptorPoolCreateInfo.allocate(arena);
        VkDescriptorPoolCreateInfo.sType(poolInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
        VkDescriptorPoolCreateInfo.flags(poolInfo, 0);
        VkDescriptorPoolCreateInfo.maxSets(poolInfo, 1);
        VkDescriptorPoolCreateInfo.poolSizeCount(poolInfo, 1);
        VkDescriptorPoolCreateInfo.pPoolSizes(poolInfo, poolSize);
        
        MemorySegment poolPtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createDescriptorPool(device.handle(), poolInfo, poolPtr).check();
        descriptorPool = new VkDescriptorPool(poolPtr.get(ValueLayout.ADDRESS, 0), device);
        
        // Allocate descriptor set
        MemorySegment allocInfo = VkDescriptorSetAllocateInfo.allocate(arena);
        VkDescriptorSetAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
        VkDescriptorSetAllocateInfo.descriptorPool(allocInfo, descriptorPool.handle());
        VkDescriptorSetAllocateInfo.descriptorSetCount(allocInfo, 1);
        MemorySegment layoutArray = arena.allocate(ValueLayout.ADDRESS);
        layoutArray.set(ValueLayout.ADDRESS, 0, descriptorSetLayout.handle());
        VkDescriptorSetAllocateInfo.pSetLayouts(allocInfo, layoutArray);
        
        MemorySegment setPtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.allocateDescriptorSets(device.handle(), allocInfo, setPtr).check();
        descriptorSet = new VkDescriptorSet(setPtr.get(ValueLayout.ADDRESS, 0));
        
        // Update descriptor set with 4 textures including depth
        MemorySegment imageInfos = arena.allocate(VkDescriptorImageInfo.layout(), 4);
        MemorySegment writeDescriptorSets = arena.allocate(VkWriteDescriptorSet.layout(), 4);
        
        // Binding 0: currentFrame
        MemorySegment imageInfo0 = imageInfos.asSlice(0, VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo0, colorTarget.sampler());
        VkDescriptorImageInfo.imageView(imageInfo0, colorTarget.imageView());
        VkDescriptorImageInfo.imageLayout(imageInfo0, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write0 = writeDescriptorSets.asSlice(0, VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write0, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write0, descriptorSet.handle());
        VkWriteDescriptorSet.dstBinding(write0, 0);
        VkWriteDescriptorSet.dstArrayElement(write0, 0);
        VkWriteDescriptorSet.descriptorType(write0, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write0, 1);
        VkWriteDescriptorSet.pImageInfo(write0, imageInfo0);
        
        // Binding 1: depthTexture (for edge detection shader)
        MemorySegment imageInfo1 = imageInfos.asSlice(VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo1, depthTarget.sampler());
        VkDescriptorImageInfo.imageView(imageInfo1, depthTarget.imageView());
        VkDescriptorImageInfo.imageLayout(imageInfo1, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write1 = writeDescriptorSets.asSlice(VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write1, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write1, descriptorSet.handle());
        VkWriteDescriptorSet.dstBinding(write1, 1);
        VkWriteDescriptorSet.dstArrayElement(write1, 0);
        VkWriteDescriptorSet.descriptorType(write1, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write1, 1);
        VkWriteDescriptorSet.pImageInfo(write1, imageInfo1);
        
        // Binding 2: previousFrame
        MemorySegment imageInfo2 = imageInfos.asSlice(2 * VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo2, previousFrame.sampler());
        VkDescriptorImageInfo.imageView(imageInfo2, previousFrame.imageView());
        VkDescriptorImageInfo.imageLayout(imageInfo2, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write2 = writeDescriptorSets.asSlice(2 * VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write2, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write2, descriptorSet.handle());
        VkWriteDescriptorSet.dstBinding(write2, 2);
        VkWriteDescriptorSet.dstArrayElement(write2, 0);
        VkWriteDescriptorSet.descriptorType(write2, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write2, 1);
        VkWriteDescriptorSet.pImageInfo(write2, imageInfo2);
        
        // Binding 3: edgeTexture
        MemorySegment imageInfo3 = imageInfos.asSlice(3 * VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo3, edgeTarget.sampler());
        VkDescriptorImageInfo.imageView(imageInfo3, edgeTarget.imageView());
        VkDescriptorImageInfo.imageLayout(imageInfo3, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write3 = writeDescriptorSets.asSlice(3 * VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write3, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write3, descriptorSet.handle());
        VkWriteDescriptorSet.dstBinding(write3, 3);
        VkWriteDescriptorSet.dstArrayElement(write3, 0);
        VkWriteDescriptorSet.descriptorType(write3, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write3, 1);
        VkWriteDescriptorSet.pImageInfo(write3, imageInfo3);
        
        Vulkan.updateDescriptorSets(device.handle(), 4, writeDescriptorSets, 0, MemorySegment.NULL);
    }
    
    private void createFramebuffers() {
        // Scene framebuffer
        sceneFramebuffer = VkFramebuffer.builder()
            .device(device)
            .renderPass(sceneRenderPass.handle())
            .attachment(new VkFramebufferAttachment(colorTarget, VkFramebufferAttachment.AttachmentType.COLOR, 0))
            .attachment(new VkFramebufferAttachment(depthTarget, VkFramebufferAttachment.AttachmentType.DEPTH, 1))
            .dimensions(width, height)
            .build(arena);
        
        // Edge framebuffer
        edgeFramebuffer = VkFramebuffer.builder()
            .device(device)
            .renderPass(edgeRenderPass.handle())
            .attachment(new VkFramebufferAttachment(edgeTarget, VkFramebufferAttachment.AttachmentType.COLOR, 0))
            .dimensions(width, height)
            .build(arena);
    }
    
    public VkRenderPass getSceneRenderPass() { return sceneRenderPass; }
    public VkFramebuffer getSceneFramebuffer() { return sceneFramebuffer; }
    
    public void performAA(MemorySegment commandBuffer, VkFramebuffer finalFramebuffer, Arena frameArena) {
        // Transition color and depth textures for edge detection sampling - use actual current layouts
        transitionImageLayout(commandBuffer, colorTarget.image(), VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        transitionImageLayout(commandBuffer, depthTarget.image(), VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, true);
        transitionImageLayout(commandBuffer, previousFrame.image(), VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        
        // 1. Edge detection pass
        VkCommandBuffer.beginRenderPass(commandBuffer, edgeRenderPass.handle(), edgeFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, edgePipeline.handle());
        MemorySegment edgeDescriptorSets = frameArena.allocate(ValueLayout.ADDRESS);
        edgeDescriptorSets.set(ValueLayout.ADDRESS, 0, descriptorSet.handle());
        Vulkan.cmdBindDescriptorSets(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, 
            edgePipeline.layout(), 0, 1, edgeDescriptorSets, 0, MemorySegment.NULL);
        Vulkan.cmdDraw(commandBuffer, 3, 1, 0, 0);
        Vulkan.cmdEndRenderPass(commandBuffer);
        
        // Transition edge texture to shader read layout after edge detection
        transitionImageLayout(commandBuffer, edgeTarget.image(), VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        
        // 2. Adaptive AA pass - need to create compatible framebuffer or use direct rendering
        VkCommandBuffer.beginRenderPass(commandBuffer, aaRenderPass.handle(), finalFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .clearDepth(1.0f, 0)
            .execute(frameArena);
        
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, aaPipeline.handle());
        MemorySegment aaDescriptorSets = frameArena.allocate(ValueLayout.ADDRESS);
        aaDescriptorSets.set(ValueLayout.ADDRESS, 0, descriptorSet.handle());
        Vulkan.cmdBindDescriptorSets(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, 
            aaPipeline.layout(), 0, 1, aaDescriptorSets, 0, MemorySegment.NULL);
        
        // Push constants for frame info
        MemorySegment pushConstants = frameArena.allocate(8); // float frameIndex + float deltaTime
        pushConstants.set(ValueLayout.JAVA_FLOAT, 0, (float)frameIndex);
        pushConstants.set(ValueLayout.JAVA_FLOAT, 4, 16.67f); // ~60fps
        Vulkan.cmdPushConstants(commandBuffer, aaPipeline.layout(), 
            VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT, 0, 8, pushConstants);
        
        Vulkan.cmdDraw(commandBuffer, 3, 1, 0, 0);
        Vulkan.cmdEndRenderPass(commandBuffer);
        
        frameIndex++;
    }
    
    public void cleanup() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.handle().equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device.handle()).check();
            Logger.debug("Device idle - starting AdaptiveAA cleanup");
        }
        
        // Clean up descriptor resources
        if (descriptorPool != null && !descriptorPool.equals(MemorySegment.NULL)) {
            Vulkan.destroyDescriptorPool(device != null ? device.handle() : MemorySegment.NULL, descriptorPool.handle());
        }
        if (descriptorSetLayout != null && !descriptorSetLayout.equals(MemorySegment.NULL)) {
            Vulkan.destroyDescriptorSetLayout(device != null ? device.handle() : MemorySegment.NULL, descriptorSetLayout.handle());
        }
        
        // Clean up other resources
        if (sceneFramebuffer != null) sceneFramebuffer.close();
        if (edgeFramebuffer != null) edgeFramebuffer.close();
        if (edgePipeline != null) edgePipeline.close();
        if (aaPipeline != null) aaPipeline.close();
        if (sceneRenderPass != null) sceneRenderPass.close();
        if (edgeRenderPass != null) edgeRenderPass.close();
        if (aaRenderPass != null) aaRenderPass.close();
        
        // Clean up textures (includes image, imageView, and sampler)
        if (colorTarget != null) colorTarget.close();
        if (depthTarget != null) depthTarget.close();
        if (edgeTarget != null) edgeTarget.close();
        if (previousFrame != null) previousFrame.close();
        
        if (allocator != null) allocator.close();
        
        Logger.debug("AdaptiveAA cleanup complete");
    }
    
    private int findSupportedDepthFormat() {
        // Try common depth formats in order of preference, requiring both attachment and sampling support
        int[] candidates = {
            VkFormat.VK_FORMAT_D32_SFLOAT,
            VkFormat.VK_FORMAT_D16_UNORM,
            VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT,
            VkFormat.VK_FORMAT_D24_UNORM_S8_UINT
        };
        
        try (Arena tempArena = Arena.ofConfined()) {
            for (int format : candidates) {
                MemorySegment formatProps = tempArena.allocate(VkFormatProperties.sizeof());
                Vulkan.getPhysicalDeviceFormatProperties(physicalDevice.handle(), format, formatProps);
                
                int optimalFeatures = VkFormatProperties.optimalTilingFeatures(formatProps);
                int requiredFeatures = VkFormatFeatureFlagBits.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT | VkFormatFeatureFlagBits.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
                if ((optimalFeatures & requiredFeatures) == requiredFeatures) {
                    return format;
                }
            }
        }
        
        throw new RuntimeException("Failed to find supported depth format with sampling support");
    }
    
    private void transitionImageLayout(MemorySegment commandBuffer, MemorySegment image, int oldLayout, int newLayout, boolean isDepth) {
        MemorySegment barrier = VkImageMemoryBarrier.allocate(arena);
        VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
        VkImageMemoryBarrier.newLayout(barrier, newLayout);
        VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, ~0);
        VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, ~0);
        VkImageMemoryBarrier.image(barrier, image);
        
        MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
        VkImageSubresourceRange.aspectMask(subresourceRange, 
            isDepth ? VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT : VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT);
        VkImageSubresourceRange.baseMipLevel(subresourceRange, 0);
        VkImageSubresourceRange.levelCount(subresourceRange, 1);
        VkImageSubresourceRange.baseArrayLayer(subresourceRange, 0);
        VkImageSubresourceRange.layerCount(subresourceRange, 1);
        
        // When transitioning from UNDEFINED, no source access mask is needed
        if (oldLayout == VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED) {
            VkImageMemoryBarrier.srcAccessMask(barrier, 0);
        } else if (isDepth) {
            VkImageMemoryBarrier.srcAccessMask(barrier, VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
        } else {
            VkImageMemoryBarrier.srcAccessMask(barrier, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        }
        VkImageMemoryBarrier.dstAccessMask(barrier, VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT);
        
        int srcStage = oldLayout == VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED ? 
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT :
            (isDepth ? VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT : VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        
        Vulkan.cmdPipelineBarrier(commandBuffer, 
            srcStage,
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }
}