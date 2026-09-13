uniform float Levels;

void main() {
    vec4 c = sceneColor(vUV);
    float steps = max(Levels - 1.0, 1.0);
    vec3 banded = floor(clamp(c.rgb, 0.0, 1.0) * steps + 0.5) / steps;
    FragColor = vec4(mix(c.rgb, banded, Intensity), c.a);
}
