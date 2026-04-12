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

    float m0 = texture(OutlineMaskSampler, texCoord).a;
    vec2 px = 1.0 / vec2(textureSize(OutlineMaskSampler, 0));

    float neigh = 0.0;

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
    }

    float outside = 1.0 - step(0.01, m0);
    float edge = step(0.01, neigh) * outside;

    if (edge < 0.01) {
        fragColor = base;
        return;
    }

    float a = edge;
    vec3 outlineColor = neigh > 0.75 ? SELECTED_COLOR : HOVER_COLOR;

    fragColor = vec4(mix(base.rgb, outlineColor, a), base.a);
}
