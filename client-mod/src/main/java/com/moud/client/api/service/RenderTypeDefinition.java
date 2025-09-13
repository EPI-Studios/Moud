package com.moud.client.api.service;

import net.minecraft.util.Identifier;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public record RenderTypeDefinition(
        Identifier shader,
        List<Identifier> textures,
        String transparency,
        boolean cull,
        boolean lightmap,
        boolean depthTest
) {

    public RenderTypeDefinition(Value options) {
        this(
                parseShader(options),
                parseTextures(options),
                parseTransparency(options),
                parseBoolean(options, "cull", true),
                parseBoolean(options, "lightmap", false),
                parseBoolean(options, "depthTest", true)
        );
    }

    private static Identifier parseShader(Value options) {
        if (!options.hasMember("shader")) {
            throw new IllegalArgumentException("Shader is required");
        }
        String shaderStr = options.getMember("shader").asString();
        return Identifier.tryParse(shaderStr);
    }

    private static List<Identifier> parseTextures(Value options) {
        List<Identifier> textures = new ArrayList<>();
        if (options.hasMember("textures")) {
            Value texturesValue = options.getMember("textures");
            if (texturesValue.hasArrayElements()) {
                long size = texturesValue.getArraySize();
                for (long i = 0; i < size; i++) {
                    String textureStr = texturesValue.getArrayElement(i).asString();
                    Identifier texture = Identifier.tryParse(textureStr);
                    if (texture != null) {
                        textures.add(texture);
                    }
                }
            }
        }
        return textures;
    }

    private static String parseTransparency(Value options) {
        if (options.hasMember("transparency")) {
            return options.getMember("transparency").asString();
        }
        return "opaque";
    }

    private static boolean parseBoolean(Value options, String key, boolean defaultValue) {
        if (options.hasMember(key)) {
            return options.getMember(key).asBoolean();
        }
        return defaultValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RenderTypeDefinition that = (RenderTypeDefinition) obj;
        return cull == that.cull &&
                lightmap == that.lightmap &&
                depthTest == that.depthTest &&
                Objects.equals(shader, that.shader) &&
                Objects.equals(textures, that.textures) &&
                Objects.equals(transparency, that.transparency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shader, textures, transparency, cull, lightmap, depthTest);
    }
}