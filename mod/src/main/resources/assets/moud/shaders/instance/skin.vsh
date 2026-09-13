#version 330 core

layout(location = 0) in vec3 Position;

layout(location = 1) in vec4 InstTransform0;
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;

layout(location = 5) in vec4 InstColor;
layout(location = 6) in vec2 InstLight;

layout(location = 7) in vec4 InstUv;
layout(location = 8) in vec2 InstBox;
layout(location = 9) in vec2 InstOverlay;

uniform mat4 ProjViewMatrix;
uniform vec3 CameraPos;
uniform int WorldSpace;

layout(location = 10) in vec4 InstSheet;

out vec4 vColor;
out vec3 vPos;
out vec2 vLight;
out vec2 vUv;
out float vCutout;
out vec2 vOverlay;
out vec3 vNormal;

vec4 faceRect(int face, vec2 origin, float w, float h, float d) {
    if (face == 0) return vec4(origin.x + d,             origin.y + d, w, h);
    if (face == 1) return vec4(origin.x + d + w + d,     origin.y + d, w, h);
    if (face == 2) return vec4(origin.x + d + w,         origin.y + d, d, h);
    if (face == 3) return vec4(origin.x,                 origin.y + d, d, h);
    if (face == 4) return vec4(origin.x + d + w,         origin.y,     w, d);
    return vec4(origin.x + d, origin.y, w, d);
}

vec3 faceNormal(int face) {
    if (face == 0) return vec3(0.0, 0.0, -1.0);
    if (face == 1) return vec3(0.0, 0.0, 1.0);
    if (face == 2) return vec3(-1.0, 0.0, 0.0);
    if (face == 3) return vec3(1.0, 0.0, 0.0);
    if (face == 4) return vec3(0.0, -1.0, 0.0);
    return vec3(0.0, 1.0, 0.0);
}

vec2 faceCoord(int face, vec3 p) {
    if (face == 0) return vec2(0.5 - p.x, 0.5 - p.y);
    if (face == 1) return vec2(p.x + 0.5, 0.5 - p.y);
    if (face == 2) return vec2(p.z + 0.5, 0.5 - p.y);
    if (face == 3) return vec2(0.5 - p.z, 0.5 - p.y);
    if (face == 4) return vec2(0.5 - p.x, 0.5 - p.z);
    return vec2(0.5 - p.x, 0.5 - p.z);
}

void main() {
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    if (WorldSpace == 1) {
        model[3].xyz -= CameraPos;
    }
    vec4 pos = model * vec4(Position, 1.0);
    gl_Position = ProjViewMatrix * pos;

    int face = gl_VertexID / 4;

    int rect_face = face;
    vec3 corner = Position;
    if (InstSheet.z > 0.5) {
        if (face == 2) rect_face = 3;
        else if (face == 3) rect_face = 2;
        corner.x = -corner.x;
    }

    vec4 rect = faceRect(rect_face, InstUv.xy, InstUv.z, InstUv.w, InstBox.x);
    vec2 within = faceCoord(rect_face, corner);

    vec2 texel = vec2(1.0) / InstSheet.xy;
    vec2 uv = (rect.xy + within * rect.zw) * texel;
    vUv = clamp(uv, (rect.xy + 0.03) * texel, (rect.xy + rect.zw - 0.03) * texel);

    vNormal = normalize(mat3(model) * faceNormal(rect_face));

    vColor = InstColor;
    vPos = pos.xyz;
    vLight = InstLight;
    vCutout = InstBox.y;
    vOverlay = InstOverlay;
}
