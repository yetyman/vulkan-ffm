package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

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
    
    /** @return a new builder for configuring pipeline creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a graphics pipeline configured for rendering a simple triangle.
     */
    public static VkPipeline createTrianglePipeline(Arena arena, MemorySegment device, MemorySegment renderPass, int width, int height, byte[] vertShader, byte[] fragShader) {
        return builder()
            .device(device)
            .renderPass(renderPass)
            .viewport(0, 0, width, height)
            .vertexShader(vertShader)
            .fragmentShader(fragShader)
            .triangleTopology()
            .build(arena);
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
    
    /**
     * Builder for complete graphics pipeline creation.
     */
    public static class Builder {
        private MemorySegment device;
        private MemorySegment renderPass;
        private int subpass = 0;
        private MemorySegment basePipeline = MemorySegment.NULL;
        private int basePipelineIndex = -1;
        private int flags = 0;
        
        // Shader stages
        private byte[] vertShader, tessControlShader, tessEvalShader, geomShader, fragShader;
        
        // Vertex input
        private java.util.List<VertexBinding> vertexBindings = new java.util.ArrayList<>();
        private java.util.List<VertexAttribute> vertexAttributes = new java.util.ArrayList<>();
        
        // Input assembly
        private int topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        private boolean primitiveRestart = false;
        
        // Tessellation
        private int patchControlPoints = 3;
        
        // Viewport
        private java.util.List<ViewportConfig> viewports = new java.util.ArrayList<>();
        private java.util.List<ScissorConfig> scissors = new java.util.ArrayList<>();
        
        // Rasterization
        private boolean depthClamp = false;
        private boolean rasterizerDiscard = false;
        private int polygonMode = VkPolygonMode.VK_POLYGON_MODE_FILL;
        private int cullMode = 0;
        private int frontFace = VkFrontFace.VK_FRONT_FACE_COUNTER_CLOCKWISE;
        private boolean depthBias = false;
        private float depthBiasConstant = 0.0f, depthBiasClamp = 0.0f, depthBiasSlope = 0.0f;
        private float lineWidth = 1.0f;
        
        // Multisampling
        private int rasterizationSamples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT;
        private boolean sampleShading = false;
        private float minSampleShading = 1.0f;
        private int[] sampleMask = null;
        private boolean alphaToCoverage = false, alphaToOne = false;
        
        // Depth/Stencil
        private boolean depthTest = false, depthWrite = true;
        private int depthCompareOp = VkCompareOp.VK_COMPARE_OP_LESS;
        private boolean depthBoundsTest = false;
        private float minDepthBounds = 0.0f, maxDepthBounds = 1.0f;
        private boolean stencilTest = false;
        private StencilOpState frontStencil = new StencilOpState();
        private StencilOpState backStencil = new StencilOpState();
        
        // Color blending
        private boolean logicOpEnable = false;
        private int logicOp = VkLogicOp.VK_LOGIC_OP_COPY;
        private java.util.List<ColorBlendAttachment> colorAttachments = new java.util.ArrayList<>();
        private float[] blendConstants = {0.0f, 0.0f, 0.0f, 0.0f};
        
        // Dynamic state
        private java.util.List<Integer> dynamicStates = new java.util.ArrayList<>();
        
        // Pipeline layout
        private MemorySegment[] descriptorSetLayouts = null;
        private java.util.List<PushConstantRange> pushConstantRanges = new java.util.ArrayList<>();
        
        private Builder() {}
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder renderPass(MemorySegment renderPass) {
            this.renderPass = renderPass;
            return this;
        }
        
        public Builder vertexShader(byte[] shader) {
            this.vertShader = shader;
            return this;
        }
        
        public Builder fragmentShader(byte[] shader) {
            this.fragShader = shader;
            return this;
        }
        
        public Builder viewport(float x, float y, float width, float height) {
            viewports.clear();
            scissors.clear();
            viewports.add(new ViewportConfig(x, y, width, height, 0.0f, 1.0f));
            scissors.add(new ScissorConfig((int)x, (int)y, (int)width, (int)height));
            return this;
        }
        
        public Builder triangleTopology() {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            return this;
        }
        
        public Builder lineTopology() {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            return this;
        }
        
        public Builder wireframe() {
            this.polygonMode = VkPolygonMode.VK_POLYGON_MODE_LINE;
            return this;
        }
        
        public Builder cullBack() {
            this.cullMode = VkCullModeFlagBits.VK_CULL_MODE_BACK_BIT;
            return this;
        }
        
        // Shader stage methods
        public Builder tessellationControlShader(byte[] shader) {
            this.tessControlShader = shader;
            return this;
        }
        
        public Builder tessellationEvaluationShader(byte[] shader) {
            this.tessEvalShader = shader;
            return this;
        }
        
        public Builder geometryShader(byte[] shader) {
            this.geomShader = shader;
            return this;
        }
        
        // Vertex input methods
        public Builder vertexBinding(int binding, int stride, int inputRate) {
            vertexBindings.add(new VertexBinding(binding, stride, inputRate));
            return this;
        }
        
        public Builder vertexAttribute(int location, int binding, int format, int offset) {
            vertexAttributes.add(new VertexAttribute(location, binding, format, offset));
            return this;
        }
        
        // Input assembly methods
        public Builder primitiveRestart(boolean enable) {
            this.primitiveRestart = enable;
            return this;
        }
        
        public Builder pointTopology() {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            return this;
        }
        
        public Builder lineStripTopology() {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            return this;
        }
        
        public Builder triangleStripTopology() {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            return this;
        }
        
        public Builder triangleFanTopology() {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
            return this;
        }
        
        public Builder patchTopology(int controlPoints) {
            this.topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_PATCH_LIST;
            this.patchControlPoints = controlPoints;
            return this;
        }
        
        // Viewport methods
        public Builder addViewport(float x, float y, float width, float height, float minDepth, float maxDepth) {
            viewports.add(new ViewportConfig(x, y, width, height, minDepth, maxDepth));
            return this;
        }
        
        public Builder addScissor(int x, int y, int width, int height) {
            scissors.add(new ScissorConfig(x, y, width, height));
            return this;
        }
        
        // Rasterization methods
        public Builder depthClamp(boolean enable) {
            this.depthClamp = enable;
            return this;
        }
        
        public Builder rasterizerDiscard(boolean enable) {
            this.rasterizerDiscard = enable;
            return this;
        }
        
        public Builder pointMode() {
            this.polygonMode = VkPolygonMode.VK_POLYGON_MODE_POINT;
            return this;
        }
        
        public Builder cullFront() {
            this.cullMode = VkCullModeFlagBits.VK_CULL_MODE_FRONT_BIT;
            return this;
        }
        
        public Builder cullFrontAndBack() {
            this.cullMode = VkCullModeFlagBits.VK_CULL_MODE_FRONT_AND_BACK;
            return this;
        }
        
        public Builder frontFaceClockwise() {
            this.frontFace = VkFrontFace.VK_FRONT_FACE_CLOCKWISE;
            return this;
        }
        
        public Builder depthBias(float constant, float clamp, float slope) {
            this.depthBias = true;
            this.depthBiasConstant = constant;
            this.depthBiasClamp = clamp;
            this.depthBiasSlope = slope;
            return this;
        }
        
        public Builder lineWidth(float width) {
            this.lineWidth = width;
            return this;
        }
        
        // Multisampling methods
        public Builder multisampling(int samples) {
            this.rasterizationSamples = samples;
            return this;
        }
        
        public Builder sampleShading(boolean enable, float minSampleShading) {
            this.sampleShading = enable;
            this.minSampleShading = minSampleShading;
            return this;
        }
        
        public Builder sampleMask(int[] mask) {
            this.sampleMask = mask;
            return this;
        }
        
        public Builder alphaToCoverage(boolean enable) {
            this.alphaToCoverage = enable;
            return this;
        }
        
        public Builder alphaToOne(boolean enable) {
            this.alphaToOne = enable;
            return this;
        }
        
        // Depth/Stencil methods
        public Builder depthTest(boolean enable) {
            this.depthTest = enable;
            return this;
        }
        
        public Builder depthWrite(boolean enable) {
            this.depthWrite = enable;
            return this;
        }
        
        public Builder depthCompareOp(int compareOp) {
            this.depthCompareOp = compareOp;
            return this;
        }
        
        public Builder depthBounds(boolean enable, float min, float max) {
            this.depthBoundsTest = enable;
            this.minDepthBounds = min;
            this.maxDepthBounds = max;
            return this;
        }
        
        public Builder stencilTest(boolean enable) {
            this.stencilTest = enable;
            return this;
        }
        
        public Builder frontStencil(int failOp, int passOp, int depthFailOp, int compareOp, int compareMask, int writeMask, int reference) {
            this.frontStencil = new StencilOpState(failOp, passOp, depthFailOp, compareOp, compareMask, writeMask, reference);
            return this;
        }
        
        public Builder backStencil(int failOp, int passOp, int depthFailOp, int compareOp, int compareMask, int writeMask, int reference) {
            this.backStencil = new StencilOpState(failOp, passOp, depthFailOp, compareOp, compareMask, writeMask, reference);
            return this;
        }
        
        // Color blending methods
        public Builder logicOp(boolean enable, int op) {
            this.logicOpEnable = enable;
            this.logicOp = op;
            return this;
        }
        
        public Builder colorAttachment(boolean blendEnable, int srcColorBlendFactor, int dstColorBlendFactor, int colorBlendOp,
                                       int srcAlphaBlendFactor, int dstAlphaBlendFactor, int alphaBlendOp, int colorWriteMask) {
            colorAttachments.add(new ColorBlendAttachment(blendEnable, srcColorBlendFactor, dstColorBlendFactor, colorBlendOp,
                srcAlphaBlendFactor, dstAlphaBlendFactor, alphaBlendOp, colorWriteMask));
            return this;
        }
        
        public Builder blendConstants(float r, float g, float b, float a) {
            this.blendConstants = new float[]{r, g, b, a};
            return this;
        }
        
        // Dynamic state methods
        public Builder dynamicViewport() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT);
            return this;
        }
        
        public Builder dynamicScissor() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_SCISSOR);
            return this;
        }
        
        public Builder dynamicLineWidth() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_LINE_WIDTH);
            return this;
        }
        
        public Builder dynamicDepthBias() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_DEPTH_BIAS);
            return this;
        }
        
        public Builder dynamicBlendConstants() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_BLEND_CONSTANTS);
            return this;
        }
        
        public Builder dynamicDepthBounds() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_DEPTH_BOUNDS);
            return this;
        }
        
        public Builder dynamicStencilCompareMask() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK);
            return this;
        }
        
        public Builder dynamicStencilWriteMask() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_STENCIL_WRITE_MASK);
            return this;
        }
        
        public Builder dynamicStencilReference() {
            dynamicStates.add(VkDynamicState.VK_DYNAMIC_STATE_STENCIL_REFERENCE);
            return this;
        }
        
        // Pipeline layout methods
        public Builder descriptorSetLayouts(MemorySegment... layouts) {
            this.descriptorSetLayouts = layouts;
            return this;
        }
        
        public Builder pushConstantRange(int stageFlags, int offset, int size) {
            pushConstantRanges.add(new PushConstantRange(stageFlags, offset, size));
            return this;
        }
        
        // Pipeline creation methods
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        public Builder subpass(int subpass) {
            this.subpass = subpass;
            return this;
        }
        
        public Builder basePipeline(MemorySegment pipeline) {
            this.basePipeline = pipeline;
            return this;
        }
        
        public Builder basePipelineIndex(int index) {
            this.basePipelineIndex = index;
            return this;
        }
        
        public VkPipeline build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (renderPass == null) throw new IllegalStateException("renderPass not set");
            if (vertShader == null) throw new IllegalStateException("vertex shader not set");
            
            // Create shader modules
            java.util.List<VkShaderModule> shaderModules = new java.util.ArrayList<>();
            java.util.List<MemorySegment> stages = new java.util.ArrayList<>();
            
            MemorySegment mainName = arena.allocateFrom("main");
            
            // Vertex shader (required)
            VkShaderModule vertModule = VkShaderModule.create(arena, device, vertShader);
            shaderModules.add(vertModule);
            MemorySegment vertStage = VkPipelineShaderStageCreateInfo.allocate(arena);
            VkPipelineShaderStageCreateInfo.sType(vertStage, 18);
            VkPipelineShaderStageCreateInfo.stage(vertStage, VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT);
            VkPipelineShaderStageCreateInfo.module(vertStage, vertModule.handle());
            VkPipelineShaderStageCreateInfo.pName(vertStage, mainName);
            stages.add(vertStage);
            
            // Optional shader stages
            if (tessControlShader != null) {
                VkShaderModule module = VkShaderModule.create(arena, device, tessControlShader);
                shaderModules.add(module);
                MemorySegment stage = VkPipelineShaderStageCreateInfo.allocate(arena);
                VkPipelineShaderStageCreateInfo.sType(stage, 18);
                VkPipelineShaderStageCreateInfo.stage(stage, VkShaderStageFlagBits.VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT);
                VkPipelineShaderStageCreateInfo.module(stage, module.handle());
                VkPipelineShaderStageCreateInfo.pName(stage, mainName);
                stages.add(stage);
            }
            
            if (tessEvalShader != null) {
                VkShaderModule module = VkShaderModule.create(arena, device, tessEvalShader);
                shaderModules.add(module);
                MemorySegment stage = VkPipelineShaderStageCreateInfo.allocate(arena);
                VkPipelineShaderStageCreateInfo.sType(stage, 18);
                VkPipelineShaderStageCreateInfo.stage(stage, VkShaderStageFlagBits.VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT);
                VkPipelineShaderStageCreateInfo.module(stage, module.handle());
                VkPipelineShaderStageCreateInfo.pName(stage, mainName);
                stages.add(stage);
            }
            
            if (geomShader != null) {
                VkShaderModule module = VkShaderModule.create(arena, device, geomShader);
                shaderModules.add(module);
                MemorySegment stage = VkPipelineShaderStageCreateInfo.allocate(arena);
                VkPipelineShaderStageCreateInfo.sType(stage, 18);
                VkPipelineShaderStageCreateInfo.stage(stage, VkShaderStageFlagBits.VK_SHADER_STAGE_GEOMETRY_BIT);
                VkPipelineShaderStageCreateInfo.module(stage, module.handle());
                VkPipelineShaderStageCreateInfo.pName(stage, mainName);
                stages.add(stage);
            }
            
            if (fragShader != null) {
                VkShaderModule module = VkShaderModule.create(arena, device, fragShader);
                shaderModules.add(module);
                MemorySegment stage = VkPipelineShaderStageCreateInfo.allocate(arena);
                VkPipelineShaderStageCreateInfo.sType(stage, 18);
                VkPipelineShaderStageCreateInfo.stage(stage, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT);
                VkPipelineShaderStageCreateInfo.module(stage, module.handle());
                VkPipelineShaderStageCreateInfo.pName(stage, mainName);
                stages.add(stage);
            }
            
            try {
                // Create stages array
                MemorySegment stagesArray = arena.allocate(VkPipelineShaderStageCreateInfo.layout(), stages.size());
                for (int i = 0; i < stages.size(); i++) {
                    MemorySegment.copy(stages.get(i), 0, stagesArray, i * VkPipelineShaderStageCreateInfo.layout().byteSize(), VkPipelineShaderStageCreateInfo.layout().byteSize());
                }
                
                // Vertex input state
                MemorySegment vertexInputInfo = VkPipelineVertexInputStateCreateInfo.allocate(arena);
                VkPipelineVertexInputStateCreateInfo.sType(vertexInputInfo, 19);
                
                if (!vertexBindings.isEmpty()) {
                    MemorySegment bindingDescs = arena.allocate(VkVertexInputBindingDescription.layout(), vertexBindings.size());
                    for (int i = 0; i < vertexBindings.size(); i++) {
                        VertexBinding binding = vertexBindings.get(i);
                        MemorySegment desc = bindingDescs.asSlice(i * VkVertexInputBindingDescription.layout().byteSize(), VkVertexInputBindingDescription.layout());
                        VkVertexInputBindingDescription.binding(desc, binding.binding());
                        VkVertexInputBindingDescription.stride(desc, binding.stride());
                        VkVertexInputBindingDescription.inputRate(desc, binding.inputRate());
                    }
                    VkPipelineVertexInputStateCreateInfo.vertexBindingDescriptionCount(vertexInputInfo, vertexBindings.size());
                    VkPipelineVertexInputStateCreateInfo.pVertexBindingDescriptions(vertexInputInfo, bindingDescs);
                }
                
                if (!vertexAttributes.isEmpty()) {
                    MemorySegment attrDescs = arena.allocate(VkVertexInputAttributeDescription.layout(), vertexAttributes.size());
                    for (int i = 0; i < vertexAttributes.size(); i++) {
                        VertexAttribute attr = vertexAttributes.get(i);
                        MemorySegment desc = attrDescs.asSlice(i * VkVertexInputAttributeDescription.layout().byteSize(), VkVertexInputAttributeDescription.layout());
                        VkVertexInputAttributeDescription.location(desc, attr.location());
                        VkVertexInputAttributeDescription.binding(desc, attr.binding());
                        VkVertexInputAttributeDescription.format(desc, attr.format());
                        VkVertexInputAttributeDescription.offset(desc, attr.offset());
                    }
                    VkPipelineVertexInputStateCreateInfo.vertexAttributeDescriptionCount(vertexInputInfo, vertexAttributes.size());
                    VkPipelineVertexInputStateCreateInfo.pVertexAttributeDescriptions(vertexInputInfo, attrDescs);
                }
                
                // Input assembly state
                MemorySegment inputAssembly = VkPipelineInputAssemblyStateCreateInfo.allocate(arena);
                VkPipelineInputAssemblyStateCreateInfo.sType(inputAssembly, 20);
                VkPipelineInputAssemblyStateCreateInfo.topology(inputAssembly, topology);
                VkPipelineInputAssemblyStateCreateInfo.primitiveRestartEnable(inputAssembly, primitiveRestart ? 1 : 0);
                
                // Tessellation state
                MemorySegment tessellationState = MemorySegment.NULL;
                if (tessControlShader != null || tessEvalShader != null) {
                    tessellationState = VkPipelineTessellationStateCreateInfo.allocate(arena);
                    VkPipelineTessellationStateCreateInfo.sType(tessellationState, 21);
                    VkPipelineTessellationStateCreateInfo.patchControlPoints(tessellationState, patchControlPoints);
                }
                
                // Viewport state
                MemorySegment viewportState = VkPipelineViewportStateCreateInfo.allocate(arena);
                VkPipelineViewportStateCreateInfo.sType(viewportState, 22);
                
                if (viewports.isEmpty()) {
                    viewports.add(new ViewportConfig(0, 0, 800, 600, 0.0f, 1.0f));
                }
                if (scissors.isEmpty()) {
                    scissors.add(new ScissorConfig(0, 0, (int)viewports.get(0).width(), (int)viewports.get(0).height()));
                }
                
                MemorySegment viewportsArray = arena.allocate(VkViewport.layout(), viewports.size());
                for (int i = 0; i < viewports.size(); i++) {
                    ViewportConfig vp = viewports.get(i);
                    MemorySegment viewport = viewportsArray.asSlice(i * VkViewport.layout().byteSize(), VkViewport.layout());
                    VkViewport.x(viewport, vp.x());
                    VkViewport.y(viewport, vp.y());
                    VkViewport.width(viewport, vp.width());
                    VkViewport.height(viewport, vp.height());
                    VkViewport.minDepth(viewport, vp.minDepth());
                    VkViewport.maxDepth(viewport, vp.maxDepth());
                }
                
                MemorySegment scissorsArray = arena.allocate(VkRect2D.layout(), scissors.size());
                for (int i = 0; i < scissors.size(); i++) {
                    ScissorConfig sc = scissors.get(i);
                    MemorySegment scissor = scissorsArray.asSlice(i * VkRect2D.layout().byteSize(), VkRect2D.layout());
                    VkOffset2D.x(VkRect2D.offset(scissor), sc.x());
                    VkOffset2D.y(VkRect2D.offset(scissor), sc.y());
                    VkExtent2D.width(VkRect2D.extent(scissor), sc.width());
                    VkExtent2D.height(VkRect2D.extent(scissor), sc.height());
                }
                
                VkPipelineViewportStateCreateInfo.viewportCount(viewportState, viewports.size());
                VkPipelineViewportStateCreateInfo.pViewports(viewportState, viewportsArray);
                VkPipelineViewportStateCreateInfo.scissorCount(viewportState, scissors.size());
                VkPipelineViewportStateCreateInfo.pScissors(viewportState, scissorsArray);
                
                // Rasterization state
                MemorySegment rasterizer = VkPipelineRasterizationStateCreateInfo.allocate(arena);
                VkPipelineRasterizationStateCreateInfo.sType(rasterizer, 23);
                VkPipelineRasterizationStateCreateInfo.depthClampEnable(rasterizer, depthClamp ? 1 : 0);
                VkPipelineRasterizationStateCreateInfo.rasterizerDiscardEnable(rasterizer, rasterizerDiscard ? 1 : 0);
                VkPipelineRasterizationStateCreateInfo.polygonMode(rasterizer, polygonMode);
                VkPipelineRasterizationStateCreateInfo.cullMode(rasterizer, cullMode);
                VkPipelineRasterizationStateCreateInfo.frontFace(rasterizer, frontFace);
                VkPipelineRasterizationStateCreateInfo.depthBiasEnable(rasterizer, depthBias ? 1 : 0);
                VkPipelineRasterizationStateCreateInfo.depthBiasConstantFactor(rasterizer, depthBiasConstant);
                VkPipelineRasterizationStateCreateInfo.depthBiasClamp(rasterizer, depthBiasClamp);
                VkPipelineRasterizationStateCreateInfo.depthBiasSlopeFactor(rasterizer, depthBiasSlope);
                VkPipelineRasterizationStateCreateInfo.lineWidth(rasterizer, lineWidth);
                
                // Multisample state
                MemorySegment multisampling = VkPipelineMultisampleStateCreateInfo.allocate(arena);
                VkPipelineMultisampleStateCreateInfo.sType(multisampling, 24);
                VkPipelineMultisampleStateCreateInfo.rasterizationSamples(multisampling, rasterizationSamples);
                VkPipelineMultisampleStateCreateInfo.sampleShadingEnable(multisampling, sampleShading ? 1 : 0);
                VkPipelineMultisampleStateCreateInfo.minSampleShading(multisampling, minSampleShading);
                if (sampleMask != null) {
                    MemorySegment maskArray = arena.allocate(ValueLayout.JAVA_INT, sampleMask.length);
                    for (int i = 0; i < sampleMask.length; i++) {
                        maskArray.setAtIndex(ValueLayout.JAVA_INT, i, sampleMask[i]);
                    }
                    VkPipelineMultisampleStateCreateInfo.pSampleMask(multisampling, maskArray);
                }
                VkPipelineMultisampleStateCreateInfo.alphaToCoverageEnable(multisampling, alphaToCoverage ? 1 : 0);
                VkPipelineMultisampleStateCreateInfo.alphaToOneEnable(multisampling, alphaToOne ? 1 : 0);
                
                // Depth/Stencil state
                MemorySegment depthStencilState = MemorySegment.NULL;
                if (depthTest || stencilTest) {
                    depthStencilState = VkPipelineDepthStencilStateCreateInfo.allocate(arena);
                    VkPipelineDepthStencilStateCreateInfo.sType(depthStencilState, 25);
                    VkPipelineDepthStencilStateCreateInfo.depthTestEnable(depthStencilState, depthTest ? 1 : 0);
                    VkPipelineDepthStencilStateCreateInfo.depthWriteEnable(depthStencilState, depthWrite ? 1 : 0);
                    VkPipelineDepthStencilStateCreateInfo.depthCompareOp(depthStencilState, depthCompareOp);
                    VkPipelineDepthStencilStateCreateInfo.depthBoundsTestEnable(depthStencilState, depthBoundsTest ? 1 : 0);
                    VkPipelineDepthStencilStateCreateInfo.stencilTestEnable(depthStencilState, stencilTest ? 1 : 0);
                    
                    MemorySegment front = VkPipelineDepthStencilStateCreateInfo.front(depthStencilState);
                    VkStencilOpState.failOp(front, frontStencil.failOp());
                    VkStencilOpState.passOp(front, frontStencil.passOp());
                    VkStencilOpState.depthFailOp(front, frontStencil.depthFailOp());
                    VkStencilOpState.compareOp(front, frontStencil.compareOp());
                    VkStencilOpState.compareMask(front, frontStencil.compareMask());
                    VkStencilOpState.writeMask(front, frontStencil.writeMask());
                    VkStencilOpState.reference(front, frontStencil.reference());
                    
                    MemorySegment back = VkPipelineDepthStencilStateCreateInfo.back(depthStencilState);
                    VkStencilOpState.failOp(back, backStencil.failOp());
                    VkStencilOpState.passOp(back, backStencil.passOp());
                    VkStencilOpState.depthFailOp(back, backStencil.depthFailOp());
                    VkStencilOpState.compareOp(back, backStencil.compareOp());
                    VkStencilOpState.compareMask(back, backStencil.compareMask());
                    VkStencilOpState.writeMask(back, backStencil.writeMask());
                    VkStencilOpState.reference(back, backStencil.reference());
                    
                    VkPipelineDepthStencilStateCreateInfo.minDepthBounds(depthStencilState, minDepthBounds);
                    VkPipelineDepthStencilStateCreateInfo.maxDepthBounds(depthStencilState, maxDepthBounds);
                }
                
                // Color blend state
                MemorySegment colorBlending = VkPipelineColorBlendStateCreateInfo.allocate(arena);
                VkPipelineColorBlendStateCreateInfo.sType(colorBlending, 26);
                VkPipelineColorBlendStateCreateInfo.logicOpEnable(colorBlending, logicOpEnable ? 1 : 0);
                VkPipelineColorBlendStateCreateInfo.logicOp(colorBlending, logicOp);
                
                if (colorAttachments.isEmpty()) {
                    colorAttachments.add(new ColorBlendAttachment(false, 0, 0, 0, 0, 0, 0, 0xF));
                }
                
                MemorySegment attachmentsArray = arena.allocate(VkPipelineColorBlendAttachmentState.layout(), colorAttachments.size());
                for (int i = 0; i < colorAttachments.size(); i++) {
                    ColorBlendAttachment att = colorAttachments.get(i);
                    MemorySegment attachment = attachmentsArray.asSlice(i * VkPipelineColorBlendAttachmentState.layout().byteSize(), VkPipelineColorBlendAttachmentState.layout());
                    VkPipelineColorBlendAttachmentState.blendEnable(attachment, att.blendEnable() ? 1 : 0);
                    VkPipelineColorBlendAttachmentState.srcColorBlendFactor(attachment, att.srcColorBlendFactor());
                    VkPipelineColorBlendAttachmentState.dstColorBlendFactor(attachment, att.dstColorBlendFactor());
                    VkPipelineColorBlendAttachmentState.colorBlendOp(attachment, att.colorBlendOp());
                    VkPipelineColorBlendAttachmentState.srcAlphaBlendFactor(attachment, att.srcAlphaBlendFactor());
                    VkPipelineColorBlendAttachmentState.dstAlphaBlendFactor(attachment, att.dstAlphaBlendFactor());
                    VkPipelineColorBlendAttachmentState.alphaBlendOp(attachment, att.alphaBlendOp());
                    VkPipelineColorBlendAttachmentState.colorWriteMask(attachment, att.colorWriteMask());
                }
                
                VkPipelineColorBlendStateCreateInfo.attachmentCount(colorBlending, colorAttachments.size());
                VkPipelineColorBlendStateCreateInfo.pAttachments(colorBlending, attachmentsArray);
                VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 0, blendConstants[0]);
                VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 1, blendConstants[1]);
                VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 2, blendConstants[2]);
                VkPipelineColorBlendStateCreateInfo.blendConstants(colorBlending, 3, blendConstants[3]);
                
                // Dynamic state
                MemorySegment dynamicState = MemorySegment.NULL;
                if (!dynamicStates.isEmpty()) {
                    dynamicState = VkPipelineDynamicStateCreateInfo.allocate(arena);
                    VkPipelineDynamicStateCreateInfo.sType(dynamicState, 27);
                    
                    MemorySegment statesArray = arena.allocate(ValueLayout.JAVA_INT, dynamicStates.size());
                    for (int i = 0; i < dynamicStates.size(); i++) {
                        statesArray.setAtIndex(ValueLayout.JAVA_INT, i, dynamicStates.get(i));
                    }
                    VkPipelineDynamicStateCreateInfo.dynamicStateCount(dynamicState, dynamicStates.size());
                    VkPipelineDynamicStateCreateInfo.pDynamicStates(dynamicState, statesArray);
                }
                
                // Pipeline layout
                MemorySegment pipelineLayoutInfo = VkPipelineLayoutCreateInfo.allocate(arena);
                VkPipelineLayoutCreateInfo.sType(pipelineLayoutInfo, 30);
                
                if (descriptorSetLayouts != null && descriptorSetLayouts.length > 0) {
                    MemorySegment layoutsArray = arena.allocate(ValueLayout.ADDRESS, descriptorSetLayouts.length);
                    for (int i = 0; i < descriptorSetLayouts.length; i++) {
                        layoutsArray.setAtIndex(ValueLayout.ADDRESS, i, descriptorSetLayouts[i]);
                    }
                    VkPipelineLayoutCreateInfo.setLayoutCount(pipelineLayoutInfo, descriptorSetLayouts.length);
                    VkPipelineLayoutCreateInfo.pSetLayouts(pipelineLayoutInfo, layoutsArray);
                }
                
                if (!pushConstantRanges.isEmpty()) {
                    MemorySegment rangesArray = arena.allocate(VkPushConstantRange.layout(), pushConstantRanges.size());
                    for (int i = 0; i < pushConstantRanges.size(); i++) {
                        PushConstantRange range = pushConstantRanges.get(i);
                        MemorySegment rangeStruct = rangesArray.asSlice(i * VkPushConstantRange.layout().byteSize(), VkPushConstantRange.layout());
                        VkPushConstantRange.stageFlags(rangeStruct, range.stageFlags());
                        VkPushConstantRange.offset(rangeStruct, range.offset());
                        VkPushConstantRange.size(rangeStruct, range.size());
                    }
                    VkPipelineLayoutCreateInfo.pushConstantRangeCount(pipelineLayoutInfo, pushConstantRanges.size());
                    VkPipelineLayoutCreateInfo.pPushConstantRanges(pipelineLayoutInfo, rangesArray);
                }
                
                MemorySegment pipelineLayoutPtr = arena.allocate(ValueLayout.ADDRESS);
                VulkanExtensions.createPipelineLayout(device, pipelineLayoutInfo, pipelineLayoutPtr).check();
                MemorySegment pipelineLayout = pipelineLayoutPtr.get(ValueLayout.ADDRESS, 0);
                
                // Graphics pipeline
                MemorySegment pipelineInfo = VkGraphicsPipelineCreateInfo.allocate(arena);
                VkGraphicsPipelineCreateInfo.sType(pipelineInfo, 29);
                VkGraphicsPipelineCreateInfo.flags(pipelineInfo, flags);
                VkGraphicsPipelineCreateInfo.stageCount(pipelineInfo, stages.size());
                VkGraphicsPipelineCreateInfo.pStages(pipelineInfo, stagesArray);
                VkGraphicsPipelineCreateInfo.pVertexInputState(pipelineInfo, vertexInputInfo);
                VkGraphicsPipelineCreateInfo.pInputAssemblyState(pipelineInfo, inputAssembly);
                VkGraphicsPipelineCreateInfo.pTessellationState(pipelineInfo, tessellationState);
                VkGraphicsPipelineCreateInfo.pViewportState(pipelineInfo, viewportState);
                VkGraphicsPipelineCreateInfo.pRasterizationState(pipelineInfo, rasterizer);
                VkGraphicsPipelineCreateInfo.pMultisampleState(pipelineInfo, multisampling);
                VkGraphicsPipelineCreateInfo.pDepthStencilState(pipelineInfo, depthStencilState);
                VkGraphicsPipelineCreateInfo.pColorBlendState(pipelineInfo, colorBlending);
                VkGraphicsPipelineCreateInfo.pDynamicState(pipelineInfo, dynamicState);
                VkGraphicsPipelineCreateInfo.layout(pipelineInfo, pipelineLayout);
                VkGraphicsPipelineCreateInfo.renderPass(pipelineInfo, renderPass);
                VkGraphicsPipelineCreateInfo.subpass(pipelineInfo, subpass);
                VkGraphicsPipelineCreateInfo.basePipelineHandle(pipelineInfo, basePipeline);
                VkGraphicsPipelineCreateInfo.basePipelineIndex(pipelineInfo, basePipelineIndex);
                
                MemorySegment pipelinePtr = arena.allocate(ValueLayout.ADDRESS);
                VulkanExtensions.createGraphicsPipelines(device, MemorySegment.NULL, 1, pipelineInfo, pipelinePtr).check();
                return new VkPipeline(pipelinePtr.get(ValueLayout.ADDRESS, 0), pipelineLayout, device);
            } finally {
                for (VkShaderModule module : shaderModules) {
                    module.close();
                }
            }
        }
        
        // Helper records for configuration
        private record VertexBinding(int binding, int stride, int inputRate) {}
        private record VertexAttribute(int location, int binding, int format, int offset) {}
        private record ViewportConfig(float x, float y, float width, float height, float minDepth, float maxDepth) {}
        private record ScissorConfig(int x, int y, int width, int height) {}
        private record StencilOpState(int failOp, int passOp, int depthFailOp, int compareOp, int compareMask, int writeMask, int reference) {
            StencilOpState() { this(0, 0, 0, 0, 0, 0, 0); }
        }
        private record ColorBlendAttachment(boolean blendEnable, int srcColorBlendFactor, int dstColorBlendFactor, int colorBlendOp,
                                            int srcAlphaBlendFactor, int dstAlphaBlendFactor, int alphaBlendOp, int colorWriteMask) {}
        private record PushConstantRange(int stageFlags, int offset, int size) {}
    }
}