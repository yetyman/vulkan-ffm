package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
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
    
    public AdaptiveAA(Arena arena, MemorySegment device, int width, int height) {
        this.arena = arena;
        this.device = device;
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
        
        // Depth target
        depthTarget = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT)
            .build(arena);
        
        depthTargetView = VkImageView.builder()
            .device(device)
            .image(depthTarget.handle())
            .format(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT)
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
        // Scene render pass (color + depth)
        sceneRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_R8G8B8A8_UNORM, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .build(arena);
        
        // Edge detection render pass
        edgeRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_R8_UNORM, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .build(arena);
        
        // Final AA render pass (to swapchain)
        aaRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .build(arena);
    }
    
    private void createPipelines() {
        // Create descriptor set layout for texture sampling
        MemorySegment descriptorSetLayout = createDescriptorSetLayout();
        
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
            .fullscreenTriangle()
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
            .fullscreenTriangle()
            .descriptorSetLayouts(descriptorSetLayout)
            .build(arena);
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
        // Create binding for scene color texture
        MemorySegment binding = VkDescriptorSetLayoutBinding.allocate(arena);
        VkDescriptorSetLayoutBinding.binding(binding, 0);
        VkDescriptorSetLayoutBinding.descriptorType(binding, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkDescriptorSetLayoutBinding.descriptorCount(binding, 1);
        VkDescriptorSetLayoutBinding.stageFlags(binding, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT);
        VkDescriptorSetLayoutBinding.pImmutableSamplers(binding, MemorySegment.NULL);
        
        MemorySegment layoutInfo = VkDescriptorSetLayoutCreateInfo.allocate(arena);
        VkDescriptorSetLayoutCreateInfo.sType(layoutInfo, VkStructureType.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
        VkDescriptorSetLayoutCreateInfo.pNext(layoutInfo, MemorySegment.NULL);
        VkDescriptorSetLayoutCreateInfo.flags(layoutInfo, 0);
        VkDescriptorSetLayoutCreateInfo.bindingCount(layoutInfo, 1);
        VkDescriptorSetLayoutCreateInfo.pBindings(layoutInfo, binding);
        
        MemorySegment layoutPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createDescriptorSetLayout(device, layoutInfo, layoutPtr).check();
        descriptorSetLayout = layoutPtr.get(ValueLayout.ADDRESS, 0);
        return descriptorSetLayout;
    }
    
    private void createDescriptorSets() {
        // Create descriptor pool
        MemorySegment poolSize = VkDescriptorPoolSize.allocate(arena);
        VkDescriptorPoolSize.type(poolSize, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkDescriptorPoolSize.descriptorCount(poolSize, 1);
        
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
        
        // Update descriptor set with scene texture
        MemorySegment imageInfo = VkDescriptorImageInfo.allocate(arena);
        VkDescriptorImageInfo.sampler(imageInfo, sampler);
        VkDescriptorImageInfo.imageView(imageInfo, colorTargetView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment writeDescriptorSet = VkWriteDescriptorSet.allocate(arena);
        VkWriteDescriptorSet.sType(writeDescriptorSet, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(writeDescriptorSet, descriptorSet);
        VkWriteDescriptorSet.dstBinding(writeDescriptorSet, 0);
        VkWriteDescriptorSet.dstArrayElement(writeDescriptorSet, 0);
        VkWriteDescriptorSet.descriptorType(writeDescriptorSet, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(writeDescriptorSet, 1);
        VkWriteDescriptorSet.pImageInfo(writeDescriptorSet, imageInfo);
        
        VulkanExtensions.updateDescriptorSets(device, 1, writeDescriptorSet, 0, MemorySegment.NULL);
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
        // 1. Edge detection pass
        VkCommandBuffer.beginRenderPass(commandBuffer, edgeRenderPass.handle(), edgeFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, edgePipeline.handle());
        // Bind color and depth textures as input
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0); // Fullscreen triangle
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        
        // 2. Adaptive AA pass
        VkCommandBuffer.beginRenderPass(commandBuffer, aaRenderPass.handle(), finalFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
            .execute(frameArena);
        
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, aaPipeline.handle());
        // Bind descriptor set for texture sampling
        MemorySegment descriptorSets = frameArena.allocate(ValueLayout.ADDRESS);
        descriptorSets.set(ValueLayout.ADDRESS, 0, descriptorSet);
        VulkanExtensions.cmdBindDescriptorSets(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, 
            aaPipeline.layout(), 0, 1, descriptorSets, 0, MemorySegment.NULL);
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0); // Fullscreen triangle
        VulkanExtensions.cmdEndRenderPass(commandBuffer);
        
        frameIndex++;
    }
    
    public void cleanup() {
        sceneFramebuffer.close();
        edgeFramebuffer.close();
        edgePipeline.close();
        aaPipeline.close();
        sceneRenderPass.close();
        edgeRenderPass.close();
        aaRenderPass.close();
        colorTargetView.close();
        depthTargetView.close();
        edgeTargetView.close();
        previousFrameView.close();
        colorTarget.close();
        depthTarget.close();
        edgeTarget.close();
        previousFrame.close();
    }
}