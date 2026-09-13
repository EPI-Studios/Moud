#version 330 core

in vec2 vUV;
out vec4 FragColor;

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform vec2 ScreenSize;
uniform float Time;
uniform float Intensity;
uniform vec3 CameraPosition;
uniform vec3 PrevCameraPosition;
uniform mat4 ViewProj;
uniform mat4 InvViewProj;
uniform mat4 PrevViewProj;
uniform int ZeroToOne;

vec4 sceneColor(vec2 uv) {
    return texture(SceneColorSampler, uv);
}

float sceneDepth(vec2 uv) {
    return texture(SceneDepthSampler, uv).r;
}

bool isSky(vec2 uv) {
    return sceneDepth(uv) >= 0.99999;
}

vec3 viewPosition(vec2 uv) {
    float d = sceneDepth(uv);
    float z = ZeroToOne == 1 ? d : d * 2.0 - 1.0;
    vec4 world = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return world.xyz / world.w;
}

vec3 worldPosition(vec2 uv) {
    return viewPosition(uv) + CameraPosition;
}

float linearDepth(vec2 uv) {
    return isSky(uv) ? 10000.0 : length(viewPosition(uv));
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

#line 1
