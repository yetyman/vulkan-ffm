package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import static io.github.yetyman.vulkan.VkConstants.*;

/**
 * Wrapper for Vulkan graphics pipeline (VkPipeline) with automatic resource management.
 * A graphics pipeline defines all stages of rendering from vertex input to fragment output.
 */
public class VkPipeline implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment layout;
    private final MemorySegment device;
    
    private VkPipeline(MemorySegment handle, MemorySegment layout, MemorySegment device) {
        this.handle = handle;
        this.layout = layout;
        this.device = device;
    }
    
    /**
     * Creates a graphics pipeline configured for rendering a simple triangle.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @param renderPass the VkRenderPass handle
     * @param width viewport width in pixels
     * @param height viewport height in pixels
     * @param vertShader compiled SPIR-V vertex shader bytecode
     * @param fragShader compiled SPIR-V fragment shader bytecode
     * @return a new VkPipeline instance
     */
    public static VkPipeline createTrianglePipeline(Arena arena, MemorySegment device, MemorySegment renderPass, int width, int height, byte[] vertShader, byte[] fragShader) {
        VkShaderModule vertModule = VkShaderModule.create(arena, device, vertShader);
        VkShaderModule fragModule = VkShaderModule.create(arena, device, fragShader);
        
        try {
            MemorySegment mainName = arena.allocateFrom("main");
            
            MemorySegment vertStage = VkPipelineShaderStageCreateInfo.allocate(arena);
            VkPipelineShaderStageCreateInfo.sType(vertStage, 18);
            VkPipelineShaderStageCreateInfo.pNext(vertStage, MemorySegment.NULL);
            VkPipelineShaderStageCreateInfo.flags(vertStage, 0);
            VkPipelineShaderStageCreateInfo.stage(vertStage, VK_SHADER_STAGE_VERTEX_BIT);
            VkPipelineShaderStageCreateInfo.module(vertStage, vertModule.handle());
            VkPipelineShaderStageCreateInfo.pName(vertStage, mainName);
            VkPipelineShaderStageCreateInfo.pSpecializationInfo(vertStage, MemorySegment.NULL);
            
            MemorySegment fragStage = VkPipelineShaderStageCreateInfo.allocate(arena);
            VkPipelineShaderStageCreateInfo.sType(fragStage, 18);
            VkPipelineShaderStageCreateInfo.pNext(fragStage, MemorySegment.NULL);
            VkPipelineShaderStageCreateInfo.flags(fragStage, 0);
            VkPipelineShaderStageCreateInfo.stage(fragStage, VK_SHADER_STAGE_FRAGMENT_BIT);
            VkPipelineShaderStageCreateInfo.module(fragStage, fragModule.handle());
            VkPipelineShaderStageCreateInfo.pName(fragStage, mainName);
            VkPipelineShaderStageCreateInfo.pSpecializationInfo(fragStage, MemorySegment.NULL);
            
            MemorySegment vertexInputInfo = VkPipelineVertexInputStateCreateInfo.allocate(arena);
            VkPipelineVertexInputStateCreateInfo.sType(vertexInputInfo, 19);
            VkPipelineVertexInputStateCreateInfo.pNext(vertexInputInfo, MemorySegment.NULL);
            VkPipelineVertexInputStateCreateInfo.flags(vertexInputInfo, 0);
            VkPipelineVertexInputStateCreateInfo.vertexBindingDescriptionCount(vertexInputInfo, 0);
            VkPipelineVertexInputStateCreateInfo.pVertexBindingDescriptions(vertexInputInfo, MemorySegment.NULL);
            VkPipelineVertexInputStateCreateInfo.vertexAttributeDescriptionCount(vertexInputInfo, 0);
            VkPipelineVertexInputStateCreateInfo.pVertexAttributeDescriptions(vertexInputInfo, MemorySegment.NULL);
            
            MemorySegment inputAssembly = VkPipelineInputAssemblyStateCreateInfo.allocate(arena);
            VkPipelineInputAssemblyStateCreateInfo.sType(inputAssembly, 20);
            VkPipelineInputAssemblyStateCreateInfo.pNext(inputAssembly, MemorySegment.NULL);
            VkPipelineInputAssemblyStateCreateInfo.flags(inputAssembly, 0);
            VkPipelineInputAssemblyStateCreateInfo.topology(inputAssembly, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineInputAssemblyStateCreateInfo.primitiveRestartEnable(inputAssembly, 0);
            
            MemorySegment viewport = VkViewport.allocate(arena);
            VkViewport.x(viewport, 0.0f);
            VkViewport.y(viewport, 0.0f);
            VkViewport.width(viewport, (float)width);
            VkViewport.height(viewport, (float)height);
            VkViewport.minDepth(viewport, 0.0f);
            VkViewport.maxDepth(viewport, 1.0f);
            
            MemorySegment scissor = VkRect2D.allocate(arena);
            VkOffset2D.x(VkRect2D.offset(scissor), 0);
            VkOffset2D.y(VkRect2D.offset(scissor), 0);
            VkExtent2D.width(VkRect2D.extent(scissor), width);
            VkExtent2D.height(VkRect2D.extent(scissor), height);
            
            MemorySegment viewportState = VkPipelineViewportStateCreateInfo.allocate(arena);
            VkPipelineViewportStateCreateInfo.sType(viewportState, 22);
            VkPipelineViewportStateCreateInfo.pNext(viewportState, MemorySegment.NULL);
            VkPipelineViewportStateCreateInfo.flags(viewportState, 0);
            VkPipelineViewportStateCreateInfo.viewportCount(viewportState, 1);
            VkPipelineViewportStateCreateInfo.pViewports(viewportState, viewport);
            VkPipelineViewportStateCreateInfo.scissorCount(viewportState, 1);
            VkPipelineViewportStateCreateInfo.pScissors(viewportState, scissor);
            
            MemorySegment rasterizer = VkPipelineRasterizationStateCreateInfo.allocate(arena);
            VkPipelineRasterizationStateCreateInfo.sType(rasterizer, 23);
            VkPipelineRasterizationStateCreateInfo.pNext(rasterizer, MemorySegment.NULL);
            VkPipelineRasterizationStateCreateInfo.flags(rasterizer, 0);
            VkPipelineRasterizationStateCreateInfo.depthClampEnable(rasterizer, 0);
            VkPipelineRasterizationStateCreateInfo.rasterizerDiscardEnable(rasterizer, 0);
            VkPipelineRasterizationStateCreateInfo.polygonMode(rasterizer, VK_POLYGON_MODE_FILL);
            VkPipelineRasterizationStateCreateInfo.cullMode(rasterizer, 0);
            VkPipelineRasterizationStateCreateInfo.frontFace(rasterizer, VK_FRONT_FACE_CLOCKWISE);
            VkPipelineRasterizationStateCreateInfo.depthBiasEnable(rasterizer, 0);
            VkPipelineRasterizationStateCreateInfo.depthBiasConstantFactor(rasterizer, 0.0f);
            VkPipelineRasterizationStateCreateInfo.depthBiasClamp(rasterizer, 0.0f);
            VkPipelineRasterizationStateCreateInfo.depthBiasSlopeFactor(rasterizer, 0.0f);
            VkPipelineRasterizationStateCreateInfo.lineWidth(rasterizer, 1.0f);
            
            MemorySegment multisampling = VkPipelineMultisampleStateCreateInfo.allocate(arena);
            VkPipelineMultisampleStateCreateInfo.sType(multisampling, 24);
            VkPipelineMultisampleStateCreateInfo.pNext(multisampling, MemorySegment.NULL);
            VkPipelineMultisampleStateCreateInfo.flags(multisampling, 0);
            VkPipelineMultisampleStateCreateInfo.rasterizationSamples(multisampling, VK_SAMPLE_COUNT_1_BIT);
            VkPipelineMultisampleStateCreateInfo.sampleShadingEnable(multisampling, 0);
            VkPipelineMultisampleStateCreateInfo.minSampleShading(multisampling, 1.0f);
            VkPipelineMultisampleStateCreateInfo.pSampleMask(multisampling, MemorySegment.NULL);
            VkPipelineMultisampleStateCreateInfo.alphaToCoverageEnable(multisampling, 0);
            VkPipelineMultisampleStateCreateInfo.alphaToOneEnable(multisampling, 0);
            
            MemorySegment colorBlendAttachment = VkPipelineColorBlendAttachmentState.allocate(arena);
            VkPipelineColorBlendAttachmentState.colorWriteMask(colorBlendAttachment, 0xF);
            VkPipelineColorBlendAttachmentState.blendEnable(colorBlendAttachment, 0);
            VkPipelineColorBlendAttachmentState.srcColorBlendFactor(colorBlendAttachment, VK_BLEND_FACTOR_ONE);
            VkPipelineColorBlendAttachmentState.dstColorBlendFactor(colorBlendAttachment, VK_BLEND_FACTOR_ZERO);
            VkPipelineColorBlendAttachmentState.colorBlendOp(colorBlendAttachment, VK_BLEND_OP_ADD);
            VkPipelineColorBlendAttachmentState.srcAlphaBlendFactor(colorBlendAttachment, VK_BLEND_FACTOR_ONE);
            VkPipelineColorBlendAttachmentState.dstAlphaBlendFactor(colorBlendAttachment, VK_BLEND_FACTOR_ZERO);
            VkPipelineColorBlendAttachmentState.alphaBlendOp(colorBlendAttachment, VK_BLEND_OP_ADD);
            
            MemorySegment colorBlending = VkPipelineColorBlendStateCreateInfo.allocate(arena);
            VkPipelineColorBlendStateCreateInfo.sType(colorBlending, 26);
            VkPipelineColorBlendStateCreateInfo.pNext(colorBlending, MemorySegment.NULL);
            VkPipelineColorBlendStateCreateInfo.flags(colorBlending, 0);
            VkPipelineColorBlendStateCreateInfo.logicOpEnable(colorBlending, 0);
            VkPipelineColorBlendStateCreateInfo.logicOp(colorBlending, VK_LOGIC_OP_COPY);
            VkPipelineColorBlendStateCreateInfo.attachmentCount(colorBlending, 1);
            VkPipelineColorBlendStateCreateInfo.pAttachments(colorBlending, colorBlendAttachment);
            VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 0, 0.0f);
            VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 1, 0.0f);
            VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 2, 0.0f);
            VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 3, 0.0f);
            
            MemorySegment pipelineLayoutInfo = VkPipelineLayoutCreateInfo.allocate(arena);
            VkPipelineLayoutCreateInfo.sType(pipelineLayoutInfo, 30);
            VkPipelineLayoutCreateInfo.pNext(pipelineLayoutInfo, MemorySegment.NULL);
            VkPipelineLayoutCreateInfo.flags(pipelineLayoutInfo, 0);
            VkPipelineLayoutCreateInfo.setLayoutCount(pipelineLayoutInfo, 0);
            VkPipelineLayoutCreateInfo.pSetLayouts(pipelineLayoutInfo, MemorySegment.NULL);
            VkPipelineLayoutCreateInfo.pushConstantRangeCount(pipelineLayoutInfo, 0);
            VkPipelineLayoutCreateInfo.pPushConstantRanges(pipelineLayoutInfo, MemorySegment.NULL);
            
            MemorySegment pipelineLayoutPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createPipelineLayout(device, pipelineLayoutInfo, pipelineLayoutPtr).check();
            MemorySegment pipelineLayout = pipelineLayoutPtr.get(ValueLayout.ADDRESS, 0);
            
            MemorySegment stages = arena.allocate(96);
            MemorySegment.copy(vertStage, 0, stages, 0, 48);
            MemorySegment.copy(fragStage, 0, stages, 48, 48);
            
            MemorySegment pipelineInfo = VkGraphicsPipelineCreateInfo.allocate(arena);
            VkGraphicsPipelineCreateInfo.sType(pipelineInfo, 29);
            VkGraphicsPipelineCreateInfo.pNext(pipelineInfo, MemorySegment.NULL);
            VkGraphicsPipelineCreateInfo.flags(pipelineInfo, 0);
            VkGraphicsPipelineCreateInfo.stageCount(pipelineInfo, 2);
            VkGraphicsPipelineCreateInfo.pStages(pipelineInfo, stages);
            VkGraphicsPipelineCreateInfo.pVertexInputState(pipelineInfo, vertexInputInfo);
            VkGraphicsPipelineCreateInfo.pInputAssemblyState(pipelineInfo, inputAssembly);
            VkGraphicsPipelineCreateInfo.pTessellationState(pipelineInfo, MemorySegment.NULL);
            VkGraphicsPipelineCreateInfo.pViewportState(pipelineInfo, viewportState);
            VkGraphicsPipelineCreateInfo.pRasterizationState(pipelineInfo, rasterizer);
            VkGraphicsPipelineCreateInfo.pMultisampleState(pipelineInfo, multisampling);
            VkGraphicsPipelineCreateInfo.pDepthStencilState(pipelineInfo, MemorySegment.NULL);
            VkGraphicsPipelineCreateInfo.pColorBlendState(pipelineInfo, colorBlending);
            VkGraphicsPipelineCreateInfo.pDynamicState(pipelineInfo, MemorySegment.NULL);
            VkGraphicsPipelineCreateInfo.layout(pipelineInfo, pipelineLayout);
            VkGraphicsPipelineCreateInfo.renderPass(pipelineInfo, renderPass);
            VkGraphicsPipelineCreateInfo.subpass(pipelineInfo, 0);
            VkGraphicsPipelineCreateInfo.basePipelineHandle(pipelineInfo, MemorySegment.NULL);
            VkGraphicsPipelineCreateInfo.basePipelineIndex(pipelineInfo, -1);
            
            MemorySegment pipelinePtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createGraphicsPipelines(device, MemorySegment.NULL, 1, pipelineInfo, pipelinePtr).check();
            return new VkPipeline(pipelinePtr.get(ValueLayout.ADDRESS, 0), pipelineLayout, device);
        } finally {
            vertModule.close();
            fragModule.close();
        }
    }
    
    /** @return the VkPipeline handle */
    public MemorySegment handle() { return handle; }
    
    /** @return the VkPipelineLayout handle */
    public MemorySegment layout() { return layout; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyPipeline(device, handle);
        VulkanExtensions.destroyPipelineLayout(device, layout);
    }
}