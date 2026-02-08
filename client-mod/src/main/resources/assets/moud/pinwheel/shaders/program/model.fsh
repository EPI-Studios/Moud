#version 150

#include veil:light

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec4 vertexColor;
in vec4 lightColor;
#ifdef VEIL_NORMAL
in vec3 vertexNormal;
#endif

out vec4 fragColor;

void main() {
    // #veil:albedo
    vec4 albedo = texture(Sampler0, texCoord) * vertexColor * ColorModulator;
    if (albedo.a < 0.1) {
        discard;
    }

    vec4 litColor = vec4(albedo.rgb * lightColor.rgb, albedo.a);

#ifdef VEIL_NORMAL
    vec3 normalDir = normalize(vertexNormal);
#endif

    fragColor = litColor;
}
