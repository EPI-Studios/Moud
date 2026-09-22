#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec3 vWorld;

uniform sampler2D LightMap;
uniform sampler3D BlockLight;
uniform vec4 LightVolume;

// minecraft only stores light per block, so read the volume a little off the surface, the way a
// block face takes the light of the air in front of it rather than its own
vec2 litBy(vec3 world, vec3 normal, vec2 fallback) {
    if (LightVolume.w < 1.0) return fallback;
    vec3 at = (world + normal * 0.5 - LightVolume.xyz + 0.5) / LightVolume.w;
    if (any(lessThan(at, vec3(0.0))) || any(greaterThan(at, vec3(1.0)))) return fallback;
    return texture(BlockLight, at).rg * 255.0;
}


out vec4 FragColor;

void main() {
    if (vColor.a < 0.001) discard;

    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    if (dot(n, vPos) > 0.0) {
        n = -n;
    }

    float face = abs(n.y) > max(abs(n.x), abs(n.z))
            ? (n.y > 0.0 ? 1.0 : 0.5)
            : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);

    vec2 uv = clamp((litBy(vWorld, n, vLight) / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, uv).rgb;

    FragColor = vec4(vColor.rgb * face * light, vColor.a);
}
