package com.moud.client.fabric.render.material;


import java.util.Map;
import java.util.Objects;

public record MoudShaderUniform(
        String name,
        String glslType,
        boolean exposed,
        Map<String, String> editorHints
) {
    public MoudShaderUniform {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(glslType, "glslType");
        if (editorHints == null) {
            editorHints = Map.of();
        } else {
            editorHints = Map.copyOf(editorHints);
        }
    }

    public String uiLabel() {
        String label = editorHints.get("label");
        if (label != null && !label.isBlank()) {
            return label;
        }
        return name;
    }

    public String uiGroup() {
        String group = editorHints.get("group");
        if (group != null && !group.isBlank()) {
            return group;
        }
        return "Shader Parameters";
    }

    public boolean isSampler() {
        String t = glslType.toLowerCase();
        return t.startsWith("sampler") || t.startsWith("texture");
    }
}

