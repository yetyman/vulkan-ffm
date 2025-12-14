package io.github.yetyman.glfw;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class GLFWLoader {
    private static boolean loaded = false;
    
    public static synchronized void load() {
        if (loaded) return;
        
        String os = System.getProperty("os.name").toLowerCase();
        String libName = getLibraryName(os);
        String resourcePath = "/natives/" + getPlatform(os) + "/" + libName;
        
        try (InputStream in = GLFWLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.loadLibrary("glfw3");
                loaded = true;
                return;
            }
            
            Path tempDir = Files.createTempDirectory("glfw-natives");
            Path tempLib = tempDir.resolve(libName);
            
            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            System.load(tempLib.toAbsolutePath().toString());
            loaded = true;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GLFW library", e);
        }
    }
    
    private static String getPlatform(String os) {
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        if (os.contains("mac")) return "macos";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
    
    private static String getLibraryName(String os) {
        if (os.contains("win")) return "glfw3.dll";
        if (os.contains("linux")) return "libglfw.so.3";
        if (os.contains("mac")) return "libglfw.3.dylib";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
}
