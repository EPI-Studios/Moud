package com.moud.net.protocol;

import java.util.List;

public record RigidBodySnapshot(long serverTick, double timeOfTickMillis, List<RigidBodyEntry> entries) implements Message {
    public RigidBodySnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public MessageType type() {
        return MessageType.RIGID_BODY_SNAPSHOT;
    }
}
