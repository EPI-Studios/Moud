uniform vec4 Color;
uniform float Thickness;
uniform float Threshold;

void main() {
    vec4 c = sceneColor(vUV);
    vec2 t = Thickness / ScreenSize;
    float d = linearDepth(vUV);
    float far = max(max(linearDepth(vUV + vec2(t.x, 0.0)), linearDepth(vUV - vec2(t.x, 0.0))),
            max(linearDepth(vUV + vec2(0.0, t.y)), linearDepth(vUV - vec2(0.0, t.y))));
    float near = min(min(linearDepth(vUV + vec2(t.x, 0.0)), linearDepth(vUV - vec2(t.x, 0.0))),
            min(linearDepth(vUV + vec2(0.0, t.y)), linearDepth(vUV - vec2(0.0, t.y))));
    float jump = max(far - d, d - near) / max(d, 0.001);
    float edge = step(Threshold, jump);
    FragColor = vec4(mix(c.rgb, Color.rgb, edge * Intensity), c.a);
}
