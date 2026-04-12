package com.moud.server.minestom.collision.source;

import com.moud.core.scene.Node;
import com.moud.core.physics.CollisionGeometry;

import java.util.List;

public final class GeometrySourceRegistry {
    private final List<CollisionGeometrySource> sources;

    public GeometrySourceRegistry(List<CollisionGeometrySource> sources) {
        this.sources = List.copyOf(sources);
    }

    public boolean supports(String typeId) {
        for (CollisionGeometrySource source : sources) {
            if (source.supports(typeId)) {
                return true;
            }
        }
        return false;
    }

    public CollisionGeometry extract(Node node, String typeId) {
        for (CollisionGeometrySource source : sources) {
            if (source.supports(typeId)) {
                return source.extract(node, typeId);
            }
        }
        return CollisionGeometry.EMPTY;
    }
}
