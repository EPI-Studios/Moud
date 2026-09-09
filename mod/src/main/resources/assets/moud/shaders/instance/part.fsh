#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;

uniform sampler2D LightMap;

out vec4 FragColor;

void main() {
    if (vColor.a < 0.001) discard;

    // instanced geometry has no normals, so the face normal is the derivative of the position,
    // which is constant across a triangle and is what keeps this flat rather than smooth
    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    // vPos is relative to the camera, so the outward face is the one pointing back at it
    if (dot(n, vPos) > 0.0) {
        n = -n;
    }

    // the same four numbers a block face gets, so a part sitting in a wall of blocks reads as one
    float face = abs(n.y) > max(abs(n.x), abs(n.z))
            ? (n.y > 0.0 ? 1.0 : 0.5)
            : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);

    // minecraft's own light map, sampled the way minecraft samples it, so a part dims with the
    // block light around it and with the hour of the day. the half texel is what keeps a level
    // off the seam between two entries
    vec2 uv = clamp((vLight / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, uv).rgb;

    // and nothing else touches the colour: a part in full light, on its top face, is drawn in the
    // exact rgb the place asked for
    FragColor = vec4(vColor.rgb * face * light, vColor.a);
}
