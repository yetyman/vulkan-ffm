package io.github.yetyman.vulkan.util;

import java.util.EnumSet;
import java.util.Set;

/**
 * Simple logging utility with configurable log levels
 */
public class Logger {
    
    public enum Level {
        DEBUG,    // Detailed debug information
        INFO,     // General information
        WORK,     // Work queue and threading info
        LOD,      // LOD rendering details
        RENDER,   // Rendering operations
        MATRIX,   // Matrix calculations
        DRAW,     // Draw calls
        INPUT,    // Input events
        AA,       // Anti-aliasing
        LOAD,     // Model loading
        ERROR     // Errors (always enabled)
    }
    
    private static final Set<Level> enabledLevels = EnumSet.of(
        Level.INFO,   // Keep general info
        Level.ERROR   // Always show errors
    );
    
    public static void enable(Level... levels) {
        for (Level level : levels) {
            enabledLevels.add(level);
        }
    }
    
    public static void disable(Level... levels) {
        for (Level level : levels) {
            if (level != Level.ERROR) { // Never disable errors
                enabledLevels.remove(level);
            }
        }
    }
    
    public static void setLevels(Level... levels) {
        enabledLevels.clear();
        enabledLevels.add(Level.ERROR); // Always include errors
        for (Level level : levels) {
            enabledLevels.add(level);
        }
    }
    
    public static boolean isEnabled(Level level) {
        return enabledLevels.contains(level);
    }
    
    public static void log(Level level, String message) {
        if (isEnabled(level)) {
            System.out.println("[" + level.name() + "] " + message);
        }
    }
    
    public static void debug(String message) { log(Level.DEBUG, message); }
    public static void info(String message) { log(Level.INFO, message); }
    public static void work(String message) { log(Level.WORK, message); }
    public static void lod(String message) { log(Level.LOD, message); }
    public static void render(String message) { log(Level.RENDER, message); }
    public static void matrix(String message) { log(Level.MATRIX, message); }
    public static void draw(String message) { log(Level.DRAW, message); }
    public static void input(String message) { log(Level.INPUT, message); }
    public static void aa(String message) { log(Level.AA, message); }
    public static void load(String message) { log(Level.LOAD, message); }
    public static void error(String message) { log(Level.ERROR, message); }
}