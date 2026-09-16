#version 330 core
in vec2 uv;
out vec4 color;
uniform sampler2D Canvas;
uniform float Opaque;
void main() {
    vec4 c = texture(Canvas, uv);
    color = mix(c, vec4(c.rgb, 1.0), Opaque);
}
