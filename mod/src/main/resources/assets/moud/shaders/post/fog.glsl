uniform vec4 Color;
uniform float Start;
uniform float Density;
uniform float HeightFalloff;
uniform float BaseHeight;
uniform int Sky;

void main() {
    vec4 c = sceneColor(vUV);
    float amount;
    if (isSky(vUV)) {
        amount = Sky == 1 ? 1.0 : 0.0;
    } else {
        float d = length(viewPosition(vUV));
        amount = 1.0 - exp(-max(d - Start, 0.0) * Density);
        if (HeightFalloff > 0.0) {
            amount *= exp(-max(worldPosition(vUV).y - BaseHeight, 0.0) * HeightFalloff);
        }
    }
    FragColor = vec4(mix(c.rgb, Color.rgb, clamp(amount, 0.0, 1.0) * Intensity), c.a);
}
