#version 450

layout (push_constant) uniform PC {
    float windowW;
    float windowH;
} pc;

// 6 floats per square: x, y, r, g, b, radius
layout (set = 0, binding = 0) readonly buffer SquareData {
    float data[];
} squares;

layout (location = 0) out vec3 fragColor;
layout (location = 1) out vec2 fragUV;
layout (location = 2) flat out int fragSliceType;
layout (location = 3) out float fragRadius;

const vec2 quadVerts[6] = vec2[](
vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

const float SIZE = 80.0;

const int SLICE_COL[9] = int[](0, 1, 2, 0, 1, 2, 0, 1, 2);
const int SLICE_ROW[9] = int[](0, 0, 0, 1, 1, 1, 2, 2, 2);

void main() {
    int squareIdx = gl_InstanceIndex / 9;
    int sliceIdx = gl_InstanceIndex % 9;

    int base = squareIdx * 6;
    float sx = squares.data[base + 0];
    float sy = squares.data[base + 1];
    float sr = squares.data[base + 2];
    float sg = squares.data[base + 3];
    float sb = squares.data[base + 4];
    float rad = squares.data[base + 5];

    int col = SLICE_COL[sliceIdx];
    int row = SLICE_ROW[sliceIdx];

    float colX[3], colW[3];
    colX[0] = 0.0;             colW[0] = rad;
    colX[1] = rad;             colW[1] = SIZE - 2.0 * rad;
    colX[2] = SIZE - rad;      colW[2] = rad;

    float rowY[3], rowH[3];
    rowY[0] = 0.0;             rowH[0] = rad;
    rowY[1] = rad;             rowH[1] = SIZE - 2.0 * rad;
    rowY[2] = SIZE - rad;      rowH[2] = rad;

    vec2 localPos = quadVerts[gl_VertexIndex];
    vec2 pixelPos = vec2(sx + colX[col] + localPos.x * colW[col],
    sy + rowY[row] + localPos.y * rowH[row]);

    vec2 ndc = (pixelPos / vec2(pc.windowW, pc.windowH)) * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);

    fragColor = vec3(sr, sg, sb);
    fragUV = localPos;
    fragRadius = rad;

    bool isCorner = (col == 0 || col == 2) && (row == 0 || row == 2);
    if (!isCorner) {
        fragSliceType = 0;
    } else if (col == 0 && row == 0) {
        fragSliceType = 1;
    } else if (col == 2 && row == 0) {
        fragSliceType = 2;
    } else if (col == 0 && row == 2) {
        fragSliceType = 3;
    } else {
        fragSliceType = 4;
    }
}
