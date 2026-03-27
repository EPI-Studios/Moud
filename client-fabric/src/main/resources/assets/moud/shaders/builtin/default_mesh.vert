in vec3 aPos;
in vec2 aTexCoord;
in vec3 aNormal;

uniform mat4 ModelMat;
uniform mat4 WorldMat;
uniform mat4 ViewMat;
uniform mat4 ProjMat;

out vec2 vTexCoord;
out vec3 vNormal;
out vec3 vWorldPos;

void main() {
    vWorldPos = (WorldMat * vec4(aPos, 1.0)).xyz;
    vTexCoord = aTexCoord;
    vNormal = mat3(WorldMat) * aNormal;
    gl_Position = ProjMat * ViewMat * ModelMat * vec4(aPos, 1.0);
}
