#version 330 core

in vec3 vWorld;
out vec4 FragColor;

uniform vec4 Tint;
uniform vec3 Ambient;
uniform vec3 LightColor;
uniform vec3 LightDirection;
uniform vec3 CameraPosition;

void main() {
    vec3 normal = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
    if (dot(normal, CameraPosition - vWorld) < 0.0) normal = -normal;
    float facing = max(dot(normal, -normalize(LightDirection)), 0.0);
    vec3 lit = Tint.rgb * (Ambient + LightColor * facing);
    FragColor = vec4(lit, Tint.a);
}
