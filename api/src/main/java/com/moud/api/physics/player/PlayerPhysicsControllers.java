package com.moud.api.physics.player;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerPhysicsControllers {
    public static final String DEFAULT_ID = "moud:default";

    private static final ConcurrentMap<String, PlayerPhysicsController> CONTROLLERS = new ConcurrentHashMap<>();

    static {
        register(DEFAULT_ID, new DefaultPlayerPhysicsController());
    }

    private PlayerPhysicsControllers() {
    }

    public static void register(String id, PlayerPhysicsController controller) {
        String normalized = normalizeId(id);
        CONTROLLERS.put(normalized, Objects.requireNonNull(controller, "controller"));
    }

    public static PlayerPhysicsController get(String id) {
        if (id == null || id.isBlank()) {
            return CONTROLLERS.get(DEFAULT_ID);
        }
        PlayerPhysicsController controller = CONTROLLERS.get(normalizeId(id));
        return controller != null ? controller : CONTROLLERS.get(DEFAULT_ID);
    }

    public static boolean has(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return CONTROLLERS.containsKey(normalizeId(id));
    }

    public static boolean unregister(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String normalized = normalizeId(id);
        if (DEFAULT_ID.equals(normalized)) {
            return false;
        }
        return CONTROLLERS.remove(normalized) != null;
    }

    public static void clearNonDefault() {
        CONTROLLERS.keySet().removeIf(key -> !DEFAULT_ID.equals(key));
        CONTROLLERS.computeIfAbsent(DEFAULT_ID, ignored -> new DefaultPlayerPhysicsController());
    }

    private static String normalizeId(String id) {
        String trimmed = Objects.requireNonNull(id, "id").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Controller id cannot be empty");
        }
        return trimmed;
    }
}
