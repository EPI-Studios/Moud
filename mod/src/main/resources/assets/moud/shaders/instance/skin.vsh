#version 330 core

layout(location = 0) in vec3 Position;

layout(location = 1) in vec4 InstTransform0;
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;

layout(location = 5) in vec4 InstColor;
layout(location = 6) in vec2 InstLight;

// where this box sits on the skin, and how big it is there. u, v, width, height in texels
layout(location = 7) in vec4 InstUv;
// depth in texels, and whether this is the shell over a part rather than the part
layout(location = 8) in vec2 InstBox;

uniform mat4 ProjViewMatrix;
uniform vec3 CameraPos;
uniform int WorldSpace;

// the layout below is stated in texels of a 64x64 skin and only makes sense against one, so this
// is a constant rather than a uniform. it was a uniform, and amnetic has no way to feed one: it
// stayed zero, every texel size divided by it, and the whole body sampled at infinity and
// discarded -- a skin that is packed, counted, and never drawn
const vec2 SkinSize = vec2(64.0, 64.0);

out vec4 vColor;
out vec3 vPos;
out vec2 vLight;
out vec2 vUv;
out float vShell;

// the unit cube is 24 vertices in six quads and carries neither normals nor texture coordinates,
// so the face is the quad this vertex belongs to. the order is the one MeshData.unitCube builds:
// -z, +z, -x, +x, -y, +y
//
// a skin unwraps a box the same way every time: the top and bottom sit above a row of four sides,
// each rect offset by the depth and width of the box in texels
//
//        u+d      u+d+w
//         +--------+--------+
//         |  top   | bottom |
//  +------+--------+--------+--------+
//  | right| front  |  left  |  back  |
//  +------+--------+--------+--------+
//  u     u+d     u+d+w   u+2d+w   u+2d+2w
vec4 faceRect(int face, vec2 origin, float w, float h, float d) {
    if (face == 0) return vec4(origin.x + d,             origin.y + d, w, h);  // front, -z
    if (face == 1) return vec4(origin.x + d + w + d,     origin.y + d, w, h);  // back, +z
    if (face == 2) return vec4(origin.x + d + w,         origin.y + d, d, h);  // left, -x
    if (face == 3) return vec4(origin.x,                 origin.y + d, d, h);  // right, +x
    if (face == 4) return vec4(origin.x + d + w,         origin.y,     w, d);  // bottom, -y
    return vec4(origin.x + d, origin.y, w, d);                                 // top, +y
}

// where this corner sits inside its face, left to right and top to bottom the way a texture reads
vec2 faceCoord(int face, vec3 p) {
    if (face == 0) return vec2(0.5 - p.x, 0.5 - p.y);
    if (face == 1) return vec2(p.x + 0.5, 0.5 - p.y);
    if (face == 2) return vec2(0.5 - p.z, 0.5 - p.y);
    if (face == 3) return vec2(p.z + 0.5, 0.5 - p.y);
    if (face == 4) return vec2(p.x + 0.5, 0.5 - p.z);
    return vec2(p.x + 0.5, p.z + 0.5);
}

void main() {
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    if (WorldSpace == 1) {
        model[3].xyz -= CameraPos;
    }
    vec4 pos = model * vec4(Position, 1.0);
    gl_Position = ProjViewMatrix * pos;

    int face = gl_VertexID / 4;
    vec4 rect = faceRect(face, InstUv.xy, InstUv.z, InstUv.w, InstBox.x);
    vec2 within = faceCoord(face, Position);

    // half a texel in from every edge, so a face never bleeds into the one packed beside it
    vec2 texel = vec2(1.0) / SkinSize;
    vec2 uv = (rect.xy + within * rect.zw) * texel;
    vUv = clamp(uv, (rect.xy + 0.03) * texel, (rect.xy + rect.zw - 0.03) * texel);

    vColor = InstColor;
    vPos = pos.xyz;
    vLight = InstLight;
    vShell = InstBox.y;
}
