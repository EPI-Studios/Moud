uniform float Amount;

void main() {
    vec4 c = sceneColor(vUV);
    vec2 off = (vUV - 0.5) * 2.0 * Amount;
    vec3 split = vec3(sceneColor(vUV + off).r, c.g, sceneColor(vUV - off).b);
    FragColor = vec4(mix(c.rgb, split, Intensity), c.a);
}
