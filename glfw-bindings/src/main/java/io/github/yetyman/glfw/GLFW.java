package io.github.yetyman.glfw;

import io.github.yetyman.glfw.generated.GLFWFFM;
import java.lang.foreign.*;

public class GLFW {
    static {
        GLFWLoader.load();
    }
    
    // Constants
    public static final int GLFW_TRUE = GLFWFFM.GLFW_TRUE();
    public static final int GLFW_FALSE = GLFWFFM.GLFW_FALSE();
    public static final int GLFW_CLIENT_API = GLFWFFM.GLFW_CLIENT_API();
    public static final int GLFW_NO_API = GLFWFFM.GLFW_NO_API();
    public static final int GLFW_RESIZABLE = GLFWFFM.GLFW_RESIZABLE();
    
    // Core functions
    public static boolean glfwInit() {
        return GLFWFFM.glfwInit() == GLFW_TRUE;
    }
    
    public static void glfwTerminate() {
        GLFWFFM.glfwTerminate();
    }
    
    public static void glfwWindowHint(int hint, int value) {
        GLFWFFM.glfwWindowHint(hint, value);
    }
    
    public static MemorySegment glfwCreateWindow(int width, int height, String title) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment titleSeg = arena.allocateFrom(title);
            return GLFWFFM.glfwCreateWindow(width, height, titleSeg, MemorySegment.NULL, MemorySegment.NULL);
        }
    }
    
    public static void glfwDestroyWindow(MemorySegment window) {
        GLFWFFM.glfwDestroyWindow(window);
    }
    
    public static boolean glfwWindowShouldClose(MemorySegment window) {
        return GLFWFFM.glfwWindowShouldClose(window) == GLFW_TRUE;
    }
    
    public static void glfwPollEvents() {
        GLFWFFM.glfwPollEvents();
    }
    
    public static String[] glfwGetRequiredInstanceExtensions(Arena arena) {
        MemorySegment countSeg = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment extensionsPtr = GLFWFFM.glfwGetRequiredInstanceExtensions(countSeg);
        
        if (extensionsPtr.equals(MemorySegment.NULL)) {
            return null;
        }
        
        int count = countSeg.get(ValueLayout.JAVA_INT, 0);
        String[] result = new String[count];
        
        MemorySegment extensions = extensionsPtr.reinterpret(ValueLayout.ADDRESS.byteSize() * count);
        
        for (int i = 0; i < count; i++) {
            MemorySegment strPtr = extensions.getAtIndex(ValueLayout.ADDRESS, i);
            result[i] = strPtr.reinterpret(Long.MAX_VALUE).getString(0);
        }
        
        return result;
    }
    
    public static long glfwGetWin32Window(MemorySegment window) {
        return GLFWNativeWin32.glfwGetWin32Window(window);
    }
}
