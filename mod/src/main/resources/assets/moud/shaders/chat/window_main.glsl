
void main() {
    vec2 px = vec2(vUV.x, 1.0 - vUV.y) * ScreenSize;
    vec2 local = (px - Rect.xy) / max(Rect.zw, vec2(1.0));
    if (local.x < 0.0 || local.y < 0.0 || local.x > 1.0 || local.y > 1.0) discard;
    FragColor = windowColor(vUV, local, Time);
}
