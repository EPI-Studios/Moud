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
    PostProcessStage stage;
    Identifier programId;
    ShaderProgram program;
    long assetVersion = Long.MIN_VALUE;
    AssetHash assetHash;

    PostProcessEffect(String id,
                      PostProcessSourceKind sourceKind,
                      String sourceValue,
                      int priority,
                      long registrationOrder) {
        this(id, sourceKind, sourceValue, priority, registrationOrder, PostProcessStage.WORLD);
    }

    PostProcessEffect(String id,
                      PostProcessSourceKind sourceKind,
                      String sourceValue,
                      int priority,
                      long registrationOrder,
                      PostProcessStage stage) {
        this.id = id;
        this.sourceKind = sourceKind;
        this.sourceValue = sourceValue;
        this.priority = priority;
        this.registrationOrder = registrationOrder;
        this.stage = stage == null ? PostProcessStage.WORLD : stage;
    }

    int priority() {
        return priority;
    }

    long registrationOrder() {
        return registrationOrder;
    }
}
