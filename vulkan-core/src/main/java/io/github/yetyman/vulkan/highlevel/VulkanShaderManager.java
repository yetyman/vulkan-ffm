package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.*;
import java.util.*;

/**
 * Manages shader loading and module creation.
 */
public class VulkanShaderManager implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment device;
    private final Map<String, byte[]> shaderCache = new HashMap<>();
    private final List<MemorySegment> shaderModules = new ArrayList<>();
    
    private VulkanShaderManager(Arena arena, MemorySegment device) {
        this.arena = arena;
        this.device = device;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public byte[] loadShader(String path) {
        return shaderCache.computeIfAbsent(path, ShaderLoader::compileShader);
    }
    
    public MemorySegment createShaderModule(String path) {
        byte[] code = loadShader(path);
        VkShaderModule module = VkShaderModule.create(arena, device, code);
        shaderModules.add(module.handle());
        return module.handle();
    }
    
    public ShaderSet.Builder createShaderSet() {
        return new ShaderSet.Builder(this);
    }
    
    @Override
    public void close() {
        for (MemorySegment module : shaderModules) {
            VulkanExtensions.destroyShaderModule(device, module);
        }
        shaderModules.clear();
        shaderCache.clear();
    }
    
    public static class ShaderSet {
        private final Map<String, byte[]> shaders;
        
        private ShaderSet(Map<String, byte[]> shaders) {
            this.shaders = Map.copyOf(shaders);
        }
        
        public byte[] vertex() { return shaders.get("vertex"); }
        public byte[] fragment() { return shaders.get("fragment"); }
        public byte[] geometry() { return shaders.get("geometry"); }
        public byte[] compute() { return shaders.get("compute"); }
        public byte[] tessControl() { return shaders.get("tessControl"); }
        public byte[] tessEvaluation() { return shaders.get("tessEvaluation"); }
        
        public byte[] get(String stage) { return shaders.get(stage); }
        public Set<String> stages() { return shaders.keySet(); }
        
        public static class Builder {
            private final VulkanShaderManager manager;
            private final Map<String, byte[]> shaders = new HashMap<>();
            
            Builder(VulkanShaderManager manager) {
                this.manager = manager;
            }
            
            public Builder vertex(String path) {
                shaders.put("vertex", manager.loadShader(path));
                return this;
            }
            
            public Builder fragment(String path) {
                shaders.put("fragment", manager.loadShader(path));
                return this;
            }
            
            public Builder geometry(String path) {
                shaders.put("geometry", manager.loadShader(path));
                return this;
            }
            
            public Builder compute(String path) {
                shaders.put("compute", manager.loadShader(path));
                return this;
            }
            
            public Builder tessControl(String path) {
                shaders.put("tessControl", manager.loadShader(path));
                return this;
            }
            
            public Builder tessEvaluation(String path) {
                shaders.put("tessEvaluation", manager.loadShader(path));
                return this;
            }
            
            public Builder shader(String stage, String path) {
                shaders.put(stage, manager.loadShader(path));
                return this;
            }
            
            public ShaderSet build() {
                return new ShaderSet(shaders);
            }
        }
    }
    
    public static class Builder {
        private Arena arena;
        private MemorySegment device;
        
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device().handle();
            return this;
        }
        
        public VulkanShaderManager build() {
            if (arena == null) throw new IllegalStateException("arena not set");
            if (device == null) throw new IllegalStateException("device not set");
            return new VulkanShaderManager(arena, device);
        }
    }
}