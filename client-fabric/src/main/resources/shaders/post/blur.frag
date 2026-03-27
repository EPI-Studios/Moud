#version 330 core
in vec2 vUv;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform vec2 uDirection;

out vec4 FragColor;

const float w0 = 0.227027;
const float w1 = 0.1945946;
const float w2 = 0.1216216;
const float w3 = 0.054054;
const float w4 = 0.016216;

void main() {
    vec2 off = uDirection * uTexelSize;
    vec4 c = texture(uTexture, vUv) * w0;
    c += texture(uTexture, vUv + off * 1.0) * w1;
    c += texture(uTexture, vUv - off * 1.0) * w1;
    c += texture(uTexture, vUv + off * 2.0) * w2;
    c += texture(uTexture, vUv - off * 2.0) * w2;
    c += texture(uTexture, vUv + off * 3.0) * w3;
    c += texture(uTexture, vUv - off * 3.0) * w3;
    c += texture(uTexture, vUv + off * 4.0) * w4;
    c += texture(uTexture, vUv - off * 4.0) * w4;
    FragColor = c;
}

