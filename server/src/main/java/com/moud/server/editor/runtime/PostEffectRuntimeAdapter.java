package com.moud.server.editor.runtime;

import com.moud.network.MoudPackets;
import com.moud.server.rendering.FogUniformRegistry;
import com.moud.server.rendering.PostEffectStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public final class PostEffectRuntimeAdapter implements SceneRuntimeAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostEffectRuntimeAdapter.class);

    private static final String PROP_EFFECT_ID = "effectId";
    private static final String PROP_ID = "id";
    private static final String PROP_UNIFORMS = "uniforms";

    private static final String EFFECT_FOG = "veil:fog";
    private static final String EFFECT_HEIGHT_FOG = "veil:height_fog";

    private final String sceneId;
    private String currentEffectId;

    public PostEffectRuntimeAdapter(String sceneId) {
        this.sceneId = sceneId;
    }

    @Override
    public void create(MoudPackets.SceneObjectSnapshot snapshot) {
        applyState(snapshot);
    }

    @Override
    public void update(MoudPackets.SceneObjectSnapshot snapshot) {
        applyState(snapshot);
    }

    @Override
    public void remove() {
        if (currentEffectId != null) {
            PostEffectStateManager.getInstance().remove(currentEffectId);
            LOGGER.info("Removed post effect '{}' from scene '{}'", currentEffectId, sceneId);
            currentEffectId = null;
        }
    }

    private void applyState(MoudPackets.SceneObjectSnapshot snapshot) {
        Map<String, Object> props = snapshot.properties();

        Optional<String> effectIdOpt = findEffectId(props);
        if (effectIdOpt.isEmpty()) {
            LOGGER.warn("Skipping post_effect for scene '{}': missing 'effectId' or 'id'", sceneId);
            return;
        }

        String effectId = effectIdOpt.get();
        Map<String, Object> uniforms = resolveUniforms(effectId, props.get(PROP_UNIFORMS));

        this.currentEffectId = effectId;
        PostEffectStateManager.getInstance().apply(effectId, uniforms);

        LOGGER.debug("Applied post effect '{}' for scene '{}'", effectId, sceneId);
    }

    private Map<String, Object> resolveUniforms(String effectId, Object rawUniforms) {
        if (rawUniforms instanceof Map<?, ?> rawMap) {
            Map<String, Object> typedMap = convertToStringKeyMap(rawMap);
            return sanitize(effectId, typedMap);
        }
        return getDefaults(effectId);
    }

    private Map<String, Object> getDefaults(String effectId) {
        return switch (effectId.toLowerCase(Locale.ROOT)) {
            case EFFECT_FOG -> FogUniformRegistry.defaultFog();
            case EFFECT_HEIGHT_FOG -> FogUniformRegistry.defaultHeightFog();
            default -> Collections.emptyMap();
        };
    }

    private Map<String, Object> sanitize(String effectId, Map<String, Object> input) {
        return switch (effectId.toLowerCase(Locale.ROOT)) {
            case EFFECT_FOG -> FogUniformRegistry.sanitizeFog(input);
            case EFFECT_HEIGHT_FOG -> FogUniformRegistry.sanitizeHeightFog(input);
            default -> input;
        };
    }

    /**
     * converts a generic map to a Map<String, Object> to ensure type safety.
     */
    private Map<String, Object> convertToStringKeyMap(Map<?, ?> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) return Collections.emptyMap();

        return rawMap.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        Map.Entry::getValue
                ));
    }

    private Optional<String> findEffectId(Map<String, Object> props) {
        return Optional.ofNullable(props.get(PROP_EFFECT_ID))
                .or(() -> Optional.ofNullable(props.get(PROP_ID)))
                .map(Object::toString)
                .filter(s -> !s.isBlank());
    }
}