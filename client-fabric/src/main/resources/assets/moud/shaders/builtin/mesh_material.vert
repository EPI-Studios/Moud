in vec3 aPos;
in vec2 aTexCoord;
in vec3 aNormal;

uniform mat4 ModelMat;
uniform mat4 WorldMat;
uniform mat4 ViewMat;
uniform mat4 ProjMat;
uniform vec2 UvScale;
uniform vec2 UvOffset;

out vec2 texCoord;
out vec3 vNormal;
out vec3 vWorldPos;

void main() {
    vWorldPos = (WorldMat * vec4(aPos, 1.0)).xyz;
    texCoord = aTexCoord * UvScale + UvOffset;
    vNormal = mat3(WorldMat) * aNormal;
    gl_Position = ProjMat * ViewMat * ModelMat * vec4(aPos, 1.0);
}
