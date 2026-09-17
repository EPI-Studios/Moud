#version 330 core

uniform sampler2D Sprite;
uniform sampler2D LightMap;

in vec2 vUv;
in vec4 vTint;
in vec4 vLight;

out vec4 FragColor;

void main() {
    vec4 texel = texture(Sprite, vUv);
    float alpha = texel.a * vTint.a;
    if (alpha < 0.002) discard;

    vec2 cell = clamp(vLight.xy / 256.0 + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = mix(vec3(1.0), texture(LightMap, cell).rgb, vLight.z);

    vec3 color = texel.rgb * vTint.rgb * light * alpha;
    FragColor = vec4(color, alpha * (1.0 - vLight.w));
}
