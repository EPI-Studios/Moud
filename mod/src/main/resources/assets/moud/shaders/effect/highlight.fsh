#version 330 core

const int MAXIMUM_RADIUS = 4;

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;
uniform vec2 Texel;
uniform float Radius;
uniform vec4 FillColor;
uniform vec4 OutlineColor;

in vec2 vUV;
out vec4 FragColor;

void main() {
    vec3 scene = texture(SceneSampler, vUV).rgb;
    if (texture(MaskSampler, vUV).r > 0.5) {
        FragColor = vec4(mix(scene, FillColor.rgb, FillColor.a), 1.0);
        return;
    }
    float squaredRadius = Radius * Radius;
    float edge = 0.0;
    for (int y = -MAXIMUM_RADIUS; y <= MAXIMUM_RADIUS; y++) {
        for (int x = -MAXIMUM_RADIUS; x <= MAXIMUM_RADIUS; x++) {
            if (float(x * x + y * y) > squaredRadius) continue;
            edge = max(edge, texture(MaskSampler, vUV + vec2(float(x), float(y)) * Texel).r);
        }
    }
    FragColor = vec4(mix(scene, OutlineColor.rgb, OutlineColor.a * edge), 1.0);
}
