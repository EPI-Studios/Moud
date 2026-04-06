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

vec3 shadePbr(vec3 albedo, float metallic, float roughness, float specular, vec3 N, vec3 V, vec3 L, vec3 radiance) {
    vec3 H = normalize(V + L);
    float NdotL = saturate(dot(N, L));
    float NdotV = saturate(dot(N, V));
    float VdotH = saturate(dot(V, H));

    vec3 F0 = mix(vec3(0.16 * specular * specular), albedo, metallic);
    float D = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(VdotH, F0);

    vec3 numerator = D * G * F;
    float denom = max(4.0 * NdotV * NdotL, 1e-6);
    vec3 specular_term = numerator / denom;

    vec3 kS = F;
    vec3 kD = (vec3(1.0) - kS) * (1.0 - metallic);
    
    return (kD * albedo / PI + specular_term) * radiance * NdotL;
}

vec3 linearToSrgb(vec3 c) {
    vec3 lo = c * 12.92;
    vec3 hi = pow(c, vec3(1.0 / 2.4)) * 1.055 - vec3(0.055);
    bvec3 cutoff = lessThanEqual(c, vec3(0.0031308));
    return vec3(cutoff.x ? lo.x : hi.x, cutoff.y ? lo.y : hi.y, cutoff.z ? lo.z : hi.z);
}

void main() {
    vec4 albedoColor = texture(AlbedoSampler, texCoord);
    if (albedoColor.a == 0.0 && length(albedoColor.rgb) == 0.0) {
        discard;
    }
    float ao = albedoColor.a;
    if (ao <= 0.0) ao = 1.0;

    vec4 debug = texture(DebugSampler, texCoord);
    float specular = saturate(debug.a); 

    vec4 normalTex = texture(NormalSampler, texCoord);
    vec3 normalVS = normalize(normalTex.xyz);

    float depth = texture(DepthSampler, texCoord).r;
    vec3 viewPos = screenToViewSpace(texCoord, depth).xyz;
    vec3 V = normalize(-viewPos);

    vec3 lightDirectionVS = normalize((VeilCamera.ViewMat * vec4(normalize(LightDirection), 0.0)).xyz);
    vec3 L = normalize(-lightDirectionVS);

    if (debug.r > 0.0) {
        float roughness = clamp(debug.r, 0.04, 1.0);
        float metallic = saturate(debug.g);

        vec3 albedo = albedoColor.rgb;
        vec3 radiance = LightColor;
        vec3 lit = shadePbr(albedo, metallic, roughness, specular, normalVS, V, L, radiance);

        vec3 worldNormal = (inverse(VeilCamera.ViewMat) * vec4(normalVS, 0.0)).xyz;
        float skyIntensity = smoothstep(-0.2, 1.0, worldNormal.y) * 0.2;
        vec3 skyColor = mix(vec3(0.3, 0.35, 0.5), vec3(0.6, 0.7, 0.9), worldNormal.y * 0.5 + 0.5);
        vec3 ambient = albedo * skyIntensity * skyColor * (1.0 - metallic) * ao;
        vec3 reflection = albedo * skyIntensity * skyColor * metallic * ao * (1.0 - roughness);
        lit += ambient + reflection;

        lit *= mix(1.0, ao, 0.85);
        
        lit = lit / (lit + vec3(1.0));
        
        fragColor = vec4(linearToSrgb(lit), 1.0);
    } else {
        float diffuse = saturate(dot(normalVS, L));
        float reflectivity = 0.05;
        vec3 diffuseColor = diffuse * LightColor;
        vec3 finalColor = albedoColor.rgb * diffuseColor * (1.0 - reflectivity) + diffuseColor * reflectivity;
        
        finalColor = finalColor / (finalColor + vec3(1.0));
        
        fragColor = vec4(linearToSrgb(finalColor), 1.0);
    }
}
