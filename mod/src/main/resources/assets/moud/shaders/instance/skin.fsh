#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec2 vUv;
in float vShell;

uniform sampler2D TextureSampler;
uniform sampler2D LightMap;

out vec4 FragColor;

void main() {
    vec4 skin = texture(TextureSampler, vUv);

    // the shell is a hat, a sleeve or a jacket: where the skin left it blank there is nothing to
    // draw, and drawing it anyway is the black box every bad skin renderer puts on someone's head
    if (vShell > 0.5 && skin.a < 0.1) discard;
    if (skin.a < 0.01) discard;

    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    if (dot(n, vPos) > 0.0) {
        n = -n;
    }

    float face = abs(n.y) > max(abs(n.x), abs(n.z))
            ? (n.y > 0.0 ? 1.0 : 0.5)
            : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);

    vec2 uv = clamp((vLight / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, uv).rgb;

    // the tint is what a place set on the part, white unless it asked for something
    FragColor = vec4(skin.rgb * vColor.rgb * face * light, skin.a * vColor.a);
}
