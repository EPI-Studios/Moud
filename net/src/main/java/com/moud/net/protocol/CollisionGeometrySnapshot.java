package com.moud.net.protocol;

import com.moud.core.physics.CollisionGeometry;
import java.util.List;

public record CollisionGeometrySnapshot(long nodeId, List<CollisionGeometry> hulls) implements Message {
    @Override
    public MessageType type() {
        return MessageType.COLLISION_GEOMETRY;
    }
}
