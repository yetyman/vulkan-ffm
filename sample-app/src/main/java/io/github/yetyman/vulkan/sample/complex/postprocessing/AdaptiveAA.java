package io.github.yetyman.vulkan.sample.complex.postprocessing;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;

public class AdaptiveAA {
    private final Arena arena;
    private final MemorySegment device;
    private final int width, height;
    
    // Render targets
    private VkImage colorTarget;
    private VkImageView colorTargetView;
    private VkImage depthTarget;
    private VkImageView depthTargetView;
    private VkImage edgeTarget;
    private VkImageView edgeTargetView;
    private VkImage previousFrame;
    private VkImageView previousFrameView;
    
    // Render passes and pipelines
    private VkRenderPass sceneRenderPass;
    private VkRenderPass edgeRenderPass;
    private VkRenderPass aaRenderPass;
    private VkPipeline edgePipeline;
    private VkPipeline aaPipeline;
    
    // Descriptor sets
    private MemorySegment descriptorSetLayout;
    private MemorySegment sampler;
    private MemorySegment descriptorPool;
    private MemorySegment descriptorSet;
    
    // Framebuffers
    private VkFramebuffer sceneFramebuffer;
    private VkFramebuffer edgeFramebuffer;
    
    private int frameIndex = 0;
    
    private final MemorySegment physicalDevice;
    
    public AdaptiveAA(Arena arena, MemorySegment device, MemorySegment physicalDevice, int width, int height) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.width = width;
        this.height = height;
        
