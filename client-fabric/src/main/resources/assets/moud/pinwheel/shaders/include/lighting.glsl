// heads up: this file gets hoisted before the post-process header, so the
// light structs don't exist yet here. everything takes plain args because of that.
// write the loop yourself in main() and call these per light.
// there's an example at the bottom if you need it.
//
// #include moud:lighting

const float MOUD_LIGHTING_PI = 3.14159265358979;

// controls how light scatters through the volume depending on angle
// g > 0 = forward scattering (bright on the side facing the light)
// g = 0 = scatters the same in every direction
// g < 0 = back scattering (brighter when looking away from the light)
float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float denom = pow(max(1.0 + g2 - 2.0 * g * cosTheta, 1e-4), 1.5);
    return (1.0 - g2) / (4.0 * MOUD_LIGHTING_PI * denom);
}

// scatters evenly in all directions, no preference
float phaseUniform() {
    return 1.0 / (4.0 * MOUD_LIGHTING_PI);
}

// point light falloff, 1 at the source, 0 at the edge of radius
float attenuationPoint(float dist, float radius) {
    if (dist >= radius || radius <= 1e-4) return 0.0;
    float x = dist / radius;
    return (1.0 - x) * (1.0 - x);
}

// spotlight falloff, fades toward the cone edge and cuts off outside it
// spotDir is the direction the light is pointing
float attenuationSpot(vec3 p, vec3 spotPos, vec3 spotDir, float angleDeg, float distMax) {
    vec3  toSample       = p - spotPos;
    float distAlongLight = length(toSample);
    if (distAlongLight >= distMax || distMax <= 1e-4) return 0.0;

    vec3  lightDir     = toSample / max(distAlongLight, 1e-4);
    float cosTheta     = dot(lightDir, normalize(spotDir));
    float cosHalfAngle = cos(radians(max(angleDeg, 0.1) * 0.5));
    if (cosTheta < cosHalfAngle) return 0.0;

    float coneEdge = clamp((1.0 - cosHalfAngle / max(cosTheta, 1e-4)) * 4.0, 0.0, 1.0);

    float distFalloff = 1.0 - distAlongLight / distMax;
    distFalloff *= distFalloff;

    return coneEdge * distFalloff;
}

// sun / sky / any light with no position
vec3 scatterDirectional(vec3 lightDir, vec3 lightColor, float brightness,
vec3 rayDir, float anisotropy) {
    vec3  dir   = normalize(lightDir);
    float phase = phaseHG(dot(-rayDir, -dir), anisotropy);
    return lightColor * brightness * phase;
}

// point light, fades with distance
vec3 scatterPoint(vec3 lightPos, vec3 lightColor, float brightness, float radius,
vec3 p, vec3 rayDir, float anisotropy) {
    vec3  toLight = lightPos - p;
    float dist    = length(toLight);
    float atten   = attenuationPoint(dist, radius);
    if (atten <= 0.0) return vec3(0.0);

    vec3  toL   = toLight / max(dist, 1e-4);
    float phase = phaseHG(dot(-rayDir, toL), anisotropy);
    return lightColor * brightness * phase * atten;
}

// spotlight, cone shaped
vec3 scatterSpot(vec3 lightPos, vec3 lightDir, vec3 lightColor, float brightness,
float angleDeg, float distMax,
vec3 p, vec3 rayDir, float anisotropy) {
    float atten = attenuationSpot(p, lightPos, lightDir, angleDeg, distMax);
    if (atten <= 0.0) return vec3(0.0);

    vec3  toSample = normalize(p - lightPos);
    float phase    = phaseHG(dot(-rayDir, -toSample), anisotropy);
    return lightColor * brightness * phase * atten;
}

// ---- example ----
//
//   vec3 inscatter = vec3(0.0);
//   for (int i = 0; i < NumPointLights; i++) {
//       inscatter += scatterPoint(
//           PointLights[i].position,
//           PointLights[i].color,
//           PointLights[i].brightness,
//           PointLights[i].radius,
//           p, rayDir, 0.6);
//   }
//   for (int i = 0; i < NumSpotLights; i++) {
//       inscatter += scatterSpot(
//           SpotLights[i].position, SpotLights[i].direction,
//           SpotLights[i].color, SpotLights[i].brightness,
//           SpotLights[i].angle, SpotLights[i].distance,
//           p, rayDir, 0.6);
//   }
//   for (int i = 0; i < NumDirLights; i++) {
//       inscatter += scatterDirectional(
//           DirLights[i].direction,
//           DirLights[i].color,
//           DirLights[i].brightness,
//           rayDir, 0.6);
//   }