package com.moud.client.fabric.render;

import com.moud.core.assets.AssetHash;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

final class PostProcessEffect {
    final String id;
    final long registrationOrder;
    final Map<String, float[]> floatUniforms = new LinkedHashMap<>();
    final Map<String, String> textureUniforms = new LinkedHashMap<>();
    int priority;
    PostProcessSourceKind sourceKind;
    String sourceValue;
    Identifier programId;
    ShaderProgram program;
    long assetVersion = Long.MIN_VALUE;
    AssetHash assetHash;

    PostProcessEffect(String id,
                      PostProcessSourceKind sourceKind,
                      String sourceValue,
                      int priority,
                      long registrationOrder) {
        this.id = id;
        this.sourceKind = sourceKind;
        this.sourceValue = sourceValue;
        this.priority = priority;
        this.registrationOrder = registrationOrder;
    }

    int priority() {
        return priority;
    }

    long registrationOrder() {
        return registrationOrder;
    }
}
