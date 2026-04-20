in vec3 aPos;

uniform mat4 ModelMat;
uniform mat4 LightViewProj;

void main() {
    gl_Position = LightViewProj * ModelMat * vec4(aPos, 1.0);
}
