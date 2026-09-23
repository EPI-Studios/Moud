#version 330 core

uniform sampler2D Panorama;
uniform mat4 Turn;

in vec3 vDirection;

out vec4 FragColor;

const float PI = 3.14159265359;

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// the middle of the panorama looks towards -z and its top row straight up, the same way the
// renderer's sky probe reads it
void main() {
    vec3 d = normalize(mat3(Turn) * vDirection);
    vec2 uv = vec2(0.5 + atan(d.x, -d.z) / (2.0 * PI), acos(clamp(d.y, -1.0, 1.0)) / PI);
    vec3 hdr = textureLod(Panorama, uv, 0.0).rgb;
    FragColor = vec4(pow(aces(hdr), vec3(1.0 / 2.2)), 1.0);
}
