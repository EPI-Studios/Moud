uniform float Radius;
uniform float Softness;
uniform vec4 Color;

void main() {
    vec4 c = sceneColor(vUV);
    float dist = length((vUV - 0.5) * 2.0) / 1.41421356;
    float v = smoothstep(Radius, Radius + Softness, dist);
    FragColor = vec4(mix(c.rgb, Color.rgb, v * Intensity), c.a);
}
