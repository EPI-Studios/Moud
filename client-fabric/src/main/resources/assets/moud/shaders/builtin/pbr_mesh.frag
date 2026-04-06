#version 150

in vec2 vTexCoord;
in vec3 vNormal;
in vec3 vWorldPos;
in vec3 vViewPos;
in vec3 vViewNormal;

// @expose
uniform sampler2D albedo_texture;
// @expose
uniform sampler2D normal_texture;
// @expose
uniform sampler2D roughness_texture;
// @expose
uniform sampler2D metallic_texture;
// @expose
uniform sampler2D emission_texture;
// @expose
uniform sampler2D ao_texture;
// @expose
uniform sampler2D orm_texture;
// @expose
uniform sampler2D heightmap_texture;

uniform vec4 Tint;
// @expose
uniform vec4 albedo_color;

uniform mat4 ViewMat;
uniform vec3 CameraPos;

// @expose
uniform float metallic;
// @expose
uniform float roughness;
// @expose
uniform float specular;
// @expose
uniform float ambient_light;
// @expose
uniform float heightmap_scale;
// @expose
uniform float normal_scale;
// @expose
uniform int roughness_texture_channel;
// @expose
uniform int normal_enabled;
// @expose
uniform int emission_enabled;
// @expose
uniform int ao_enabled;
// @expose
uniform int heightmap_enabled;
// @expose
uniform int heightmap_deep_parallax;
// @expose
uniform int heightmap_min_layers;
// @expose
uniform int heightmap_max_layers;
// @expose
uniform int orm_enabled;

struct PointLight { vec3 position; vec3 color; float brightness; float radius; };
struct DirLight   { vec3 direction; vec3 color; float brightness; };
struct SpotLight  { vec3 position; vec3 direction; vec3 color; float brightness; float angle; float distance; };

uniform int NumPointLights;
uniform PointLight PointLights[16];
uniform int NumDirLights;
uniform DirLight DirLights[4];
uniform int NumSpotLights;
uniform SpotLight SpotLights[8];

out vec4 fragColor;

const float PI = 3.14159265359;

vec3 safeNormalize(vec3 v) {
    float lenSq = dot(v, v);
    if (lenSq <= 1.0e-20) return vec3(0.0);
    return v * inversesqrt(lenSq);
}

vec3 fallbackTangent(vec3 N) {
    vec3 axis = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    return safeNormalize(cross(axis, N));
}

vec3 srgbToLinear(vec3 c) {
    vec3 lo = c / 12.92;
    vec3 hi = pow((c + vec3(0.055)) / 1.055, vec3(2.4));
    bvec3 cutoff = lessThanEqual(c, vec3(0.04045));
    return vec3(cutoff.x ? lo.x : hi.x, cutoff.y ? lo.y : hi.y, cutoff.z ? lo.z : hi.z);
}

vec3 linearToSrgb(vec3 c) {
    vec3 lo = c * 12.92;
    vec3 hi = pow(c, vec3(1.0 / 2.4)) * 1.055 - vec3(0.055);
    bvec3 cutoff = lessThanEqual(c, vec3(0.0031308));
    return vec3(cutoff.x ? lo.x : hi.x, cutoff.y ? lo.y : hi.y, cutoff.z ? lo.z : hi.z);
}

mat3 tangentBasis(vec3 N, vec3 pos, vec2 uv) {
    vec3 dp1 = dFdx(pos);
    vec3 dp2 = dFdy(pos);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);

    float det = duv1.x * duv2.y - duv1.y * duv2.x;
    vec3 T = (dp1 * duv2.y - dp2 * duv1.y);
    if (abs(det) > 1.0e-20) T *= (1.0 / det);

    T = safeNormalize(T - N * dot(N, T));
    if (dot(T, T) <= 1.0e-20) T = fallbackTangent(N);
    vec3 B = cross(N, T);

    return mat3(T, B, N);
}

float distributionGGX(float NdotH, float rough) {
    float a = rough * rough;
    float a2 = a * a;
    float denom = NdotH * NdotH * (a2 - 1.0) + 1.0;
    return a2 / (PI * denom * denom);
}

