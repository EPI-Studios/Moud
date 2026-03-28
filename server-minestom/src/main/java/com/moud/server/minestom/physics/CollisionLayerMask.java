package com.moud.server.minestom.physics;

import com.moud.core.scene.Node;

public final class CollisionLayerMask {
    public static final String KEY_LAYER = "collision_layer";
    public static final String KEY_MASK = "collision_mask";
    public static final int DEFAULT_LAYER = 1;
    public static final int DEFAULT_MASK = 1;

    private CollisionLayerMask() {
    }

    public static int layer(Node node) {
        return parseBits(node, KEY_LAYER, DEFAULT_LAYER);
    }

    public static int mask(Node node) {
        return parseBits(node, KEY_MASK, DEFAULT_MASK);
    }

    public static int clampBits(int value) {
        if (value <= 0) {
            return 0;
        }
        return value == Integer.MIN_VALUE ? 0 : Math.abs(value);
    }

    private static int parseBits(Node node, String key, int fallback) {
        if (node == null || key == null) {
            return fallback;
        }
        String raw = node.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return clampBits(Integer.parseInt(raw.trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean canCollide(int layerA, int maskA, int layerB, int maskB) {
        return (layerA & maskB) != 0 && (layerB & maskA) != 0;
    }

    public static long packUserData(int layerBits, int maskBits) {
        long layer = clampBits(layerBits) & 0xFFFF_FFFFL;
        long mask = clampBits(maskBits) & 0xFFFF_FFFFL;
        return layer | (mask << 32);
    }

    public static int unpackLayer(long userData) {
        return (int) userData;
    }

    public static int unpackMask(long userData) {
        return (int) (userData >>> 32);
    }
}
