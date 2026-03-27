package com.moud.server.minestom.physics;

import com.github.stephengold.joltjni.GroupFilter;
import com.github.stephengold.joltjni.readonly.ConstCollisionGroup;
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

    public static final class LayerMaskGroupFilter extends GroupFilter {
        @Override
        public boolean canCollide(ConstCollisionGroup a, ConstCollisionGroup b) {
            if (a == null || b == null) {
                return true;
            }
            int layerA = clampBits(a.getGroupId());
            int maskA = clampBits(a.getSubGroupId());
            int layerB = clampBits(b.getGroupId());
            int maskB = clampBits(b.getSubGroupId());
            return (layerA & maskB) != 0 && (layerB & maskA) != 0;
        }
    }
}
