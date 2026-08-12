#version 450

// Vertex-pulling shader: reads all attributes from storage buffers, no vertex input at all.
// Demonstrates that shader-decoded attributes (formats without a VkFormat) are first-class.

// Storage buffers bound per attribute stream
layout (set = 0, binding = 0) readonly buffer PositionBuf {
    float positions[];    // F32x3, tightly packed
};
layout (set = 0, binding = 1) readonly buffer NormalBuf {
    // Oct16 encoded normals: 2 x int16 packed into a single uint per vertex.
    // This is a format with no VkFormat -- it CANNOT be a vertex input attribute.
    // It must be decoded manually in the shader.
    uint packedNormals[];
};
layout (set = 0, binding = 2) readonly buffer IndexBuf {
    uint indices[];
};

layout (push_constant) uniform PushConstants {
    mat4 mvp;
    uint vertexBase;
    uint indexBase;
} pc;

layout (location = 0) out vec3 fragNormal;

// Octahedral decoding: maps oct16 (2 x snorm16) to a unit vec3
vec3 octDecode(uint packed) {
    // Unpack two signed 16-bit values
    int ix = int(packed & 0xFFFFu);
    int iy = int((packed >> 16) & 0xFFFFu);
    // Sign-extend from 16-bit
    if (ix >= 0x8000) ix -= 0x10000;
    if (iy >= 0x8000) iy -= 0x10000;
    // Map to [-1, 1]
    float x = float(ix) / 32767.0;
    float y = float(iy) / 32767.0;
    // Reconstruct Z
    float z = 1.0 - abs(x) - abs(y);
    if (z < 0.0) {
        float oldX = x;
        x = (1.0 - abs(y)) * (x >= 0.0 ? 1.0 : -1.0);
        y = (1.0 - abs(oldX)) * (y >= 0.0 ? 1.0 : -1.0);
    }
    return normalize(vec3(x, y, z));
}

void main() {
    uint idx = indices[pc.indexBase + gl_VertexIndex] + pc.vertexBase;

    // Pull position (3 floats per vertex)
    vec3 pos = vec3(
        positions[idx * 3 + 0],
        positions[idx * 3 + 1],
        positions[idx * 3 + 2]
    );

    // Pull and decode oct16 normal
    vec3 normal = octDecode(packedNormals[idx]);

    gl_Position = pc.mvp * vec4(pos, 1.0);
    fragNormal = normalize((pc.mvp * vec4(normal, 0.0)).xyz);
}
