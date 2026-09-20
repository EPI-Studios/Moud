#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec3 vWorld;
in float vTile;

uniform sampler2D LightMap;
uniform sampler2D Albedo;

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

    vec2 lightUv = clamp((vLight / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, lightUv).rgb;

    // tiled in world space, so a sphere or a wedge needs no texture coordinates of its own
    vec3 at = vWorld / max(vTile, 0.001);
    vec3 blend = abs(n);
    blend /= max(blend.x + blend.y + blend.z, 0.0001);
    vec4 tex = texture(Albedo, at.zy) * blend.x
            + texture(Albedo, at.xz) * blend.y
            + texture(Albedo, at.xy) * blend.z;

    float alpha = vColor.a * tex.a;
    if (alpha < 0.004) discard;

    FragColor = vec4(vColor.rgb * tex.rgb * face * light, alpha);
}
