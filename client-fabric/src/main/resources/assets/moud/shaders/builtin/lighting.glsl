// Moud built-in lighting - copy these into your shader to use scene lights.
//
// Uniforms set automatically per frame:
//   NumPointLights, NumDirLights, NumSpotLights
//   PointLights[i], DirLights[i], SpotLights[i]

struct PointLight {
    vec3 position;
    vec3 color;
    float brightness;
    float radius;
};

struct DirLight {
    vec3 direction;
    vec3 color;
    float brightness;
};

struct SpotLight {
    vec3 position;
    vec3 direction;
    vec3 color;
    float brightness;
    float angle;
    float distance;
};

vec3 calcPointLight(PointLight light, vec3 worldPos, vec3 normal) {
    vec3 toLight = light.position - worldPos;
    float dist = length(toLight);
    if (dist > light.radius) return vec3(0.0);
    float NdotL = max(dot(normal, toLight / dist), 0.0);
    float atten = 1.0 - smoothstep(0.0, light.radius, dist);
    return light.color * light.brightness * NdotL * atten * atten;
}

float directionalDiffuse(vec3 normal, vec3 lightDir) {
    float wrap = 0.35;
    float d = clamp((dot(normal, lightDir) + wrap) / (1.0 + wrap), 0.0, 1.0);
    return d * d * (3.0 - 2.0 * d);
}

vec3 calcDirLight(DirLight light, vec3 normal) {
    float NdotL = directionalDiffuse(normal, -normalize(light.direction));
    return light.color * light.brightness * NdotL;
}

vec3 calcSpotLight(SpotLight light, vec3 worldPos, vec3 normal) {
    vec3 toLight = light.position - worldPos;
    float dist = length(toLight);
    if (dist > light.distance) return vec3(0.0);
    vec3 L = toLight / dist;
    float NdotL = max(dot(normal, L), 0.0);
    float cosAngle = cos(radians(light.angle));
    float theta = dot(-L, light.direction);
    float spot = smoothstep(cosAngle - 0.05, cosAngle, theta);
    float atten = 1.0 - smoothstep(0.0, light.distance, dist);
    return light.color * light.brightness * NdotL * atten * spot;
}
