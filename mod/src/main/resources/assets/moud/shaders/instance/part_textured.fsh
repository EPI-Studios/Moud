#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec3 vWorld;
in float vTile;
in vec3 vOffset;
in vec3 vSurface;
in vec3 vLocal;
in mat3 vNormalMatrix;

uniform sampler2D LightMap;
uniform sampler3D BlockLight;
uniform vec4 LightVolume;

uniform sampler2D Albedo;

// 1 ball, 2 cylinder along x, anything else has flat faces
uniform float Shape;
uniform samplerCube EnvCube;
// xyz towards the sun, w its intensity
uniform vec4 SunLight;
// rgb sun colour, a exposure
uniform vec4 SunTint;
// diffuse scale, specular scale, the probe's last mip, whether the probe is ready
uniform vec4 Environment;

const float PI = 3.14159265359;

out vec4 FragColor;
layout(location = 1) out vec4 GNormal;
layout(location = 2) out vec4 GMaterial;
layout(location = 4) out vec4 GAlbedo;

// minecraft only stores light per block, so read the volume a little off the surface, the way a
// block face takes the light of the air in front of it rather than its own
vec2 litBy(vec3 world, vec3 normal, vec2 fallback) {
    if (LightVolume.w < 1.0) return fallback;
    vec3 at = (world + normal * 0.5 - LightVolume.xyz + 0.5) / LightVolume.w;
    if (any(lessThan(at, vec3(0.0))) || any(greaterThan(at, vec3(1.0)))) return fallback;
    return texture(BlockLight, at).rg * 255.0;
}

// the true normal of a round shape, from where on the unit shape this fragment is
vec3 shapeNormal(vec3 faceted) {
    vec3 local;
    if (Shape > 0.5 && Shape < 1.5) {
        local = vLocal;
    } else if (Shape > 1.5 && Shape < 2.5) {
        vec3 face = cross(dFdx(vLocal), dFdy(vLocal));
        if (abs(face.x) > 0.5 * length(face)) return faceted;
        local = vec3(0.0, vLocal.yz);
    } else {
        return faceted;
    }
    return normalize(vNormalMatrix * local);
}

float encodedLight(vec2 lit) {
    vec2 level = floor(clamp(lit / 16.0, 0.0, 15.0) + 0.5);
    return (level.x * 16.0 + level.y) / 255.0;
}

float distributionGGX(float NoH, float rough) {
    float a = rough * rough;
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    return a2 / max(PI * d * d, 1e-7);
}

float geometrySmith(float NoV, float NoL, float rough) {
    float k = (rough + 1.0) * (rough + 1.0) / 8.0;
    return NoV / (NoV * (1.0 - k) + k) * NoL / (NoL * (1.0 - k) + k);
}

vec3 fresnelSchlick(float c, vec3 f0) {
    return f0 + (1.0 - f0) * pow(clamp(1.0 - c, 0.0, 1.0), 5.0);
}

vec3 fresnelRough(float c, vec3 f0, float rough) {
    return f0 + (max(vec3(1.0 - rough), f0) - f0) * pow(clamp(1.0 - c, 0.0, 1.0), 5.0);
}

