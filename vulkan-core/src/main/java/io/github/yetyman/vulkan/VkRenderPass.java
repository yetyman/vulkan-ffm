package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;
import static io.github.yetyman.vulkan.VkConstants.*;
import static io.github.yetyman.vulkan.generated.vulkanffm_4.*;

/**
 * Wrapper for Vulkan render pass (VkRenderPass) with automatic resource management.
 * A render pass describes the structure and format of attachments used during rendering.
 */
public class VkRenderPass implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkRenderPass(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a render pass with a single color attachment.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @return a new VkRenderPass instance
     */
    public static VkRenderPass create(Arena arena, MemorySegment device) {
        return builder()
            .device(device)
            .colorAttachment(VK_FORMAT_B8G8R8A8_SRGB, VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE)
            .subpassDependency(VK_SUBPASS_EXTERNAL, 0, 
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .build(arena);
    }
    
    /** @return a new builder for configuring render pass creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkRenderPass handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyRenderPass(device, handle);
    }
    
    /**
     * Builder for flexible render pass creation.
     */
    public static class Builder {
        private MemorySegment device;
        private final List<AttachmentConfig> attachments = new ArrayList<>();
        private final List<DependencyConfig> dependencies = new ArrayList<>();
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Adds a color attachment */
        public Builder colorAttachment(int format, int loadOp, int storeOp) {
            attachments.add(new AttachmentConfig(format, VK_SAMPLE_COUNT_1_BIT, loadOp, storeOp, 
                0, 0, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, true));
            return this;
        }
        
        /** Adds a depth attachment */
        public Builder depthAttachment(int format, int loadOp, int storeOp) {
            attachments.add(new AttachmentConfig(format, VK_SAMPLE_COUNT_1_BIT, loadOp, storeOp,
                loadOp, storeOp, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL(), false));
            return this;
        }
        
        /** Adds a custom attachment with full control */
        public Builder attachment(int format, int samples, int loadOp, int storeOp, 
                                  int stencilLoadOp, int stencilStoreOp, 
                                  int initialLayout, int finalLayout, boolean isColor) {
            attachments.add(new AttachmentConfig(format, samples, loadOp, storeOp,
                stencilLoadOp, stencilStoreOp, initialLayout, finalLayout, isColor));
            return this;
        }
        
        /** Adds a subpass dependency */
        public Builder subpassDependency(int srcSubpass, int dstSubpass, 
                                         int srcStageMask, int dstStageMask,
                                         int srcAccessMask, int dstAccessMask) {
            dependencies.add(new DependencyConfig(srcSubpass, dstSubpass, srcStageMask, dstStageMask, srcAccessMask, dstAccessMask));
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the render pass */
        public VkRenderPass build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (attachments.isEmpty()) throw new IllegalStateException("no attachments defined");
            
            // Allocate attachments
            MemorySegment attachmentDescs = arena.allocate(VkAttachmentDescription.layout(), attachments.size());
            List<Integer> colorIndices = new ArrayList<>();
            Integer depthIndex = null;
            
            for (int i = 0; i < attachments.size(); i++) {
                AttachmentConfig cfg = attachments.get(i);
                MemorySegment desc = attachmentDescs.asSlice(i * VkAttachmentDescription.layout().byteSize(), VkAttachmentDescription.layout());
                VkAttachmentDescription.flags(desc, 0);
                VkAttachmentDescription.format(desc, cfg.format);
                VkAttachmentDescription.samples(desc, cfg.samples);
                VkAttachmentDescription.loadOp(desc, cfg.loadOp);
                VkAttachmentDescription.storeOp(desc, cfg.storeOp);
                VkAttachmentDescription.stencilLoadOp(desc, cfg.stencilLoadOp);
                VkAttachmentDescription.stencilStoreOp(desc, cfg.stencilStoreOp);
                VkAttachmentDescription.initialLayout(desc, cfg.initialLayout);
                VkAttachmentDescription.finalLayout(desc, cfg.finalLayout);
                
                if (cfg.isColor) colorIndices.add(i);
                else depthIndex = i;
            }
            
            // Create attachment references
            MemorySegment colorRefs = colorIndices.isEmpty() ? MemorySegment.NULL : 
                arena.allocate(VkAttachmentReference.layout(), colorIndices.size());
            for (int i = 0; i < colorIndices.size(); i++) {
                MemorySegment ref = colorRefs.asSlice(i * VkAttachmentReference.layout().byteSize(), VkAttachmentReference.layout());
                VkAttachmentReference.attachment(ref, colorIndices.get(i));
                VkAttachmentReference.layout(ref, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            
            MemorySegment depthRef = MemorySegment.NULL;
            if (depthIndex != null) {
                depthRef = VkAttachmentReference.allocate(arena);
                VkAttachmentReference.attachment(depthRef, depthIndex);
                VkAttachmentReference.layout(depthRef, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL());
            }
            
            // Create subpass
            MemorySegment subpass = VkSubpassDescription.allocate(arena);
            VkSubpassDescription.flags(subpass, 0);
            VkSubpassDescription.pipelineBindPoint(subpass, VK_PIPELINE_BIND_POINT_GRAPHICS);
            VkSubpassDescription.inputAttachmentCount(subpass, 0);
            VkSubpassDescription.pInputAttachments(subpass, MemorySegment.NULL);
            VkSubpassDescription.colorAttachmentCount(subpass, colorIndices.size());
            VkSubpassDescription.pColorAttachments(subpass, colorRefs);
            VkSubpassDescription.pResolveAttachments(subpass, MemorySegment.NULL);
            VkSubpassDescription.pDepthStencilAttachment(subpass, depthRef);
            VkSubpassDescription.preserveAttachmentCount(subpass, 0);
            VkSubpassDescription.pPreserveAttachments(subpass, MemorySegment.NULL);
            
            // Create dependencies
            MemorySegment dependencyDescs = dependencies.isEmpty() ? MemorySegment.NULL :
                arena.allocate(VkSubpassDependency.layout(), dependencies.size());
            for (int i = 0; i < dependencies.size(); i++) {
                DependencyConfig cfg = dependencies.get(i);
                MemorySegment dep = dependencyDescs.asSlice(i * VkSubpassDependency.layout().byteSize(), VkSubpassDependency.layout());
                VkSubpassDependency.srcSubpass(dep, cfg.srcSubpass);
                VkSubpassDependency.dstSubpass(dep, cfg.dstSubpass);
                VkSubpassDependency.srcStageMask(dep, cfg.srcStageMask);
                VkSubpassDependency.dstStageMask(dep, cfg.dstStageMask);
                VkSubpassDependency.srcAccessMask(dep, cfg.srcAccessMask);
                VkSubpassDependency.dstAccessMask(dep, cfg.dstAccessMask);
                VkSubpassDependency.dependencyFlags(dep, 0);
            }
            
            // Create render pass
            MemorySegment renderPassInfo = VkRenderPassCreateInfo.allocate(arena);
            VkRenderPassCreateInfo.sType(renderPassInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            VkRenderPassCreateInfo.pNext(renderPassInfo, MemorySegment.NULL);
            VkRenderPassCreateInfo.flags(renderPassInfo, flags);
            VkRenderPassCreateInfo.attachmentCount(renderPassInfo, attachments.size());
            VkRenderPassCreateInfo.pAttachments(renderPassInfo, attachmentDescs);
            VkRenderPassCreateInfo.subpassCount(renderPassInfo, 1);
            VkRenderPassCreateInfo.pSubpasses(renderPassInfo, subpass);
            VkRenderPassCreateInfo.dependencyCount(renderPassInfo, dependencies.size());
            VkRenderPassCreateInfo.pDependencies(renderPassInfo, dependencyDescs);
            
            MemorySegment renderPassPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createRenderPass(device, renderPassInfo, renderPassPtr).check();
            return new VkRenderPass(renderPassPtr.get(ValueLayout.ADDRESS, 0), device);
        }
        
        private record AttachmentConfig(int format, int samples, int loadOp, int storeOp,
                                        int stencilLoadOp, int stencilStoreOp, 
                                        int initialLayout, int finalLayout, boolean isColor) {}
        
        private record DependencyConfig(int srcSubpass, int dstSubpass, 
                                        int srcStageMask, int dstStageMask,
                                        int srcAccessMask, int dstAccessMask) {}
    }
}