in vec3 aPos;

uniform mat4 ModelMat;
uniform mat4 ViewMat;
uniform mat4 ProjMat;

out vec4 vClipPos;

void main() {
    gl_Position = ProjMat * ViewMat * ModelMat * vec4(aPos, 1.0);
    vClipPos = gl_Position;
}