float geometrySchlickGGX(float cosTheta, float rough) {
    float r = rough + 1.0;
    float k = (r * r) / 8.0;
    return cosTheta / (cosTheta * (1.0 - k) + k);
}

float geometrySmith(float NdotV, float NdotL, float rough) {
    return geometrySchlickGGX(NdotV, rough) * geometrySchlickGGX(NdotL, rough);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 evaluateLight(vec3 N, vec3 V, float NdotV, vec3 F0, vec3 albedo,
                   float mat_roughness, float mat_metallic,
                   vec3 lightDir, vec3 lightColor) {
    float NdotL = max(dot(N, lightDir), 0.0);
    if (NdotL <= 0.0) return vec3(0.0);

    vec3 H = safeNormalize(lightDir + V);
    float NdotH = max(dot(N, H), 0.0);
    float HdotV = max(dot(H, V), 0.0);

    float D = distributionGGX(NdotH, mat_roughness);
    float G = geometrySmith(NdotV, NdotL, mat_roughness);
    vec3 F = fresnelSchlick(HdotV, F0);

    vec3 specContrib = (D * G * F) / max(4.0 * NdotV * NdotL, 0.001);
    vec3 kD = (vec3(1.0) - F) * (1.0 - mat_metallic);
    vec3 diffuse = kD * albedo / PI;

    return (diffuse + specContrib) * lightColor * NdotL;
}

float sampleRoughnessChannel(vec2 uv) {
    vec4 texel = texture(roughness_texture, uv);
    if (roughness_texture_channel == 1) return texel.g;
    if (roughness_texture_channel == 2) return texel.b;
    if (roughness_texture_channel == 3) return texel.a;
    if (roughness_texture_channel == 4) return dot(texel.rgb, vec3(0.333333));
    return texel.r;
}

float sampleHeight(vec2 uv) {
    return 1.0 - texture(heightmap_texture, uv).r;
}

vec2 applyHeightMap(vec3 N, vec3 pos, vec2 uv) {
    mat3 tbn = tangentBasis(N, pos, uv);
    vec3 viewDir = safeNormalize(transpose(tbn) * safeNormalize(CameraPos - pos));
    if (dot(viewDir, viewDir) <= 1.0e-20) {
        return uv;
    }

    float scale = heightmap_scale * 0.01;
    if (heightmap_deep_parallax != 0) {
        float minLayers = max(1.0, float(heightmap_min_layers));
        float maxLayers = max(minLayers, float(heightmap_max_layers));
        float numLayers = mix(maxLayers, minLayers, abs(dot(vec3(0.0, 0.0, 1.0), viewDir)));
        float layerDepth = 1.0 / numLayers;
        float currentDepth = 0.0;
        vec2 delta = (viewDir.xy * scale) / numLayers;
        vec2 ofs = uv;
        float depth = sampleHeight(ofs);
        while (currentDepth < depth) {
            ofs -= delta;
            depth = sampleHeight(ofs);
            currentDepth += layerDepth;
        }

        vec2 prevOfs = ofs + delta;
        float afterDepth = depth - currentDepth;
        float beforeDepth = sampleHeight(prevOfs) - currentDepth + layerDepth;
        float denom = afterDepth - beforeDepth;
        float weight = abs(denom) > 1.0e-20 ? afterDepth / denom : 0.0;
        return mix(ofs, prevOfs, clamp(weight, 0.0, 1.0));
    }

    float depth = sampleHeight(uv);
    return uv - viewDir.xy * depth * scale;
}

vec3 applyNormalMap(vec3 N, vec3 pos, vec2 uv, float scale) {
    vec3 n = texture(normal_texture, uv).xyz * 2.0 - 1.0;
    n.xy *= scale;
    n = safeNormalize(n);

    return normalize(tangentBasis(N, pos, uv) * n);
}

void main() {
    vec3 baseNormal = normalize(vNormal);
    if (!gl_FrontFacing) baseNormal = -baseNormal;

    vec2 uv = vTexCoord;
    if (heightmap_enabled != 0) {
        uv = applyHeightMap(baseNormal, vWorldPos, uv);
    }

    vec4 albedoSample = texture(albedo_texture, uv);
    albedoSample.rgb = srgbToLinear(albedoSample.rgb);

    albedoSample.rgb *= albedo_color.rgb * Tint.rgb;
    albedoSample.a *= albedo_color.a * Tint.a;
    if (albedoSample.a < 0.01) discard;

    vec3 albedo = albedoSample.rgb;

    float mat_metallic = metallic;
    float mat_roughness = roughness;
    float ao = 1.0;

    if (orm_enabled != 0) {
        vec4 ormSample = texture(orm_texture, uv);
        ao = ormSample.r;
        mat_roughness *= ormSample.g;
        mat_metallic *= ormSample.b;
    } else {
        mat_roughness *= sampleRoughnessChannel(uv);
        mat_metallic *= texture(metallic_texture, uv).r;
        if (ao_enabled != 0) {
            ao = texture(ao_texture, uv).r;
        }
    }

    mat_roughness = clamp(mat_roughness, 0.04, 1.0);
    mat_metallic = clamp(mat_metallic, 0.0, 1.0);

    vec3 N = baseNormal;
    if (normal_enabled != 0) {
        float ns = normal_scale > 0.0 ? normal_scale : 1.0;
        N = applyNormalMap(N, vWorldPos, uv, ns);
    }

    vec3 emissive = vec3(0.0);
    if (emission_enabled != 0) {
        emissive = srgbToLinear(texture(emission_texture, uv).rgb);
    }

    vec3 viewN = normalize(mat3(ViewMat) * N);

    // #veil:albedo
    vec4 albedoColor = vec4(albedo, ao);

    // #veil:normal
    vec4 normalColor = vec4(viewN, 1.0);

    // #veil:debug
    vec4 debugColor = vec4(mat_roughness, mat_metallic, 0.0, specular);

    vec3 V = safeNormalize(CameraPos - vWorldPos);
    float NdotV = max(dot(N, V), 0.001);
    vec3 F0 = mix(vec3(0.04), albedo, mat_metallic);

    vec3 color = vec3(0.0);

    for (int i = 0; i < NumPointLights; i++) {
        vec3 toLight = PointLights[i].position - vWorldPos;
        float dist = length(toLight);
        if (dist < PointLights[i].radius && dist > 0.001) {
            vec3 L = toLight / dist;
            float atten = 1.0 - smoothstep(0.0, PointLights[i].radius, dist);
            atten *= atten;
            vec3 lc = PointLights[i].color * PointLights[i].brightness * atten;
            color += evaluateLight(N, V, NdotV, F0, albedo, mat_roughness, mat_metallic, L, lc);
        }
    }

    for (int i = 0; i < NumDirLights; i++) {
        vec3 L = -normalize(DirLights[i].direction);
        vec3 lc = DirLights[i].color * DirLights[i].brightness;
        color += evaluateLight(N, V, NdotV, F0, albedo, mat_roughness, mat_metallic, L, lc);
    }

    for (int i = 0; i < NumSpotLights; i++) {
        vec3 toLight = SpotLights[i].position - vWorldPos;
        float dist = length(toLight);
        if (dist < SpotLights[i].distance && dist > 0.001) {
            vec3 L = toLight / dist;
            float cosAngle = dot(-L, normalize(SpotLights[i].direction));
            float cosOuter = cos(radians(SpotLights[i].angle * 0.5));
            float cosInner = mix(cosOuter, 1.0, 0.2);
            float spotFade = clamp((cosAngle - cosOuter) / max(cosInner - cosOuter, 0.001), 0.0, 1.0);
            if (spotFade > 0.0) {
                float atten = 1.0 - smoothstep(0.0, SpotLights[i].distance, dist);
                atten *= atten;
                vec3 lc = SpotLights[i].color * SpotLights[i].brightness * atten * spotFade;
                color += evaluateLight(N, V, NdotV, F0, albedo, mat_roughness, mat_metallic, L, lc);
            }
        }
    }

    vec3 ambient = albedo * 0.15 * ao * ambient_light;
    color += ambient + emissive;

    color = color / (color + vec3(1.0));

    fragColor = vec4(linearToSrgb(color), albedoSample.a);
}
