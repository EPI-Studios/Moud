in vec2 vTexCoord;
in vec3 vNormal;
in vec3 vWorldPos;

uniform sampler2D Texture0;
uniform vec4 Tint;

struct PointLight { vec3 position; vec3 color; float brightness; float radius; };
struct DirLight { vec3 direction; vec3 color; float brightness; };

uniform int NumPointLights;
uniform PointLight PointLights[16];
uniform int NumDirLights;
uniform DirLight DirLights[4];

out vec4 fragColor;

vec3 linearToSrgb(vec3 c) {
    vec3 lo = c * 12.92;
    vec3 hi = pow(c, vec3(1.0 / 2.4)) * 1.055 - vec3(0.055);
    bvec3 cutoff = lessThanEqual(c, vec3(0.0031308));
    return vec3(cutoff.x ? lo.x : hi.x, cutoff.y ? lo.y : hi.y, cutoff.z ? lo.z : hi.z);
}

void main() {
    vec4 texColor = texture(Texture0, vTexCoord);
    vec3 baseColor = texColor.rgb * Tint.rgb;
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

    vec3 finalColor = linearToSrgb(baseColor * lighting);
    fragColor = vec4(finalColor, texColor.a * Tint.a);
    if (fragColor.a < 0.01) discard;
}
