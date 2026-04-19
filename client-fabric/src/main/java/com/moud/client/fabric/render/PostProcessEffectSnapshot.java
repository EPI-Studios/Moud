package com.moud.client.fabric.render;

import java.util.Map;

record PostProcessEffectSnapshot(
        String id,
        int priority,
        long registrationOrder,
        PostProcessSourceKind sourceKind,
        String sourceValue,
        Map<String, float[]> floatUniforms,
        Map<String, String> textureUniforms) {
}
