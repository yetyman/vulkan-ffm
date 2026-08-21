#version 450

// Vertex-pulling quad grid shader.
// Reads vertex positions from an SSBO. Each quad has 4 unique vertices (TL, TR, BL, BR).
// Grid dimensions passed via push constants for coloring.

layout (push_constant) uniform PushConstants {
    vec2 gridOffset;  // NDC offset for this grid column
    vec2 gridScale;   // scale factor to map grid coords [0,1] to NDC
    int quadCols;     // number of quad columns
    int quadRows;     // number of quad rows
} pc;

layout (std430, set = 0, binding = 0) readonly buffer VertexBuffer {
    vec2 positions[];
} vertices;

layout (location = 0) out vec4 fragColor;

void main() {
    vec2 pos = vertices.positions[gl_VertexIndex];

    // Map grid-local position to NDC
    vec2 ndcPos = pos * pc.gridScale + pc.gridOffset;
    gl_Position = vec4(ndcPos, 0.0, 1.0);

    // Color from quad position: derive row/col from vertex index
    // 4 vertices per quad: vertexIndex / 4 = quad index
    int quadIndex = gl_VertexIndex / 4;
    int quadCol = quadIndex % pc.quadCols;
    int quadRow = quadIndex / pc.quadCols;
    float col = float(quadCol) / float(pc.quadCols - 1);
    float row = float(quadRow) / float(pc.quadRows - 1);
    fragColor = vec4(col, row, 0.5, 1.0);
}
