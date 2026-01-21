package io.github.yetyman.vulkan.sample.complex.models.decimation;

public record SimplifiedMesh(float[] vertices, int[] indices) {
    public int vertexCount() {
        return vertices.length / 8;
    }
    
    public int triangleCount() {
        return indices.length / 3;
    }
}
