package io.github.yetyman.vulkan;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class ShaderLoader {
    
    public static byte[] compileShader(String resourcePath) {
        try {
            // Load GLSL source
            String glslSource = loadResource(resourcePath);
            
            // Create temp files
            Path tempGlsl = Files.createTempFile("shader", getShaderExtension(resourcePath));
            Path tempSpv = Files.createTempFile("shader", ".spv");
            
            try {
                // Write GLSL to temp file
                Files.writeString(tempGlsl, glslSource);
                
                // Compile with glslc
                ProcessBuilder pb = new ProcessBuilder("glslc", tempGlsl.toString(), "-o", tempSpv.toString());
                Process process = pb.start();
                
                if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    String error = new String(process.getErrorStream().readAllBytes());
                    throw new RuntimeException("Shader compilation failed: " + error);
                }
                
                // Read compiled SPIR-V
                return Files.readAllBytes(tempSpv);
                
            } finally {
                Files.deleteIfExists(tempGlsl);
                Files.deleteIfExists(tempSpv);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile shader: " + resourcePath, e);
        }
    }
    
    private static String getShaderExtension(String path) {
        if (path.endsWith(".vert")) return ".vert";
        if (path.endsWith(".frag")) return ".frag";
        if (path.endsWith(".comp")) return ".comp";
        if (path.endsWith(".geom")) return ".geom";
        if (path.endsWith(".tesc")) return ".tesc";
        if (path.endsWith(".tese")) return ".tese";
        return ".glsl";
    }
    
    private static String loadResource(String path) throws IOException {
        try (InputStream is = ShaderLoader.class.getResourceAsStream(path)) {
            if (is == null) throw new FileNotFoundException(path);
            return new String(is.readAllBytes());
        }
    }
}