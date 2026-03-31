in vec4 vClipPos;

uniform sampler2D DecalTexture;
uniform sampler2D DepthSampler;
uniform vec4 Tint;
uniform mat4 InvViewProjMat;
uniform mat4 InvDecalMat;
uniform vec3 CameraPos;

struct PointLight { vec3 position; vec3 color; float brightness; float radius; };
struct DirLight { vec3 direction; vec3 color; float brightness; };

uniform int NumPointLights;
uniform PointLight PointLights[16];
uniform int NumDirLights;
uniform DirLight DirLights[4];

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

    vec3 dx = dFdx(worldPos);
    vec3 dy = dFdy(worldPos);
    vec3 N = normalize(cross(dx, dy));
    vec3 viewDir = normalize(CameraPos - worldPos);
    if (dot(N, viewDir) < 0.0) {
        N = -N;
    }

    vec3 lighting = vec3(0.15);

    for (int i = 0; i < NumPointLights; i++) {
        vec3 toLight = PointLights[i].position - worldPos;
        float dist = length(toLight);
        if (dist < PointLights[i].radius && dist > 1e-4) {
            float NdotL = max(dot(N, toLight / dist), 0.0);
            float atten = 1.0 - smoothstep(0.0, PointLights[i].radius, dist);
            lighting += PointLights[i].color * PointLights[i].brightness * NdotL * atten * atten;
        }
    }

    for (int i = 0; i < NumDirLights; i++) {
        float NdotL = max(dot(N, -DirLights[i].direction), 0.0);
        lighting += DirLights[i].color * DirLights[i].brightness * NdotL;
    }

    fragColor = vec4(texColor.rgb * Tint.rgb * lighting, texColor.a * Tint.a);
}
