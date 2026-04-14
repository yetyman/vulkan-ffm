#version 450

layout (push_constant) uniform PC {
    float windowW;
    float windowH;
} pc;

// 10 floats per square: x, y, r, g, b, radius, borderWidth, borderR, borderG, borderB
layout (set = 0, binding = 0) readonly buffer SquareData {
    float data[];
} squares;

layout (location = 0) out vec3 fragColor;
layout (location = 1) out vec2 fragLocalPx;
layout (location = 2) flat out float fragRadius;
layout (location = 3) flat out float fragBorderWidth;
layout (location = 4) flat out vec3 fragBorderColor;
layout (location = 5) flat out vec2 fragSquareOrigin;

const vec2 quadVerts[6] = vec2[](
vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

const float SIZE = 80.0;

const int SLICE_COL[9] = int[](0, 1, 2, 0, 1, 2, 0, 1, 2);
const int SLICE_ROW[9] = int[](0, 0, 0, 1, 1, 1, 2, 2, 2);

void main() {
    int squareIdx = gl_InstanceIndex / 9;
    int sliceIdx  = gl_InstanceIndex % 9;

    int base = squareIdx * 10;
    float sx  = squares.data[base + 0];
    float sy  = squares.data[base + 1];
    float sr  = squares.data[base + 2];
    float sg  = squares.data[base + 3];
    float sb  = squares.data[base + 4];
    float rad = squares.data[base + 5];
    float bw  = squares.data[base + 6];
    float br  = squares.data[base + 7];
    float bg  = squares.data[base + 8];
    float bb  = squares.data[base + 9];

    int col = SLICE_COL[sliceIdx];
    int row = SLICE_ROW[sliceIdx];

    float colX[3], colW[3];
    colX[0] = 0.0;        colW[0] = rad;
    colX[1] = rad;        colW[1] = SIZE - 2.0 * rad;
    colX[2] = SIZE - rad; colW[2] = rad;

    float rowY[3], rowH[3];
    rowY[0] = 0.0;        rowH[0] = rad;
    rowY[1] = rad;        rowH[1] = SIZE - 2.0 * rad;
    rowY[2] = SIZE - rad; rowH[2] = rad;

    vec2 localPos = quadVerts[gl_VertexIndex];
    float localX = colX[col] + localPos.x * colW[col];
    float localY = rowY[row] + localPos.y * rowH[row];

    // Snap square origin to integer pixels so local coords align with pixel grid
    float snappedSx = floor(sx);
    float snappedSy = floor(sy);

    vec2 pixelPos = vec2(snappedSx + localX, snappedSy + localY);
    vec2 ndc = (pixelPos / vec2(pc.windowW, pc.windowH)) * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);

    fragColor        = vec3(sr, sg, sb);
    fragLocalPx      = vec2(localX, localY);
    fragRadius       = rad;
    fragBorderWidth  = bw;
    fragBorderColor  = vec3(br, bg, bb);
    fragSquareOrigin = vec2(snappedSx, snappedSy);
}