// karis' fit of the split-sum brdf, so no lookup texture is needed
vec2 environmentBrdf(float rough, float NoV) {
    vec4 r = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
    float a004 = min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y;
    return vec2(-1.04, 1.04) * a004 + r.zw;
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// the sun and the sky probe on a metal/roughness surface, lit and graded the way models are, with
// minecraft's light map deciding how much daylight and torchlight reach this spot
vec3 physical(vec3 albedo, vec3 N, vec2 lit) {
    float rough = clamp(vSurface.y, 0.045, 1.0);
    float metallic = clamp(vSurface.z, 0.0, 1.0);
    vec3 base = pow(albedo, vec3(2.2));
    vec3 V = normalize(-vPos);
    float NoV = max(dot(N, V), 1e-4);
    vec3 f0 = mix(vec3(0.04), base, metallic);

    vec2 levels = clamp(lit / 240.0, 0.0, 1.0);
    vec3 daylight = pow(texture(LightMap, vec2(0.5 / 16.0, levels.y * (15.0 / 16.0) + 0.5 / 16.0)).rgb, vec3(2.2));
    vec3 torch = pow(texture(LightMap, vec2(levels.x * (15.0 / 16.0) + 0.5 / 16.0, 0.5 / 16.0)).rgb, vec3(2.2));

    vec3 L = normalize(SunLight.xyz);
    vec3 H = normalize(V + L);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    vec3 F = fresnelSchlick(max(dot(V, H), 0.0), f0);
    vec3 spec = distributionGGX(NoH, rough) * geometrySmith(NoV, NoL, rough) * F / max(4.0 * NoV * NoL, 1e-4);
    vec3 kd = (1.0 - F) * (1.0 - metallic);
    float up = smoothstep(-0.05, 0.1, L.y);
    vec3 sun = SunTint.rgb * SunLight.w * PI * up * NoL * levels.y;
    vec3 direct = (kd * base / PI + spec) * sun;

    vec3 irradiance = vec3(0.6, 0.7, 0.85);
    vec3 reflected = irradiance;
    vec3 R = reflect(-V, N);
    if (Environment.w > 0.5) {
        irradiance = textureLod(EnvCube, N, Environment.z).rgb;
        reflected = textureLod(EnvCube, R, rough * Environment.z).rgb;
    }
    vec3 Fr = fresnelRough(NoV, f0, rough);
    vec2 brdf = environmentBrdf(rough, NoV);
    vec3 diffuse = irradiance * Environment.x * base * (1.0 - Fr) * (1.0 - metallic);
    vec3 specular = reflected * Environment.y * (Fr * brdf.x + brdf.y);
    vec3 ambient = (diffuse + specular) * daylight + base * torch * (1.0 - metallic);

    vec3 color = (direct + ambient) * SunTint.a;
    return pow(aces(color), vec3(1.0 / 2.2));
}

void main() {
    if (vColor.a < 0.001) discard;

    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    if (dot(n, vPos) > 0.0) {
        n = -n;
    }
    vec2 lit = litBy(vWorld, n, vLight);
    bool pbr = vSurface.x > 0.5;
    vec3 N = pbr ? shapeNormal(n) : n;

    // tiled in world space, so a sphere or a wedge needs no texture coordinates of its own
    vec3 at = (vWorld - vOffset) / max(vTile, 0.001);
    vec3 blend = abs(n);
    blend /= max(blend.x + blend.y + blend.z, 0.0001);
    vec4 tex = texture(Albedo, at.zy) * blend.x
            + texture(Albedo, at.xz) * blend.y
            + texture(Albedo, at.xy) * blend.z;

    float alpha = vColor.a * tex.a;
    if (alpha < 0.004) discard;

    vec3 albedo = vColor.rgb * tex.rgb;
    GNormal = vec4(N, 1.0);
    if (pbr) {
        FragColor = vec4(physical(albedo, N, lit), alpha);
        GMaterial = vec4(clamp(vSurface.y, 0.045, 1.0), clamp(vSurface.z, 0.0, 1.0), 0.0, encodedLight(lit));
        GAlbedo = vec4(albedo, 1.0);
        return;
    }

    float face = abs(n.y) > max(abs(n.x), abs(n.z))
            ? (n.y > 0.0 ? 1.0 : 0.5)
            : (abs(n.z) > abs(n.x) ? 0.8 : 0.6);
    vec2 lightUv = clamp((lit / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, lightUv).rgb;

    FragColor = vec4(albedo * face * light, alpha);
    GMaterial = vec4(1.0, 0.0, 0.0, encodedLight(lit));
    GAlbedo = vec4(albedo * face, 1.0);
}
