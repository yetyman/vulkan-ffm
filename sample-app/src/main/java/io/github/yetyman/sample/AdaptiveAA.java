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
            .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT, 0, 8)
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
        // Create 4 bindings for all textures
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
        // Create descriptor pool
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
        
        // Update descriptor set with all textures
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
        
        // Binding 1: previousFrame
        MemorySegment imageInfo1 = imageInfos.asSlice(VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo1, sampler);
        VkDescriptorImageInfo.imageView(imageInfo1, previousFrameView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo1, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write1 = writeDescriptorSets.asSlice(VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write1, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write1, descriptorSet);
        VkWriteDescriptorSet.dstBinding(write1, 1);
        VkWriteDescriptorSet.dstArrayElement(write1, 0);
        VkWriteDescriptorSet.descriptorType(write1, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write1, 1);
        VkWriteDescriptorSet.pImageInfo(write1, imageInfo1);
        
        // Binding 2: edgeTexture
        MemorySegment imageInfo2 = imageInfos.asSlice(2 * VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo2, sampler);
        VkDescriptorImageInfo.imageView(imageInfo2, edgeTargetView.handle());
        VkDescriptorImageInfo.imageLayout(imageInfo2, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        MemorySegment write2 = writeDescriptorSets.asSlice(2 * VkWriteDescriptorSet.layout().byteSize(), VkWriteDescriptorSet.layout());
        VkWriteDescriptorSet.sType(write2, VkStructureType.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        VkWriteDescriptorSet.dstSet(write2, descriptorSet);
        VkWriteDescriptorSet.dstBinding(write2, 2);
        VkWriteDescriptorSet.dstArrayElement(write2, 0);
        VkWriteDescriptorSet.descriptorType(write2, VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        VkWriteDescriptorSet.descriptorCount(write2, 1);
        VkWriteDescriptorSet.pImageInfo(write2, imageInfo2);
        
        // Binding 3: depthTexture
        MemorySegment imageInfo3 = imageInfos.asSlice(3 * VkDescriptorImageInfo.layout().byteSize(), VkDescriptorImageInfo.layout());
        VkDescriptorImageInfo.sampler(imageInfo3, sampler);
        VkDescriptorImageInfo.imageView(imageInfo3, depthTargetView.handle());
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
        
        // 2. Adaptive AA pass
        VkCommandBuffer.beginRenderPass(commandBuffer, aaRenderPass.handle(), finalFramebuffer.handle())
            .renderArea(0, 0, width, height)
            .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
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