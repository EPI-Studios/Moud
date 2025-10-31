#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 NormalMat;
uniform sampler2D Sampler2;
uniform int LightmapCoords;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 normal;
out vec4 lightmapColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    normal = normalize(NormalMat * Normal);

    int blockLight = LightmapCoords & 0xFFFF;
    int skyLight = (LightmapCoords >> 16) & 0xFFFF;

    lightmapColor = texelFetch(Sampler2, ivec2(blockLight, skyLight), 0);
}