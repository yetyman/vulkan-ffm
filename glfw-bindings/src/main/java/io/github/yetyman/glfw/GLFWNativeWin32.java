package io.github.yetyman.glfw;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class GLFWNativeWin32 {
    private static final MethodHandle glfwGetWin32Window_handle;
    
    static {
        try {
            SymbolLookup lookup = SymbolLookup.libraryLookup(System.mapLibraryName("glfw3"), Arena.ofAuto());
            MemorySegment symbol = lookup.find("glfwGetWin32Window").orElseThrow();
            
            glfwGetWin32Window_handle = Linker.nativeLinker().downcallHandle(
                symbol,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to load GLFW Win32 functions", e);
        }
    }
    
    public static long glfwGetWin32Window(MemorySegment window) {
        try {
            MemorySegment hwnd = (MemorySegment) glfwGetWin32Window_handle.invoke(window);
            return hwnd.address();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get Win32 window handle", e);
        }
    }
}
