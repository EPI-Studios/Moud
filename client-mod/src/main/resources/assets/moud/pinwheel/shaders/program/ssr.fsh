#version 150

#include veil:space_helper
#include veil:color_utilities

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D VeilDynamicNormal;
uniform sampler2D VeilDynamicDebug;

uniform float SsrStrength;
uniform float SsrMaxDistance;
uniform float SsrStepSize;
uniform float SsrThickness;
uniform float SsrEdgeFade;

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453123);
}

bool traceSsr(vec3 originVS, vec3 dirVS, out vec2 hitUv) {
    float maxDist = SsrMaxDistance > 0.0 ? SsrMaxDistance : 32.0;
    float stepSize = SsrStepSize > 0.0 ? SsrStepSize : 0.2;
    float thickness = SsrThickness > 0.0 ? SsrThickness : 0.002;

    float t = stepSize;
    for (int i = 0; i < 64; i++) {
        vec3 posVS = originVS + dirVS * t;
        vec3 proj = viewToScreenSpace(vec4(posVS, 1.0));
        vec2 uv = proj.xy;

        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return false;

        float sceneZ = texture(DiffuseDepthSampler, uv).r;
        float dz = proj.z - sceneZ;

        if (dz > 0.0 && dz < thickness) {
             hitUv = uv;
             return true;
        }

        t += stepSize;
        if (t > maxDist) return false;
    }
    return false;
}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec4 debug = texture(VeilDynamicDebug, texCoord);

    if (debug.r <= 0.0) {
        fragColor = base;
        gl_FragDepth = depth;
        return;
    }

    float roughness = clamp(debug.r, 0.04, 1.0);
    float metallic = clamp(debug.g, 0.0, 1.0);
    float skyExposure = clamp(debug.a, 0.0, 1.0);

    float strength = SsrStrength > 0.0 ? SsrStrength : 0.35;
    float reflectBase = mix(0.04, 1.0, metallic);
    float reflectWeight = strength * reflectBase * pow(1.0 - roughness, 2.0);

    if (reflectWeight <= 0.001) {
        fragColor = base;
        gl_FragDepth = depth;
        return;
    }

    vec3 normalVS = normalize(texture(VeilDynamicNormal, texCoord).xyz);
    vec3 viewPos = screenToViewSpace(texCoord, depth).xyz;
    vec3 viewDir = normalize(viewPos);
    vec3 R = reflect(viewDir, normalVS);

    float jitter = (hash12(gl_FragCoord.xy) - 0.5) * 0.02;
    vec3 dir = normalize(R + vec3(jitter, -jitter, 0.0));

    vec2 hitUv;
    if (!traceSsr(viewPos, dir, hitUv)) {
        fragColor = base;
        gl_FragDepth = depth;
        return;
    }

    float fadeWidth = SsrEdgeFade > 0.0 ? SsrEdgeFade : 0.12;
    float edgeDist = min(min(hitUv.x, 1.0 - hitUv.x), min(hitUv.y, 1.0 - hitUv.y));
    float fade = clamp(edgeDist / max(fadeWidth, 1e-5), 0.0, 1.0);

    vec3 hitColor = texture(DiffuseSampler0, hitUv).rgb;
    float brightness = clamp(luminance(base.rgb) * 1.5, 0.0, 1.0);
    float mask = max(0.1, max(skyExposure, brightness));
    float mixFactor = clamp(reflectWeight * fade * mask, 0.0, 1.0);

    fragColor = vec4(mix(base.rgb, hitColor, mixFactor), base.a);
    gl_FragDepth = depth;
}