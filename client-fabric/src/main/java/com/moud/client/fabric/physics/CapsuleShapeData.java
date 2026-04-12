package com.moud.client.fabric.physics;

import com.moud.core.util.ParseUtils;
import com.moud.net.protocol.SceneSnapshot;

public record CapsuleShapeData(float radius, float height) {
    public static final float MIN_RADIUS = 0.05f;
    public static CapsuleShapeData fromNode(SceneSnapshot.NodeSnapshot node) {
        float radius = ParseUtils.parseFloat(stringProp(node, "radius"), 0.5f);
        radius = Math.max(MIN_RADIUS, radius);

        float height = ParseUtils.parseFloat(stringProp(node, "height"), 2.0f);
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
