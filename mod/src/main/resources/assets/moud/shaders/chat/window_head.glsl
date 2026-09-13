#version 330 core
in vec2 vUV;
out vec4 FragColor;
uniform sampler2D SceneColorSampler;
uniform vec2 ScreenSize;
uniform vec4 Rect;
uniform float Time;
vec4 scene(vec2 uv) { return texture(SceneColorSampler, uv); }
#line 1
