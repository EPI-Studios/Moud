package com.moud.client.fabric.player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBodyScale {
    private static final ConcurrentHashMap<String, float[]> wholeByUuid = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, Float>> partByUuid = new ConcurrentHashMap<>();

    private PlayerBodyScale() {
    }

    public static void setWhole(String uuid, float x, float y, float z) {
        if (uuid == null || uuid.isBlank()) return;
        wholeByUuid.put(uuid, new float[]{x, y, z});
    }

    public static void clearWhole(String uuid) {
        if (uuid == null) return;
        wholeByUuid.remove(uuid);
    }

    public static float[] getWhole(String uuid) {
        return uuid == null ? null : wholeByUuid.get(uuid);
    }

    public static void setPart(String uuid, String part, float scale) {
        if (uuid == null || uuid.isBlank() || part == null || part.isBlank()) return;
        partByUuid.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(part.trim().toLowerCase(), scale);
    }

    public static void clearPart(String uuid, String part) {
        if (uuid == null || part == null) return;
        Map<String, Float> m = partByUuid.get(uuid);
        if (m != null) m.remove(part.trim().toLowerCase());
    }

    public static Map<String, Float> partsOf(String uuid) {
        if (uuid == null) return Map.of();
        Map<String, Float> m = partByUuid.get(uuid);
        return m == null ? Map.of() : new HashMap<>(m);
    }

    public static float partScale(String uuid, String part) {
        if (uuid == null || part == null) return 1f;
        Map<String, Float> m = partByUuid.get(uuid);
        if (m == null) return 1f;
        Float v = m.get(part);
        return v == null ? 1f : v;
    }

    public static void clearPlayer(String uuid) {
        if (uuid == null) return;
        wholeByUuid.remove(uuid);
        partByUuid.remove(uuid);
    }

    public static void clear() {
        wholeByUuid.clear();
        partByUuid.clear();
    }
}
