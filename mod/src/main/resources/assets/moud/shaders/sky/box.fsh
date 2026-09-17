#version 330 core

uniform sampler2D Sky;
uniform vec4 Tint;

in vec2 vUv;

out vec4 FragColor;

void main() {
    vec4 texel = texture(Sky, vUv) * Tint;
    if (texel.a < 0.01) discard;
    FragColor = texel;
}
