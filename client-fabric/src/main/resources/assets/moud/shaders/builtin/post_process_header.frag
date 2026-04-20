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

struct MoudPointLight {
    vec3 position;
    vec3 color;
    float brightness;
    float radius;
};
struct MoudDirLight {
    vec3 direction;
    vec3 color;
    float brightness;
};
struct MoudSpotLight {
    vec3 position;
    vec3 direction;
    vec3 color;
    float brightness;
    float angle;
    float distance;
};

uniform int NumPointLights;
uniform MoudPointLight PointLights[16];
uniform int NumDirLights;
uniform MoudDirLight DirLights[4];
uniform int NumSpotLights;
uniform MoudSpotLight SpotLights[8];

uniform mat4 moud_viewProj;
uniform mat4 moud_invViewProj;
uniform vec3 moud_cameraPos;

#define vTexCoord texCoord
