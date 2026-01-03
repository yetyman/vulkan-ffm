package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Vulkan render pass (VkRenderPass) with automatic resource management.
 * A render pass describes the structure and format of attachments used during rendering.
 */
public class VkRenderPass implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    
    private VkRenderPass(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a render pass with a single color attachment.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @return a new VkRenderPass instance
     */
    public static VkRenderPass create(Arena arena, VkDevice device) {
        return builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
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
        Vulkan.destroyRenderPass(device.handle(), handle);
    }
    
    /**
     * Builder for complete render pass creation.
     */
    public static class Builder {
        private VkDevice device;
        private final List<AttachmentConfig> attachments = new ArrayList<>();
        private final List<SubpassConfig> subpasses = new ArrayList<>();
        private final List<DependencyConfig> dependencies = new ArrayList<>();
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Adds a color attachment */
        public Builder colorAttachment(int format, int loadOp, int storeOp) {
            attachments.add(new AttachmentConfig(attachments.size(), format, VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT, loadOp, storeOp, 
                VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE, 
                VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, 0));
            return this;
        }
        
        /** Adds a depth attachment */
        public Builder depthAttachment(int format, int loadOp, int storeOp) {
            // For depth+stencil formats, use the same ops for stencil
            int stencilLoadOp = (format == VkFormat.VK_FORMAT_D24_UNORM_S8_UINT || format == VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT) ? 
                loadOp : VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            int stencilStoreOp = (format == VkFormat.VK_FORMAT_D24_UNORM_S8_UINT || format == VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT) ? 
                storeOp : VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE;
            attachments.add(new AttachmentConfig(attachments.size(), format, VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT, loadOp, storeOp,
                stencilLoadOp, stencilStoreOp, VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED, VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, 0));
            return this;
        }
        
        /** Adds a custom attachment with full control */
        public Builder attachment(int format, int samples, int loadOp, int storeOp, 
                                  int stencilLoadOp, int stencilStoreOp, 
                                  int initialLayout, int finalLayout, int flags) {
            attachments.add(new AttachmentConfig(attachments.size(), format, samples, loadOp, storeOp,
                stencilLoadOp, stencilStoreOp, initialLayout, finalLayout, flags));
            return this;
        }
        
        /** Begins a new subpass */
        public SubpassBuilder beginSubpass() {
            return new SubpassBuilder(this);
        }
        
        /** Adds a complete subpass configuration */
        public Builder subpass(int pipelineBindPoint, int[] inputAttachments, int[] colorAttachments, 
                               int[] resolveAttachments, int depthStencilAttachment, int[] preserveAttachments) {
            subpasses.add(new SubpassConfig(pipelineBindPoint, inputAttachments, colorAttachments, 
                resolveAttachments, depthStencilAttachment, preserveAttachments, 0));
            return this;
        }
        
        /** Adds a subpass dependency */
        public Builder subpassDependency(int srcSubpass, int dstSubpass, 
                                         int srcStageMask, int dstStageMask,
                                         int srcAccessMask, int dstAccessMask) {
            dependencies.add(new DependencyConfig(srcSubpass, dstSubpass, srcStageMask, dstStageMask, srcAccessMask, dstAccessMask));
            return this;
        }
        
        /** Adds external to subpass dependency */
        public Builder externalDependency(int dstSubpass, int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
            return subpassDependency(~0, dstSubpass, srcStageMask, dstStageMask, srcAccessMask, dstAccessMask);
        }
        
        /** Adds subpass to external dependency */
        public Builder toExternalDependency(int srcSubpass, int srcStageMask, int dstStageMask, int srcAccessMask, int dstAccessMask) {
            return subpassDependency(srcSubpass, ~0, srcStageMask, dstStageMask, srcAccessMask, dstAccessMask);
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
            
            // If no subpasses defined, create a default one
            if (subpasses.isEmpty()) {
                List<Integer> colorAttachments = new ArrayList<>();
                int depthAttachment = ~0;
                
                for (AttachmentConfig att : attachments) {
                    if (att.finalLayout() == VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR ||
                        att.finalLayout() == VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
                        colorAttachments.add(att.index());
                    } else if (att.finalLayout() == VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                        depthAttachment = att.index();
                    }
                }
                
                subpasses.add(new SubpassConfig(VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    new int[0], colorAttachments.stream().mapToInt(i -> i).toArray(),
                    new int[0], depthAttachment, new int[0], 0));
            }
            
            // Allocate attachments
            MemorySegment attachmentDescs = arena.allocate(VkAttachmentDescription.layout(), attachments.size());
            
            for (int i = 0; i < attachments.size(); i++) {
                AttachmentConfig cfg = attachments.get(i);
                MemorySegment desc = attachmentDescs.asSlice(i * VkAttachmentDescription.layout().byteSize(), VkAttachmentDescription.layout());
                VkAttachmentDescription.flags(desc, cfg.flags());
                VkAttachmentDescription.format(desc, cfg.format());
                VkAttachmentDescription.samples(desc, cfg.samples());
                VkAttachmentDescription.loadOp(desc, cfg.loadOp());
                VkAttachmentDescription.storeOp(desc, cfg.storeOp());
                VkAttachmentDescription.stencilLoadOp(desc, cfg.stencilLoadOp());
                VkAttachmentDescription.stencilStoreOp(desc, cfg.stencilStoreOp());
                VkAttachmentDescription.initialLayout(desc, cfg.initialLayout());
                VkAttachmentDescription.finalLayout(desc, cfg.finalLayout());
            }
            
            // Create subpasses
            MemorySegment subpassDescs = arena.allocate(VkSubpassDescription.layout(), subpasses.size());
            
            for (int i = 0; i < subpasses.size(); i++) {
                SubpassConfig cfg = subpasses.get(i);
                MemorySegment subpass = subpassDescs.asSlice(i * VkSubpassDescription.layout().byteSize(), VkSubpassDescription.layout());
                
                VkSubpassDescription.flags(subpass, cfg.flags());
                VkSubpassDescription.pipelineBindPoint(subpass, cfg.pipelineBindPoint());
                
                // Input attachments
                if (cfg.inputAttachments().length > 0) {
                    MemorySegment inputRefs = arena.allocate(VkAttachmentReference.layout(), cfg.inputAttachments().length);
                    for (int j = 0; j < cfg.inputAttachments().length; j++) {
                        MemorySegment ref = inputRefs.asSlice(j * VkAttachmentReference.layout().byteSize(), VkAttachmentReference.layout());
                        VkAttachmentReference.attachment(ref, cfg.inputAttachments()[j]);
                        VkAttachmentReference.layout(ref, VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    }
                    VkSubpassDescription.inputAttachmentCount(subpass, cfg.inputAttachments().length);
                    VkSubpassDescription.pInputAttachments(subpass, inputRefs);
                } else {
                    VkSubpassDescription.inputAttachmentCount(subpass, 0);
                    VkSubpassDescription.pInputAttachments(subpass, MemorySegment.NULL);
                }
                
                // Color attachments
                if (cfg.colorAttachments().length > 0) {
                    MemorySegment colorRefs = arena.allocate(VkAttachmentReference.layout(), cfg.colorAttachments().length);
                    for (int j = 0; j < cfg.colorAttachments().length; j++) {
                        MemorySegment ref = colorRefs.asSlice(j * VkAttachmentReference.layout().byteSize(), VkAttachmentReference.layout());
                        VkAttachmentReference.attachment(ref, cfg.colorAttachments()[j]);
                        VkAttachmentReference.layout(ref, VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                    }
                    VkSubpassDescription.colorAttachmentCount(subpass, cfg.colorAttachments().length);
                    VkSubpassDescription.pColorAttachments(subpass, colorRefs);
                    
                    // Resolve attachments
                    if (cfg.resolveAttachments().length > 0) {
                        MemorySegment resolveRefs = arena.allocate(VkAttachmentReference.layout(), cfg.resolveAttachments().length);
                        for (int j = 0; j < cfg.resolveAttachments().length; j++) {
                            MemorySegment ref = resolveRefs.asSlice(j * VkAttachmentReference.layout().byteSize(), VkAttachmentReference.layout());
                            VkAttachmentReference.attachment(ref, cfg.resolveAttachments()[j]);
                            VkAttachmentReference.layout(ref, VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                        }
                        VkSubpassDescription.pResolveAttachments(subpass, resolveRefs);
                    } else {
                        VkSubpassDescription.pResolveAttachments(subpass, MemorySegment.NULL);
                    }
                } else {
                    VkSubpassDescription.colorAttachmentCount(subpass, 0);
                    VkSubpassDescription.pColorAttachments(subpass, MemorySegment.NULL);
                    VkSubpassDescription.pResolveAttachments(subpass, MemorySegment.NULL);
                }
                
                // Depth/stencil attachment
                if (cfg.depthStencilAttachment() != ~0) {
                    MemorySegment depthRef = VkAttachmentReference.allocate(arena);
                    VkAttachmentReference.attachment(depthRef, cfg.depthStencilAttachment());
                    VkAttachmentReference.layout(depthRef, VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                    VkSubpassDescription.pDepthStencilAttachment(subpass, depthRef);
                } else {
                    VkSubpassDescription.pDepthStencilAttachment(subpass, MemorySegment.NULL);
                }
                
                // Preserve attachments
                if (cfg.preserveAttachments().length > 0) {
                    MemorySegment preserveArray = arena.allocate(ValueLayout.JAVA_INT, cfg.preserveAttachments().length);
                    for (int j = 0; j < cfg.preserveAttachments().length; j++) {
                        preserveArray.setAtIndex(ValueLayout.JAVA_INT, j, cfg.preserveAttachments()[j]);
                    }
                    VkSubpassDescription.preserveAttachmentCount(subpass, cfg.preserveAttachments().length);
                    VkSubpassDescription.pPreserveAttachments(subpass, preserveArray);
                } else {
                    VkSubpassDescription.preserveAttachmentCount(subpass, 0);
                    VkSubpassDescription.pPreserveAttachments(subpass, MemorySegment.NULL);
                }
            }
            
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
            VkRenderPassCreateInfo.subpassCount(renderPassInfo, subpasses.size());
            VkRenderPassCreateInfo.pSubpasses(renderPassInfo, subpassDescs);
            VkRenderPassCreateInfo.dependencyCount(renderPassInfo, dependencies.size());
            VkRenderPassCreateInfo.pDependencies(renderPassInfo, dependencyDescs);
            
            MemorySegment renderPassPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createRenderPass(device.handle(), renderPassInfo, renderPassPtr).check();
            return new VkRenderPass(renderPassPtr.get(ValueLayout.ADDRESS, 0), device);
        }
        
        private record AttachmentConfig(int index, int format, int samples, int loadOp, int storeOp,
                                        int stencilLoadOp, int stencilStoreOp, 
                                        int initialLayout, int finalLayout, int flags) {}
        
        private record SubpassConfig(int pipelineBindPoint, int[] inputAttachments, int[] colorAttachments,
                                     int[] resolveAttachments, int depthStencilAttachment, int[] preserveAttachments, int flags) {}
        
        private record DependencyConfig(int srcSubpass, int dstSubpass, 
                                        int srcStageMask, int dstStageMask,
                                        int srcAccessMask, int dstAccessMask) {}
        
        /**
         * Builder for individual subpass configuration.
         */
        public static class SubpassBuilder {
            private final Builder parent;
            private int pipelineBindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS;
            private final List<Integer> inputAttachments = new ArrayList<>();
            private final List<Integer> colorAttachments = new ArrayList<>();
            private final List<Integer> resolveAttachments = new ArrayList<>();
            private int depthStencilAttachment = ~0;
            private final List<Integer> preserveAttachments = new ArrayList<>();
            private int flags = 0;
            
            private SubpassBuilder(Builder parent) {
                this.parent = parent;
            }
            
            public SubpassBuilder pipelineBindPoint(int bindPoint) {
                this.pipelineBindPoint = bindPoint;
                return this;
            }
            
            public SubpassBuilder inputAttachment(int attachment) {
                inputAttachments.add(attachment);
                return this;
            }
            
            public SubpassBuilder colorAttachment(int attachment) {
                colorAttachments.add(attachment);
                return this;
            }
            
            public SubpassBuilder resolveAttachment(int attachment) {
                resolveAttachments.add(attachment);
                return this;
            }
            
            public SubpassBuilder depthStencilAttachment(int attachment) {
                this.depthStencilAttachment = attachment;
                return this;
            }
            
            public SubpassBuilder preserveAttachment(int attachment) {
                preserveAttachments.add(attachment);
                return this;
            }
            
            public SubpassBuilder flags(int flags) {
                this.flags = flags;
                return this;
            }
            
            public Builder endSubpass() {
                parent.subpasses.add(new SubpassConfig(pipelineBindPoint,
                    inputAttachments.stream().mapToInt(i -> i).toArray(),
                    colorAttachments.stream().mapToInt(i -> i).toArray(),
                    resolveAttachments.stream().mapToInt(i -> i).toArray(),
                    depthStencilAttachment,
                    preserveAttachments.stream().mapToInt(i -> i).toArray(),
                    flags));
                return parent;
            }
        }
    }
}