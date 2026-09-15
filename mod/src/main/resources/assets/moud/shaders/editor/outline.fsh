#version 330 core

const int MAXIMUM_RADIUS = 6;

uniform sampler2D SceneSampler;
uniform sampler2D MaskSampler;
uniform vec2 Texel;
uniform float Radius;
uniform vec4 SelectedColor;
uniform vec4 HoveredColor;
uniform float FillAlpha;

in vec2 vUV;
out vec4 FragColor;

vec2 maskAt(vec2 uv) {
    return texture(MaskSampler, uv).rg;
}

void main() {
    vec3 scene = texture(SceneSampler, vUV).rgb;
    vec2 centre = maskAt(vUV);
    if (centre.r > 0.5) {
        FragColor = vec4(mix(scene, SelectedColor.rgb, FillAlpha), 1.0);
        return;
    }
    float squaredRadius = Radius * Radius;
    vec2 neighbour = vec2(0.0);
    for (int y = -MAXIMUM_RADIUS; y <= MAXIMUM_RADIUS; y++) {
        for (int x = -MAXIMUM_RADIUS; x <= MAXIMUM_RADIUS; x++) {
            if (float(x * x + y * y) > squaredRadius) continue;
            neighbour = max(neighbour, maskAt(vUV + vec2(float(x), float(y)) * Texel));
        }
    }
    if (neighbour.r > 0.5) {
        FragColor = vec4(mix(scene, SelectedColor.rgb, SelectedColor.a), 1.0);
    } else if (neighbour.g > 0.5 && centre.g < 0.5) {
        FragColor = vec4(mix(scene, HoveredColor.rgb, HoveredColor.a), 1.0);
    } else {
        FragColor = vec4(scene, 1.0);
    }
}
