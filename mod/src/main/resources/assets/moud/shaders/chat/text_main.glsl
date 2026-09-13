
void main() {
    if (vClip.z > 0.0 && (vPos.x < vClip.x || vPos.y < vClip.y || vPos.x > vClip.z || vPos.y > vClip.w)) discard;
    vec4 base = texture(Tex, vUv) * vColor;
    vec4 rect = vec4(vParams.y, vParams.z, vParams.w, vExtra.x);
    vec2 glyph = (vUv - rect.xy) / max(rect.zw - rect.xy, vec2(1e-6));
    vec4 result = textColor(base, vPos / ScreenSize, glyph, Time);
    if (result.a <= 0.001) discard;
    FragColor = result;
}
