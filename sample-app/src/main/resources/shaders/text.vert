#version 450

// Per-glyph instanced quad. One instance per glyph; 4 vertices per quad via gl_VertexIndex,
// positions/UVs computed from instance data rather than a vertex buffer.

layout (push_constant) uniform PushConstants {
    vec2 screenSize; // viewport size in pixels, for pixel-to-NDC conversion
} pc;

struct GlyphInstance {
    vec2 posMin;   // top-left screen position in pixels
    vec2 posMax;   // bottom-right screen position in pixels
    vec2 uvMin;
    vec2 uvMax;
    vec4 color;
};

layout (std430, set = 0, binding = 0) readonly buffer GlyphInstances {
    GlyphInstance glyphs[];
};

layout (location = 0) out vec2 fragUV;
layout (location = 1) out vec4 fragColor;

void main() {
    GlyphInstance g = glyphs[gl_InstanceIndex];

    // gl_VertexIndex in [0,3] traces a quad: 0=TL, 1=TR, 2=BL, 3=BR (triangle-strip topology)
    vec2 corner = vec2(
        (gl_VertexIndex == 1 || gl_VertexIndex == 3) ? g.posMax.x : g.posMin.x,
        (gl_VertexIndex >= 2) ? g.posMax.y : g.posMin.y
    );
    vec2 uv = vec2(
        (gl_VertexIndex == 1 || gl_VertexIndex == 3) ? g.uvMax.x : g.uvMin.x,
        (gl_VertexIndex >= 2) ? g.uvMax.y : g.uvMin.y
    );

    vec2 ndc = (corner / pc.screenSize) * 2.0 - 1.0;
    gl_Position = vec4(ndc, 0.0, 1.0);
    fragUV = uv;
    fragColor = g.color;
}
