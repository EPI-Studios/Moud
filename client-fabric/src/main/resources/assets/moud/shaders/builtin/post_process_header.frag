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

#define vTexCoord texCoord
