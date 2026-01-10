#include veil:common
#include veil:space_helper
#include veil:light

in vec2 texCoord;

uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DebugSampler;

uniform vec3 LightColor;
uniform vec3 LightDirection;

out vec4 fragColor;

float saturate(float v) {
    return clamp(v, 0.0, 1.0);
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
    vec4 albedoColor = texture(AlbedoSampler, texCoord);
    if (albedoColor.a == 0.0) {
        discard;
    }

    vec4 debug = texture(DebugSampler, texCoord);
    float skyMask = smoothstep(0.0, 0.2, saturate(debug.a));

    vec3 normalVS = normalize(texture(NormalSampler, texCoord).xyz);
    float depth = texture(DepthSampler, texCoord).r;
    vec3 viewPos = screenToViewSpace(texCoord, depth).xyz;
    vec3 V = normalize(-viewPos);

    vec3 lightDirectionVS = normalize((VeilCamera.ViewMat * vec4(normalize(LightDirection), 0.0)).xyz);
    vec3 L = normalize(-lightDirectionVS);

    float nDotL = saturate(dot(normalVS, L));
    float direct = nDotL * skyMask;

    if (debug.r > 0.0) {
        float roughness = clamp(debug.r, 0.04, 1.0);
        float metallic = saturate(debug.g);
        float ao = saturate(debug.b);

        vec3 albedo = albedoColor.rgb;
        vec3 radiance = LightColor;
        vec3 lit = shadePbr(albedo, metallic, roughness, normalVS, V, L, radiance, direct * skyMask);
        lit *= PI;
        lit *= mix(1.0, ao, 0.85);
        fragColor = vec4(lit, 1.0);
    } else {
        float diffuse = clamp(smoothstep(-0.2, 0.2, -dot(normalVS, lightDirectionVS)), 0.0, 1.0);
        diffuse *= skyMask;
        float reflectivity = 0.05;
        vec3 diffuseColor = diffuse * LightColor;
        fragColor = vec4(albedoColor.rgb * diffuseColor * (1.0 - reflectivity) + diffuseColor * reflectivity, 1.0);
    }
}
