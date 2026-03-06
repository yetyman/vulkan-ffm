package io.github.yetyman.vulkan.shaders;

/**
 * Tracks a single push constant member value with a dirty flag.
 * Flushed via vkCmdPushConstants in ShaderInstance.flush().
 *
 * @param <T> the Java type of the value
 */
public class PushConstant<T> {
    private final String name;
    private final int offset;
    private final int size;
    private T pendingValue;
    private boolean dirty;

    PushConstant(String name, int offset, int size) {
        this.name = name;
        this.offset = offset;
        this.size = size;
    }

    /** Sets the value and marks this constant dirty. */
    public void set(T value) {
        this.pendingValue = value;
        this.dirty = true;
    }

    public String name() { return name; }
    public int offset() { return offset; }
    public int size() { return size; }
    public T pendingValue() { return pendingValue; }
    public boolean isDirty() { return dirty; }
    void clearDirty() { dirty = false; }
}
