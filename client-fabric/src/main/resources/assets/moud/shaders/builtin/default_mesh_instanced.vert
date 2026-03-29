in vec3 aPos;
in vec2 aTexCoord;
in vec3 aNormal;

in vec4 aWorldMat0;
in vec4 aWorldMat1;
in vec4 aWorldMat2;
in vec4 aWorldMat3;
in vec4 aTint;

uniform mat4 ViewMat;
uniform mat4 ProjMat;
uniform vec3 CameraPos;

out vec2 vTexCoord;
out vec3 vNormal;
out vec3 vWorldPos;
out vec4 vTint;

void main() {
    mat4 WorldMat = mat4(aWorldMat0, aWorldMat1, aWorldMat2, aWorldMat3);

    vWorldPos = (WorldMat * vec4(aPos, 1.0)).xyz;
    vTexCoord = aTexCoord;
    vNormal = mat3(WorldMat) * aNormal;
    vTint = aTint;
    gl_Position = ProjMat * ViewMat * vec4(vWorldPos - CameraPos, 1.0);
}
