#version 150

#include veil:fog

uniform sampler2D Sampler0; // baseColor
uniform sampler2D Sampler1; // normal
uniform sampler2D Sampler2; // metallicRoughness
uniform sampler2D Sampler3; // emissive
uniform sampler2D Sampler4; // occlusion

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;
in vec2 lightMapUV;
in vec2 materialMR;

out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

vec3 safeNormalize(vec3 v) {
    float lenSq = dot(v, v);
    if (lenSq <= 1.0e-20) {
        return vec3(0.0);
    }
    return v * inversesqrt(lenSq);
}

vec3 fallbackTangent(vec3 N) {
    vec3 axis = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    return safeNormalize(cross(axis, N));
}

vec3 applyNormalMap(vec3 N, vec3 pos, vec2 uv) {
    vec3 n = texture(Sampler1, uv).xyz * 2.0 - 1.0;
    n = safeNormalize(n);

    vec3 dp1 = dFdx(pos);
    vec3 dp2 = dFdy(pos);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);

    float det = duv1.x * duv2.y - duv1.y * duv2.x;
    vec3 T = (dp1 * duv2.y - dp2 * duv1.y);
    if (abs(det) > 1.0e-20) {
        T *= (1.0 / det);
    }

    T = safeNormalize(T - N * dot(N, T));
    if (dot(T, T) <= 1.0e-20) {
        T = fallbackTangent(N);
    }
    vec3 B = cross(N, T);

    mat3 TBN = mat3(T, B, N);
    return normalize(TBN * n);
}

void main() {
    vec4 baseSample = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

#ifdef GLTF_ALPHA_MODE_MASK
    if (baseSample.a < 0.5) discard;
#endif

#ifdef GLTF_ALPHA_MODE_BLEND
    if (baseSample.a < 0.01) discard;
#endif

    float albedoAlpha = baseSample.a;
#ifdef GLTF_ALPHA_MODE_OPAQUE
    albedoAlpha = 1.0;
#endif
#ifdef GLTF_ALPHA_MODE_MASK
    albedoAlpha = 1.0;
#endif

    // #veil:albedo
    vec4 albedoColor = vec4(baseSample.rgb, albedoAlpha);

    vec3 N = normalize(viewNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    N = applyNormalMap(N, viewPos, texCoord0);

    // #veil:normal
    vec4 normalColor = vec4(N, 1.0);

    vec3 mr = texture(Sampler2, texCoord0).rgb;
    float metallic = saturate(mr.b * materialMR.x);
    float roughness = clamp(mr.g * materialMR.y, 0.04, 1.0);

    float ao = texture(Sampler4, texCoord0).r;
    vec3 emissive = texture(Sampler3, texCoord0).rgb;

    // #veil:debug
    vec4 debugColor = vec4(roughness, metallic, ao, lightMapUV.y);

    float blockLight = saturate(lightMapUV.x);
    vec3 torchAmbient = albedoColor.rgb * blockLight * ao;
    vec4 outColor = vec4(emissive + torchAmbient, baseSample.a);

#ifdef GLTF_ALPHA_MODE_OPAQUE
    outColor.a = 1.0;
#endif

    fragColor = linear_fog(outColor, vertexDistance, FogStart, FogEnd, FogColor);
}
