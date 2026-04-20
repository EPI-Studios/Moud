in vec2 vTexCoord;
in vec3 vNormal;
in vec3 vWorldPos;
in vec4 vTint;

uniform sampler2D Texture0;

struct PointLight { vec3 position; vec3 color; float brightness; float radius; };
struct DirLight { vec3 direction; vec3 color; float brightness; };
struct SpotLight { vec3 position; vec3 direction; vec3 color; float brightness; float angle; float distance; };

uniform int NumPointLights;
uniform PointLight PointLights[16];
uniform int NumDirLights;
uniform DirLight DirLights[4];
uniform int NumSpotLights;
uniform SpotLight SpotLights[8];

uniform int NumSpotShadows;
uniform int SpotShadowLightIdx[4];
uniform vec4 SpotShadowTile[4];
uniform mat4 SpotShadowMatrix[4];
uniform sampler2D SpotShadowMap;

float moud_sampleSpotShadow(vec3 worldPos, int lightIdx) {
    if (NumSpotShadows == 0) return 1.0;
    for (int s = 0; s < 4; s++) {
        if (s >= NumSpotShadows) break;
        if (SpotShadowLightIdx[s] != lightIdx) continue;
        vec4 clip = SpotShadowMatrix[s] * vec4(worldPos, 1.0);
        if (clip.w <= 0.0) return 1.0;
        vec3 ndc = clip.xyz / clip.w;
        if (abs(ndc.x) > 1.0 || abs(ndc.y) > 1.0 || ndc.z > 1.0 || ndc.z < -1.0) return 1.0;
        vec2 tileUv = ndc.xy * 0.5 + 0.5;
        vec2 atlasUv = mix(SpotShadowTile[s].xy, SpotShadowTile[s].zw, tileUv);
        float mapDepth = texture(SpotShadowMap, atlasUv).r;
        float fragDepth = ndc.z * 0.5 + 0.5;
        return (fragDepth - 0.003) > mapDepth ? 0.0 : 1.0;
    }
    return 1.0;
}

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Texture0, vTexCoord);
    vec3 baseColor = texColor.rgb * vTint.rgb;
    vec3 N = normalize(vNormal);

    vec3 lighting = vec3(0.15);

    for (int i = 0; i < NumPointLights; i++) {
        vec3 toLight = PointLights[i].position - vWorldPos;
        float dist = length(toLight);
        if (dist < PointLights[i].radius) {
            float NdotL = max(dot(N, toLight / dist), 0.0);
            float atten = 1.0 - smoothstep(0.0, PointLights[i].radius, dist);
            lighting += PointLights[i].color * PointLights[i].brightness * NdotL * atten * atten;
        }
    }

    for (int i = 0; i < NumDirLights; i++) {
        float NdotL = max(dot(N, -DirLights[i].direction), 0.0);
        lighting += DirLights[i].color * DirLights[i].brightness * NdotL;
    }

    for (int i = 0; i < NumSpotLights; i++) {
        vec3 toLight = SpotLights[i].position - vWorldPos;
        float dist = length(toLight);
        if (dist >= SpotLights[i].distance) continue;
        vec3 L = toLight / max(dist, 1e-4);
        float theta = dot(-L, normalize(SpotLights[i].direction));
        float cosHalf = cos(radians(max(SpotLights[i].angle, 0.1) * 0.5));
        if (theta <= cosHalf) continue;
        float coneEdge = clamp((theta - cosHalf) / max(1.0 - cosHalf, 1e-4), 0.0, 1.0);
        float NdotL = max(dot(N, L), 0.0);
        float atten = 1.0 - smoothstep(0.0, SpotLights[i].distance, dist);
        float shadow = moud_sampleSpotShadow(vWorldPos, i);
        lighting += SpotLights[i].color * SpotLights[i].brightness * NdotL * atten * coneEdge * shadow;
    }

    fragColor = vec4(baseColor * lighting, texColor.a * vTint.a);
    if (fragColor.a < 0.01) discard;
}
