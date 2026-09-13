uniform float Strength;
uniform float Samples;

void main() {
    vec4 c = sceneColor(vUV);
    vec3 rel = isSky(vUV) ? normalize(viewPosition(vUV)) * 10000.0 : viewPosition(vUV);
    vec3 world = rel + CameraPosition;
    vec4 prev = PrevViewProj * vec4(world - PrevCameraPosition, 1.0);
    if (prev.w <= 0.0) {
        FragColor = c;
        return;
    }
    vec2 prevUV = prev.xy / prev.w * 0.5 + 0.5;
    vec2 velocity = (vUV - prevUV) * Strength * Intensity;
    int n = int(clamp(Samples, 2.0, 32.0));
    vec3 sum = vec3(0.0);
    for (int i = 0; i < n; i++) {
        float t = float(i) / float(n - 1) - 0.5;
        sum += sceneColor(clamp(vUV + velocity * t, vec2(0.0), vec2(1.0))).rgb;
    }
    FragColor = vec4(sum / float(n), c.a);
}
