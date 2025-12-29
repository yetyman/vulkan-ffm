// Deprecated: Use io.github.yetyman.vulkan.highlevel.ShaderLoader
package io.github.yetyman.vulkan;

@Deprecated(forRemoval = true)
public class ShaderLoader {
    @Deprecated(forRemoval = true)
    public static byte[] compileShader(String resourcePath) {
        return io.github.yetyman.vulkan.highlevel.ShaderLoader.compileShader(resourcePath);
    }
}