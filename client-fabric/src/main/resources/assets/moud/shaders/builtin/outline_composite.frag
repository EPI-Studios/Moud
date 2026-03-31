uniform sampler2D DiffuseSampler0;
uniform sampler2D MainDepthSampler;
uniform sampler2D OutlineMaskSampler;
uniform sampler2D OutlineDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

const vec3 HOVER_COLOR    = vec3(0.85, 0.85, 0.85);
const vec3 SELECTED_COLOR = vec3(1.0, 0.6, 0.15);
const float OUTLINE_RADIUS = 3.0;

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float sceneDepth = texture(MainDepthSampler, texCoord).r;

    float m0 = texture(OutlineMaskSampler, texCoord).a;
    vec2 px = 1.0 / vec2(textureSize(OutlineMaskSampler, 0));

    float neigh = 0.0;
    float bestDepth = 1.0;

    for (float r = 1.0; r <= OUTLINE_RADIUS; r += 1.0) {
        vec2 off = px * r;

        float s1 = texture(OutlineMaskSampler, texCoord + vec2( off.x,  0.0  )).a;
        float s2 = texture(OutlineMaskSampler, texCoord + vec2(-off.x,  0.0  )).a;
        float s3 = texture(OutlineMaskSampler, texCoord + vec2( 0.0,    off.y)).a;
        float s4 = texture(OutlineMaskSampler, texCoord + vec2( 0.0,   -off.y)).a;
        float s5 = texture(OutlineMaskSampler, texCoord + vec2( off.x,  off.y)).a;
        float s6 = texture(OutlineMaskSampler, texCoord + vec2(-off.x,  off.y)).a;
        float s7 = texture(OutlineMaskSampler, texCoord + vec2( off.x, -off.y)).a;
        float s8 = texture(OutlineMaskSampler, texCoord + vec2(-off.x, -off.y)).a;

        float ringMax = max(max(max(s1, s2), max(s3, s4)), max(max(s5, s6), max(s7, s8)));
        neigh = max(neigh, ringMax);

        if (s1 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2( off.x,  0.0  )).r);
        if (s2 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2(-off.x,  0.0  )).r);
        if (s3 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2( 0.0,    off.y)).r);
        if (s4 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2( 0.0,   -off.y)).r);
        if (s5 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2( off.x,  off.y)).r);
        if (s6 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2(-off.x,  off.y)).r);
        if (s7 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2( off.x, -off.y)).r);
        if (s8 > 0.01) bestDepth = min(bestDepth, texture(OutlineDepthSampler, texCoord + vec2(-off.x, -off.y)).r);
    }

    float edge = clamp(neigh - m0, 0.0, 1.0);

    if (edge < 0.01) {
        fragColor = base;
        return;
    }

    float visible = bestDepth <= sceneDepth + 0.00015 ? 1.0 : 0.0;
    float a = edge * visible;
    vec3 outlineColor = neigh > 0.75 ? SELECTED_COLOR : HOVER_COLOR;

    fragColor = vec4(mix(base.rgb, outlineColor, a), base.a);
}
