package io.github.yetyman.vulkan.sample.complex.postprocessing;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.highlevel.CompiledShader;
import io.github.yetyman.vulkan.highlevel.ShaderLoader;
import io.github.yetyman.vulkan.highlevel.VkTexture;
import io.github.yetyman.vulkan.highlevel.PoolAllocator;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;

public class AdaptiveAA {
    public enum Mode { NONE, MSAA, POST_PROCESS }
    
    private final Arena arena;
    private final VkDevice device;
    private final PoolAllocator allocator;
    private final int width, height;
    private final Mode mode;
    private final int samples;
    
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
    
    private AdaptiveAA(Arena arena, VkDevice device, int width, int height, Mode mode, int samples) {
        this.arena = arena;
        this.device = device;
        this.allocator = PoolAllocator.builder()
            .device(device)
            .build(arena);
        this.width = width;
        this.height = height;
        this.mode = mode;
        this.samples = samples;
        
        createRenderTargets();
        createRenderPasses();
        createPipelines();
        createFramebuffers();
        createDescriptorSets();
    }
    
    private void createRenderTargets() {
        boolean useMSAA = (mode == Mode.MSAA);
        
        // Color target
        colorTarget = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value())
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value() | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value())
            .samples(useMSAA ? samples : 1)
            .linear()
            .clampToEdge()
            .build(arena);
        
        // Depth target
        int depthFormat = findSupportedDepthFormat();
        depthTarget = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(depthFormat)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT.value() | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value())
            .samples(useMSAA ? samples : 1)
            .nearest()
            .clampToEdge()
            .build(arena);
        
        // Resolved/edge target
        edgeTarget = VkTexture.builder()
            .device(device)
            .allocator(allocator)
            .size(width, height)
            .format(useMSAA ? VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value() : VkFormat.VK_FORMAT_R8_UNORM.value())
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value() | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value())
            .linear()
            .clampToEdge()
            .build(arena);
        
        // Previous frame for post-process AA
        if (!useMSAA) {
            previousFrame = VkTexture.builder()
                .device(device)
                .allocator(allocator)
                .size(width, height)
                .format(VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value())
                .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value() | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value())
                .linear()
                .clampToEdge()
                .build(arena);
        }
    }
    
    private void createRenderPasses() {
        boolean useMSAA = (mode == Mode.MSAA);
        int depthFormat = findSupportedDepthFormat();
        int sampleCount = samples == 2 ? VkSampleCountFlagBits.VK_SAMPLE_COUNT_2_BIT.value() :
                          samples == 4 ? VkSampleCountFlagBits.VK_SAMPLE_COUNT_4_BIT.value() :
                          samples == 8 ? VkSampleCountFlagBits.VK_SAMPLE_COUNT_8_BIT.value() :
                          samples == 16 ? VkSampleCountFlagBits.VK_SAMPLE_COUNT_16_BIT.value() :
                          samples == 32 ? VkSampleCountFlagBits.VK_SAMPLE_COUNT_32_BIT.value() :
                          samples == 64 ? VkSampleCountFlagBits.VK_SAMPLE_COUNT_64_BIT.value() :
                          VkSampleCountFlagBits.VK_SAMPLE_COUNT_4_BIT.value();
        
        if (useMSAA) {
            // MSAA render pass with resolve
            sceneRenderPass = VkRenderPass.builder()
                .device(device)
                .attachment(VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value(), sampleCount,
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(), 0)
            .attachment(VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value(), VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), 0)
            .attachment(depthFormat, sampleCount,
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL.value(), 0)
                .beginSubpass()
                    .colorAttachment(0)
                    .resolveAttachment(1)
                    .depthStencilAttachment(2)
                .endSubpass()
                .build(arena);
        } else {
            // Post-process AA render pass (single sample) - transition to shader read layout
            sceneRenderPass = VkRenderPass.builder()
                .device(device)
                .attachment(VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT.value(), VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), 0)
                .attachment(depthFormat, VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), 0)
                .beginSubpass()
                    .colorAttachment(0)
                    .depthStencilAttachment(1)
                .endSubpass()
                .build(arena);
            
            // Edge detection render pass - output to shader read layout
            edgeRenderPass = VkRenderPass.builder()
                .device(device)
                .attachment(VkFormat.VK_FORMAT_R8_UNORM.value(), VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value(),
                    VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), 0)
                .beginSubpass()
                    .colorAttachment(0)
                .endSubpass()
                .build(arena);
        }
        
        // Final pass to swapchain - must match swapchain framebuffer (color + depth)
        aaRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value(), VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value())
            .depthAttachment(depthFormat, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value())
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value() | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value(), 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value() | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value(),
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value() | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value())
            .build(arena);
    }
    
    private void createPipelines() {
        boolean useMSAA = (mode == Mode.MSAA);
        
        // Create descriptor set layout
        descriptorSetLayout = createDescriptorSetLayout();
        
        try {
            if (useMSAA) {
                // Simple blit pipeline for MSAA final output
                byte[] vertCode = ShaderLoader.compileShader("/shaders/fullscreen.vert").getSpirV();
                byte[] fragCode = ShaderLoader.compileShader("/shaders/blit.frag").getSpirV();
                
                aaPipeline = VkPipeline.builder()
                    .device(device)
                    .renderPass(aaRenderPass.handle())
                    .viewport(0, 0, width, height)
                    .vertexShader(vertCode)
                    .fragmentShader(fragCode)
                    .triangleTopology()
                    .descriptorSetLayouts(descriptorSetLayout.handle())
                    .depthTest(true)
                    .depthWrite(false)
                    .depthCompareOp(VkCompareOp.VK_COMPARE_OP_ALWAYS.value())
                    .build(arena);
            } else {
                // Post-process AA pipelines
                byte[] edgeVertCode = ShaderLoader.compileShader("/shaders/fullscreen.vert").getSpirV();
                byte[] edgeFragCode = ShaderLoader.compileShader("/shaders/edge_detect.frag").getSpirV();
                
                edgePipeline = VkPipeline.builder()
                    .device(device)
                    .renderPass(edgeRenderPass.handle())
                    .viewport(0, 0, width, height)
                    .vertexShader(edgeVertCode)
                    .fragmentShader(edgeFragCode)
                    .triangleTopology()
                    .descriptorSetLayouts(descriptorSetLayout.handle())
                    .build(arena);
                
                byte[] aaVertCode = ShaderLoader.compileShader("/shaders/fullscreen.vert").getSpirV();
                byte[] aaFragCode = ShaderLoader.compileShader("/shaders/adaptive_aa.frag").getSpirV();
                
                aaPipeline = VkPipeline.builder()
                    .device(device)
                    .renderPass(aaRenderPass.handle())
                    .viewport(0, 0, width, height)
                    .vertexShader(aaVertCode)
                    .fragmentShader(aaFragCode)
                    .triangleTopology()
                    .descriptorSetLayouts(descriptorSetLayout.handle())
                    .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0, 8)
                    .depthTest(true)
                    .depthWrite(false)
                    .depthCompareOp(VkCompareOp.VK_COMPARE_OP_ALWAYS.value())
                    .build(arena);
            }
        } catch (Exception e) {
            Logger.error("Failed to create AA pipeline: " + e.getMessage());
            throw new RuntimeException("AdaptiveAA pipeline creation failed", e);
        }
    }
    

    private VkDescriptorSetLayout createDescriptorSetLayout() {
        boolean useMSAA = (mode == Mode.MSAA);
        
        if (useMSAA) {
            // MSAA mode - only need 1 sampler for blit
            return VkDescriptorSetLayout.builder()
                .device(device)
                .combinedImageSampler(0, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
                .build(arena);
        } else {
            // Post-process mode - need 4 samplers
            return VkDescriptorSetLayout.builder()
                .device(device)
                .combinedImageSampler(0, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
                .combinedImageSampler(1, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
                .combinedImageSampler(2, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
                .combinedImageSampler(3, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
                .build(arena);
        }
    }
    
    private void createDescriptorSets() {
        boolean useMSAA = (mode == Mode.MSAA);
        
        descriptorPool = VkDescriptorPool.builder()
            .device(device)
            .maxSets(1)
            .combinedImageSamplers(useMSAA ? 1 : 4)
            .build(arena);
        
        descriptorSet = descriptorPool.allocateDescriptorSet(descriptorSetLayout);
        
        if (useMSAA) {
            // MSAA mode - only need resolved color target
            descriptorSet.updateImageSampler(0, edgeTarget.sampler(), edgeTarget.imageView(), 
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), arena);
        } else {
            // Post-process mode - need all 4 textures
            descriptorSet.updateImageSampler(0, colorTarget.sampler(), colorTarget.imageView(), 
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), arena);
            descriptorSet.updateImageSampler(1, depthTarget.sampler(), depthTarget.imageView(), 
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), arena);
            descriptorSet.updateImageSampler(2, previousFrame.sampler(), previousFrame.imageView(), 
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), arena);
            descriptorSet.updateImageSampler(3, edgeTarget.sampler(), edgeTarget.imageView(), 
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), arena);
        }
    }
    
    private void createFramebuffers() {
        boolean useMSAA = (mode == Mode.MSAA);
        
        if (useMSAA) {
            // MSAA framebuffer with resolve
            sceneFramebuffer = VkFramebuffer.builder()
                .device(device)
                .renderPass(sceneRenderPass.handle())
                .attachment(new VkFramebufferAttachment(colorTarget, VkFramebufferAttachment.AttachmentType.COLOR, 0))
                .attachment(new VkFramebufferAttachment(edgeTarget, VkFramebufferAttachment.AttachmentType.COLOR, 1))
                .attachment(new VkFramebufferAttachment(depthTarget, VkFramebufferAttachment.AttachmentType.DEPTH, 2))
                .dimensions(width, height)
                .build(arena);
        } else {
            // Post-process AA framebuffer
            sceneFramebuffer = VkFramebuffer.builder()
                .device(device)
                .renderPass(sceneRenderPass.handle())
                .attachment(new VkFramebufferAttachment(colorTarget, VkFramebufferAttachment.AttachmentType.COLOR, 0))
                .attachment(new VkFramebufferAttachment(depthTarget, VkFramebufferAttachment.AttachmentType.DEPTH, 1))
                .dimensions(width, height)
                .build(arena);
            
            // Edge detection framebuffer
            edgeFramebuffer = VkFramebuffer.builder()
                .device(device)
                .renderPass(edgeRenderPass.handle())
                .attachment(new VkFramebufferAttachment(edgeTarget, VkFramebufferAttachment.AttachmentType.COLOR, 0))
                .dimensions(width, height)
                .build(arena);
        }
    }
    
    public VkRenderPass getSceneRenderPass() { return sceneRenderPass; }
    public VkFramebuffer getSceneFramebuffer() { return sceneFramebuffer; }
    
    public void performAA(VkCommandBuffer commandBuffer, VkFramebuffer finalFramebuffer, Arena frameArena) {
        performAA(commandBuffer, finalFramebuffer, frameArena, 0.0f, 0.0f, 0.0f, 1.0f);
    }
    
    public void performAA(VkCommandBuffer commandBuffer, VkFramebuffer finalFramebuffer, Arena frameArena, float clearR, float clearG, float clearB, float clearA) {
        boolean useMSAA = (mode == Mode.MSAA);
        
        if (useMSAA) {
            // MSAA mode - blit resolved image (already in correct layout from render pass)
            VkCommandBuffer.beginRenderPass(commandBuffer, aaRenderPass.handle(), finalFramebuffer.handle())
                .renderArea(0, 0, width, height)
                .clearColor(clearR, clearG, clearB, clearA)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            Vulkan.cmdBindPipeline(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), aaPipeline.handle());
            descriptorSet.bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), aaPipeline.layout(), 0, frameArena);
            Vulkan.cmdDraw(commandBuffer.handle(), 3, 1, 0, 0);
            Vulkan.cmdEndRenderPass(commandBuffer.handle());
        } else {
            // Post-process AA mode - edge detection + adaptive AA
            // Transition previous frame for sampling (only if first frame)
            if (frameIndex == 0) {
                transitionImageLayout(commandBuffer, previousFrame.image(), VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(), VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), false);
            }
            
            // Edge detection pass
            VkCommandBuffer.beginRenderPass(commandBuffer, edgeRenderPass.handle(), edgeFramebuffer.handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .execute(frameArena);
            
            Vulkan.cmdBindPipeline(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), edgePipeline.handle());
            descriptorSet.bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), edgePipeline.layout(), 0, frameArena);
            Vulkan.cmdDraw(commandBuffer.handle(), 3, 1, 0, 0);
            Vulkan.cmdEndRenderPass(commandBuffer.handle());
            
            // Adaptive AA pass to swapchain
            VkCommandBuffer.beginRenderPass(commandBuffer, aaRenderPass.handle(), finalFramebuffer.handle())
                .renderArea(0, 0, width, height)
                .clearColor(clearR, clearG, clearB, clearA)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            Vulkan.cmdBindPipeline(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), aaPipeline.handle());
            descriptorSet.bind(commandBuffer.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), aaPipeline.layout(), 0, frameArena);
            
            // Push constants for frame info
            VkPushConstants.builder(frameArena)
                .floatValue((float)frameIndex)
                .floatValue(16.67f)
                .build()
                .push(commandBuffer.handle(), aaPipeline.layout(), VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0);
            
            Vulkan.cmdDraw(commandBuffer.handle(), 3, 1, 0, 0);
            Vulkan.cmdEndRenderPass(commandBuffer.handle());
            
            frameIndex++;
        }
    }
    
    public void cleanup() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.handle().equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device.handle()).check();
            Logger.debug("Device idle - starting AdaptiveAA cleanup");
        }
        
        // Clean up descriptor resources
        if (descriptorPool != null) descriptorPool.close();
        if (descriptorSetLayout != null) descriptorSetLayout.close();
        
        // Clean up other resources
        if (sceneFramebuffer != null) sceneFramebuffer.close();
        if (edgeFramebuffer != null) edgeFramebuffer.close();
        if (edgePipeline != null) edgePipeline.close();
        if (aaPipeline != null) aaPipeline.close();
        if (sceneRenderPass != null) sceneRenderPass.close();
        if (edgeRenderPass != null) edgeRenderPass.close();
        if (aaRenderPass != null) aaRenderPass.close();
        
        // Clean up textures
        if (colorTarget != null) colorTarget.close();
        if (depthTarget != null) depthTarget.close();
        if (edgeTarget != null) edgeTarget.close();
        if (previousFrame != null) previousFrame.close();
        
        if (allocator != null) allocator.close();
        
        Logger.debug("AdaptiveAA cleanup complete");
    }
    
    // Public API for renderer integration
    public int getSampleCount() { return mode == Mode.MSAA ? samples : 1; }
    public Mode getMode() { return mode; }
    public int getClearColorCount() { return mode == Mode.MSAA ? 2 : 1; }
    
    // Builder for clean construction
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private Mode mode = Mode.MSAA;
        private int samples = 4;
        private int width, height;
        private Arena arena;
        private VkDevice device;

        public Builder mode(Mode mode) { this.mode = mode; return this; }
        public Builder samples(int samples) { this.samples = samples; return this; }
        public Builder dimensions(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder arena(Arena arena) { this.arena = arena; return this; }
        public Builder device(VkDevice device) { this.device = device; return this; }

        public AdaptiveAA build() {
            return new AdaptiveAA(arena, device, width, height, mode, samples);
        }
    }
    
    private int findSupportedDepthFormat() {
        // Try common depth formats in order of preference, requiring both attachment and sampling support
        int[] candidates = {
            VkFormat.VK_FORMAT_D32_SFLOAT.value(),
            VkFormat.VK_FORMAT_D16_UNORM.value(),
            VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT.value(),
            VkFormat.VK_FORMAT_D24_UNORM_S8_UINT.value()
        };
        
        try (Arena tempArena = Arena.ofConfined()) {
            for (int format : candidates) {
                VkPhysicalDevice.VkFormatPropertiesWrapper formatProps = device.physicalDevice().getFormatProperties(format, tempArena);
                int requiredFeatures = VkFormatFeatureFlagBits.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT.value() | 
                                      VkFormatFeatureFlagBits.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT.value();
                if ((formatProps.optimalTilingFeatures() & requiredFeatures) == requiredFeatures) {
                    return format;
                }
            }
        }
        
        throw new RuntimeException("Failed to find supported depth format with sampling support");
    }
    
    private void transitionImageLayout(VkCommandBuffer commandBuffer, MemorySegment image, int oldLayout, int newLayout, boolean isDepth) {
        int srcAccessMask = oldLayout == VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value() ? 0 :
            (isDepth ? VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value() : 
                      VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value());
        
        int srcStage = oldLayout == VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value() ? 
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value() :
            (isDepth ? VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value() : 
                      VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value());
        
        VkImageBarrier barrier = VkImageBarrier.builder()
            .image(image)
            .transition(oldLayout, newLayout)
            .srcAccess(srcAccessMask)
            .dstAccess(VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value())
            .aspectMask(isDepth ? VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value() : 
                                 VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
            .build(arena);
        
        barrier.execute(commandBuffer.handle(), srcStage, VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value());
    }
}