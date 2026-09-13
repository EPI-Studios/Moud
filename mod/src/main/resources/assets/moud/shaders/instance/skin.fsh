#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec2 vUv;
in float vCutout;
in vec2 vOverlay;
in vec3 vNormal;

uniform sampler2D TextureSampler;
uniform sampler2D LightMap;

out vec4 FragColor;

void main() {
    vec4 skin = texture(TextureSampler, vUv);

    if (vCutout > 0.5 && skin.a < 0.1) discard;
    if (skin.a < 0.01) discard;

    vec3 light0 = normalize(vec3(0.2, 1.0, -0.7));
    vec3 light1 = normalize(vec3(-0.2, 1.0, 0.7));
    vec3 n = normalize(vNormal);
    float diffuse = min(1.0,
            (max(0.0, dot(light0, n)) + max(0.0, dot(light1, n))) * 0.6 + 0.4);

    vec2 uv = clamp((vLight / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, uv).rgb;

    vec3 rgb = skin.rgb * vColor.rgb * diffuse;

    float stepped = floor(clamp(vOverlay.x, 0.0, 1.0) * 15.0) / 15.0;
    bool red = vOverlay.y > 0.5;
    vec3 wash = red ? vec3(1.0, 0.0, 0.0) : vec3(1.0);
    float keep = red ? 179.0 / 255.0 : floor((1.0 - stepped * 0.75) * 255.0) / 255.0;
    rgb = mix(wash, rgb, keep);

    FragColor = vec4(rgb * light, skin.a * vColor.a);
}
