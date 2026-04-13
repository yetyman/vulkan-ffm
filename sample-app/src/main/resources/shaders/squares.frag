#version 450

layout (location = 0) in vec3 fragColor;
layout (location = 1) in vec2 fragUV;
// 0=non-corner, 1=TL, 2=TR, 3=BL, 4=BR
layout (location = 2) flat in int fragSliceType;
layout (location = 3) in float fragRadius;

layout (location = 0) out vec4 outColor;

void main() {
    float alpha = 1.0;

    if (fragSliceType != 0 && fragRadius > 0.0) {
        // Arc center in UV space: the inner corner of the quad (toward square center).
        // TL quad: outer corner=UV(0,0), arc center=UV(1,1)
        // TR quad: outer corner=UV(1,0), arc center=UV(0,1)
        // BL quad: outer corner=UV(0,1), arc center=UV(1,0)
        // BR quad: outer corner=UV(1,1), arc center=UV(0,0)
        vec2 arcCenter;
        if (fragSliceType == 1) arcCenter = vec2(1.0, 1.0); // TL
        else if (fragSliceType == 2) arcCenter = vec2(0.0, 1.0); // TR
        else if (fragSliceType == 3) arcCenter = vec2(1.0, 0.0); // BL
        else arcCenter = vec2(0.0, 0.0); // BR

        // Distance from arc center in pixel space (UV * radius = pixel offset within quad)
        float dist = length((fragUV - arcCenter) * fragRadius);

        // Smooth discard outside the arc radius
        alpha = 1.0 - smoothstep(fragRadius - 1.0, fragRadius, dist);
        if (alpha <= 0.0) discard;
    }

    outColor = vec4(fragColor, alpha);
}
