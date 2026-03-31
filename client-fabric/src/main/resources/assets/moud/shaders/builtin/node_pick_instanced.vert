in vec3 aPos;
in vec4 aWorldMat0;
in vec4 aWorldMat1;
in vec4 aWorldMat2;
in vec4 aWorldMat3;
in vec4 aPickColor;

uniform mat4 ViewMat;
uniform mat4 ProjMat;

out vec4 vPickColor;

void main() {
    mat4 world = mat4(aWorldMat0, aWorldMat1, aWorldMat2, aWorldMat3);
    vPickColor = aPickColor;
    gl_Position = ProjMat * ViewMat * world * vec4(aPos, 1.0);
}
