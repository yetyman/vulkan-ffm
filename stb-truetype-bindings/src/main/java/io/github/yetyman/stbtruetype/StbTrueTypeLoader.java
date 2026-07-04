package io.github.yetyman.stbtruetype;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts and loads the bundled stb-truetype native library from classpath resources,
 * mirroring SpirvReflectLoader/NativeLibraryLoader in the other bindings modules. Must be
 * called (or triggered via a caller's static initializer) before any StbTrueTypeFFM method
 * is invoked, since the generated class's SymbolLookup chain falls back to
 * Linker.nativeLinker().defaultLookup() - which only finds libraries already loaded into the
 * process via System.load()/System.loadLibrary(), not ones sitting unpacked in a temp dir.
 */
public class StbTrueTypeLoader {
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;

        String libName = getLibraryName(System.getProperty("os.name").toLowerCase());
        String resourcePath = "/natives/" + libName;

        try (InputStream in = StbTrueTypeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.loadLibrary("stb-truetype");
                loaded = true;
                return;
            }

            Path tempDir = Files.createTempDirectory("stb-truetype-natives");
            Path tempLib = tempDir.resolve(libName);

            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            System.load(tempLib.toAbsolutePath().toString());
            loaded = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load stb-truetype library", e);
        }
    }

    private static String getLibraryName(String os) {
        if (os.contains("win")) return "stb-truetype.dll";
        if (os.contains("linux")) return "libstb-truetype.so";
        if (os.contains("mac")) return "libstb-truetype.dylib";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
}
