#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 Uv;
layout(location = 2) in vec4 Tint;
layout(location = 3) in vec4 Light;

uniform mat4 ViewProj;

out vec2 vUv;
out vec4 vTint;
out vec4 vLight;

void main() {
    gl_Position = ViewProj * vec4(Position, 1.0);
    vUv = Uv;
    vTint = Tint;
    vLight = Light;
}
