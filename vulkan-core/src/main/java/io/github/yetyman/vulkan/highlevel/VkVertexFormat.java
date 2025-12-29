package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Vertex format builder for creating vertex input descriptions.
 * Provides common vertex attribute layouts and fluent configuration.
 */
public class VkVertexFormat {
    private final List<VertexBinding> bindings = new ArrayList<>();
    private final List<VertexAttribute> attributes = new ArrayList<>();
    
    private VkVertexFormat() {}
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a simple position-only format (3 floats).
     */
    public static VkVertexFormat position3D() {
        return builder()
            .binding(0, 12, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX)
            .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 0)
            .build();
    }
    
    /**
     * Creates position + color format (3 + 3 floats).
     */
    public static VkVertexFormat positionColor() {
        return builder()
            .binding(0, 24, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX)
            .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 0)  // position
            .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 12) // color
            .build();
    }
    
    /**
     * Creates position + texture coordinate format (3 + 2 floats).
     */
    public static VkVertexFormat positionTexture() {
        return builder()
            .binding(0, 20, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX)
            .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 0)  // position
            .attribute(1, 0, VkFormat.VK_FORMAT_R32G32_SFLOAT, 12)    // texCoord
            .build();
    }
    
    /**
     * Creates position + normal + texture coordinate format (3 + 3 + 2 floats).
     */
    public static VkVertexFormat positionNormalTexture() {
        return builder()
            .binding(0, 32, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX)
            .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 0)  // position
            .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 12) // normal
            .attribute(2, 0, VkFormat.VK_FORMAT_R32G32_SFLOAT, 24)    // texCoord
            .build();
    }
    
    /**
     * Creates full vertex format with position, normal, texture, and tangent (3 + 3 + 2 + 4 floats).
     */
    public static VkVertexFormat positionNormalTextureTangent() {
        return builder()
            .binding(0, 48, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX)
            .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 0)  // position
            .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 12) // normal
            .attribute(2, 0, VkFormat.VK_FORMAT_R32G32_SFLOAT, 24)    // texCoord
            .attribute(3, 0, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT, 32) // tangent
            .build();
    }
    
    /**
     * Creates vertex input state create info for pipeline creation.
     */
    public MemorySegment createVertexInputState(Arena arena) {
        MemorySegment vertexInputInfo = VkPipelineVertexInputStateCreateInfo.allocate(arena);
        VkPipelineVertexInputStateCreateInfo.sType(vertexInputInfo, VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
        
        if (!bindings.isEmpty()) {
            MemorySegment bindingDescs = arena.allocate(VkVertexInputBindingDescription.layout(), bindings.size());
            for (int i = 0; i < bindings.size(); i++) {
                VertexBinding binding = bindings.get(i);
                MemorySegment desc = bindingDescs.asSlice(i * VkVertexInputBindingDescription.layout().byteSize(), 
                    VkVertexInputBindingDescription.layout());
                VkVertexInputBindingDescription.binding(desc, binding.binding());
                VkVertexInputBindingDescription.stride(desc, binding.stride());
                VkVertexInputBindingDescription.inputRate(desc, binding.inputRate());
            }
            VkPipelineVertexInputStateCreateInfo.vertexBindingDescriptionCount(vertexInputInfo, bindings.size());
            VkPipelineVertexInputStateCreateInfo.pVertexBindingDescriptions(vertexInputInfo, bindingDescs);
        }
        
        if (!attributes.isEmpty()) {
            MemorySegment attrDescs = arena.allocate(VkVertexInputAttributeDescription.layout(), attributes.size());
            for (int i = 0; i < attributes.size(); i++) {
                VertexAttribute attr = attributes.get(i);
                MemorySegment desc = attrDescs.asSlice(i * VkVertexInputAttributeDescription.layout().byteSize(),
                    VkVertexInputAttributeDescription.layout());
                VkVertexInputAttributeDescription.location(desc, attr.location());
                VkVertexInputAttributeDescription.binding(desc, attr.binding());
                VkVertexInputAttributeDescription.format(desc, attr.format());
                VkVertexInputAttributeDescription.offset(desc, attr.offset());
            }
            VkPipelineVertexInputStateCreateInfo.vertexAttributeDescriptionCount(vertexInputInfo, attributes.size());
            VkPipelineVertexInputStateCreateInfo.pVertexAttributeDescriptions(vertexInputInfo, attrDescs);
        }
        
        return vertexInputInfo;
    }
    
    /**
     * Gets the stride for a specific binding.
     */
    public int getStride(int binding) {
        return bindings.stream()
            .filter(b -> b.binding() == binding)
            .mapToInt(VertexBinding::stride)
            .findFirst()
            .orElse(0);
    }
    
    /**
     * Gets all bindings.
     */
    public List<VertexBinding> getBindings() {
        return new ArrayList<>(bindings);
    }
    
    /**
     * Gets all attributes.
     */
    public List<VertexAttribute> getAttributes() {
        return new ArrayList<>(attributes);
    }
    
    public static class Builder {
        private final VkVertexFormat format = new VkVertexFormat();
        
        /**
         * Adds a vertex binding description.
         */
        public Builder binding(int binding, int stride, int inputRate) {
            format.bindings.add(new VertexBinding(binding, stride, inputRate));
            return this;
        }
        
        /**
         * Adds a per-vertex binding.
         */
        public Builder perVertexBinding(int binding, int stride) {
            return binding(binding, stride, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX);
        }
        
        /**
         * Adds a per-instance binding.
         */
        public Builder perInstanceBinding(int binding, int stride) {
            return binding(binding, stride, VkVertexInputRate.VK_VERTEX_INPUT_RATE_INSTANCE);
        }
        
        /**
         * Adds a vertex attribute description.
         */
        public Builder attribute(int location, int binding, int formatValue, int offset) {
            format.attributes.add(new VertexAttribute(location, binding, formatValue, offset));
            return this;
        }
        
        /**
         * Adds a float attribute.
         */
        public Builder floatAttribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32_SFLOAT, offset);
        }
        
        /**
         * Adds a vec2 attribute.
         */
        public Builder vec2Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32_SFLOAT, offset);
        }
        
        /**
         * Adds a vec3 attribute.
         */
        public Builder vec3Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, offset);
        }
        
        /**
         * Adds a vec4 attribute.
         */
        public Builder vec4Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT, offset);
        }
        
        /**
         * Adds an int attribute.
         */
        public Builder intAttribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32_SINT, offset);
        }
        
        /**
         * Adds an ivec2 attribute.
         */
        public Builder ivec2Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32_SINT, offset);
        }
        
        /**
         * Adds an ivec3 attribute.
         */
        public Builder ivec3Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32B32_SINT, offset);
        }
        
        /**
         * Adds an ivec4 attribute.
         */
        public Builder ivec4Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32B32A32_SINT, offset);
        }
        
        /**
         * Adds a uint attribute.
         */
        public Builder uintAttribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32_UINT, offset);
        }
        
        /**
         * Adds a uvec2 attribute.
         */
        public Builder uvec2Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32_UINT, offset);
        }
        
        /**
         * Adds a uvec3 attribute.
         */
        public Builder uvec3Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32B32_UINT, offset);
        }
        
        /**
         * Adds a uvec4 attribute.
         */
        public Builder uvec4Attribute(int location, int binding, int offset) {
            return attribute(location, binding, VkFormat.VK_FORMAT_R32G32B32A32_UINT, offset);
        }
        
        /**
         * Adds normalized byte attributes (0-255 -> 0.0-1.0).
         */
        public Builder byteNormAttribute(int location, int binding, int offset, int components) {
            int formatValue = switch (components) {
                case 1 -> VkFormat.VK_FORMAT_R8_UNORM;
                case 2 -> VkFormat.VK_FORMAT_R8G8_UNORM;
                case 3 -> VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case 4 -> VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                default -> throw new IllegalArgumentException("Invalid component count: " + components);
            };
            return attribute(location, binding, formatValue, offset);
        }
        
        /**
         * Adds normalized short attributes (0-65535 -> 0.0-1.0).
         */
        public Builder shortNormAttribute(int location, int binding, int offset, int components) {
            int formatValue = switch (components) {
                case 1 -> VkFormat.VK_FORMAT_R16_UNORM;
                case 2 -> VkFormat.VK_FORMAT_R16G16_UNORM;
                case 3 -> VkFormat.VK_FORMAT_R16G16B16_UNORM;
                case 4 -> VkFormat.VK_FORMAT_R16G16B16A16_UNORM;
                default -> throw new IllegalArgumentException("Invalid component count: " + components);
            };
            return attribute(location, binding, formatValue, offset);
        }
        
        public VkVertexFormat build() {
            return format;
        }
    }
    
    public record VertexBinding(int binding, int stride, int inputRate) {}
    public record VertexAttribute(int location, int binding, int format, int offset) {}
}