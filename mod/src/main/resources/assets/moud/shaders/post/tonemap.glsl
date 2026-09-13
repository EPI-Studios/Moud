uniform int Tonemapper;
uniform float Exposure;

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

vec3 agx(vec3 color) {
    const mat3 inset = mat3(0.842479, 0.0423282, 0.0423756, 0.0784336, 0.878468, 0.0784336,
            0.0792237, 0.0791661, 0.879142);
    const mat3 outset = mat3(1.19688, -0.0528968, -0.0529716, -0.0980209, 1.15190, -0.0980435,
            -0.0990297, -0.0989612, 1.15107);
    color = inset * max(color, 1e-10);
    color = clamp((log2(color) + 12.47393) / 16.5, 0.0, 1.0);
    vec3 x2 = color * color;
    vec3 x4 = x2 * x2;
    color = 15.5 * x4 * x2 - 40.14 * x4 * color + 31.96 * x4 - 6.868 * x2 * color
            + 0.4298 * x2 + 0.1191 * color - 0.00232;
    color = outset * color;
    return clamp(pow(max(color, 0.0), vec3(2.2)), 0.0, 1.0);
}

vec3 uncharted(vec3 x) {
    const float A = 0.15, B = 0.50, C = 0.10, D = 0.20, E = 0.02, F = 0.30;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

void main() {
    vec4 c = sceneColor(vUV);
    vec3 x = c.rgb * Exposure;
    vec3 mapped = x;
    if (Tonemapper == 0) mapped = aces(x);
    else if (Tonemapper == 1) mapped = agx(x);
    else if (Tonemapper == 2) mapped = x / (1.0 + x);
    else if (Tonemapper == 3) mapped = uncharted(x * 2.0) / uncharted(vec3(11.2));
    FragColor = vec4(mix(c.rgb, mapped, Intensity), c.a);
}
