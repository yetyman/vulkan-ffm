package io.github.yetyman.vulkan.highlevel;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.*;

/**
 * Fluent builder for shader loading with pluggable compilation strategies.
 */
public class ShaderLoader {
    private static Function<String, byte[]> defaultCompiler = ShaderLoader::glslcCompile;
    
    private String resourcePath;
    private Function<String, byte[]> compiler;
    private final Map<String, String> defines = new HashMap<>();
    private final List<String> includePaths = new ArrayList<>();
    private boolean optimize = false;
    
    private ShaderLoader(String resourcePath) {
        this.resourcePath = resourcePath;
        this.compiler = defaultCompiler;
    }
    
    public static ShaderLoader load(String resourcePath) {
        return new ShaderLoader(resourcePath);
    }
    
    public static void setDefaultCompiler(Function<String, byte[]> compiler) {
        defaultCompiler = compiler;
    }
    
    public ShaderLoader compiler(Function<String, byte[]> compiler) {
        this.compiler = compiler;
        return this;
    }
    
    public ShaderLoader define(String name, String value) {
        defines.put(name, value);
        return this;
    }
    
    public ShaderLoader includePath(String path) {
        includePaths.add(path);
        return this;
    }
    
    public ShaderLoader optimize() {
        this.optimize = true;
        return this;
    }
    
    public byte[] compile() {
        return compiler.apply(resourcePath);
    }
    
    public byte[] loadSpirV() {
        try (InputStream is = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new FileNotFoundException(resourcePath);
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SPIR-V: " + resourcePath, e);
        }
    }
    
    // Static convenience methods
    public static byte[] compileShader(String resourcePath) {
        return load(resourcePath).compile();
    }
    
    public static byte[] loadSpirV(String resourcePath) {
        return load(resourcePath).loadSpirV();
    }
    
    private static byte[] glslcCompile(String resourcePath) {
        try {
            String glslSource = loadResource(resourcePath);
            Path tempGlsl = Files.createTempFile("shader", getShaderExtension(resourcePath));
            Path tempSpv = Files.createTempFile("shader", ".spv");
            
            try {
                Files.writeString(tempGlsl, glslSource);
                ProcessBuilder pb = new ProcessBuilder("glslc", tempGlsl.toString(), "-o", tempSpv.toString());
                Process process = pb.start();
                
                if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    String error = new String(process.getErrorStream().readAllBytes());
                    throw new RuntimeException("Shader compilation failed: " + error);
                }
                
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