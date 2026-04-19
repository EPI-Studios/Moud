package com.moud.client.fabric.physics;

import com.moud.client.fabric.scene.ClientSchemaBus;
import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;

public record CapsuleShapeData(float radius, float height) {
    public static final float FALLBACK_RADIUS = 0.3f;
    public static final float FALLBACK_HEIGHT = 1.8f;
    public static final float MIN_RADIUS = 0.01f;

    public static CapsuleShapeData fromNode(SceneSnapshot.NodeSnapshot node) {
        float fallbackRadius = FALLBACK_RADIUS;
        float fallbackHeight = FALLBACK_HEIGHT;
        if (node != null && node.type() != null) {
            fallbackRadius = ParseUtils.parseFloat(
                    ClientSchemaBus.defaultOf(node.type(), "radius", null), FALLBACK_RADIUS);
            fallbackHeight = ParseUtils.parseFloat(
                    ClientSchemaBus.defaultOf(node.type(), "height", null), FALLBACK_HEIGHT);
        }

        float radius = ParseUtils.parseFloat(stringProp(node, "radius"), fallbackRadius);
        radius = Math.max(MIN_RADIUS, radius);

        float height = ParseUtils.parseFloat(stringProp(node, "height"), fallbackHeight);
        height = Math.max(radius * 2.0f, height);

        return new CapsuleShapeData(radius, height);
    }

    public float bottomSphereY() {
        return radius;
    }

    public float topSphereY() {
        return height - radius;
    }

    private static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || node.properties() == null || key == null) return null;
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }

}
