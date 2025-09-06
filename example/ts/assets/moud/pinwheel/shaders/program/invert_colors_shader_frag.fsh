#version 150

uniform sampler2D DiffuseSampler0;
uniform float GameTime;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5);
    vec2 offset = texCoord - center;
    float dist = length(offset);

    float waveTime = GameTime * 2000.0;
    float wave = sin(dist * 20.0 - waveTime) * 0.02;
    vec2 distortedCoord = texCoord + normalize(offset) * wave;

    vec4 color = texture(DiffuseSampler0, distortedCoord);

    float hue = atan(offset.y, offset.x) + waveTime * 0.001;
    vec3 rainbow = vec3(
        sin(hue) * 0.5 + 0.5,
        sin(hue + 2.094) * 0.5 + 0.5,
        sin(hue + 4.188) * 0.5 + 0.5
    );

    float pulseIntensity = (sin(waveTime * 0.005) + 1.0) * 0.3;
    color.rgb = mix(color.rgb, rainbow, pulseIntensity);

    float chromatic = dist * 0.02;
    color.r = texture(DiffuseSampler0, distortedCoord + vec2(chromatic, 0)).r;
    color.b = texture(DiffuseSampler0, distortedCoord - vec2(chromatic, 0)).b;

    fragColor = color;
}