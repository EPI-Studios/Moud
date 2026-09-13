#version 330 core
in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
flat in vec4 vParams;
flat in vec2 vExtra;
flat in vec4 vClip;
uniform sampler2D Tex;
uniform vec2 ScreenSize;
uniform float Time;
out vec4 FragColor;
#line 1
