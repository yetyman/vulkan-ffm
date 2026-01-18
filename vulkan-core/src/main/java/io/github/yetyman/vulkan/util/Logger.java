package io.github.yetyman.vulkan.util;

import java.util.EnumSet;
import java.util.Set;

/**
 * Simple logging utility with configurable log levels
 */
public class Logger {
    
    public enum Level {
        ERROR,    // Errors (always enabled)
        WARN,     // Warnings
        INFO,     // General information
        DEBUG     // Detailed debug information
    }
    
    private static final Set<Level> enabledLevels = EnumSet.of(
        Level.ERROR,
        Level.WARN,
        Level.INFO
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
    
    public static void error(String message) { log(Level.ERROR, message); }
    public static void warn(String message) { log(Level.WARN, message); }
    public static void info(String message) { log(Level.INFO, message); }
    public static void debug(String message) { log(Level.DEBUG, message); }
}