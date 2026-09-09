#version 330 core

layout(location = 0) in vec3 Position;

layout(location = 1) in vec4 InstTransform0;
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;

layout(location = 5) in vec4 InstColor;
layout(location = 6) in vec2 InstLight;

uniform mat4 ProjViewMatrix;
uniform vec3 CameraPos;
uniform int WorldSpace;

out vec4 vColor;
out vec3 vPos;
out vec2 vLight;

void main() {
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    if (WorldSpace == 1) {
        model[3].xyz -= CameraPos;
    }
    vec4 pos = model * vec4(Position, 1.0);
    gl_Position = ProjViewMatrix * pos;
    vColor = InstColor;
    vPos = pos.xyz;
    vLight = InstLight;
}
