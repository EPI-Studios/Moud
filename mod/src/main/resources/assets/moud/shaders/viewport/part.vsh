#version 330 core

layout(location = 0) in vec3 Position;

uniform mat4 ViewProj;
uniform mat4 Model;

out vec3 vWorld;

void main() {
    vec4 world = Model * vec4(Position, 1.0);
    vWorld = world.xyz;
    gl_Position = ViewProj * world;
}
