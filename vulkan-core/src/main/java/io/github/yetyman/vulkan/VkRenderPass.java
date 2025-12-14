package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import static io.github.yetyman.vulkan.VkConstants.*;

public class VkRenderPass implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkRenderPass(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    public static VkRenderPass create(Arena arena, MemorySegment device) {
        MemorySegment attachmentDesc = VkAttachmentDescription.allocate(arena);
        VkAttachmentDescription.flags(attachmentDesc, 0);
        VkAttachmentDescription.format(attachmentDesc, VK_FORMAT_B8G8R8A8_SRGB);
        VkAttachmentDescription.samples(attachmentDesc, VK_SAMPLE_COUNT_1_BIT);
        VkAttachmentDescription.loadOp(attachmentDesc, VK_ATTACHMENT_LOAD_OP_CLEAR);
        VkAttachmentDescription.storeOp(attachmentDesc, VK_ATTACHMENT_STORE_OP_STORE);
        VkAttachmentDescription.stencilLoadOp(attachmentDesc, 0);
        VkAttachmentDescription.stencilStoreOp(attachmentDesc, 0);
        VkAttachmentDescription.initialLayout(attachmentDesc, VK_IMAGE_LAYOUT_UNDEFINED);
        VkAttachmentDescription.finalLayout(attachmentDesc, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        
        MemorySegment colorAttachmentRef = VkAttachmentReference.allocate(arena);
        VkAttachmentReference.attachment(colorAttachmentRef, 0);
        VkAttachmentReference.layout(colorAttachmentRef, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        
        MemorySegment subpass = VkSubpassDescription.allocate(arena);
        VkSubpassDescription.flags(subpass, 0);
        VkSubpassDescription.pipelineBindPoint(subpass, VK_PIPELINE_BIND_POINT_GRAPHICS);
        VkSubpassDescription.inputAttachmentCount(subpass, 0);
        VkSubpassDescription.pInputAttachments(subpass, MemorySegment.NULL);
        VkSubpassDescription.colorAttachmentCount(subpass, 1);
        VkSubpassDescription.pColorAttachments(subpass, colorAttachmentRef);
        VkSubpassDescription.pResolveAttachments(subpass, MemorySegment.NULL);
        VkSubpassDescription.pDepthStencilAttachment(subpass, MemorySegment.NULL);
        VkSubpassDescription.preserveAttachmentCount(subpass, 0);
        VkSubpassDescription.pPreserveAttachments(subpass, MemorySegment.NULL);
        
        MemorySegment dependency = VkSubpassDependency.allocate(arena);
        VkSubpassDependency.srcSubpass(dependency, VK_SUBPASS_EXTERNAL);
        VkSubpassDependency.dstSubpass(dependency, 0);
        VkSubpassDependency.srcStageMask(dependency, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        VkSubpassDependency.dstStageMask(dependency, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        VkSubpassDependency.srcAccessMask(dependency, 0);
        VkSubpassDependency.dstAccessMask(dependency, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        VkSubpassDependency.dependencyFlags(dependency, 0);
        
        MemorySegment renderPassInfo = VkRenderPassCreateInfo.allocate(arena);
        VkRenderPassCreateInfo.sType(renderPassInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
        VkRenderPassCreateInfo.pNext(renderPassInfo, MemorySegment.NULL);
        VkRenderPassCreateInfo.flags(renderPassInfo, 0);
        VkRenderPassCreateInfo.attachmentCount(renderPassInfo, 1);
        VkRenderPassCreateInfo.pAttachments(renderPassInfo, attachmentDesc);
        VkRenderPassCreateInfo.subpassCount(renderPassInfo, 1);
        VkRenderPassCreateInfo.pSubpasses(renderPassInfo, subpass);
        VkRenderPassCreateInfo.dependencyCount(renderPassInfo, 1);
        VkRenderPassCreateInfo.pDependencies(renderPassInfo, dependency);
        
        MemorySegment renderPassPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createRenderPass(device, renderPassInfo, renderPassPtr).check();
        return new VkRenderPass(renderPassPtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyRenderPass(device, handle);
    }
}