package com.moud.server.editor.runtime;

import com.moud.server.MoudEngine;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class SceneRuntimeFactory {
    private static final Map<String, BiFunction<String, MoudEngine, SceneRuntimeAdapter>> REGISTRY =
            new ConcurrentHashMap<>();

    static {
        register("model", (sceneId, engine) -> new ModelRuntimeAdapter(sceneId));
        register("display", (sceneId, engine) -> new DisplayRuntimeAdapter(sceneId));
        register("light", (sceneId, engine) -> new LightRuntimeAdapter(sceneId));
        register("terrain", (sceneId, engine) -> new TerrainRuntimeAdapter(sceneId));
        register("player_model", (sceneId, engine) -> new PlayerModelRuntimeAdapter(sceneId));
        register("camera", (sceneId, engine) -> new CameraRuntimeAdapter());
        register("particle_emitter", (sceneId, engine) -> new ParticleEmitterRuntimeAdapter());
        register("post_effect", (sceneId, engine) -> new PostEffectRuntimeAdapter(sceneId));
        register("zone", (sceneId, engine) -> new ZoneRuntimeAdapter());
    }

    private SceneRuntimeFactory() {
    }

    public static void register(String type, BiFunction<String, MoudEngine, SceneRuntimeAdapter> provider) {
        Objects.requireNonNull(provider, "provider");
        String normalized = normalizeType(type);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("type cannot be null/blank");
        }
        REGISTRY.put(normalized, provider);
    }

    public static SceneRuntimeAdapter create(String sceneId, String objectType) {
        String normalized = normalizeType(objectType);
        if (normalized.isEmpty()) {
            return null;
        }

        BiFunction<String, MoudEngine, SceneRuntimeAdapter> provider = REGISTRY.get(normalized);
        if (provider == null) {
            return null;
        }

        return provider.apply(sceneId, MoudEngine.getInstance());
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
