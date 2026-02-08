package com.moud.server.camera;

import com.moud.server.logging.MoudLogger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraRegistry {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(CameraRegistry.class);
    private static final CameraRegistry INSTANCE = new CameraRegistry();

    public static CameraRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<String, SceneCamera> byId = new ConcurrentHashMap<>();
    private final Map<String, SceneCamera> byLabel = new ConcurrentHashMap<>();

    private CameraRegistry() {
    }

    public void upsert(SceneCamera camera) {
        if (camera == null) {
            return;
        }
        byId.put(camera.id(), camera);
        if (camera.label() != null && !camera.label().isBlank()) {
            byLabel.put(camera.label(), camera);
        }
        LOGGER.debug("Camera upserted: id={}, label={}", camera.id(), camera.label());
    }

    public SceneCamera getById(String id) {
        return id == null ? null : byId.get(id);
    }

    public SceneCamera getByLabel(String label) {
        return label == null ? null : byLabel.get(label);
    }

    public Collection<SceneCamera> all() {
        return byId.values();
    }
}
