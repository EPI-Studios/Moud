uniform float Amount;
uniform float Size;

void main() {
    vec4 c = sceneColor(vUV);
    vec2 cell = floor(vUV * ScreenSize / max(Size, 1.0));
    float n = hash12(cell + floor(Time * 24.0) * 17.0) - 0.5;
    FragColor = vec4(c.rgb + n * Amount * Intensity, c.a);
}
