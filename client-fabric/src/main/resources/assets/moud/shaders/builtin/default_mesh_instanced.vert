in vec3 aPos;
in vec2 aTexCoord;
in vec3 aNormal;

in vec4 aModelMat0;
in vec4 aModelMat1;
in vec4 aModelMat2;
in vec4 aModelMat3;
in vec4 aWorldMat0;
in vec4 aWorldMat1;
in vec4 aWorldMat2;
in vec4 aWorldMat3;
in vec4 aTint;

uniform mat4 ViewMat;
uniform mat4 ProjMat;

out vec2 vTexCoord;
out vec3 vNormal;
out vec3 vWorldPos;
out vec4 vTint;

void main() {
    mat4 WorldMat = mat4(aWorldMat0, aWorldMat1, aWorldMat2, aWorldMat3);
    mat4 ModelMat = mat4(aModelMat0, aModelMat1, aModelMat2, aModelMat3);

    vWorldPos = (WorldMat * vec4(aPos, 1.0)).xyz;
    vTexCoord = aTexCoord;
    vNormal = mat3(WorldMat) * aNormal;
    vTint = aTint;
    gl_Position = ProjMat * ViewMat * ModelMat * vec4(aPos, 1.0);
}