        createRenderTargets();
        createRenderPasses();
        createSampler();
        createPipelines();
        createFramebuffers();
        createDescriptorSets();
    }
    
    private void createRenderTargets() {
        // Color target (RGBA8)
        colorTarget = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .build(arena);
        
        colorTargetView = VkImageView.builder()
            .device(device)
            .image(colorTarget.handle())
            .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
            .build(arena);
        
        // Depth target - use supported format with sampling for AA
        int depthFormat = findSupportedDepthFormat();
        depthTarget = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(depthFormat)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .build(arena);
        
        depthTargetView = VkImageView.builder()
            .device(device)
            .image(depthTarget.handle())
            .format(depthFormat)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT)
            .build(arena);
        
        // Edge detection target (R8)
        edgeTarget = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(VkFormat.VK_FORMAT_R8_UNORM)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .build(arena);
        
        edgeTargetView = VkImageView.builder()
            .device(device)
            .image(edgeTarget.handle())
            .format(VkFormat.VK_FORMAT_R8_UNORM)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
            .build(arena);
        
        // Previous frame for TAA
        previousFrame = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .build(arena);
        
        previousFrameView = VkImageView.builder()
            .device(device)
            .image(previousFrame.handle())
            .format(VkFormat.VK_FORMAT_R8G8B8A8_UNORM)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT)
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
        MemorySegment descriptorSetLayout = createDescriptorSetLayout();
        
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
                .descriptorSetLayouts(descriptorSetLayout)
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
                .descriptorSetLayouts(descriptorSetLayout)
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
    
    private void createSampler() {
        MemorySegment samplerInfo = VkSamplerCreateInfo.allocate(arena);
        VkSamplerCreateInfo.sType(samplerInfo, VkStructureType.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
        VkSamplerCreateInfo.magFilter(samplerInfo, VkFilter.VK_FILTER_NEAREST);
        VkSamplerCreateInfo.minFilter(samplerInfo, VkFilter.VK_FILTER_NEAREST);
        VkSamplerCreateInfo.addressModeU(samplerInfo, VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        VkSamplerCreateInfo.addressModeV(samplerInfo, VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        VkSamplerCreateInfo.addressModeW(samplerInfo, VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        VkSamplerCreateInfo.anisotropyEnable(samplerInfo, 0);
        VkSamplerCreateInfo.maxAnisotropy(samplerInfo, 1.0f);
        VkSamplerCreateInfo.borderColor(samplerInfo, VkBorderColor.VK_BORDER_COLOR_INT_OPAQUE_BLACK);
        VkSamplerCreateInfo.unnormalizedCoordinates(samplerInfo, 0);
        VkSamplerCreateInfo.compareEnable(samplerInfo, 0);
        VkSamplerCreateInfo.compareOp(samplerInfo, VkCompareOp.VK_COMPARE_OP_ALWAYS);
        VkSamplerCreateInfo.mipmapMode(samplerInfo, VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR);
        VkSamplerCreateInfo.mipLodBias(samplerInfo, 0.0f);
        VkSamplerCreateInfo.minLod(samplerInfo, 0.0f);
        VkSamplerCreateInfo.maxLod(samplerInfo, 0.0f);
        
        MemorySegment samplerPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createSampler(device, samplerInfo, samplerPtr).check();
        sampler = samplerPtr.get(ValueLayout.ADDRESS, 0);
    }
    
    private MemorySegment createDescriptorSetLayout() {
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
        VulkanExtensions.createDescriptorSetLayout(device, layoutInfo, layoutPtr).check();
        descriptorSetLayout = layoutPtr.get(ValueLayout.ADDRESS, 0);
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
        VulkanExtensions.createDescriptorPool(device, poolInfo, poolPtr).check();
        descriptorPool = poolPtr.get(ValueLayout.ADDRESS, 0);
        
        // Allocate descriptor set
        MemorySegment allocInfo = VkDescriptorSetAllocateInfo.allocate(arena);
        VkDescriptorSetAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
        VkDescriptorSetAllocateInfo.descriptorPool(allocInfo, descriptorPool);
        VkDescriptorSetAllocateInfo.descriptorSetCount(allocInfo, 1);
        MemorySegment layoutArray = arena.allocate(ValueLayout.ADDRESS);
        layoutArray.set(ValueLayout.ADDRESS, 0, descriptorSetLayout);
        VkDescriptorSetAllocateInfo.pSetLayouts(allocInfo, layoutArray);
        
        MemorySegment setPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.allocateDescriptorSets(device, allocInfo, setPtr).check();
        descriptorSet = setPtr.get(ValueLayout.ADDRESS, 0);
        
        // Update descriptor set with 4 textures including depth
        MemorySegment imageInfos = arena.allocate(VkDescriptorImageInfo.layout(), 4);
        MemorySegment writeDescriptorSets = arena.allocate(VkWriteDescriptorSet.layout(), 4);
        
        // Binding 0: currentFrame
        MemorySegment imageInfo0 = imageInfos.asSlice(0, VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo0, sampler);
        VkDescriptorImageInfo.imageView(imageInfo0, colorTargetView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo0, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write0 = writeDescriptorSets.asSlice(0, VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write0, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write0, descriptorSet);
        VkWriteDescriptorSet.dstBinding(write0, 0);
        VkWriteDescriptorSet.dstArrayElement(write0, 0);
        VkWriteDescriptorSet.descriptorType(write0, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write0, 1);
        VkWriteDescriptorSet.pImageInfo(write0, imageInfo0);
        
        // Binding 1: depthTexture (for edge detection shader)
        MemorySegment imageInfo1 = imageInfos.asSlice(VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo1, sampler);
        VkDescriptorImageInfo.imageView(imageInfo1, depthTargetView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo1, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write1 = writeDescriptorSets.asSlice(VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write1, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write1, descriptorSet);
        VkWriteDescriptorSet.dstBinding(write1, 1);
        VkWriteDescriptorSet.dstArrayElement(write1, 0);
        VkWriteDescriptorSet.descriptorType(write1, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write1, 1);
        VkWriteDescriptorSet.pImageInfo(write1, imageInfo1);
        
        // Binding 2: previousFrame
        MemorySegment imageInfo2 = imageInfos.asSlice(2 * VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo2, sampler);
        VkDescriptorImageInfo.imageView(imageInfo2, previousFrameView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo2, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write2 = writeDescriptorSets.asSlice(2 * VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write2, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write2, descriptorSet);
        VkWriteDescriptorSet.dstBinding(write2, 2);
        VkWriteDescriptorSet.dstArrayElement(write2, 0);
        VkWriteDescriptorSet.descriptorType(write2, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write2, 1);
        VkWriteDescriptorSet.pImageInfo(write2, imageInfo2);
        
        // Binding 3: edgeTexture
        MemorySegment imageInfo3 = imageInfos.asSlice(3 * VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo3, sampler);
        VkDescriptorImageInfo.imageView(imageInfo3, edgeTargetView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo3, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write3 = writeDescriptorSets.asSlice(3 * VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write3, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write3, descriptorSet);
        VkWriteDescriptorSet.dstBinding(write3, 3);
        VkWriteDescriptorSet.dstArrayElement(write3, 0);
        VkWriteDescriptorSet.descriptorType(write3, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write3, 1);
        VkWriteDescriptorSet.pImageInfo(write3, imageInfo3);
        
        VulkanExtensions.updateDescriptorSets(device, 4, writeDescriptorSets, 0, MemorySegment.NULL);
    }
    
    private void createFramebuffers() {
        // Scene framebuffer
        sceneFramebuffer = VkFramebuffer.builder()
            .device(device)
            .renderPass(sceneRenderPass.handle())
            .attachment(colorTargetView.handle())
            .attachment(depthTargetView.handle())
            .dimensions(width, height)
            .build(arena);
        
        // Edge framebuffer
        edgeFramebuffer = VkFramebuffer.builder()
            .device(device)
            .renderPass(edgeRenderPass.handle())
            .attachment(edgeTargetView.handle())
            .dimensions(width, height)
            .build(arena);
    }
    
    public VkRenderPass getSceneRenderPass() { return sceneRenderPass; }
    public VkFramebuffer getSceneFramebuffer() { return sceneFramebuffer; }
    
    public void performAA(MemorySegment commandBuffer, VkFramebuffer finalFramebuffer, Arena frameArena) {
        // Transition color and depth textures for edge detection sampling - use actual current layouts
        transitionImageLayout(commandBuffer, colorTarget.handle(), VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        transitionImageLayout(commandBuffer, depthTarget.handle(), VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, true);
        transitionImageLayout(commandBuffer, previousFrame.handle(), VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        
        // 1. Edge detection pass
        VkCommandBuffer.beginRenderPass(commandBuffer, edgeRenderPass.handle(), edgeFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, edgePipeline.handle());
        MemorySegment edgeDescriptorSets = frameArena.allocate(ValueLayout.ADDRESS);
        edgeDescriptorSets.set(ValueLayout.ADDRESS, 0, descriptorSet);
        VulkanExtensions.cmdBindDescriptorSets(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, 
            edgePipeline.layout(), 0, 1, edgeDescriptorSets, 0, MemorySegment.NULL);
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        
        // Transition edge texture to shader read layout after edge detection
        transitionImageLayout(commandBuffer, edgeTarget.handle(), VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, false);
        
        // 2. Adaptive AA pass - need to create compatible framebuffer or use direct rendering
        VkCommandBuffer.beginRenderPass(commandBuffer, aaRenderPass.handle(), finalFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .clearDepth(1.0f, 0)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, aaPipeline.handle());
        MemorySegment aaDescriptorSets = frameArena.allocate(ValueLayout.ADDRESS);
        aaDescriptorSets.set(ValueLayout.ADDRESS, 0, descriptorSet);
        VulkanExtensions.cmdBindDescriptorSets(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, 
            aaPipeline.layout(), 0, 1, aaDescriptorSets, 0, MemorySegment.NULL);
        
        // Push constants for frame info
        MemorySegment pushConstants = frameArena.allocate(8); // float frameIndex + float deltaTime
        pushConstants.set(ValueLayout.JAVA_FLOAT, 0, (float)frameIndex);
        pushConstants.set(ValueLayout.JAVA_FLOAT, 4, 16.67f); // ~60fps
        VulkanExtensions.cmdPushConstants(commandBuffer, aaPipeline.layout(), 
            VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT, 0, 8, pushConstants);
        
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        
        frameIndex++;
    }
    
    public void cleanup() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device).check();
            Logger.debug("Device idle - starting AdaptiveAA cleanup");
        }
        
        // Clean up descriptor resources
        if (descriptorPool != null && !descriptorPool.equals(MemorySegment.NULL)) {
            VulkanExtensions.destroyDescriptorPool(device, descriptorPool);
        }
        if (descriptorSetLayout != null && !descriptorSetLayout.equals(MemorySegment.NULL)) {
            VulkanExtensions.destroyDescriptorSetLayout(device, descriptorSetLayout);
        }
        if (sampler != null && !sampler.equals(MemorySegment.NULL)) {
            VulkanExtensions.destroySampler(device, sampler);
        }
        
        // Clean up other resources
        if (sceneFramebuffer != null) sceneFramebuffer.close();
        if (edgeFramebuffer != null) edgeFramebuffer.close();
        if (edgePipeline != null) edgePipeline.close();
        if (aaPipeline != null) aaPipeline.close();
        if (sceneRenderPass != null) sceneRenderPass.close();
        if (edgeRenderPass != null) edgeRenderPass.close();
        if (aaRenderPass != null) aaRenderPass.close();
        if (colorTargetView != null) colorTargetView.close();
        if (depthTargetView != null) depthTargetView.close();
        if (edgeTargetView != null) edgeTargetView.close();
        if (previousFrameView != null) previousFrameView.close();
        if (colorTarget != null) colorTarget.close();
        if (depthTarget != null) depthTarget.close();
        if (edgeTarget != null) edgeTarget.close();
        if (previousFrame != null) previousFrame.close();
        
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
                VulkanExtensions.getPhysicalDeviceFormatProperties(physicalDevice, format, formatProps);
                
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
        
        VulkanExtensions.cmdPipelineBarrier(commandBuffer, 
            srcStage,
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, barrier);
    }
}