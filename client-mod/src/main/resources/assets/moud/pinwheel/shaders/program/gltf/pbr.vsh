#version 150

#include veil:light
#include veil:fog

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 NormalMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;
out vec2 lightMapUV;
out vec2 materialMR;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;

    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    texCoord0 = UV0;
    viewPos = view.xyz;

    viewNormal = normalize(NormalMat * Normal);
    lightMapUV = clamp(vec2(UV2) / 240.0, 0.0, 1.0);
    materialMR = clamp(vec2(UV1) / 15.0, 0.0, 1.0);

    vertexColor = Color;
}
