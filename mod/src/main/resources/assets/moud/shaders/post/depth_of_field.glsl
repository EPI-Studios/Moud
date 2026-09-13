uniform float FocusDistance;
uniform float FocusRange;
uniform float Falloff;
uniform float Size;

float blurAt(vec2 uv) {
    float d = linearDepth(uv);
    return clamp((abs(d - FocusDistance) - FocusRange) / max(Falloff, 0.01), 0.0, 1.0);
}

void main() {
    vec4 c = sceneColor(vUV);
    float coc = blurAt(vUV) * Size * Intensity;
    if (coc < 0.5) {
        FragColor = c;
        return;
    }
    vec2 texel = 1.0 / ScreenSize;
    vec3 sum = c.rgb;
    float total = 1.0;
    const int TAPS = 24;
    for (int i = 0; i < TAPS; i++) {
        float r = sqrt((float(i) + 0.5) / float(TAPS));
        float a = float(i) * 2.39996323;
        vec2 uv = vUV + vec2(cos(a), sin(a)) * r * coc * texel;
        // a sharp thing in front does not bleed into the blur behind it
        float w = blurAt(uv) > 0.0 || linearDepth(uv) > linearDepth(vUV) ? 1.0 : 0.2;
        sum += sceneColor(uv).rgb * w;
        total += w;
    }
    FragColor = vec4(sum / total, c.a);
}
