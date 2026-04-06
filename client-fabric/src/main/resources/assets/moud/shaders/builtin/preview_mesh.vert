in vec3 aPos; in vec2 aTexCoord; in vec3 aNormal;
uniform mat4 ModelMat; uniform mat4 ViewMat; uniform mat4 ProjMat; uniform mat4 WorldMat; uniform vec3 CameraPos;
out vec2 texCoord; out vec3 vNormal; out vec3 vWorldPos;
void main() {
    vec4 worldPos = WorldMat * vec4(aPos,1);
    mat3 worldNormalMat = transpose(inverse(mat3(WorldMat)));
    vWorldPos = worldPos.xyz;
    texCoord = aTexCoord;
    vNormal = normalize(worldNormalMat * aNormal);
    gl_Position = ProjMat * ViewMat * vec4(worldPos.xyz - CameraPos, 1.0);
}
