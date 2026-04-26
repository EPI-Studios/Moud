#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D screen;
uniform sampler2D depth;
uniform vec2 Resolution;
uniform vec2 TexelSize;
uniform float AspectRatio;
uniform float Time;
uniform float DeltaTime;
uniform int DepthAvailable;

struct PointLight {
    vec3 position;
    vec3 color;
    float brightness;
    float radius;
};
struct DirLight {
    vec3 direction;
    vec3 color;
    float brightness;
};
struct SpotLight {
    vec3 position;
    vec3 direction;
    vec3 color;
    float brightness;
    float angle;
    float distance;
};

uniform int NumPointLights;
uniform PointLight PointLights[16];
uniform int NumDirLights;
uniform DirLight DirLights[4];
uniform int NumSpotLights;
uniform SpotLight SpotLights[8];

uniform mat4 moud_viewProj;
uniform mat4 moud_invViewProj;
uniform mat4 moud_invProj;
uniform mat4 moud_invView;
uniform vec3 moud_cameraPos;

#define vTexCoord texCoord
