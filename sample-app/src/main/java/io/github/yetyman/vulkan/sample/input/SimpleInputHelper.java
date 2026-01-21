package io.github.yetyman.vulkan.sample.input;

import io.github.yetyman.glfw.enums.GLFWAction;
import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.input.events.KeyEvent;

/**
 * Simplified input helper for common key handling patterns in sample applications.
 * Reduces boilerplate for basic key press/release handling.
 */
public class SimpleInputHelper {
    private final InputManager inputManager;
    
    public SimpleInputHelper(InputManager inputManager) {
        this.inputManager = inputManager;
    }
    
    /**
     * Register a handler for key press events.
     * @param key GLFW key constant (e.g., GLFWKey.GLFW_KEY_SPACE)
     * @param handler Action to execute when key is pressed
     */
    public void onKeyPress(GLFWKey key, Runnable handler) {
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && 
                     ke.key() == key.value() && 
                     ke.action() == GLFWAction.GLFW_PRESS.value(),
            handler
        );
    }
    
    /**
     * Register a handler for key release events.
     * @param key GLFW key constant
     * @param handler Action to execute when key is released
     */
    public void onKeyRelease(GLFWKey key, Runnable handler) {
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && 
                     ke.key() == key.value() && 
                     ke.action() == GLFWAction.GLFW_RELEASE.value(),
            handler
        );
    }
    
    /**
     * Register a handler for key repeat events (held down).
     * @param key GLFW key constant
     * @param handler Action to execute when key repeats
     */
    public void onKeyRepeat(GLFWKey key, Runnable handler) {
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && 
                     ke.key() == key.value() && 
                     ke.action() == GLFWAction.GLFW_REPEAT.value(),
            handler
        );
    }
    
    /**
     * Register a handler for key hold events (press + repeat).
     * @param key GLFW key constant
     * @param handler Action to execute when key is held
     */
    public void onKeyHold(GLFWKey key, Runnable handler) {
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && 
                     ke.key() == key.value() && 
                     (ke.action() == GLFWAction.GLFW_PRESS.value() ||
                      ke.action() == GLFWAction.GLFW_REPEAT.value()),
            handler
        );
    }
}