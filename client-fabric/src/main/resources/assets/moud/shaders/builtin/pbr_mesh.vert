#version 150

in vec3 aPos;
in vec2 aTexCoord;
in vec3 aNormal;

uniform mat4 ModelMat;
uniform mat4 WorldMat;
uniform mat4 ViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraPos;
uniform vec2 UvScale;
uniform vec2 UvOffset;

out vec2 vTexCoord;
out vec3 vNormal;
out vec3 vWorldPos;
out vec3 vViewPos;
out vec3 vViewNormal;

void main() {
    vec4 worldPos = WorldMat * vec4(aPos, 1.0);
    mat3 worldNormalMat = transpose(inverse(mat3(WorldMat)));
    vWorldPos = worldPos.xyz;
    vTexCoord = aTexCoord * UvScale + UvOffset;
    vNormal = normalize(worldNormalMat * aNormal);

    vec4 viewPos = ViewMat * vec4(worldPos.xyz - CameraPos, 1.0);
    vViewPos = viewPos.xyz;
    vViewNormal = normalize(mat3(ViewMat) * vNormal);

    gl_Position = ProjMat * viewPos;
}
