uniform float Size;

void main() {
    vec4 c = sceneColor(vUV);
    vec2 texel = 1.0 / ScreenSize;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int x = -3; x <= 3; x++) {
        for (int y = -3; y <= 3; y++) {
            vec2 o = vec2(x, y) / 3.0;
            float w = exp(-dot(o, o) * 2.0);
            sum += sceneColor(vUV + o * Size * texel).rgb * w;
            total += w;
        }
    }
    FragColor = vec4(mix(c.rgb, sum / total, Intensity), c.a);
}
