package io.github.yetyman.vulkan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeLibraryLoader {
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final String ARCH = System.getProperty("os.arch").toLowerCase();
    
    public static void loadLibrary(String libName) {
        String resourcePath = getNativeResourcePath(libName);
        
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                // Fallback to system library
                System.loadLibrary(libName);
                return;
            }
            
            Path tempDir = Files.createTempDirectory("vulkanffm-natives");
            tempDir.toFile().deleteOnExit();
            
            Path tempLib = tempDir.resolve(getLibraryFileName(libName));
            tempLib.toFile().deleteOnExit();
            
            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            System.load(tempLib.toAbsolutePath().toString());
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library: " + libName, e);
        }
    }
    
    private static String getNativeResourcePath(String libName) {
        String platform = getPlatform();
        return "/natives/" + platform + "/" + getLibraryFileName(libName);
    }
    
    private static String getPlatform() {
        if (OS.contains("win")) return "windows";
        if (OS.contains("linux")) return "linux";
        if (OS.contains("mac")) return "macos";
        throw new UnsupportedOperationException("Unsupported OS: " + OS);
    }
    
    private static String getLibraryFileName(String libName) {
        if (OS.contains("win")) return libName + ".dll";
        if (OS.contains("linux")) return "lib" + libName + ".so";
        if (OS.contains("mac")) return "lib" + libName + ".dylib";
        throw new UnsupportedOperationException("Unsupported OS: " + OS);
    }
}
