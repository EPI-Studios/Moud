uniform float Amount;

void main() {
    vec4 c = sceneColor(vUV);
    vec2 t = 1.0 / ScreenSize;
    vec3 around = sceneColor(vUV + vec2(t.x, 0.0)).rgb + sceneColor(vUV - vec2(t.x, 0.0)).rgb
            + sceneColor(vUV + vec2(0.0, t.y)).rgb + sceneColor(vUV - vec2(0.0, t.y)).rgb;
    vec3 sharp = c.rgb + (c.rgb * 4.0 - around) * Amount;
    FragColor = vec4(mix(c.rgb, max(sharp, 0.0), Intensity), c.a);
}
