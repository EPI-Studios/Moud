#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord;
in vec3 normal;
in vec4 lightmapColor;

out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, texCoord);
    if (textureColor.a < 0.1) {
        discard;
    }

    // Directional lighting
    vec3 lightDir = normalize(vec3(0.5, 1.0, 0.2));
    float diffuse = max(dot(normal, lightDir), 0.3);

    // Combine directional lighting with world lightmap
    vec3 lighting = lightmapColor.rgb * diffuse;

    fragColor = textureColor * ColorModulator;
    fragColor.rgb *= lighting;
}