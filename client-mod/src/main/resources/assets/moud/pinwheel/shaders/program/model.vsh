#version 150

#include veil:light

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler2;
#ifdef VEIL_NORMAL
uniform mat3 NormalMat;
#endif

out vec2 texCoord;
out vec4 vertexColor;
out vec4 lightColor;
#ifdef VEIL_NORMAL
out vec3 vertexNormal;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;

    // #veil:light_color
    lightColor = minecraft_sample_lightmap(Sampler2, UV2);

#ifdef VEIL_LIGHT_UV
    // #veil:light_uv
    vec2 lightUvCoords = vec2(UV2) / 256.0;
#endif

#ifdef VEIL_NORMAL
    // #veil:normal
    vertexNormal = NormalMat * Normal;
#endif
}
