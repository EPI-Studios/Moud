#include veil:common
#include veil:space_helper
#include veil:light

in mat4 lightMat;
in vec3 lightColor;
in vec2 size;
in float maxAngle;
in float maxDistance;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DebugSampler;

uniform vec2 ScreenSize;

out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
}

// acos approximation
// faster and also doesn't flicker weirdly
float sacos(float x) {
    float y = abs(clamp(x, -1.0, 1.0));
    float z = (-0.168577 * y + 1.56723) * sqrt(1.0 - y);
    return mix(0.5 * PI, z, sign(x));
}

struct AreaLightResult { vec3 position; float angle; };
AreaLightResult closestPointOnPlaneAndAngle(vec3 point, mat4 planeMatrix, vec2 planeSize) {
    planeMatrix[3].xyz *= -1.0;
    vec3 localSpacePoint = (planeMatrix * vec4(point, 1.0)).xyz;
    vec3 localSpacePointOnPlane = vec3(clamp(localSpacePoint.xy, -planeSize, planeSize), 0);

    vec3 direction = normalize(localSpacePoint - localSpacePointOnPlane);
    float angle = sacos(dot(direction, vec3(0.0, 0.0, 1.0)));

    return AreaLightResult((inverse(planeMatrix) * vec4(localSpacePointOnPlane, 1.0)).xyz, angle);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = saturate(dot(N, H));
    float NdotH2 = NdotH * NdotH;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    return a2 / max(PI * denom * denom, 1e-6);
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / max(NdotV * (1.0 - k) + k, 1e-6);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = saturate(dot(N, V));
    float NdotL = saturate(dot(N, L));
    float ggx1 = geometrySchlickGGX(NdotV, roughness);
    float ggx2 = geometrySchlickGGX(NdotL, roughness);
    return ggx1 * ggx2;
}

vec3 shadePbr(vec3 albedo, float metallic, float roughness, vec3 N, vec3 V, vec3 L, vec3 radiance, float directWeight) {
    vec3 H = normalize(V + L);
    float NdotL = saturate(dot(N, L));
    float NdotV = saturate(dot(N, V));
    float VdotH = saturate(dot(V, H));

    vec3 F0 = mix(vec3(0.04), albedo, metallic);
    float D = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(VdotH, F0);

    vec3 numerator = D * G * F;
    float denom = max(4.0 * NdotV * NdotL, 1e-6);
    vec3 specular = numerator / denom;

    vec3 kS = F;
    vec3 kD = (vec3(1.0) - kS) * (1.0 - metallic);
    vec3 diffuse = kD * albedo / PI;

    return (diffuse + specular) * radiance * (directWeight);
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;

    vec4 albedoColor = texture(AlbedoSampler, screenUv);
    if (albedoColor.a == 0.0) {
        discard;
    }

    vec3 normalVS = normalize(texture(NormalSampler, screenUv).xyz);
    float depth = texture(DepthSampler, screenUv).r;
    vec3 posWorld = screenToWorldSpace(screenUv, depth).xyz;
    vec3 viewPos = screenToViewSpace(screenUv, depth).xyz;

    AreaLightResult areaLightInfo = closestPointOnPlaneAndAngle(posWorld, lightMat, size);
    vec3 lightPos = areaLightInfo.position;
    float angle = areaLightInfo.angle;

    vec3 offset = lightPos - posWorld;
    vec3 L = normalize((VeilCamera.ViewMat * vec4(offset, 0.0)).xyz);

    float distAtten = attenuate_no_cusp(length(offset), maxDistance);
    float angleFalloff = clamp(angle, 0.0, maxAngle) / maxAngle;
    angleFalloff = smoothstep(1.0, 0.0, angleFalloff);
    float attenuation = distAtten * angleFalloff;

    float nDotL = saturate(dot(normalVS, L));
    float direct = nDotL;

    vec4 debug = texture(DebugSampler, screenUv);
    vec3 outRgb;
    // PBR presence is detected via roughness > 0 (roughness is clamped to >= 0.04 on write).
    if (debug.r > 0.0) {
        float roughness = clamp(debug.r, 0.04, 1.0);
        float metallic = saturate(debug.g);
        float ao = saturate(debug.b);
        vec3 V = normalize(-viewPos);

        vec3 albedo = albedoColor.rgb;
        vec3 radiance = lightColor * attenuation;
        vec3 lit = shadePbr(albedo, metallic, roughness, normalVS, V, L, radiance, direct);
        // Veil’s default light intensity is tuned for a non-PBR (Lambert-ish) model. Scale to keep brightness comparable.
        lit *= PI;
        lit *= mix(1.0, ao, 0.85);
        outRgb = lit;
    } else {
        float diffuse = direct * attenuation;
        float reflectivity = 0.05;
        vec3 diffuseColor = diffuse * lightColor;
        outRgb = albedoColor.rgb * diffuseColor * (1.0 - reflectivity) + diffuseColor * reflectivity;
    }

    fragColor = vec4(outRgb, 1.0);
}
