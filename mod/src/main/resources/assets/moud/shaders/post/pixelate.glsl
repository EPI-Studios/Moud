uniform float PixelSize;

void main() {
    vec2 block = max(PixelSize, 1.0) / ScreenSize;
    vec2 uv = (floor(vUV / block) + 0.5) * block;
    vec4 c = sceneColor(vUV);
    FragColor = vec4(mix(c.rgb, sceneColor(uv).rgb, Intensity), c.a);
}
