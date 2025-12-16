package io.github.yetyman.vulkan.sample.complex.models;

/**
 * Represents an instance to be rendered with its distance for sorting
 */
record RenderInstance(int instanceId, float distance) {
}