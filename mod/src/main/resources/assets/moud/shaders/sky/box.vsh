#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 Uv;

uniform mat4 ViewProj;

out vec2 vUv;

void main() {
    vec4 clip = ViewProj * vec4(Position, 1.0);
    gl_Position = clip.xyww;
    vUv = Uv;
}
