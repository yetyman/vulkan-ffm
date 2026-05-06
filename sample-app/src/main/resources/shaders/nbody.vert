#version 450

struct Particle {
    vec2 pos;
    vec2 vel;
};

layout (set = 0, binding = 0) readonly buffer Particles { Particle p[]; } buf;

layout (location = 0) out float speed;

void main() {
    Particle particle = buf.p[gl_VertexIndex];
    gl_Position = vec4(particle.pos, 0.0, 1.0);
    gl_PointSize = 2.0;
    speed = length(particle.vel);
}
