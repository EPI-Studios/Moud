package com.moud.server.minestom.collision.source;

import com.moud.core.scene.Node;
import com.moud.core.physics.CollisionGeometry;

public interface CollisionGeometrySource {
    boolean supports(String typeId);

    default boolean canHandle(Node node, String typeId) {
        return supports(typeId);
    }

    CollisionGeometry extract(Node node, String typeId);
}
