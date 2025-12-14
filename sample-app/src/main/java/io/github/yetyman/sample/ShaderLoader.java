package io.github.yetyman.sample;

import java.io.*;
import java.nio.file.*;

public class ShaderLoader {
    
    public static byte[] compileShader(String resourcePath) {
        String spvPath = resourcePath + ".spv";
        try (InputStream is = ShaderLoader.class.getResourceAsStream(spvPath)) {
            if (is == null) throw new FileNotFoundException(spvPath);
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load shader: " + spvPath, e);
        }
    }
    
    private static String loadResource(String path) throws IOException {
        try (InputStream is = ShaderLoader.class.getResourceAsStream(path)) {
            if (is == null) throw new FileNotFoundException(path);
            return new String(is.readAllBytes());
        }
    }
}
