package com.moud.client.rendering;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PostEffectUniformManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostEffectUniformManager.class);

    private final Map<Identifier, Map<String, Object>> uniformsByEffect = new ConcurrentHashMap<>();

    public void updateUniforms(String effectId, Map<String, Object> uniforms) {
        Identifier id = Identifier.tryParse(effectId);
        if (id == null) return;

        if (uniforms == null) {
            uniformsByEffect.remove(id);
            return;
        }
        Map<String, Object> sanitized = PostEffectUniformRegistry.sanitize(effectId, uniforms);
        uniformsByEffect.put(id, new HashMap<>(sanitized));
    }

    public void applyUniforms(Identifier pipelineId, PostPipeline.Context context) {
        Map<String, Object> uniforms = uniformsByEffect.getOrDefault(pipelineId, Collections.emptyMap());
        if (uniforms.isEmpty()) {
            return;
        }

        ShaderProgram shader = context.getShader(pipelineId);
        if (shader == null) {
            shader = context.getShader(Identifier.of(pipelineId.getPath()));
        }

        if (shader == null) {
            LOGGER.warn("[PostEffect] Shader not found for pipeline {}.", pipelineId);
            return;
        }

        shader.bind();

        for (Map.Entry<String, Object> entry : uniforms.entrySet()) {
            try {
                applyUniform(shader, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.error("Failed to apply uniform {} for effect {}", entry.getKey(), pipelineId, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyUniform(ShaderProgram shader, String name, Object value) {
        ShaderUniform uniform = shader.getUniform(name);
        if (uniform == null) {
            return;
        }

        if (value instanceof Number number) {
            if ("FogShape".equals(name) || value instanceof Integer || value instanceof Long) {
                uniform.setInt(number.intValue());
            } else {
                uniform.setFloat(number.floatValue());
            }
        } else if (value instanceof Boolean b) {
            uniform.setInt(b ? 1 : 0);
        } else if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null) map.put(String.valueOf(k), v);
            });
            applyVector(uniform, map);
        } else if (value instanceof JsonObject json) {
            applyVector(uniform, jsonToMap(json));
        }
    }

    private void applyVector(ShaderUniform uniform, Map<String, Object> map) {
        float x = asFloat(map.getOrDefault("x", map.getOrDefault("r", 0f)));
        float y = asFloat(map.getOrDefault("y", map.getOrDefault("g", 0f)));
        float z = asFloat(map.getOrDefault("z", map.getOrDefault("b", 0f)));

        boolean hasW = map.containsKey("w") || map.containsKey("a");

        if (hasW) {
            float w = asFloat(map.getOrDefault("w", map.getOrDefault("a", 1f)));
            uniform.setVector(x, y, z, w);
        } else {
            uniform.setVector(x, y, z);
        }
    }

    private float asFloat(Object raw) {
        if (raw instanceof Number num) {
            return num.floatValue();
        }
        try {
            return raw != null ? Float.parseFloat(raw.toString()) : 0f;
        } catch (Exception e) {
            return 0f;
        }
    }

    private Map<String, Object> jsonToMap(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement elem = entry.getValue();
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) {
                map.put(entry.getKey(), elem.getAsFloat());
            }
        }
        return map;
    }
}