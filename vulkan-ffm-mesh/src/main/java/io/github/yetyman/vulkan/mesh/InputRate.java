package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.enums.VkVertexInputRate;

/**
 * Whether a stream advances per vertex or per instance.
 */
public enum InputRate {
    /** One element per vertex. */
    VERTEX(VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX.value()),
    /** One element per instance. */
    INSTANCE(VkVertexInputRate.VK_VERTEX_INPUT_RATE_INSTANCE.value());

    private final int vkValue;

    InputRate(int vkValue) {
        this.vkValue = vkValue;
    }

    /**
     * @return the corresponding {@code VkVertexInputRate} value
     */
    public int vkValue() {
        return vkValue;
    }
}
