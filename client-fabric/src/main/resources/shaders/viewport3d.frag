#version 330 core

in vec3 vColor;

out vec4 FragColor;

uniform float uAlpha;
uniform float uColorMul;

void main() {
    FragColor = vec4(vColor * uColorMul, uAlpha);
}

