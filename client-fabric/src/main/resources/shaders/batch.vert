#version 330 core
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec4 aFillColor;
layout(location = 3) in vec4 aStrokeColor;
layout(location = 4) in vec4 aParams; // size.x, size.y, radiusPx, borderPx
layout(location = 5) in vec4 aGradColor;  // gradient color B
layout(location = 6) in vec4 aGradParams; // mode, p1, p2, p3
layout(location = 7) in vec4 aExtra; // shape-specific extra params

uniform mat4 uProjection;

out vec2 vUv;
out vec4 vFillColor;
out vec4 vStrokeColor;
out vec4 vParams;
out vec4 vGradColor;
out vec4 vGradParams;
out vec4 vExtra;

void main() {
    vUv = aUv;
    vFillColor = aFillColor;
    vStrokeColor = aStrokeColor;
    vParams = aParams;
    vGradColor = aGradColor;
    vGradParams = aGradParams;
    vExtra = aExtra;
    gl_Position = uProjection * vec4(aPos.xy, 0.0, 1.0);
}

