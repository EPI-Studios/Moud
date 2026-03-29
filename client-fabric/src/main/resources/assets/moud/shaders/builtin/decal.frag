in vec4 vClipPos;

uniform sampler2D DecalTexture;
uniform sampler2D DepthSampler;
uniform vec4 Tint;
uniform mat4 InvViewProjMat;
uniform mat4 InvDecalMat;
uniform vec3 CameraPos;

out vec4 fragColor;

void main() {
    // screen uv from the decal face
    vec2 ndc2 = vClipPos.xy / vClipPos.w;
    vec2 screenUV = ndc2 * 0.5 + 0.5;

    float depth = texture(DepthSampler, screenUV).r;
    if (depth >= 1.0) discard;
    vec4 ndcPos = vec4(ndc2, depth * 2.0 - 1.0, 1.0);
    vec4 crPos4 = InvViewProjMat * ndcPos;
    vec3 worldPos = crPos4.xyz / crPos4.w + CameraPos;

    vec4 lp4 = InvDecalMat * vec4(worldPos, 1.0);
    vec3 lp = lp4.xyz;

    if (any(lessThan(lp, vec3(0.0))) || any(greaterThan(lp, vec3(1.0)))) discard;

    // proj
    vec2 uv = lp.xz;
    vec4 texColor = texture(DecalTexture, uv);
    if (texColor.a < 0.01) discard;

    fragColor = vec4(texColor.rgb * Tint.rgb, texColor.a * Tint.a);
}
