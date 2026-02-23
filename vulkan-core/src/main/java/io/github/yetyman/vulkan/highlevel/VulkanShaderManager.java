package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkShaderModule;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;

/**
 * Manages shader loading and module creation.
 */
public class VulkanShaderManager implements AutoCloseable {
    private final VkDevice device;
    private final Map<String, CompiledShader> shaderCache = new HashMap<>();
    private final List<VkShaderModule> shaderModules = new ArrayList<>();

    private VulkanShaderManager(VkDevice device) {
        this.device = device;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CompiledShader loadShader(String path) {
        return shaderCache.computeIfAbsent(path, ShaderLoader::compileShader);
    }

    public MemorySegment createShaderModule(String path) {
        CompiledShader shader = loadShader(path);
        try (Arena arena = Arena.ofConfined()) {
            VkShaderModule module = shader.createShaderModule(device, arena);
            shaderModules.add(module);
            return module.handle();
        }
    }

    public ShaderSet.Builder createShaderSet() {
        return new ShaderSet.Builder(this);
    }

    @Override
    public void close() {
        for (VkShaderModule module : shaderModules) {
            module.close();
        }
        shaderModules.clear();
        for (CompiledShader shader : shaderCache.values()) {
            shader.close();
        }
        shaderCache.clear();
    }

    public static class ShaderSet {
        private final Map<String, CompiledShader> shaders;

        private ShaderSet(Map<String, CompiledShader> shaders) {
            this.shaders = Map.copyOf(shaders);
        }

        public CompiledShader vertex() { return shaders.get("vertex"); }
        public CompiledShader fragment() { return shaders.get("fragment"); }
        public CompiledShader geometry() { return shaders.get("geometry"); }
        public CompiledShader compute() { return shaders.get("compute"); }
        public CompiledShader tessControl() { return shaders.get("tessControl"); }
        public CompiledShader tessEvaluation() { return shaders.get("tessEvaluation"); }

        public CompiledShader get(String stage) { return shaders.get(stage); }
        public Set<String> stages() { return shaders.keySet(); }

        public static class Builder {
            private final VulkanShaderManager manager;
            private final Map<String, CompiledShader> shaders = new HashMap<>();

            Builder(VulkanShaderManager manager) {
                this.manager = manager;
            }

            public Builder vertex(String path) { shaders.put("vertex", manager.loadShader(path)); return this; }
            public Builder fragment(String path) { shaders.put("fragment", manager.loadShader(path)); return this; }
            public Builder geometry(String path) { shaders.put("geometry", manager.loadShader(path)); return this; }
            public Builder compute(String path) { shaders.put("compute", manager.loadShader(path)); return this; }
            public Builder tessControl(String path) { shaders.put("tessControl", manager.loadShader(path)); return this; }
            public Builder tessEvaluation(String path) { shaders.put("tessEvaluation", manager.loadShader(path)); return this; }
            public Builder shader(String stage, String path) { shaders.put(stage, manager.loadShader(path)); return this; }

            public ShaderSet build() {
                return new ShaderSet(shaders);
            }
        }
    }

    public static class Builder {
        private VkDevice device;

        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        public Builder context(VulkanContext context) {
            this.device = context.device();
            return this;
        }

        public VulkanShaderManager build() {
            if (device == null) throw new IllegalStateException("device not set");
            return new VulkanShaderManager(device);
        }
    }
}
