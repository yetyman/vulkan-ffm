package io.github.yetyman.spirv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class SpirvReflectLoader {
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;

        String libName = getLibraryName(System.getProperty("os.name").toLowerCase());
        String resourcePath = "/natives/" + libName;

        try (InputStream in = SpirvReflectLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.loadLibrary("spirv-reflect");
                loaded = true;
                return;
            }

            Path tempDir = Files.createTempDirectory("spirv-reflect-natives");
            Path tempLib = tempDir.resolve(libName);

            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            System.load(tempLib.toAbsolutePath().toString());
            loaded = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load SPIRV-Reflect library", e);
        }
    }

    private static String getLibraryName(String os) {
        if (os.contains("win")) return "spirv-reflect.dll";
        if (os.contains("linux")) return "libspirv-reflect.so";
        if (os.contains("mac")) return "libspirv-reflect.dylib";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
}
