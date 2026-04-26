#version 330 core

in vec2 vTexCoord;
in vec3 vNormal;
in vec3 vWorldPos;

uniform sampler2D Texture0;
uniform vec4 Tint;
uniform int fullbright;
uniform float ambient_light;

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

// MRT outputs — see pbr_mesh.frag for rationale.
layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 VeilDynamicAlbedo;
layout(location = 2) out vec4 VeilDynamicNormal;
layout(location = 3) out vec4 VeilDynamicLightUV;
layout(location = 4) out vec4 VeilDynamicLightColor;
layout(location = 5) out vec4 VeilDynamicDebug;

vec3 linearToSrgb(vec3 c) {
    vec3 lo = c * 12.92;
    vec3 hi = pow(c, vec3(1.0 / 2.4)) * 1.055 - vec3(0.055);
    bvec3 cutoff = lessThanEqual(c, vec3(0.0031308));
    return vec3(cutoff.x ? lo.x : hi.x, cutoff.y ? lo.y : hi.y, cutoff.z ? lo.z : hi.z);
}

float directionalDiffuse(vec3 normal, vec3 lightDir) {
    // since they usally represent sun light
    // i made them softer
    float wrap = 0.35;
    float d = clamp((dot(normal, lightDir) + wrap) / (1.0 + wrap), 0.0, 1.0);
    return d * d * (3.0 - 2.0 * d);
}

void main() {
    // Default G-buffer writes — overwritten in the lit branch. Means the
    // outputs are always defined (GLSL leaves unwritten frag outputs as
    // undefined, which can flicker in the framebuffer inspector).
    VeilDynamicAlbedo     = vec4(0.0);
    VeilDynamicNormal     = vec4(0.0, 0.0, 1.0, 1.0);
    VeilDynamicLightUV    = vec4(0.0);
    VeilDynamicLightColor = vec4(0.0);
    VeilDynamicDebug      = vec4(0.0);

    vec4 texColor = texture(Texture0, vTexCoord);
    if (texColor.a * Tint.a < 0.01) discard;
    if (fullbright != 0) {
        fragColor = vec4(linearToSrgb(texColor.rgb * Tint.rgb), texColor.a * Tint.a);
        VeilDynamicAlbedo = vec4(texColor.rgb * Tint.rgb, 1.0);
    } else {
        vec3 baseColor = texColor.rgb * Tint.rgb;
        vec3 N = normalize(vNormal);

        // Ambient baseline. See default_mesh_instanced.frag for the
        // rationale. 0.45 floor + ambient_light contribution; clamped to
        // non-negative to defend against editor float noise.
        float amb = max(ambient_light, 0.0);
        vec3 lighting = vec3(max(0.45, 0.15 + 0.35 * amb));

        for (int i = 0; i < NumPointLights; i++) {
            vec3  toLight = PointLights[i].position - vWorldPos;
            float dist2   = dot(toLight, toLight);
            float r       = PointLights[i].radius;
            float r2      = r * r;
            if (dist2 < r2 && dist2 > 1.0e-6) {
                float dist  = sqrt(dist2);
                vec3  L     = toLight / dist;
                float NdotL = max(dot(N, L), 0.0);
                // Windowed inverse-square — smooth iso-contours, no banding.
                float window = max(1.0 - dist2 / r2, 0.0);
                float atten  = (window * window) / (1.0 + dist2);
                lighting += PointLights[i].color * PointLights[i].brightness * NdotL * atten;
            }
        }

        for (int i = 0; i < NumDirLights; i++) {
            float NdotL = directionalDiffuse(N, -normalize(DirLights[i].direction));
            lighting += DirLights[i].color * DirLights[i].brightness * NdotL;
        }

        for (int i = 0; i < NumSpotLights; i++) {
            vec3  toLight = SpotLights[i].position - vWorldPos;
            float dist2   = dot(toLight, toLight);
            float r       = SpotLights[i].distance;
            float r2      = r * r;
            if (dist2 >= r2 || dist2 <= 1.0e-6) continue;
            float dist  = sqrt(dist2);
            vec3  L     = toLight / dist;
            float theta = dot(-L, normalize(SpotLights[i].direction));
            float cosHalf = cos(radians(max(SpotLights[i].angle, 0.1) * 0.5));
            if (theta <= cosHalf) continue;
            float coneEdge = clamp((theta - cosHalf) / max(1.0 - cosHalf, 1e-4), 0.0, 1.0);
            float NdotL = max(dot(N, L), 0.0);
            float window = max(1.0 - dist2 / r2, 0.0);
            float atten  = (window * window) / (1.0 + dist2);
            float shadow = moud_sampleSpotShadow(vWorldPos, i);
            lighting += SpotLights[i].color * SpotLights[i].brightness * NdotL * atten * coneEdge * shadow;
        }

        vec3 finalColor = linearToSrgb(baseColor * lighting);
        // Sub-LSB hash dither breaks the smooth attenuation falloff into
        // noise instead of visible iso-contour bands.
        float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
        finalColor += dither / 255.0;
        fragColor = vec4(finalColor, texColor.a * Tint.a);

        VeilDynamicAlbedo     = vec4(baseColor, 1.0);
        VeilDynamicNormal     = vec4(N, 1.0);
        VeilDynamicLightUV    = vec4(0.0);
        VeilDynamicLightColor = vec4(0.0);
        VeilDynamicDebug      = vec4(0.0);
    }
}
